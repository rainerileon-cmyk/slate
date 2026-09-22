package dev.fallingcloud.slate.core.media;

import dev.fallingcloud.slate.core.config.JsonConfig;

/**
 * {@code config/slate/media.json}: the settings the media stack owns itself (the modules that use it keep
 * their own policy - embed links, download caps, thumbnail rows - in their configs and push it into the
 * static policy fields of {@link Attachment} / {@link MediaCache}).
 *
 * <p>The Tenor key lives here because every module's GIF picker is the same {@code GifLibraryScreen};
 * one key, one place. Free from Google's Tenor console; blank disables online search while favourites and
 * recents keep working.</p>
 */
public final class MediaConfig {

    /** Tenor v2 API key for the GIF library's online search. Blank = search tab explains the setup. */
    public String tenorApiKey = "";
    /** Keep received/downloaded media on disk under {@code config/slate/cache/media/} so it survives a rejoin. */
    public boolean diskCache = true;
    /** Disk cache budget; the oldest files go first. */
    public int maxDiskCacheMb = 64;
    /** In-memory entries kept before the least recently drawn ones are evicted (textures freed). */
    public int maxEntries = 64;
    /** User-Agent sent with link embeds and Tenor requests. */
    public String userAgent = "Slate/1.0 (Minecraft)";

    private static JsonConfig<MediaConfig> file;

    public static synchronized JsonConfig<MediaConfig> file() {
        if (file == null) file = JsonConfig.of("media", MediaConfig.class, MediaConfig::new);
        return file;
    }

    public static MediaConfig get() {
        return file().get();
    }
}
