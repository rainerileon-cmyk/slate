package dev.fallingcloud.slate.core.module;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.layout.action.ActionType;
import dev.fallingcloud.slate.core.layout.action.Actions;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Registry of loaded Slate modules. Registration order = loader construction order. */
public final class Modules {

    private static final Map<String, SlateModule> MODULES = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final List<SlateModule> CLIENT_INITIALISED = new ArrayList<>();

    /**
     * Registers and initialises a module. Runs {@link SlateModule#init()} immediately and, on the client,
     * {@link SlateModule#initClient()} right after. Registering the same id twice is a no-op.
     */
    public static void register(final SlateModule module) {
        Slate.init();
        if (MODULES.putIfAbsent(module.id(), module) != null) return;
        Slate.LOGGER.info("[Slate] module {} ({})", module.id(), module.displayName().getString());
        module.init();
        for (final ActionType action : module.actions()) Actions.register(action);
        if (SlatePlatform.get().isClient()) {
            module.initClient();
            synchronized (CLIENT_INITIALISED) { CLIENT_INITIALISED.add(module); }
        }
    }

    public static boolean isLoaded(final String id) {
        return MODULES.containsKey(id);
    }

    public static Optional<SlateModule> get(final String id) {
        return Optional.ofNullable(MODULES.get(id));
    }

    public static List<SlateModule> all() {
        synchronized (MODULES) { return List.copyOf(MODULES.values()); }
    }

    private Modules() {}
}
