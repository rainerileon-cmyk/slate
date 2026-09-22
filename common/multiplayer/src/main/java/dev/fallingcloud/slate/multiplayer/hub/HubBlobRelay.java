package dev.fallingcloud.slate.multiplayer.hub;

import dev.fallingcloud.slate.core.net.blob.BlobPayloads;
import dev.fallingcloud.slate.multiplayer.MultiplayerServerConfig;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The hub's own blob relay for social targets ({@code p:}, {@code g:}, {@code s:}), independent of the
 * transport: blobs arrive as passthrough frames from either session kind and are forwarded to every
 * recipient session (raw payloads to players on this server, passthrough frames to TCP clients). Like
 * Core's relay it forwards chunks as they arrive and enforces every limit against the bytes actually
 * seen; unlike it, DM/group attachments are additionally assembled and stored so history can show them
 * later ({@link SocialMessage.MediaRequest}). Server main thread only.
 */
final class HubBlobRelay {

    private static final class Transfer {
        final BlobPayloads.Start start;
        final List<HubSession> recipients;
        final boolean frame;
        final ByteArrayOutputStream store;
        int bytesSeen, nextIndex;
        final long startedMs = System.currentTimeMillis();

        Transfer(final BlobPayloads.Start start, final List<HubSession> recipients, final boolean frame, final boolean keep) {
            this.start = start;
            this.recipients = recipients;
            this.frame = frame;
            this.store = keep ? new ByteArrayOutputStream(start.totalBytes()) : null;
        }
    }

    private final SocialHub hub;
    private final MultiplayerServerConfig cfg;
    private final Map<UUID, Map<String, Transfer>> transfers = new HashMap<>();
    private final Map<UUID, RateBucket> bandwidth = new HashMap<>();
    private long lastWarnMs;

    HubBlobRelay(final SocialHub hub, final MultiplayerServerConfig cfg) {
        this.hub = hub;
        this.cfg = cfg;
    }

    /** A blob payload arrived from a session (already unwrapped from its passthrough frame). */
    void onPayload(final HubSession sender, final CustomPacketPayload payload) {
        if (payload instanceof BlobPayloads.Start s) onStart(sender, s);
        else if (payload instanceof BlobPayloads.Chunk c) onChunk(sender, c);
        else if (payload instanceof BlobPayloads.End e) onEnd(sender, e);
    }

    private void onStart(final HubSession sender, final BlobPayloads.Start start) {
        if (!BlobPayloads.validId(start.id()) || start.totalBytes() <= 0) return;
        final boolean frame = "frame".equals(start.kind());
        final int cap = (frame ? cfg.frameMaxKb : cfg.mediaMaxKb) * 1024;
        if (start.totalBytes() > cap) {
            warn("rejected blob " + start.id() + " from " + sender.name() + ": " + start.totalBytes() + " bytes (cap " + cap + ")");
            return;
        }
        final Map<String, Transfer> mine = transfers.computeIfAbsent(sender.uuid(), k -> new HashMap<>());
        if (mine.size() >= 4) {
            warn(sender.name() + " exceeded concurrent blob cap");
            return;
        }
        final List<HubSession> recipients = hub.blobRecipients(sender, start.target());
        final boolean keep = cfg.storeMedia && !frame && isStorableKind(start.kind()) && (start.target().startsWith("p:") || start.target().startsWith("g:"));
        final BlobPayloads.Start stamped = start.withSender(sender.name());
        final Transfer t = new Transfer(stamped, recipients, frame, keep);
        mine.put(start.id(), t);
        forward(t, stamped);
    }

