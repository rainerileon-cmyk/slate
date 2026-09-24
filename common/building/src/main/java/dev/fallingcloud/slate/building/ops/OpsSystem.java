package dev.fallingcloud.slate.building.ops;

import dev.fallingcloud.slate.building.ops.server.OpsSelfTest;
import dev.fallingcloud.slate.building.ops.server.OpsServer;
import dev.fallingcloud.slate.building.ops.server.Symmetry;
import dev.fallingcloud.slate.core.event.SlateEvents;

/**
 * Common (both sides) initialisation of the building-mode server: executor tick, history, clipboard, symmetry
 * hooks, logout settling. Called from {@code SlateBuilding.init()} after the network is registered. Commands
 * ({@code /slatebuild}) are registered by each loader's ops glue through {@code OpsCommands}; the symmetry replays
 * come from the {@code slate_building.ops.mixins.json} mixins.
 */
public final class OpsSystem {

    private static boolean initialised;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        SlateEvents.SERVER_TICK_END.register(OpsServer::tick);
        SlateEvents.SERVER_TICK_END.register(Symmetry::flushAtTickEnd);
        SlateEvents.SERVER_TICK_END.register(OpsSelfTest::tick);
        SlateEvents.PLAYER_JOINED.register(OpsServer::onJoin);
        SlateEvents.PLAYER_LEFT.register(OpsServer::onLeave);
        SlateEvents.SERVER_STOPPING.register(OpsServer::onStopping);
    }

    private OpsSystem() {}
}
