package dev.fallingcloud.slate.core.net.blob;

import dev.fallingcloud.slate.core.net.SlateNetwork;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client side: sends a blob as a paced stream of chunks (3 per tick = 60/s, ~1.4 MB/s, inside the relay's
 * default limit) and calls back with the id when the last chunk left. Payloads go through the current
 * {@link Link}: by default the server's payload channel; the Multiplayer module swaps in its hub link
 * while connected to a hub from the title screen. Client main thread only.
 */
public final class BlobSender {

    /** Where payloads go. */
    public interface Link {
        boolean ready();
        void send(CustomPacketPayload payload);
    }

    private static final Link SERVER_LINK = new Link() {
        @Override public boolean ready() { return Minecraft.getInstance().getConnection() != null && SlateNetwork.get().serverHasChannel(BlobPayloads.Start.TYPE); }
        @Override public void send(final CustomPacketPayload payload) { SlateNetwork.get().sendToServer(payload); }
    };

    private record Pending(CustomPacketPayload payload, String completeId) {}

    private static final int CHUNKS_PER_TICK = 3;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Deque<Pending> QUEUE = new ArrayDeque<>();
    private static volatile Link link = SERVER_LINK;
    private static Consumer<String> onComplete = id -> {};
    private static final java.util.Map<String, Consumer<String>> COMPLETIONS = new java.util.HashMap<>();

    public static void setLink(final Link l) { link = l == null ? SERVER_LINK : l; }

    public static Link serverLink() { return SERVER_LINK; }

    public static boolean ready() { return link.ready(); }

    public static String newId() { return "%08x".formatted(RANDOM.nextInt()); }

    /**
     * Queue a blob. Returns its id immediately; {@code whenSent} runs (with the id) after the End left.
     */
    public static String send(final String kind, final byte[] bytes, final int durationMs, final String target, final String meta, final Consumer<String> whenSent) {
        final String id = newId();
        QUEUE.add(new Pending(new BlobPayloads.Start(id, kind, bytes.length, durationMs, "", target == null ? "" : target, meta == null ? "" : meta), null));
        for (int at = 0, index = 0; at < bytes.length; index++) {
            final int n = Math.min(BlobPayloads.CHUNK_BYTES, bytes.length - at);
            final byte[] slice = new byte[n];
            System.arraycopy(bytes, at, slice, 0, n);
            QUEUE.add(new Pending(new BlobPayloads.Chunk(id, index, slice), null));
            at += n;
        }
        QUEUE.add(new Pending(new BlobPayloads.End(id), id));
        if (whenSent != null) COMPLETIONS.put(id, whenSent);
        return id;
    }

    /** Bytes still queued (for progress UI). */
    public static int queued() { return QUEUE.size(); }

    /** Called every client tick by Core. */
    public static void tick() {
        if (QUEUE.isEmpty()) return;
        if (!link.ready()) { QUEUE.clear(); COMPLETIONS.clear(); return; }
        for (int i = 0; i < CHUNKS_PER_TICK && !QUEUE.isEmpty(); i++) {
            final Pending p = QUEUE.poll();
            link.send(p.payload());
            if (p.completeId() != null) {
                final Consumer<String> c = COMPLETIONS.remove(p.completeId());
                if (c != null) c.accept(p.completeId());
                onComplete.accept(p.completeId());
            }
        }
    }

    private BlobSender() {}
}
