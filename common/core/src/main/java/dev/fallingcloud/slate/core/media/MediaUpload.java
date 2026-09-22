package dev.fallingcloud.slate.core.media;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.net.blob.BlobSender;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Sends local media over the blob channel and stores it in the {@link MediaCache} so the sender's own
 * message renders instantly. Kind names on the wire: {@code image}, {@code voice}, {@code file}. The
 * {@code target} is {@link BlobSender}'s routing string: {@code ""} for public chat (every other player),
 * {@code p:<uuid>} / {@code g:<id>} for the Multiplayer module's private channels.
 *
 * <p>{@code onSent} runs on the client main thread with the blob id once the last chunk has left the
 * queue - that is when the caller posts the chat token ({@link Attachment#token}), so receivers almost
 * always have the announcement (and usually the bytes) before the chat line lands.</p>
 */
public final class MediaUpload {

    /**
     * Upload ceiling. The relay's own cap defaults to 3072 KB and transfers are chunked at 24 KB, so this is
     * a quality budget rather than a wire limit; it sits just under the relay cap.
     */
    public static final int MAX_BYTES = 2_800_000;

    /** True when a blob link is up (connected to a server that has Slate, or a hub link is installed). */
    public static boolean ready() {
        return BlobSender.ready();
    }

    /** Queues an encoded image (PNG/JPEG/GIF bytes). Returns the blob id immediately. */
    public static String sendImage(final byte[] bytes, final String target, @Nullable final Consumer<String> onSent) {
        final String id = BlobSender.send(MediaCache.KIND_IMAGE, bytes, 0, target, "", onSent);
        MediaCache.putLocal(id, bytes, MediaCache.KIND_IMAGE, 0);
        return id;
    }

    /** Queues a serialised voice clip. {@code durationMs} rides on the Start payload for the card label. */
    public static String sendVoice(final byte[] bytes, final int durationMs, final String target, @Nullable final Consumer<String> onSent) {
        final String id = BlobSender.send(MediaCache.KIND_VOICE, bytes, durationMs, target, "", onSent);
        MediaCache.putLocal(id, bytes, MediaCache.KIND_VOICE, durationMs);
        return id;
    }

    /** Queues a generic file; the (sanitised) name travels in the Start's meta. */
    public static String sendFile(final byte[] bytes, final String name, final String target, @Nullable final Consumer<String> onSent) {
        final String safe = Attachment.safeName(name);
        final String id = BlobSender.send(MediaCache.KIND_FILE, bytes, 0, target, safe, onSent);
        MediaCache.putLocal(id, bytes, MediaCache.KIND_FILE, 0, safe);
        return id;
    }

    /**
     * Reads an image file off-thread, shrinks stills to fit {@link #MAX_BYTES} (GIFs are never re-encoded)
     * and sends it. {@code onSent} as above; {@code onError} (render thread) gets a short reason.
     */
    public static void sendImageFile(final Path path, final String target, @Nullable final Consumer<String> onSent, @Nullable final Consumer<String> onError) {
        final Thread worker = new Thread(() -> {
            try {
                final byte[] raw = Files.readAllBytes(path);
                final byte[] send = ClipboardImages.shrinkToFit(raw, MAX_BYTES);
                if (send == null) {
                    fail(onError, "too large (" + raw.length / 1024 + " KB, limit " + MAX_BYTES / 1024 + " KB)");
                    return;
                }
                Minecraft.getInstance().execute(() -> sendImage(send, target, onSent));
            } catch (final Throwable e) {
                Slate.LOGGER.warn("[Slate] send image file failed: {}", e.toString());
                fail(onError, e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }, "slate-sendfile");
        worker.setDaemon(true);
        worker.start();
    }

    /** Reads any file off-thread and sends it as a generic attachment (bounded by {@link #MAX_BYTES}). */
    public static void sendAnyFile(final Path path, final String target, @Nullable final Consumer<String> onSent, @Nullable final Consumer<String> onError) {
        final Thread worker = new Thread(() -> {
            try {
                final long size = Files.size(path);
                if (size > MAX_BYTES) {
                    fail(onError, "too large (" + size / 1024 + " KB, limit " + MAX_BYTES / 1024 + " KB)");
                    return;
                }
                final byte[] raw = Files.readAllBytes(path);
                final String name = path.getFileName().toString();
                Minecraft.getInstance().execute(() -> {
                    if (ClipboardImages.isImage(raw) || ClipboardImages.isGif(raw)) sendImage(raw, target, onSent);
                    else sendFile(raw, name, target, onSent);
                });
            } catch (final Throwable e) {
                Slate.LOGGER.warn("[Slate] send file failed: {}", e.toString());
                fail(onError, e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }, "slate-sendfile");
        worker.setDaemon(true);
        worker.start();
    }

    private static void fail(@Nullable final Consumer<String> onError, final String why) {
        if (onError != null) Minecraft.getInstance().execute(() -> onError.accept(why));
    }

    private MediaUpload() {}
}
