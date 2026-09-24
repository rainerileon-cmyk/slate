package dev.fallingcloud.slate.building.ops.client;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.BuildingHarness;
import dev.fallingcloud.slate.building.ops.server.OpsSelfTest;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

/**
 * The {@code ops} dev-harness scenario ({@code -PbuildingHarness=ops}): runs {@link OpsSelfTest} on the integrated
 * server as the player (fill, hollow, walls, line, sphere, replace, clear, copy + rotated paste, stack, mirrored
 * place and break, undo / redo; then the survival economy), then photographs the result from above. Client only;
 * registered from the ops loader glue.
 */
public final class OpsHarness {

    public static void register() {
        BuildingHarness.register("ops", s -> s
            .command("time set noon")
            .command("weather clear")
            .log("ops self-test starting")
            .run(OpsHarness::startSelfTest)
            .waitUntil(OpsSelfTest::finished, 6000)
            .run(() -> SlateBuilding.LOGGER.info("[BuildingHarness] ops self-test: {} passed, {} failed", OpsSelfTest.passed(), OpsSelfTest.failed()))
            // A spectator camera hovers (no fall, no hand, no hotbar) above the test rows.
            .command("gamemode spectator")
            .command("tp @s 6 -40 -12 0 40")
            .run(OpsHarness::cleanScreen)
            .wait(80)
            .run(OpsHarness::cleanScreen)
            .screenshot("ops"));
    }

    private static void cleanScreen() {
        final Minecraft mc = Minecraft.getInstance();
        mc.getToasts().clear();
        mc.gui.getChat().clearMessages(false);
    }

    private static void startSelfTest() {
        final Minecraft mc = Minecraft.getInstance();
        final IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) throw new IllegalStateException("the ops scenario needs a singleplayer world");
        final UUID id = mc.player.getUUID();
        server.execute(() -> OpsSelfTest.start(server, id));
    }

    private OpsHarness() {}
}
