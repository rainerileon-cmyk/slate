package dev.fallingcloud.slate.core.layout.action;

import dev.fallingcloud.slate.core.Slate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Registry of {@link ActionType}s. Core registers the built-ins from the client bootstrap. */
public final class Actions {

    private static final Map<String, ActionType> TYPES = Collections.synchronizedMap(new LinkedHashMap<>());

    public static void register(final ActionType type) {
        if (TYPES.putIfAbsent(type.id(), type) != null) {
            Slate.LOGGER.warn("[Slate] action {} registered twice; keeping the first", type.id());
        }
    }

    public static Optional<ActionType> get(final String id) {
        return Optional.ofNullable(TYPES.get(id));
    }

    public static List<ActionType> all() {
        synchronized (TYPES) { return List.copyOf(TYPES.values()); }
    }

    /** Runs a saved action; unknown ids log and do nothing. */
    public static void run(final String id, final Map<String, String> args) {
        final ActionType t = TYPES.get(id);
        if (t == null) { Slate.LOGGER.warn("[Slate] unknown action {}", id); return; }
        try {
            t.run(args == null ? Map.of() : args);
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] action {} failed", id, e);
        }
    }

    private Actions() {}
}
