package dev.fallingcloud.slate.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A Gson-backed config file under {@code config/slate/}. The value type is a plain POJO with public
 * fields and field initialisers as defaults: Gson instantiates it and only overwrites the fields present in
 * the file, so a missing key keeps its default and a stale file never crashes. Writes are atomic
 * (temp file + move) so a crash mid-save cannot truncate the config.
 */
public final class JsonConfig<T> {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Class<T> type;
    private final Supplier<T> defaults;
    private T value;

    private JsonConfig(final Path file, final Class<T> type, final Supplier<T> defaults) {
        this.file = file;
        this.type = type;
        this.defaults = defaults;
        load();
    }

    /** {@code config/slate/<name>.json}. */
    public static <T> JsonConfig<T> of(final String name, final Class<T> type, final Supplier<T> defaults) {
        return new JsonConfig<>(dir().resolve(name + ".json"), type, defaults);
    }

    /** Any path (used for layouts, caches, per-server data). */
    public static <T> JsonConfig<T> at(final Path file, final Class<T> type, final Supplier<T> defaults) {
        return new JsonConfig<>(file, type, defaults);
    }

    public static Path dir() {
        return SlatePlatform.get().configDir().resolve("slate");
    }

    public T get() { return value; }

    public Path file() { return file; }

    public synchronized void load() {
        T loaded = null;
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(r, type);
            } catch (final Exception e) {
                Slate.LOGGER.warn("[Slate] could not read {} - using defaults ({})", file, e.toString());
            }
        }
        value = loaded != null ? loaded : defaults.get();
        if (loaded == null) save();                 // materialise the defaults so users can find the file
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            final Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(value), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException e) {
            Slate.LOGGER.error("[Slate] could not write {}", file, e);
        }
    }

    /** Edit-then-save in one call. */
    public synchronized void update(final Consumer<T> edit) {
        edit.accept(value);
        save();
    }

    /** Replaces the value with fresh defaults and saves. */
    public synchronized void reset() {
        value = defaults.get();
        save();
    }
}
