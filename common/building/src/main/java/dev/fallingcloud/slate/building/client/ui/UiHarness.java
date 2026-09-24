package dev.fallingcloud.slate.building.client.ui;

import dev.fallingcloud.slate.building.client.BuildingHarness;
import dev.fallingcloud.slate.building.client.hud.ModeHud;
import dev.fallingcloud.slate.building.client.input.BuildInput;
import dev.fallingcloud.slate.building.client.input.BuildKeys;
import dev.fallingcloud.slate.building.client.input.ExclusiveKeys;
import dev.fallingcloud.slate.building.client.input.KeyClaims;
import dev.fallingcloud.slate.building.client.menu.BuildMenuScreen;
import dev.fallingcloud.slate.building.client.menu.WheelEditorScreen;
import dev.fallingcloud.slate.building.client.mode.ClientModeState;
import dev.fallingcloud.slate.building.client.settings.BuildingSettingsScreen;
import dev.fallingcloud.slate.building.client.wheel.RadialWheel;
import dev.fallingcloud.slate.building.client.wheel.WheelConfig;
import dev.fallingcloud.slate.building.client.wheel.WheelOverlay;
import dev.fallingcloud.slate.building.client.wheel.WheelTarget;
import dev.fallingcloud.slate.building.config.WheelSettings;
import dev.fallingcloud.slate.building.net.OpResult;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.OpMessages;
import dev.fallingcloud.slate.building.variant.Shape;
import java.util.List;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

/**
 * Dev-harness scenarios of the UI ({@code -PbuildingHarness=wheel,menu,input}): both skins of the swap wheel (every page,
 * hovered slices, the in-world reshape wheel) and of the build menu, the wheel editor, the standalone settings and
 * the mode HUD, all on the real variant registry, chisel index and toolbox.
 */
final class UiHarness {

    static void init() {
        BuildingHarness.register("wheel", UiHarness::wheel);
        BuildingHarness.register("menu", UiHarness::menu);
        BuildingHarness.register("input", UiHarness::input);
    }

    // ------------------------------------------------------------------ scenes

    private static BuildingHarness.Script scene(final BuildingHarness.Script s) {
        return s.command("time set 6000")
            .command("weather clear")
            .command("tp @s 0.5 -60 0.5 0 12")
            .command("fill -8 -61 -2 8 -61 14 minecraft:oak_planks")
            .command("fill -8 -60 12 8 -56 12 minecraft:stone_bricks")
            .command("fill -3 -60 8 3 -60 8 minecraft:oak_stairs[facing=south]")
            .command("setblock 0 -60 5 minecraft:stone_brick_stairs[facing=south]")
            .command("item replace entity @s hotbar.0 with minecraft:oak_planks 64")
            .command("item replace entity @s hotbar.1 with minecraft:stone_bricks 48")
            .command("item replace entity @s hotbar.2 with minecraft:oak_stairs 16")
            .run(() -> {
                final Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.getInventory().selected = 0;
                mc.getToasts().clear();
            })
            .wait(30);
    }

    private static void openHeld() {
        final WheelTarget t = WheelTarget.heldTarget();
        if (t != null) WheelOverlay.INSTANCE.openFor(t, true);
    }

