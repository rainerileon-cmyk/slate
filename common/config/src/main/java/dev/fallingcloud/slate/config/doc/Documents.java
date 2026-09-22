package dev.fallingcloud.slate.config.doc;

import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One document instance per file, shared by resolvers, pages and editors so an edit in one place is
 * visible everywhere. Documents re-read themselves when the file changes on disk.
 */
public final class Documents {

    private static final Map<String, FileDocument> CACHE = new ConcurrentHashMap<>();

    /** Resolve a file argument: absolute, or relative to the game dir, or relative to the config dir. */
    public static Path locate(final String file) {
        final Path p = Path.of(file);
        if (p.isAbsolute()) return p;
        final Path game = SlatePlatform.get().gameDir().resolve(file);
        if (java.nio.file.Files.exists(game)) return game;
        final Path cfg = SlatePlatform.get().configDir().resolve(file);
        if (java.nio.file.Files.exists(cfg)) return cfg;
        return file.startsWith("config/") || file.startsWith("config\\") ? game : cfg;
    }

    /** A path relative to the game dir with forward slashes (the id form). */
    public static String relativeToGame(final Path path) {
        final Path game = SlatePlatform.get().gameDir().toAbsolutePath().normalize();
        final Path abs = path.toAbsolutePath().normalize();
        return abs.startsWith(game) ? game.relativize(abs).toString().replace('\\', '/') : abs.toString().replace('\\', '/');
    }

    public static String relativeToConfig(final Path path) {
        final Path cfg = SlatePlatform.get().configDir().toAbsolutePath().normalize();
        final Path abs = path.toAbsolutePath().normalize();
        return abs.startsWith(cfg) ? cfg.relativize(abs).toString().replace('\\', '/') : abs.getFileName().toString();
    }

    public static JsonDocument json(final Path path, final String modId) {
        return (JsonDocument) CACHE.computeIfAbsent(key(path), k -> new JsonDocument(path, modId, relativeToGame(path)));
    }

    public static TomlDocument toml(final Path path, final String modId) {
        return (TomlDocument) CACHE.computeIfAbsent(key(path), k -> new TomlDocument(path, modId, relativeToConfig(path)));
    }

    public static PropertiesDocument properties(final Path path, final String modId) {
        return (PropertiesDocument) CACHE.computeIfAbsent(key(path), k -> new PropertiesDocument(path, modId, relativeToGame(path)));
    }

    /** Open by extension; empty for unknown kinds. */
    public static Optional<FileDocument> open(final Path path, final String modId) {
        final String n = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".json") || n.endsWith(".json5") || n.endsWith(".jsonc")) return Optional.of(json(path, modId));
        if (n.endsWith(".toml")) return Optional.of(toml(path, modId));
        if (n.endsWith(".properties") || n.endsWith(".cfg") || n.endsWith(".txt") && n.contains("options")) return Optional.of(properties(path, modId));
        return Optional.empty();
    }

    public static boolean isSupported(final Path path) {
        final String n = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".json") || n.endsWith(".json5") || n.endsWith(".jsonc") || n.endsWith(".toml") || n.endsWith(".properties") || n.endsWith(".cfg");
    }

    public static void forget(final Path path) {
        CACHE.remove(key(path));
    }

    private static String key(final Path p) {
        return p.toAbsolutePath().normalize().toString();
    }

    private Documents() {}
}
