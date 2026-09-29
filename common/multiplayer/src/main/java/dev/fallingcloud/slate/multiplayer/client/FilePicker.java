package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * "Attach an image": Core's file dialog ({@code core.media.FilePicker}, the one tinyfd dialog of the suite), then
 * the chosen file is read off-thread, size-checked (re-encoded down when needed, GIFs kept untouched) and handed
 * back on the main thread as {@code (bytes, kind, name)}.
 */
public final class FilePicker {

    @FunctionalInterface
    public interface Picked {
        void picked(byte[] bytes, String kind, String fileName);
    }

    public static void pickImage(final Picked onPicked, final Consumer<String> onError) {
        if (!dev.fallingcloud.slate.core.media.FilePicker.available()) return;
        dev.fallingcloud.slate.core.media.FilePicker.pickImage(path -> load(path, onPicked, onError));
    }

    private static void load(final Path path, final Picked onPicked, final Consumer<String> onError) {
        final Thread t = new Thread(() -> {
            try {
                final byte[] raw = Files.readAllBytes(path);
                final int maxBytes = Math.max(64, MultiplayerConfigs.client().imageMaxKb) * 1024;
                final boolean gif = ImageEncoding.isGif(raw);
                byte[] send = raw;
                if (raw.length > maxBytes) send = gif ? null : ImageEncoding.shrinkToFit(raw, maxBytes);
                else if (!gif && !ImageEncoding.isPngOrJpeg(raw)) send = ImageEncoding.shrinkToFit(raw, maxBytes);
                final byte[] payload = send;
                final String name = path.getFileName().toString();
                Minecraft.getInstance().execute(() -> {
                    if (payload == null) onError.accept("That image is too large to send");
                    else onPicked.picked(payload, gif ? "gif" : "image", name);
                });
            } catch (final Throwable e) {
                SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] cannot read {}: {}", path, e.toString());
                Minecraft.getInstance().execute(() -> onError.accept("Could not read that file"));
            }
        }, "slate-image-load");
        t.setDaemon(true);
        t.start();
    }

    private FilePicker() {}
}