    private static void wheel(final BuildingHarness.Script s) {
        scene(s);
        for (final String skin : new String[] {"DARK", "VANILLA"}) {
            final String k = skin.toLowerCase(java.util.Locale.ROOT);
            s.skin(skin).wait(5)
                .run(UiHarness::openHeld).wait(4).screenshot("wheel-" + k + "-opening").wait(30)
                .screenshot("wheel-" + k + "-open")
                .run(() -> WheelOverlay.INSTANCE.debugPoint(2)).wait(20).screenshot("wheel-" + k + "-hover")
                .run(() -> WheelOverlay.INSTANCE.debugPoint(RadialWheel.CENTER)).wait(20).screenshot("wheel-" + k + "-center")
                .run(() -> WheelOverlay.INSTANCE.debugPage(1)).wait(4).screenshot("wheel-" + k + "-page2-in").wait(30)
                .run(() -> WheelOverlay.INSTANCE.debugPoint(1)).wait(20).screenshot("wheel-" + k + "-page2")
                .run(() -> WheelOverlay.INSTANCE.close(false)).wait(20)
                // Stone bricks have a real chisel group (the shipped "Stone" defaults): its page follows the shape pages.
                .run(() -> Minecraft.getInstance().player.getInventory().selected = 1).wait(3)
                .run(UiHarness::openHeld).wait(30)
                .run(() -> WheelOverlay.INSTANCE.debugPage(2)).wait(30)
                .run(() -> WheelOverlay.INSTANCE.debugPoint(3)).wait(20).screenshot("wheel-" + k + "-chisel")
                .run(() -> WheelOverlay.INSTANCE.close(false)).wait(20)
                .run(() -> Minecraft.getInstance().player.getInventory().selected = 0).wait(3)
                // In-world reshape wheel on the stone brick stairs in front of the player.
                .run(() -> WheelOverlay.INSTANCE.openFor(new WheelTarget(WheelTarget.Source.WORLD, Blocks.STONE_BRICKS, Shape.STAIRS, 1, -1,
                    new BlockPos(0, -60, 5)), true)).wait(30)
                .run(() -> WheelOverlay.INSTANCE.debugPoint(1)).wait(20).screenshot("wheel-" + k + "-world")
                .run(() -> WheelOverlay.INSTANCE.close(false)).wait(20);
        }
        s.skin("DARK");
    }

