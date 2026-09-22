package dev.fallingcloud.slate.core.media;

import dev.fallingcloud.slate.core.config.JsonConfig;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The local GIF library: favourites and recents, persisted to {@code config/slate/media/gifs.json}.
 *
 * <p>Local-first on purpose: chat already carries GIF links around, so the library builds itself - every
 * GIF link seen in chat lands in Recent, starring promotes it to Favourites. Insertion-ordered, newest
 * first, capped so the file cannot grow without bound. Online search ({@link GifSearch}) is the optional
 * extra on top.</p>
 */
public final class GifStore {

    private static final int MAX_RECENT = 60;
    private static final int MAX_FAVORITES = 300;

    /** Serialised shape of the json file. */
    public static final class Data {
        public List<String> favorites = new ArrayList<>();
        public List<String> recent = new ArrayList<>();
    }

    private static JsonConfig<Data> file;

    private static synchronized JsonConfig<Data> file() {
        if (file == null) file = JsonConfig.at(JsonConfig.dir().resolve("media").resolve("gifs.json"), Data.class, Data::new);
        final Data d = file.get();
        if (d.favorites == null) d.favorites = new ArrayList<>();
        if (d.recent == null) d.recent = new ArrayList<>();
        return file;
    }

    public static synchronized List<String> favorites() { return new ArrayList<>(file().get().favorites); }

    public static synchronized List<String> recent() { return new ArrayList<>(file().get().recent); }

    public static synchronized boolean isFavorite(final String url) {
        return file().get().favorites.contains(url);
    }

    /** Stars / unstars; returns the new state. */
    public static synchronized boolean toggleFavorite(final String url) {
        final Data d = file().get();
        final boolean now;
        if (d.favorites.remove(url)) {
            now = false;
        } else {
            d.favorites.add(0, url);
            while (d.favorites.size() > MAX_FAVORITES) d.favorites.remove(d.favorites.size() - 1);
            now = true;
        }
        file().save();
        return now;
    }

    /** Called for every GIF link that appears in chat, ours or anyone's. Newest first, deduplicated. */
    public static synchronized void noteRecent(final String url) {
        if (url == null || url.isBlank()) return;
        final Data d = file().get();
        final LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(url);
        set.addAll(d.recent);
        d.recent = new ArrayList<>(set);
        while (d.recent.size() > MAX_RECENT) d.recent.remove(d.recent.size() - 1);
        file().save();
    }

    public static synchronized void clearRecent() {
        file().get().recent.clear();
        file().save();
    }

    private GifStore() {}
}
