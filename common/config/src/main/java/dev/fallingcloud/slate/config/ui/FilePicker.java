package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.config.SlateConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * The native "open files" dialog (LWJGL's tinyfd, which Minecraft already ships for its crash boxes) and a copy
 * into a game folder, for importing resource and shader packs without leaving the game. The dialog blocks the
 * render thread while it is open, like vanilla's own dialogs.
 */
public final class FilePicker {

    private FilePicker() {}

    /** Files the player picked (multi-select), or empty when cancelled or when no dialog can be shown. */
    public static List<Path> openFiles(final Component title, final Path startDir, final String description, final String... patterns) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PointerBuffer filters = stack.mallocPointer(patterns.length);
            for (final String p : patterns) filters.put(stack.UTF8(p));
            filters.flip();
            // A trailing separator tells tinyfd this is a folder to open in, not a file name to suggest.
            final String start = startDir.toAbsolutePath() + File.separator;
            final String result = TinyFileDialogs.tinyfd_openFileDialog(title.getString(), start, filters, description, true);
            if (result == null || result.isBlank()) return List.of();
            final List<Path> out = new ArrayList<>();
            for (final String s : result.split("\\|")) if (!s.isBlank()) out.add(Path.of(s.trim()));
            return out;
        } catch (final Throwable t) {
            SlateConfig.LOGGER.warn("[Slate Config] no file dialog available: {}", t.toString());
            return List.of();
        }
    }

    /** Copies {@code files} into {@code dir} (a "(2)" suffix when the name is taken); returns the names that landed. */
    public static List<String> copyInto(final List<Path> files, final Path dir) {
        final List<String> names = new ArrayList<>();
        try {
            Files.createDirectories(dir);
        } catch (final IOException e) {
            SlateConfig.LOGGER.warn("[Slate Config] cannot create {}: {}", dir, e.toString());
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
                if (!sameFile(file, target)) Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                names.add(target.getFileName().toString());
            } catch (final IOException e) {
                SlateConfig.LOGGER.warn("[Slate Config] cannot copy {} into {}: {}", file, dir, e.toString());
            }
        }
        return names;
    }

    private static boolean sameFile(final Path a, final Path b) {
        try {
            return Files.exists(b) && Files.isSameFile(a, b);
        } catch (final IOException e) {
            return false;
        }
    }
}
