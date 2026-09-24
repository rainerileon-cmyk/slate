package dev.fallingcloud.slate.building.neoforge.ops;

import dev.fallingcloud.slate.building.ops.server.OpsCommands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * NeoForge glue for building operations: {@code /slatebuild} through {@code RegisterCommandsEvent}. Everything else
 * (tick, login/logout, symmetry) is loader-neutral and wired in {@code OpsSystem}; protection goes through the
 * skeleton's {@code NeoForgeBuildingPlatform} (break / place events).
 */
public final class NeoOpsGlue {

    /** Both dists, from the mod constructor (mod bus events; game-bus listeners via {@code NeoForge.EVENT_BUS}). */
    public static void init(final IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, e -> OpsCommands.register(e.getDispatcher()));
    }

    /** Client dist only: registers the {@code ops} dev-harness scenario. */
    public static void initClient(final IEventBus modBus) {
        Client.init();
    }

    /** Client-class references, loaded only on the client. */
    private static final class Client {
        static void init() {
            dev.fallingcloud.slate.building.ops.client.OpsHarness.register();
        }
    }

    private NeoOpsGlue() {}
}
