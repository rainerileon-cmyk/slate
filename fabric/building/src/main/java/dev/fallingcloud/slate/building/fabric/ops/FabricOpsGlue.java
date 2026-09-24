package dev.fallingcloud.slate.building.fabric.ops;

import dev.fallingcloud.slate.building.ops.server.OpsCommands;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Fabric glue for building operations: {@code /slatebuild} through {@code CommandRegistrationCallback}. Everything
 * else (tick, login/logout, symmetry) is loader-neutral and wired in {@code OpsSystem}; protection goes through the
 * skeleton's {@code FabricBuildingPlatform}.
 */
public final class FabricOpsGlue {

    /** Both environments, from {@code SlateBuildingFabric.onInitialize} (after the registries were flushed). */
    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> OpsCommands.register(dispatcher));
    }

    /** Client only: registers the {@code ops} dev-harness scenario. */
    public static void initClient() {
        Client.init();
    }

    /** Client-class references, loaded only on the client. */
    private static final class Client {
        static void init() {
            dev.fallingcloud.slate.building.ops.client.OpsHarness.register();
        }
    }

    private FabricOpsGlue() {}
}
