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

    /**
     * The blocking multi-select "open files" dialog, opened in {@code startDir}: the files the player picked, or an
     * empty list when cancelled or when no dialog can be shown. Blocks the render thread while open, like vanilla's own
     * dialogs (the Config module's pack imports use it).
     */
    public static List<Path> openFiles(final net.minecraft.network.chat.Component title, final Path startDir, @Nullable final String description, final String... patterns) {
        if (open) return List.of();
        open = true;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = null;
            if (patterns.length > 0) {
                filters = stack.mallocPointer(patterns.length);
                for (final String p : patterns) filters.put(stack.UTF8(p));
                filters.flip();
            }
            // A trailing separator tells tinyfd this is a folder to open in, not a file name to suggest.
            final String start = startDir.toAbsolutePath() + java.io.File.separator;
            final String result = TinyFileDialogs.tinyfd_openFileDialog(title.getString(), start, filters, description, true);
            if (result == null || result.isBlank()) return List.of();
            final List<Path> out = new java.util.ArrayList<>();
            for (final String part : result.split("\\|")) if (!part.isBlank()) out.add(Path.of(part.trim()));
            if (!out.isEmpty() && out.get(0).getParent() != null) lastDir = out.get(0).getParent();
            return out;
        } catch (final Throwable t) {
            Slate.LOGGER.warn("[Slate] no file dialog available: {}", t.toString());
            return List.of();
        } finally {
            open = false;
        }
    }

    /** Copies {@code files} into {@code dir} (a "(2)" suffix when the name is taken); returns the names that landed. */
    public static List<String> copyInto(final List<Path> files, final Path dir) {
        final List<String> names = new java.util.ArrayList<>();
        try {
            Files.createDirectories(dir);
        } catch (final java.io.IOException e) {
            Slate.LOGGER.warn("[Slate] cannot create {}: {}", dir, e.toString());
            return names;
        }
        for (final Path file : files) {
            if (!Files.isRegularFile(file)) continue;
            final String name = file.getFileName().toString();
            Path target = dir.resolve(name);
            if (Files.exists(target) && !sameFile(file, target)) {
                final int dot = name.lastIndexOf('.');
                final String stem = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
                for (int i = 2; Files.exists(target); i++) target = dir.resolve(stem + " (" + i + ")" + ext);
            }
            try {
                if (!sameFile(file, target)) Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                names.add(target.getFileName().toString());
            } catch (final java.io.IOException e) {
                Slate.LOGGER.warn("[Slate] cannot copy {} into {}: {}", file, dir, e.toString());
            }
        }
        return names;
    }

    private static boolean sameFile(final Path a, final Path b) {
        try {
            return Files.exists(b) && Files.isSameFile(a, b);
        } catch (final java.io.IOException e) {
            return false;
        }
    }

    private FilePicker() {}
}
