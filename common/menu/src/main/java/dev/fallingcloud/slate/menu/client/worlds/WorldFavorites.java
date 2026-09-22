package dev.fallingcloud.slate.menu.client.worlds;

import dev.fallingcloud.slate.core.config.JsonConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code config/slate/menu/favorites.json}: favourite worlds and user tags, keyed by world folder. */
public final class WorldFavorites {

    public static final class Data {
        public List<String> worlds = new ArrayList<>();
        public Map<String, List<String>> tags = new LinkedHashMap<>();
    }

    private static JsonConfig<Data> file;

    private static synchronized JsonConfig<Data> file() {
        if (file == null) file = JsonConfig.at(JsonConfig.dir().resolve("menu").resolve("favorites.json"), Data.class, Data::new);
        return file;
    }

    public static boolean isFavorite(final String levelId) {
        return file().get().worlds.contains(levelId);
    }

    public static void setFavorite(final String levelId, final boolean favorite) {
        file().update(d -> {
            d.worlds.remove(levelId);
            if (favorite) d.worlds.add(levelId);
        });
    }

    public static boolean toggleFavorite(final String levelId) {
        final boolean now = !isFavorite(levelId);
        setFavorite(levelId, now);
        return now;
    }

    public static List<String> tags(final String levelId) {
        final List<String> t = file().get().tags.get(levelId);
        return t == null ? List.of() : List.copyOf(t);
    }

    public static void setTags(final String levelId, final List<String> tags) {
        file().update(d -> {
            final List<String> clean = new ArrayList<>();
            for (final String t : tags) {
                final String s = t == null ? "" : t.trim();
                if (!s.isEmpty() && !clean.contains(s)) clean.add(s);
            }
            if (clean.isEmpty()) d.tags.remove(levelId);
            else d.tags.put(levelId, clean);
        });
    }

    /** Every tag in use, for the filter chips. */
    public static List<String> allTags() {
        final List<String> out = new ArrayList<>();
        for (final List<String> l : file().get().tags.values()) for (final String t : l) if (!out.contains(t)) out.add(t);
        return out;
    }

    /** Drop everything about a deleted world. */
    public static void forget(final String levelId) {
        file().update(d -> { d.worlds.remove(levelId); d.tags.remove(levelId); });
    }

    private WorldFavorites() {}
}
