package dev.fallingcloud.slate.building.variant;

import dev.fallingcloud.slate.core.event.SlateEvents;

/**
 * Common (both sides) init of the variant system. The unify rules themselves live in mixins (drops, recipes,
 * creative tabs: {@code slate_building.variant.mixins.json}); the registry snapshot is rebuilt when a server starts
 * (tags are bound by then) and, through the loader glue, whenever tags are reloaded on either side.
 */
public final class VariantSystem {

    private static boolean initialised;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        SlateEvents.SERVER_STARTED.register(server -> VariantRegistry.invalidate());
    }

    /** Called by the loader glue after tags were (re)bound, on the server and on the client. */
    public static void onTagsReloaded() {
        VariantRegistry.invalidate();
    }

    private VariantSystem() {}
}
