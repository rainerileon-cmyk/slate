package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * "Attach an image" through the OS file dialog (LWJGL's tinyfd, bundled with Minecraft). The dialog blocks,
 * so it runs on its own thread; the chosen file is read, size-checked (re-encoded down when needed, GIFs
 * kept untouched) and handed back on the main thread as {@code (bytes, kind)}.
 */
public final class FilePicker {

    private static volatile boolean open;

    @FunctionalInterface
    public interface Picked {
        void picked(byte[] bytes, String kind, String fileName);
    }

    public static void pickImage(final Picked onPicked, final Consumer<String> onError) {
        if (open) return;
        open = true;
        final Path start = MultiplayerConfigsScreenshots.dir();
        final Thread t = new Thread(() -> {
            String chosen = null;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                final PointerBuffer filters = stack.mallocPointer(4);
                filters.put(stack.UTF8("*.png"));
                filters.put(stack.UTF8("*.jpg"));
                filters.put(stack.UTF8("*.jpeg"));
                filters.put(stack.UTF8("*.gif"));
                filters.flip();
                chosen = TinyFileDialogs.tinyfd_openFileDialog("Send an image", start.toAbsolutePath() + java.io.File.separator, filters, "Images", false);
            } catch (final Throwable e) {
                SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] file dialog failed: {}", e.toString());
            } finally {
                open = false;
            }
            if (chosen == null || chosen.isBlank()) return;
            final Path path = Path.of(chosen);
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
        }, "slate-filepicker");
        t.setDaemon(true);
        t.start();
    }

    /** Where the dialog opens: the screenshots folder (what people most often want to send). */
    static final class MultiplayerConfigsScreenshots {
        static Path dir() {
            final Path shots = dev.fallingcloud.slate.core.platform.SlatePlatform.get().gameDir().resolve("screenshots");
            return Files.isDirectory(shots) ? shots : dev.fallingcloud.slate.core.platform.SlatePlatform.get().gameDir();
        }
    }

    private FilePicker() {}
}
