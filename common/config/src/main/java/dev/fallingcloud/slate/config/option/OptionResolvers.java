package dev.fallingcloud.slate.config.option;

import dev.fallingcloud.slate.config.SlateConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of {@link OptionResolver}s plus a per-path cache. Pages register bindings they build
 * themselves too ({@link #publish}), so favourites and presets can reach every row without having to
 * re-derive it from a file.
 */
public final class OptionResolvers {

    private static final Map<String, OptionResolver> RESOLVERS = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<String, OptionBinding> PUBLISHED = Collections.synchronizedMap(new LinkedHashMap<>());

    public static void register(final OptionResolver r) {
        RESOLVERS.put(r.prefix(), r);
    }

    /** Pages call this for the bindings they built so {@code resolve(id)} finds them. */
    public static void publish(final OptionBinding b) {
        PUBLISHED.put(b.id(), b);
    }

    public static void publishAll(final Iterable<? extends OptionBinding> bindings) {
        for (final OptionBinding b : bindings) publish(b);
    }

    public static List<String> prefixes() {
        synchronized (RESOLVERS) { return List.copyOf(RESOLVERS.keySet()); }
    }

    /** Resolve a full path. Published bindings win, then the prefix resolver. */
    public static Optional<OptionBinding> resolve(final String path) {
        if (path == null || path.isBlank()) return Optional.empty();
        final OptionBinding published = PUBLISHED.get(path);
        if (published != null) return Optional.of(published);
        final int colon = path.indexOf(':');
        if (colon <= 0) return Optional.empty();
        final OptionResolver r = RESOLVERS.get(path.substring(0, colon));
        if (r == null) return Optional.empty();
        try {
            final Optional<OptionBinding> b = r.resolve(path.substring(colon + 1));
            b.ifPresent(x -> PUBLISHED.putIfAbsent(path, x));
            return b;
        } catch (final Exception e) {
            SlateConfig.LOGGER.warn("[Slate Config] cannot resolve {}: {}", path, e.toString());
            return Optional.empty();
        }
    }

    /** Drop cached file-backed bindings (after a file was edited externally or a document reloaded). */
    public static void invalidate(final String prefix) {
        PUBLISHED.keySet().removeIf(k -> k.startsWith(prefix + ":"));
    }

    private OptionResolvers() {}
}
