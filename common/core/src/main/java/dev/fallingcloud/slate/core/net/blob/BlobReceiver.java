package dev.fallingcloud.slate.core.net.blob;

import dev.fallingcloud.slate.core.Slate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Client side: assembles incoming transfers and hands completed blobs to listeners (the media cache,
 * stream viewers, DM attachments). Incomplete transfers are discarded after 30 s of silence.
 * Client main thread only.
 */
public final class BlobReceiver {

    /** A completed transfer. */
    public record Received(BlobPayloads.Start start, byte[] bytes) {
        public String id() { return start.id(); }
        public String kind() { return start.kind(); }
    }

    private static final class Pending {
        final BlobPayloads.Start start;
        final byte[] buffer;
        int filled, nextIndex;
        long lastMs = System.currentTimeMillis();
        Pending(final BlobPayloads.Start start) { this.start = start; this.buffer = new byte[start.totalBytes()]; }
    }

    private static final Map<String, Pending> PENDING = new HashMap<>();
    private static final List<Consumer<BlobPayloads.Start>> START_LISTENERS = new ArrayList<>();
    private static final List<Consumer<Received>> DONE_LISTENERS = new ArrayList<>();
    private static final List<ProgressListener> PROGRESS_LISTENERS = new ArrayList<>();
    public static volatile int maxIncomingBytes = 8 * 1024 * 1024;

    @FunctionalInterface
    public interface ProgressListener { void progress(String id, int received, int total); }

    public static void onStart(final Consumer<BlobPayloads.Start> l) { START_LISTENERS.add(l); }

    public static void onComplete(final Consumer<Received> l) { DONE_LISTENERS.add(l); }

    public static void onProgress(final ProgressListener l) { PROGRESS_LISTENERS.add(l); }

    public static boolean isPending(final String id) { return PENDING.containsKey(id); }

    public static void handleStart(final BlobPayloads.Start start) {
        if (!BlobPayloads.validId(start.id()) || start.totalBytes() <= 0 || start.totalBytes() > maxIncomingBytes) return;
        PENDING.put(start.id(), new Pending(start));
        for (final Consumer<BlobPayloads.Start> l : START_LISTENERS) {
            try { l.accept(start); } catch (final Exception e) { Slate.LOGGER.error("[Slate] blob start listener threw", e); }
        }
    }

    public static void handleChunk(final BlobPayloads.Chunk chunk) {
        final Pending p = PENDING.get(chunk.id());
        if (p == null) return;
        if (chunk.index() != p.nextIndex || p.filled + chunk.data().length > p.buffer.length) { PENDING.remove(chunk.id()); return; }
        System.arraycopy(chunk.data(), 0, p.buffer, p.filled, chunk.data().length);
        p.filled += chunk.data().length;
        p.nextIndex++;
        p.lastMs = System.currentTimeMillis();
        for (final ProgressListener l : PROGRESS_LISTENERS) l.progress(chunk.id(), p.filled, p.buffer.length);
    }

    public static void handleEnd(final BlobPayloads.End end) {
        final Pending p = PENDING.remove(end.id());
        if (p == null || p.filled != p.buffer.length) return;
        final Received r = new Received(p.start, p.buffer);
        for (final Consumer<Received> l : DONE_LISTENERS) {
            try { l.accept(r); } catch (final Exception e) { Slate.LOGGER.error("[Slate] blob listener threw", e); }
        }
    }

    /** Called every client tick: expire stale transfers. */
    public static void tick() {
        if (PENDING.isEmpty()) return;
        final long now = System.currentTimeMillis();
        PENDING.values().removeIf(p -> now - p.lastMs > 30_000);
    }

    public static void clear() { PENDING.clear(); }

    private BlobReceiver() {}
}
