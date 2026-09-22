package dev.fallingcloud.slate.core.media;

import dev.fallingcloud.slate.core.Slate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * Native file dialogs through LWJGL's tinyfd (bundled with Minecraft). The dialog runs on its own thread so
 * the render loop keeps going; the result lands on the render thread.
 *
 * <p>Caveat that shaped the in-game {@code ImagePickerScreen}: an OS dialog behind an exclusive-fullscreen
 * window is invisible and steals focus. {@link #available()} is false in fullscreen, and callers fall back
 * to the in-game browser.</p>
 */
public final class FilePicker {

    public static final List<String> IMAGE_PATTERNS = List.of("*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp");

    private static volatile boolean open;
    private static Path lastDir;

    /** False in exclusive fullscreen (the dialog would be hidden) or while a dialog is already open. */
    public static boolean available() {
        try {
            return !open && !Minecraft.getInstance().getWindow().isFullscreen();
        } catch (final Throwable t) {
            return false;
        }
    }

    public static boolean isOpen() { return open; }

    /** Image file dialog; {@code onPicked} runs on the render thread with the chosen path (never for cancel). */
    public static void pickImage(final Consumer<Path> onPicked) {
        pickFile("Send an image", IMAGE_PATTERNS, "Images", onPicked);
    }

    /** Any file dialog. Patterns like {@code *.zip}; empty list = every file. */
    public static void pickFile(final String title, final List<String> patterns, @Nullable final String description, final Consumer<Path> onPicked) {
        if (open) return;
        open = true;
        final Thread worker = new Thread(() -> {
            String picked = null;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = null;
                if (patterns != null && !patterns.isEmpty()) {
                    filters = stack.mallocPointer(patterns.size());
                    for (final String p : patterns) filters.put(stack.UTF8(p));
                    filters.flip();
                }
                final String start = lastDir != null && Files.isDirectory(lastDir) ? lastDir.toAbsolutePath() + java.io.File.separator : defaultDir();
                picked = TinyFileDialogs.tinyfd_openFileDialog(title, start, filters, description, false);
            } catch (final Throwable t) {
                Slate.LOGGER.warn("[Slate] file dialog failed: {}", t.toString());
            } finally {
                open = false;
            }
            if (picked == null || picked.isBlank()) return;
            final Path path = Path.of(picked.trim());
            if (path.getParent() != null) lastDir = path.getParent();
            Minecraft.getInstance().execute(() -> onPicked.accept(path));
        }, "slate-filepicker");
        worker.setDaemon(true);
        worker.start();
    }

    /** Save dialog; {@code onPicked} gets the target path. */
    public static void saveFile(final String title, final String suggestedName, final List<String> patterns, @Nullable final String description, final Consumer<Path> onPicked) {
        if (open) return;
        open = true;
        final Thread worker = new Thread(() -> {
            String picked = null;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = null;
                if (patterns != null && !patterns.isEmpty()) {
                    filters = stack.mallocPointer(patterns.size());
                    for (final String p : patterns) filters.put(stack.UTF8(p));
                    filters.flip();
                }
                final String start = (lastDir != null && Files.isDirectory(lastDir) ? lastDir.toAbsolutePath() + java.io.File.separator : defaultDir()) + suggestedName;
                picked = TinyFileDialogs.tinyfd_saveFileDialog(title, start, filters, description);
            } catch (final Throwable t) {
                Slate.LOGGER.warn("[Slate] save dialog failed: {}", t.toString());
            } finally {
                open = false;
            }
            if (picked == null || picked.isBlank()) return;
            final Path path = Path.of(picked.trim());
            if (path.getParent() != null) lastDir = path.getParent();
            Minecraft.getInstance().execute(() -> onPicked.accept(path));
        }, "slate-filepicker");
        worker.setDaemon(true);
        worker.start();
    }

    private static String defaultDir() {
        try {
            final Path pictures = Path.of(System.getProperty("user.home"), "Pictures");
            if (Files.isDirectory(pictures)) return pictures.toAbsolutePath() + java.io.File.separator;
            return Path.of(System.getProperty("user.home")).toAbsolutePath() + java.io.File.separator;
        } catch (final Exception e) {
            return "";
        }
    }

    /** The game's own screenshots folder plus the usual user folders, for quick-place buttons. */
    @Nullable
    public static Path place(final String name) {
        try {
            if (name.equals("Screenshots")) return Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
            return Path.of(System.getProperty("user.home"), name);
        } catch (final Exception e) {
            return null;
        }
    }

    private FilePicker() {}
}