    /**
     * The real input path, end to end: Alt through {@code KeyMapping.set} (so {@code ExclusiveKeys} claims it), mouse
     * movement and scrolling through {@code BuildInput}, the release applying a slice; the build-menu key through a
     * key click and {@code MenuKeys}; survival pick-block on a variant. Each step logs what it saw.
     */
    private static void input(final BuildingHarness.Script s) {
        final InputConstants.Key alt = InputConstants.getKey("key.keyboard.left.alt");
        final InputConstants.Key r = InputConstants.getKey("key.keyboard.r");
        scene(s);
        s.run(() -> KeyMapping.set(alt, true)).wait(10)
            .run(() -> check("alt opens the wheel", WheelOverlay.INSTANCE.isOpen()))
            .run(() -> check("alt is claimed", ExclusiveKeys.isHeldExclusively(BuildKeys.SWAP)))
            .run(() -> BuildInput.fireMouseLook(170, 10)).wait(12)
            .screenshot("input-wheel-look")
            .run(() -> BuildInput.fireScroll(0, -1)).wait(12)
            .screenshot("input-wheel-scroll")
            .run(() -> check("scroll stays in the hotbar slot", Minecraft.getInstance().player.getInventory().selected == 0))
            // Releases of buttons / keys held from before the wheel opened must reach vanilla (else use / attack
            // stay down and keep placing / mining after the wheel closes).
            .run(() -> check("an open wheel lets mouse-button releases through",
                !BuildInput.fireMouseButton(GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_RELEASE, 0)
                    && !BuildInput.fireMouseButton(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0)))
            .run(() -> check("an open wheel lets key releases through",
                !BuildInput.fireKey(GLFW.GLFW_KEY_1, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_1), GLFW.GLFW_RELEASE, 0)))
            .run(() -> check("the wheel stays open after those releases", WheelOverlay.INSTANCE.isOpen()))
            .run(() -> KeyMapping.set(alt, false)).wait(10)
            .run(() -> check("release closes the wheel", !WheelOverlay.INSTANCE.isOpen()))
            .run(() -> {
                // Fabric keeps ONE mapping per key: when another mod's Alt mapping owns the slot, vanilla's release
                // never reaches ours. The release hook alone (vanilla's body skipped) must free a claimed mapping
                // that setAll() put down after a screen closed.
                BuildKeys.SWAP.setDown(true);
                ExclusiveKeys.onSet(alt, false);
                check("a key release frees a claimed mapping vanilla does not reach", !BuildKeys.SWAP.isDown());
            })
            .wait(20)
            .run(() -> KeyMapping.click(r)).wait(10)
            .run(() -> check("R opens the build menu", Minecraft.getInstance().screen instanceof BuildMenuScreen)).wait(20)
            .run(() -> {
                final var screen = Minecraft.getInstance().screen;
                if (screen != null) screen.keyPressed(GLFW.GLFW_KEY_R, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_R), 0);
            }).wait(3)
            .run(() -> check("R closes the build menu", Minecraft.getInstance().screen == null))
            // Spectator keeps the inventory, but the server ignores swaps there: no wheel, and Alt / R stay with the
            // other mods on those keys.
            .command("gamemode spectator").wait(10)
            .run(() -> check("spectator: nothing to swap, Alt not claimed", WheelTarget.resolve() == null && !KeyClaims.swapWanted()))
            .run(() -> KeyMapping.set(alt, true)).wait(5)
            .run(() -> check("spectator: Alt does not open the wheel", !WheelOverlay.INSTANCE.isOpen()
                && !ExclusiveKeys.isHeldExclusively(BuildKeys.SWAP)))
            .run(() -> KeyMapping.set(alt, false))
            .run(() -> check("spectator: R is not claimed while holding a block", !KeyClaims.menuWanted()))
            .command("gamemode creative").wait(10)
            // Survival reach is 4.5 blocks: pick a stair right in front of the player.
            .command("setblock 0 -60 3 minecraft:stone_brick_stairs")
            .command("tp @s 0.5 -60 0.5 0 30")
            .command("gamemode survival").wait(10)
            .run(() -> BuildInput.firePickBlock())
            .run(() -> check("pick block selects the stone bricks slot", Minecraft.getInstance().player.getInventory().selected == 1))
            .command("gamemode creative")
            .command("setblock 0 -60 3 minecraft:air")
            .command("tp @s 0.5 -60 0.5 0 12");
    }

    private static void check(final String what, final boolean ok) {
        dev.fallingcloud.slate.building.SlateBuilding.LOGGER.info("[BuildingHarness] {} {}", ok ? "PASS" : "FAIL", what);
    }

    /** Moves the (free) mouse to the bottom-left corner so no hover state or tooltip lands in a shot. */
    private static void parkMouse() {
        final Minecraft mc = Minecraft.getInstance();
        final long window = mc.getWindow().getWindow();
        final double x = 3, y = mc.getWindow().getScreenHeight() - 3;
        GLFW.glfwSetCursorPos(window, x, y);
        // GLFW does not report programmatic moves; feed the handler directly (dev harness only, names are mojmap in dev).
        try {
            final java.lang.reflect.Method onMove = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
            onMove.setAccessible(true);
            onMove.invoke(mc.mouseHandler, window, x, y);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            dev.fallingcloud.slate.building.SlateBuilding.LOGGER.warn("[BuildingHarness] could not park the mouse: {}", e.toString());
        }
    }

    /** Window size (pixels) and GUI scale (0 = auto) for layout checks. */
    private static void window(final int w, final int h, final int guiScale) {
        final Minecraft mc = Minecraft.getInstance();
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), w, h);
        mc.options.guiScale().set(guiScale);
        mc.resizeDisplay();
    }

    private static void menu(final BuildingHarness.Script s) {
        scene(s);
        s.run(() -> {
            // The mode chip at the top never reaches under the toasts (top right): centred where it can be.
            final int[] wide = ModeHud.debugTopLayout(480, 400);
            check("HUD chip on a 480-wide GUI stays centred, left of the toasts",
                wide[0] + wide[1] <= wide[2] && Math.abs(wide[0] + wide[1] / 2 - 240) <= 1);
            final int[] small = ModeHud.debugTopLayout(427, 400);
            check("HUD chip on a 427-wide GUI stays left of the toasts", small[0] + small[1] <= small[2] && small[0] >= 2);
            check("default wheel names are translated",
                !WheelConfig.displayName(WheelSettings.defaultWheels().get(0), 0).getString().startsWith("slate_building."));
        });
        for (final String skin : new String[] {"DARK", "VANILLA"}) {
            final String k = skin.toLowerCase(java.util.Locale.ROOT);
            s.skin(skin).wait(5)
                .run(() -> {
                    ClientModeState.setHistory(3, 1, "Fill 384");
                    ClientModeState.setMode(BuildModes.FILL);
                })
                .run(() -> Minecraft.getInstance().setScreen(new BuildMenuScreen(null))).run(UiHarness::parkMouse)
                .wait(8).screenshot("menu-" + k + "-opening").wait(40)
                .screenshot("menu-" + k)
                .run(() -> {
                    // Design §5: arrows move in the modes table (vanilla would spend them on spatial focus moves).
                    if (!(Minecraft.getInstance().screen instanceof BuildMenuScreen m)) { check("build menu open for the arrow check", false); return; }
                    final String before = m.debugSelectedId();
                    m.keyPressed(GLFW.GLFW_KEY_DOWN, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_DOWN), 0);
                    check("down arrow moves the selection in the modes table", m.debugTableFocused() && !m.debugSelectedId().equals(before));
                    m.keyPressed(GLFW.GLFW_KEY_UP, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_UP), 0);
                    check("up arrow moves it back", m.debugSelectedId().equals(before));
                })
                .run(() -> { if (Minecraft.getInstance().screen instanceof BuildMenuScreen m) m.debugSelect("paste"); }).wait(25)
                .screenshot("menu-" + k + "-options")
                .run(() -> { if (Minecraft.getInstance().screen instanceof BuildMenuScreen m) m.debugSelect("stack"); }).wait(25)
                .screenshot("menu-" + k + "-stack")
                .run(() -> { if (Minecraft.getInstance().screen instanceof BuildMenuScreen m) m.debugSearch("slab"); }).wait(25)
                .screenshot("menu-" + k + "-search")
                // A search remembered across openings matches like a typed one ("Stairs" finds the stairs slice).
                .run(() -> { if (Minecraft.getInstance().screen instanceof BuildMenuScreen m) m.debugSearch("Stairs"); }).wait(3)
                .run(() -> Minecraft.getInstance().setScreen(null)).wait(3)
                .run(() -> Minecraft.getInstance().setScreen(new BuildMenuScreen(null))).run(UiHarness::parkMouse).wait(10)
                .run(() -> check("a remembered search still matches after reopening",
                    Minecraft.getInstance().screen instanceof BuildMenuScreen m && m.debugShapeSearchMatches()))
                .run(() -> { if (Minecraft.getInstance().screen instanceof BuildMenuScreen m) m.debugSearch(""); })
                .run(() -> Minecraft.getInstance().setScreen(new WheelEditorScreen(Minecraft.getInstance().screen))).run(UiHarness::parkMouse).wait(40)
                .screenshot("editor-" + k)
                .run(() -> Minecraft.getInstance().setScreen(new BuildingSettingsScreen(null))).run(UiHarness::parkMouse).wait(40)
                .screenshot("settings-" + k)
                // The Slate Config hub on the Building tab (gameplay/building), where the settings live when Config is installed.
                .run(() -> Minecraft.getInstance().setScreen(dev.fallingcloud.slate.building.client.BuildingClient.settingsScreen(null)))
                .run(UiHarness::parkMouse).wait(40)
                .screenshot("hub-" + k)
                .run(() -> Minecraft.getInstance().setScreen(dev.fallingcloud.slate.building.client.BuildingClient.settingsScreen(null, "server_ops")))
                .run(UiHarness::parkMouse).wait(40)
                .screenshot("hub-" + k + "-server")
                .run(() -> {
                    // The per-tier rules are text fields ("16, 32, 64, 128"); search brings them onto one page.
                    if (dev.fallingcloud.slate.core.platform.SlatePlatform.get().isModLoaded("slate_config")
                        && Minecraft.getInstance().screen instanceof dev.fallingcloud.slate.config.hub.ConfigHubScreen hub) hub.debugSearch("tiers");
                })
                .wait(30)
                .screenshot("hub-" + k + "-tiers")
                .run(() -> Minecraft.getInstance().setScreen(dev.fallingcloud.slate.building.client.BuildingClient.settingsScreen(null, "modes")))
                .run(UiHarness::parkMouse).wait(40)
                .screenshot("hub-" + k + "-modes")
                .run(() -> Minecraft.getInstance().setScreen(null))
                .command("gamemode survival").wait(10)
                .run(() -> Minecraft.getInstance().setScreen(new BuildMenuScreen(null))).run(UiHarness::parkMouse).wait(45)
                .screenshot("menu-" + k + "-locked")
                .run(() -> Minecraft.getInstance().setScreen(null))
                .command("gamemode creative").wait(10)
                // A bigger GUI: both wheels stacked in the left column.
                .run(() -> window(1600, 900, 2)).wait(10)
                .run(() -> Minecraft.getInstance().setScreen(new BuildMenuScreen(null))).run(UiHarness::parkMouse).wait(45)
                .screenshot("menu-" + k + "-large")
                .run(() -> Minecraft.getInstance().setScreen(new WheelEditorScreen(null))).run(UiHarness::parkMouse).wait(40)
                .screenshot("editor-" + k + "-large")
                .run(() -> Minecraft.getInstance().setScreen(null))
                .run(() -> window(854, 480, 0)).wait(10)
                // The mode HUD: a pending selection with its hints, then a running operation and its result line.
                .run(() -> {
                    ClientModeState.setMode(BuildModes.FILL);
                    ClientModeState.setAnchors(List.of(new BlockPos(-3, -60, 4), new BlockPos(4, -57, 9)));
                    ClientModeState.setPending(ClientModeState.Pending.SELECTED);
                    // Toasts at the top right: the chip must stay clear of them.
                    final Minecraft mc = Minecraft.getInstance();
                    SystemToast.add(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.translatable("slate_building.ui.menu.title"), Component.translatable("slate_building.ui.hint.menu"));
                })
                .wait(30).screenshot("hud-" + k + "-selection")
                // The GUI of a 1080p screen at GUI scale 4: the chip stays centred, narrower, its text wrapped.
                .run(() -> window(960, 540, 2)).wait(20).screenshot("hud-" + k + "-narrow")
                .run(() -> window(854, 480, 0)).wait(10)
                .run(() -> {
                    ClientModeState.setPending(ClientModeState.Pending.APPLYING);
                    ClientModeState.setProgress(new ClientModeState.Progress(1, 150, 384, "fill"));
                    ClientModeState.onResult(new OpResult(0, "fill", 372, 0, 12, "slate_building.ui.harness.filled", List.of("372")));
                })
                .wait(30).screenshot("hud-" + k + "-progress")
                .run(() -> {
                    // A refusal: its block name arrives as a "lang:" argument and must read as a name, with the error look.
                    ClientModeState.setProgress(null);
                    ClientModeState.onResult(new OpResult(0, "fill", 0, 0, 0, "slate_building.error.not_enough",
                        List.of("lang:block.minecraft.oak_planks", "12", "384")));
                    final OpResult r = ClientModeState.lastResult();
                    check("a refusal reads as text and counts as an error", r != null && OpMessages.isError(r)
                        && !OpMessages.describe(r).getString().contains("lang:"));
                })
                .wait(20).screenshot("hud-" + k + "-refused")
                .run(() -> {
                    ClientModeState.setProgress(null);
                    ClientModeState.clearSelection();
                    ClientModeState.setMode(null);
                }).wait(10);
        }
        s.skin("DARK");
    }

    private UiHarness() {}
}