    private void onChunk(final HubSession sender, final BlobPayloads.Chunk chunk) {
        final Map<String, Transfer> mine = transfers.get(sender.uuid());
        final Transfer t = mine == null ? null : mine.get(chunk.id());
        if (t == null) return;
        if (chunk.index() != t.nextIndex || chunk.data().length == 0 || t.bytesSeen + chunk.data().length > t.start.totalBytes()) {
            mine.remove(chunk.id());
            warn("dropped blob " + chunk.id() + " from " + sender.name() + ": sequence/size violation");
            return;
        }
        final RateBucket bucket = bandwidth.computeIfAbsent(sender.uuid(), k -> new RateBucket(cfg.blobKbPerSecond * 1024.0, cfg.blobKbPerSecond * 2048.0));
        if (!bucket.tryAcquire(chunk.data().length)) {
            mine.remove(chunk.id());
            if (!t.frame) warn("dropped blob " + chunk.id() + " from " + sender.name() + ": bandwidth cap");
            return;
        }
        t.nextIndex++;
        t.bytesSeen += chunk.data().length;
        if (t.store != null) t.store.writeBytes(chunk.data());
        forward(t, chunk);
    }

    private void onEnd(final HubSession sender, final BlobPayloads.End end) {
        final Map<String, Transfer> mine = transfers.get(sender.uuid());
        final Transfer t = mine == null ? null : mine.remove(end.id());
        if (t == null) return;
        if (t.bytesSeen != t.start.totalBytes()) {
            warn("blob " + end.id() + " from " + sender.name() + " ended at " + t.bytesSeen + "/" + t.start.totalBytes() + " bytes - not forwarded");
            return;
        }
        forward(t, end);
        if (t.store != null) {
            final HubStore.MediaMeta meta = new HubStore.MediaMeta();
            meta.id = t.start.id();
            meta.kind = t.start.kind();
            meta.meta = t.start.meta();
            meta.sender = sender.uuid().toString();
            meta.senderName = sender.name();
            meta.target = t.start.target();
            meta.totalBytes = t.start.totalBytes();
            meta.durationMs = t.start.durationMs();
            meta.atMs = System.currentTimeMillis();
            hub.store().storeMedia(meta, t.store.toByteArray(), cfg.mediaStoreMb * 1024L * 1024L);
        }
    }

    private void forward(final Transfer t, final CustomPacketPayload payload) {
        for (final HubSession r : t.recipients) {
            if (!r.isOpen()) continue;
            // Frames are disposable: never let a slow viewer pile up memory.
            if (t.frame && !r.canTakeMore()) continue;
            r.sendBlob(payload);
        }
    }

    /** Sends a stored attachment to one session as a complete transfer addressed to them. */
    void sendStored(final HubSession to, final HubStore.MediaMeta meta, final byte[] bytes) {
        final BlobPayloads.Start start = new BlobPayloads.Start(meta.id, meta.kind, bytes.length, meta.durationMs, meta.senderName, "p:" + to.uuid(), meta.meta);
        to.sendBlob(start);
        for (int at = 0, index = 0; at < bytes.length; index++) {
            final int n = Math.min(BlobPayloads.CHUNK_BYTES, bytes.length - at);
            final byte[] slice = new byte[n];
            System.arraycopy(bytes, at, slice, 0, n);
            to.sendBlob(new BlobPayloads.Chunk(meta.id, index, slice));
            at += n;
        }
        to.sendBlob(new BlobPayloads.End(meta.id));
    }

    void onSessionClosed(final UUID uuid) {
        transfers.remove(uuid);
        bandwidth.remove(uuid);
    }

    /** Expire transfers that stalled (a sender that vanished mid-blob). */
    void tick(final long now) {
        for (final Map<String, Transfer> mine : transfers.values()) {
            mine.values().removeIf(t -> now - t.startedMs > 60_000);
        }
        transfers.values().removeIf(Map::isEmpty);
    }

    private static boolean isStorableKind(final String kind) {
        return "image".equals(kind) || "gif".equals(kind) || "voice".equals(kind);
    }

    private void warn(final String msg) {
        final long now = System.currentTimeMillis();
        if (now - lastWarnMs < 2000) return;
        lastWarnMs = now;
        SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] {}", msg);
    }
}
