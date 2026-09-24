package dev.fallingcloud.slate.building.client.ui;

import dev.fallingcloud.slate.building.chisel.ChiselGroups;
import dev.fallingcloud.slate.building.client.BuildingHarness;
import dev.fallingcloud.slate.building.client.menu.BuildMenuScreen;
import dev.fallingcloud.slate.building.client.menu.WheelEditorScreen;
import dev.fallingcloud.slate.building.client.mode.ClientModeState;
import dev.fallingcloud.slate.building.client.settings.BuildingSettingsScreen;
import dev.fallingcloud.slate.building.client.wheel.RadialWheel;
import dev.fallingcloud.slate.building.client.wheel.WheelOverlay;
import dev.fallingcloud.slate.building.client.wheel.WheelSources;
import dev.fallingcloud.slate.building.client.wheel.WheelTarget;
import dev.fallingcloud.slate.building.net.OpResult;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.building.variant.Variant;
import java.util.List;
import java.util.Optional;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

/**
 * Dev-harness scenarios of the UI ({@code -PbuildingHarness=wheel,menu,input}): both skins of the swap wheel (every page,
 * hovered slices, the in-world reshape wheel) and of the build menu, the wheel editor, the standalone settings and
 * the mode HUD. Until every area is merged, a {@link WheelSources} layer answers what the variant registry and the
 * chisel index do not know yet (vanilla oak / stone-brick variants, a sample chisel group); the live registries
 * always win, so after the merge the shots show the real thing.
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
                WheelSources.layer(DEMO);
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
                .run(() -> WheelOverlay.INSTANCE.debugPage(2)).wait(30)
                .run(() -> WheelOverlay.INSTANCE.debugPoint(3)).wait(20).screenshot("wheel-" + k + "-chisel")
                .run(() -> WheelOverlay.INSTANCE.close(false)).wait(20)
                // In-world reshape wheel on the stone brick stairs in front of the player.
                .run(() -> WheelOverlay.INSTANCE.openFor(new WheelTarget(WheelTarget.Source.WORLD, Blocks.STONE_BRICKS, Shape.STAIRS, 1, -1,
                    new BlockPos(0, -60, 5)), true)).wait(30)
                .run(() -> WheelOverlay.INSTANCE.debugPoint(1)).wait(20).screenshot("wheel-" + k + "-world")
                .run(() -> WheelOverlay.INSTANCE.close(false)).wait(20);
        }
        s.skin("DARK").run(() -> WheelSources.layer(null));
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
            .run(() -> check("alt is claimed", dev.fallingcloud.slate.building.client.input.ExclusiveKeys.isHeldExclusively(
                dev.fallingcloud.slate.building.client.input.BuildKeys.SWAP)))
            .run(() -> dev.fallingcloud.slate.building.client.input.BuildInput.fireMouseLook(170, 10)).wait(12)
            .screenshot("input-wheel-look")
            .run(() -> dev.fallingcloud.slate.building.client.input.BuildInput.fireScroll(0, -1)).wait(12)
            .screenshot("input-wheel-scroll")
            .run(() -> check("scroll stays in the hotbar slot", Minecraft.getInstance().player.getInventory().selected == 0))
            .run(() -> KeyMapping.set(alt, false)).wait(10)
            .run(() -> check("release closes the wheel", !WheelOverlay.INSTANCE.isOpen()))
            .wait(20)
            .run(() -> KeyMapping.click(r)).wait(10)
            .run(() -> check("R opens the build menu", Minecraft.getInstance().screen instanceof BuildMenuScreen)).wait(20)
            .run(() -> {
                final var screen = Minecraft.getInstance().screen;
                if (screen != null) screen.keyPressed(GLFW.GLFW_KEY_R, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_R), 0);
            }).wait(3)
            .run(() -> check("R closes the build menu", Minecraft.getInstance().screen == null))
            // Survival reach is 4.5 blocks: pick a stair right in front of the player.
            .command("setblock 0 -60 3 minecraft:stone_brick_stairs")
            .command("tp @s 0.5 -60 0.5 0 30")
            .command("gamemode survival").wait(10)
            .run(() -> dev.fallingcloud.slate.building.client.input.BuildInput.firePickBlock())
            .run(() -> check("pick block selects the stone bricks slot", Minecraft.getInstance().player.getInventory().selected == 1))
            .command("gamemode creative")
            .command("setblock 0 -60 3 minecraft:air")
            .command("tp @s 0.5 -60 0.5 0 12")
            .run(() -> WheelSources.layer(null));
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
                .run(() -> { if (Minecraft.getInstance().screen instanceof BuildMenuScreen m) m.debugSelect("paste"); }).wait(25)
                .screenshot("menu-" + k + "-options")
                .run(() -> { if (Minecraft.getInstance().screen instanceof BuildMenuScreen m) m.debugSelect("stack"); }).wait(25)
                .screenshot("menu-" + k + "-stack")
                .run(() -> { if (Minecraft.getInstance().screen instanceof BuildMenuScreen m) m.debugSearch("slab"); }).wait(25)
                .screenshot("menu-" + k + "-search")
                .run(() -> { if (Minecraft.getInstance().screen instanceof BuildMenuScreen m) m.debugSearch(""); })
                .run(() -> Minecraft.getInstance().setScreen(new WheelEditorScreen(Minecraft.getInstance().screen))).run(UiHarness::parkMouse).wait(40)
                .screenshot("editor-" + k)
                .run(() -> Minecraft.getInstance().setScreen(new BuildingSettingsScreen(null))).run(UiHarness::parkMouse).wait(40)
                .screenshot("settings-" + k)
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
                })
                .wait(30).screenshot("hud-" + k + "-selection")
                .run(() -> {
                    ClientModeState.setPending(ClientModeState.Pending.APPLYING);
                    ClientModeState.setProgress(new ClientModeState.Progress(1, 150, 384, "fill"));
                    ClientModeState.onResult(new OpResult(0, "fill", 372, 0, 12, "slate_building.ui.harness.filled", List.of("372")));
                })
                .wait(30).screenshot("hud-" + k + "-progress")
                .run(() -> {
                    ClientModeState.setProgress(null);
                    ClientModeState.clearSelection();
                    ClientModeState.setMode(null);
                }).wait(10);
        }
        s.skin("DARK").run(() -> WheelSources.layer(null));
    }

    // ------------------------------------------------------------------ demo knowledge (harness only)

    private static final WheelSources.Source DEMO = new WheelSources.Source() {
        @Override
        public Optional<Variant> identify(final ItemStack stack) {
            if (!(stack.getItem() instanceof BlockItem bi)) return Optional.empty();
            return identify(bi.getBlock());
        }

        @Override
        public Optional<Variant> identify(final BlockGetter level, final BlockPos pos) {
            final BlockState state = level.getBlockState(pos);
            return identify(state.getBlock());
        }

        private Optional<Variant> identify(final Block block) {
            if (block == Blocks.OAK_STAIRS) return Optional.of(new Variant(Blocks.OAK_PLANKS, Shape.STAIRS));
            if (block == Blocks.OAK_SLAB) return Optional.of(new Variant(Blocks.OAK_PLANKS, Shape.SLAB));
            if (block == Blocks.OAK_FENCE) return Optional.of(new Variant(Blocks.OAK_PLANKS, Shape.FENCE));
            if (block == Blocks.STONE_BRICK_STAIRS) return Optional.of(new Variant(Blocks.STONE_BRICKS, Shape.STAIRS));
            if (block == Blocks.STONE_BRICK_SLAB) return Optional.of(new Variant(Blocks.STONE_BRICKS, Shape.SLAB));
            if (block == Blocks.STONE_BRICK_WALL) return Optional.of(new Variant(Blocks.STONE_BRICKS, Shape.WALL));
            if (block.defaultBlockState().isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                && block.asItem() != Items.AIR) {
                return Optional.of(Variant.full(block));
            }
            return Optional.empty();
        }

        @Override
        public boolean isAvailable(final Block material, final Shape shape) {
            return true;
        }

        @Override
        public ItemStack stackFor(final Block material, final Shape shape, final int count) {
            if (shape == Shape.FULL) return new ItemStack(material, Math.max(1, count));
            final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(material);
            String base = id.getPath();
            if (base.endsWith("_planks")) base = base.substring(0, base.length() - "_planks".length());
            else if (base.endsWith("s")) base = base.substring(0, base.length() - 1);
            final Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), base + "_" + shape.id()));
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, count));
        }

        @Override
        public List<ChiselGroups.Group> chiselGroups(final Block material) {
            if (material == Blocks.OAK_PLANKS || material == Blocks.SPRUCE_PLANKS) {
                return List.of(new ChiselGroups.Group("demo", Component.literal("Planks (demo)"), List.of(Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS,
                    Blocks.BIRCH_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.MANGROVE_PLANKS, Blocks.CHERRY_PLANKS)));
            }
            if (material == Blocks.STONE_BRICKS) {
                return List.of(new ChiselGroups.Group("demo", Component.literal("Stone bricks (demo)"), List.of(Blocks.STONE, Blocks.STONE_BRICKS,
                    Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS, Blocks.SMOOTH_STONE)));
            }
            return List.of();
        }
    };

    private UiHarness() {}
}
