package dev.fallingcloud.slate.core.layout;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.config.JsonConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Loads and saves {@link ScreenLayout}s under {@code config/slate/layouts/}. File name = screen id with
 * {@code :} replaced by {@code .} ({@code minecraft.title.json}). Cached in memory; {@link #invalidate}
 * after external edits. Modpacks ship defaults by putting files there (CloudLauncher content defaults).
 */
public final class LayoutStore {

    private static final Map<String, ScreenLayout> CACHE = new ConcurrentHashMap<>();

    public static Path dir() { return JsonConfig.dir().resolve("layouts"); }

    public static Path fileFor(final String screenId) {
        return dir().resolve(screenId.replace(':', '.').replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
    }

    /** The layout for a screen (empty layout when none is saved). Never null. */
    public static ScreenLayout get(final String screenId) {
        return CACHE.computeIfAbsent(screenId, id -> {
            final Path f = fileFor(id);
            if (!Files.isRegularFile(f)) return new ScreenLayout();
            try {
                final ScreenLayout l = JsonConfig.GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), ScreenLayout.class);
                return l == null ? new ScreenLayout() : l;
            } catch (final Exception e) {
                Slate.LOGGER.warn("[Slate] bad layout file {}: {}", f, e.toString());
                return new ScreenLayout();
            }
        });
    }

    public static boolean has(final String screenId) {
        return !get(screenId).isEmpty();
    }

    public static void save(final String screenId, final ScreenLayout layout) {
        layout.screen = screenId;
        CACHE.put(screenId, layout);
        final Path f = fileFor(screenId);
        try {
            Files.createDirectories(f.getParent());
            if (layout.isEmpty()) { Files.deleteIfExists(f); return; }
            final Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, JsonConfig.GSON.toJson(layout), StandardCharsets.UTF_8);
            Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException e) {
            Slate.LOGGER.error("[Slate] could not save layout {}", f, e);
        }
    }

    public static void reset(final String screenId) {
        save(screenId, new ScreenLayout());
    }

    public static void invalidate() { CACHE.clear(); }

    /** Screen ids that have a saved layout. */
    public static List<String> saved() {
        final List<String> out = new ArrayList<>();
        if (!Files.isDirectory(dir())) return out;
        try (Stream<Path> s = Files.list(dir())) {
            s.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                final String n = p.getFileName().toString();
                final String base = n.substring(0, n.length() - 5);
                final int dot = base.indexOf('.');
                out.add(dot < 0 ? base : base.substring(0, dot) + ":" + base.substring(dot + 1));
            });
        } catch (final IOException ignored) {}
        return out;
    }

    /** Export/import as a JSON string (clipboard sharing). */
    public static String export(final String screenId) {
        return JsonConfig.GSON.toJson(get(screenId));
    }

    public static boolean importJson(final String screenId, final String json) {
        try {
            final ScreenLayout l = JsonConfig.GSON.fromJson(json, ScreenLayout.class);
            if (l == null) return false;
            save(screenId, l);
            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    private LayoutStore() {}
}
