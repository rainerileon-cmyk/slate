package dev.fallingcloud.slate.core.net.blob;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of a blob transfer: validate, count, forward. A relay, not a store: chunks are forwarded as
 * they arrive and the server never assembles the blob. Per-transfer bookkeeping (declared size, bytes
 * seen, next index) enforces every limit against what actually crosses the wire. A transfer that lies
 * about its size, skips an index, floods chunks or exceeds the cap is dropped and its End never forwarded;
 * receivers time incomplete transfers out on their own. Server main thread only.
 */
public final class BlobRelay {

    /** Limits; the Multiplayer module exposes them in its server config. */
    public static volatile int maxBlobBytes = 3 * 1024 * 1024;
    public static volatile int maxChunksPerSecond = 80;
    public static volatile int maxConcurrentTransfers = 3;

    private static final class Transfer {
        final BlobPayloads.Start start;
        final List<ServerPlayer> recipients;
        int bytesSeen, nextIndex;
        Transfer(final BlobPayloads.Start start, final List<ServerPlayer> recipients) { this.start = start; this.recipients = recipients; }
    }

    private static final Map<UUID, Map<String, Transfer>> TRANSFERS = new HashMap<>();
    private static final Map<UUID, long[]> RATE = new HashMap<>();
    private static final Map<String, BlobRouter> ROUTERS = new HashMap<>();

    /** Register a router for a target prefix ({@code "p:"}, {@code "g:"}, ...). {@code ""} = broadcast. */
    public static void registerRouter(final String prefix, final BlobRouter router) {
        ROUTERS.put(prefix, router);
    }

    private static List<ServerPlayer> route(final ServerPlayer sender, final BlobPayloads.Start start) {
        final String t = start.target() == null ? "" : start.target();
        if (t.isEmpty()) {
            final List<ServerPlayer> all = new ArrayList<>();
            for (final ServerPlayer p : sender.server.getPlayerList().getPlayers()) {
                if (!p.getUUID().equals(sender.getUUID())) all.add(p);
            }
            return all;
        }
        final int colon = t.indexOf(':');
        final BlobRouter r = ROUTERS.get(colon < 0 ? t : t.substring(0, colon + 1));
        return r == null ? List.of() : r.recipients(sender, start);
    }

    public static void onStart(final BlobPayloads.Start start, final ServerPlayer sender) {
        if (!BlobPayloads.validId(start.id()) || start.totalBytes() <= 0 || start.totalBytes() > maxBlobBytes) {
            Slate.LOGGER.warn("[Slate] rejected blob {} from {}: {} bytes (cap {})", start.id(), sender.getGameProfile().getName(), start.totalBytes(), maxBlobBytes);
            return;
        }
        final Map<String, Transfer> mine = TRANSFERS.computeIfAbsent(sender.getUUID(), k -> new HashMap<>());
        if (mine.size() >= maxConcurrentTransfers) {
            Slate.LOGGER.warn("[Slate] {} exceeded concurrent blob cap", sender.getGameProfile().getName());
            return;
        }
        final List<ServerPlayer> recipients = route(sender, start);
        final Transfer t = new Transfer(start, recipients);
        mine.put(start.id(), t);
        // Sender name from the server's own profile, never from the client.
        forward(t, start.withSender(sender.getGameProfile().getName()));
    }

    public static void onChunk(final BlobPayloads.Chunk chunk, final ServerPlayer sender) {
        final Map<String, Transfer> mine = TRANSFERS.get(sender.getUUID());
        final Transfer t = mine == null ? null : mine.get(chunk.id());
        if (t == null) return;
        final long now = System.currentTimeMillis();
        final long[] window = RATE.computeIfAbsent(sender.getUUID(), k -> new long[] { now, 0 });
        if (now - window[0] > 1000) { window[0] = now; window[1] = 0; }
        if (++window[1] > maxChunksPerSecond) { drop(sender, chunk.id(), "chunk rate"); return; }
        if (chunk.index() != t.nextIndex || chunk.data().length == 0 || t.bytesSeen + chunk.data().length > t.start.totalBytes()) {
            drop(sender, chunk.id(), "sequence/size violation");
            return;
        }
        t.nextIndex++;
        t.bytesSeen += chunk.data().length;
        forward(t, chunk);
    }

    public static void onEnd(final BlobPayloads.End end, final ServerPlayer sender) {
        final Map<String, Transfer> mine = TRANSFERS.get(sender.getUUID());
        final Transfer t = mine == null ? null : mine.remove(end.id());
        if (t == null) return;
        if (t.bytesSeen != t.start.totalBytes()) {
            Slate.LOGGER.warn("[Slate] blob {} from {} ended at {}/{} bytes - not forwarded", end.id(), sender.getGameProfile().getName(), t.bytesSeen, t.start.totalBytes());
            return;
        }
        forward(t, end);
    }

    public static void onDisconnect(final UUID player) {
        TRANSFERS.remove(player);
        RATE.remove(player);
    }

    private static void drop(final ServerPlayer sender, final String id, final String why) {
        final Map<String, Transfer> mine = TRANSFERS.get(sender.getUUID());
        if (mine != null) mine.remove(id);
        Slate.LOGGER.warn("[Slate] dropped blob {} from {}: {}", id, sender.getGameProfile().getName(), why);
    }

    private static void forward(final Transfer t, final CustomPacketPayload payload) {
        final SlateNetwork net = SlateNetwork.get();
        for (final ServerPlayer p : t.recipients) {
            if (p.hasDisconnected()) continue;
            net.sendToPlayer(p, payload);
        }
    }

    private BlobRelay() {}
}
