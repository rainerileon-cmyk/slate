package dev.fallingcloud.slate.core.layout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Registry of {@link ElementType}s. Core registers the built-ins; modules add theirs in initClient. */
public final class ElementTypes {

    private static final Map<String, ElementType> TYPES = Collections.synchronizedMap(new LinkedHashMap<>());

    public static void register(final ElementType type) { TYPES.put(type.id(), type); }

    public static Optional<ElementType> get(final String id) { return Optional.ofNullable(TYPES.get(id)); }

    public static List<ElementType> all() {
        synchronized (TYPES) { return List.copyOf(TYPES.values()); }
    }

    private ElementTypes() {}
}
