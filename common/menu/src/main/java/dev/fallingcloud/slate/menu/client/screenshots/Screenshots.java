package dev.fallingcloud.slate.menu.client.screenshots;

import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.MenuIo;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** The {@code <game>/screenshots} folder: listing, rename, delete. */
public final class Screenshots {

    /** One file. {@code name} is the file name without extension. */
    public record Shot(Path path, String name, String fileName, long modified, long size) {}

    public static Path dir() {
        return new File(Minecraft.getInstance().gameDirectory, Screenshot.SCREENSHOT_DIR).toPath();
    }

    public static boolean isImage(final Path p) {
        final String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
    }

    /** Lists on the pool; newest first. */
    public static CompletableFuture<List<Shot>> scan() {
        return CompletableFuture.supplyAsync(() -> {
            final List<Shot> out = new ArrayList<>();
            final Path d = dir();
            if (!Files.isDirectory(d)) return out;
            try (Stream<Path> s = Files.list(d)) {
                for (final Path p : (Iterable<Path>) s::iterator) {
                    if (!Files.isRegularFile(p) || !isImage(p)) continue;
                    final String file = p.getFileName().toString();
                    final int dot = file.lastIndexOf('.');
                    long mod = 0, size = 0;
                    try { mod = Files.getLastModifiedTime(p).toMillis(); size = Files.size(p); } catch (final IOException ignored) {}
                    out.add(new Shot(p, dot > 0 ? file.substring(0, dot) : file, file, mod, size));
                }
            } catch (final IOException e) {
                SlateMenu.LOGGER.warn("[Slate Menu] cannot list screenshots: {}", e.toString());
            }
            out.sort((a, b) -> Long.compare(b.modified(), a.modified()));
            return out;
        }, MenuIo.POOL);
    }

    /** Renames keeping the extension; false when the name is invalid or taken. */
    public static boolean rename(final Shot shot, final String newName) {
        if (newName == null) return false;
        final String clean = newName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (clean.isEmpty()) return false;
        final String file = shot.fileName();
        final int dot = file.lastIndexOf('.');
        final String ext = dot > 0 ? file.substring(dot) : ".png";
        final Path target = shot.path().resolveSibling(clean + ext);
        if (Files.exists(target)) return false;
        try {
            Files.move(shot.path(), target, StandardCopyOption.ATOMIC_MOVE);
            Textures.invalidate(shot.path());
            return true;
        } catch (final IOException e) {
            SlateMenu.LOGGER.warn("[Slate Menu] rename failed: {}", e.toString());
            return false;
        }
    }

    public static boolean delete(final Shot shot) {
        try {
            final boolean ok = Files.deleteIfExists(shot.path());
            Textures.invalidate(shot.path());
            return ok;
        } catch (final IOException e) {
            SlateMenu.LOGGER.warn("[Slate Menu] delete failed: {}", e.toString());
            return false;
        }
    }

    private Screenshots() {}
}
