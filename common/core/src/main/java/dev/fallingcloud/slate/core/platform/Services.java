package dev.fallingcloud.slate.core.platform;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/** ServiceLoader lookup with caching; fails loudly when a loader forgot its services file. */
public final class Services {

    private static final Map<Class<?>, Object> CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> T load(final Class<T> type) {
        return (T) CACHE.computeIfAbsent(type, t -> ServiceLoader.load(t, Services.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No Slate service implementation for " + t.getName()
                + " - the loader jar is missing its META-INF/services entry")));
    }

    private Services() {}
}
