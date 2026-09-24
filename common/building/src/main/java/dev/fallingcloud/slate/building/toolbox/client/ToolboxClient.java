package dev.fallingcloud.slate.building.toolbox.client;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.BuildingHarness;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.net.OpenToolbox;
import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.toolbox.ToolboxContents;
import dev.fallingcloud.slate.building.toolbox.ToolboxMenu;
import dev.fallingcloud.slate.building.toolbox.ToolboxSelfTest;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client init of the toolbox: the {@code toolbox} dev-harness scenario and the "open my toolbox" request other UIs use
 * ({@link #requestOpen()}). The menu screen and the tooltip picture are registered from the loader toolbox glue.
 */
public final class ToolboxClient {

    private static final BlockPos CHEST = new BlockPos(3, -60, 5);

    public static void init() {
        BuildingHarness.register("toolbox", ToolboxClient::harness);
    }

    /** Asks the server to open the player's toolbox (whichever {@link ToolboxAccess#find} picks). */
    public static void requestOpen() {
        SlateNetwork.get().sendToServer(new OpenToolbox(ToolboxAccess.SLOT_FIND));
    }

    // ------------------------------------------------------------------ dev harness

    private static CompletableFuture<List<String>> pending;

    /**
     * A kitted toolbox in the hotbar, a tool/upgrade/blocks to shift-click in, a chest to link; logs the capability,
     * server and menu self-tests (PASS/FAIL), then screenshots the screen in both skins with and without tooltips.
     */
    private static void harness(final BuildingHarness.Script s) {
        s.command("time set noon")
            .command("weather clear")
            .command("gamemode survival")
            .command("tp @s 0 -60 0 0 25")
            .command("fill -4 -61 2 4 -61 6 minecraft:oak_planks")
            .command("fill -4 -60 7 4 -58 7 minecraft:stone_bricks")
            .command("setblock 3 -60 5 minecraft:chest")
            .command("setblock 3 -59 5 minecraft:furnace")
            .command("item replace entity @s hotbar.0 with slate_building:toolbox[minecraft:container=["
                + "{slot:0,item:{id:\"slate_building:iron_trowel\",count:1}},"
                + "{slot:1,item:{id:\"slate_building:copper_hammer\",count:1}},"
                + "{slot:3,item:{id:\"slate_building:diamond_blueprint\",count:1}},"
                + "{slot:5,item:{id:\"slate_building:netherite_chisel\",count:1}},"
                + "{slot:6,item:{id:\"slate_building:capacity_upgrade\",count:1}},"
                + "{slot:7,item:{id:\"slate_building:reach_upgrade\",count:1}},"
                + "{slot:10,item:{id:\"minecraft:oak_planks\",count:64}},"
                + "{slot:11,item:{id:\"minecraft:stone_bricks\",count:32}}]]")
            .command("item replace entity @s hotbar.1 with slate_building:iron_brush")
            .command("item replace entity @s hotbar.2 with slate_building:speed_upgrade")
            .command("item replace entity @s hotbar.3 with minecraft:stone_bricks 64")
            .command("item replace entity @s inventory.0 with slate_building:copper_square")
            .run(() -> Minecraft.getInstance().getToasts().clear())
            .wait(5)
            .run(() -> results("capabilities", ToolboxSelfTest.capabilities(BuildingServerSettings.local())))
            .run(() -> onServer(p -> ToolboxSelfTest.server(p, CHEST)))
            .waitUntil(() -> pending.isDone(), 200)
            .run(() -> results("server", pending.join()))
            .run(() -> SlateNetwork.get().sendToServer(new OpenToolbox(0)))
            .waitUntil(() -> Minecraft.getInstance().screen instanceof ToolboxScreen, 200)
            .run(() -> onServer(ToolboxSelfTest::menu))
            .waitUntil(() -> pending.isDone(), 200)
            .run(() -> results("menu", pending.join()))
            .run(() -> ToolboxScreen.debugHover(-1))   // screenshots ignore the real cursor
            .run(() -> Minecraft.getInstance().getToasts().clear())
            .wait(60)
            .screenshot("toolbox-dark")
            .run(() -> ToolboxScreen.debugHover(ToolboxContents.FIRST_POUCH + 5))
            .wait(8)
            .screenshot("toolbox-dark-pouch-hint")
            .run(() -> ToolboxScreen.debugHover(ToolboxMenu.HOTBAR_START))
            .wait(8)
            .screenshot("toolbox-dark-toolbox-tooltip")
            // Hovering the Square slot scrolls the unlock panel to the Square and lights its block.
            .run(() -> ToolboxScreen.debugHover(ToolboxContents.toolSlot(ToolType.SQUARE)))
            .wait(30)
            .screenshot("toolbox-dark-focus")
            .run(() -> ToolboxScreen.debugHover(-1))
            .skin("VANILLA")
            .wait(20)
            .screenshot("toolbox-vanilla")
            .run(() -> ToolboxScreen.debugHover(ToolboxContents.toolSlot(ToolType.TROWEL)))
            .wait(8)
            .screenshot("toolbox-vanilla-tool-tooltip")
            .run(() -> ToolboxScreen.debugHover(ToolboxContents.FIRST_POUCH))
            .wait(8)
            .screenshot("toolbox-vanilla-hover")
            .run(() -> ToolboxScreen.debugHover(-1))
            .skin("DARK")
            .run(ToolboxClient::closeScreen)
            .wait(10)
            // An empty toolbox, as a creative player: the empty-state hint and the creative note.
            .command("gamemode creative")
            .command("item replace entity @s hotbar.5 with slate_building:toolbox")
            .wait(5)
            .run(() -> SlateNetwork.get().sendToServer(new OpenToolbox(5)))
            .waitUntil(() -> Minecraft.getInstance().screen instanceof ToolboxScreen, 200)
            .wait(40)
            .screenshot("toolbox-dark-empty")
            .skin("VANILLA")
            .wait(10)
            .screenshot("toolbox-vanilla-empty")
            .skin("DARK")
            .run(ToolboxClient::closeScreen)
            .run(() -> ToolboxScreen.debugHover(null))
            .wait(10);
    }

    private static void closeScreen() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.closeContainer();
    }

    private static void onServer(final java.util.function.Function<ServerPlayer, List<String>> test) {
        final Minecraft mc = Minecraft.getInstance();
        final var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            pending = CompletableFuture.completedFuture(List.of("FAIL no integrated server"));
            return;
        }
        final var id = mc.player.getUUID();
        pending = server.submit(() -> {
            final ServerPlayer p = server.getPlayerList().getPlayer(id);
            return p == null ? List.of("FAIL no server player") : new ArrayList<>(test.apply(p));
        });
    }

    private static void results(final String group, final List<String> lines) {
        int pass = 0, fail = 0;
        for (final String line : lines) {
            if (line.startsWith("PASS")) pass++;
            else fail++;
            SlateBuilding.LOGGER.info("[BuildingHarness] toolbox/{} {}", group, line);
        }
        SlateBuilding.LOGGER.info("[BuildingHarness] toolbox/{}: {} passed, {} failed", group, pass, fail);
    }

    private ToolboxClient() {}
}
