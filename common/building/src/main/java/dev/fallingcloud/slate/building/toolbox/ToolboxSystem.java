package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.core.event.SlateEvents;

/**
 * Common (both sides) init of the toolbox, called from {@code SlateBuilding.init()} last: queues the
 * {@link SupplyLink} data component and flushes pouch views handed to the ops economy at the end of every server tick
 * ({@link ToolboxInventory}). The BetterInventory bridge binds lazily on first use.
 */
public final class ToolboxSystem {

    private static boolean initialised;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        SupplyLink.init();
        SlateEvents.SERVER_TICK_END.register(server -> ToolboxInventory.flushPending());
    }

    private ToolboxSystem() {}
}
