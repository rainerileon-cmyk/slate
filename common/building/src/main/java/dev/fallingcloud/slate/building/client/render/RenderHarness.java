package dev.fallingcloud.slate.building.client.render;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.block.ShapeBlockEntity;
import dev.fallingcloud.slate.building.client.BuildingHarness;
import dev.fallingcloud.slate.building.client.mode.ClientModeState;
import dev.fallingcloud.slate.building.config.PreviewSettings;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.ModeParams;
import dev.fallingcloud.slate.building.registry.BuildingBlocks;
import dev.fallingcloud.slate.building.registry.BuildingComponents;
import dev.fallingcloud.slate.building.registry.BuildingItems;
import dev.fallingcloud.slate.building.registry.RegistryRef;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.theme.Colors;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dev harness scenario {@code render} ({@code -PbuildingHarness=render}): builds a showcase of every shape in seven
 * materials that stress the renderer (stone, oak planks, tinted grass, translucent glass, an axis-dependent log,
 * light-emitting glowstone, wool) directly on the server (block states + block-entity materials, no placement code
 * involved) plus an adjacency row (face culling between neighbours), and screenshots ({@code building-render-*.png}):
 * the showcase from five angles, breaking particles, every shape item in five materials and unset, the placement
 * ghost (with a mirror symmetry active; oak stairs and our stone stairs; both skins), the overlays (animated box,
 * label, marker, line, mirror plane), planned-result ghosts in every style (shaded and through the shader-pack
 * fallback), the hull mode above {@code maxBlocks} and a ghost inside a grass tuft (the z-fighting case). The
 * player stands on invisible barriers placed under each camera position.
 */
final class RenderHarness {

    private static final Block[] MATERIALS = {
        Blocks.STONE, Blocks.OAK_PLANKS, Blocks.GRASS_BLOCK, Blocks.GLASS, Blocks.OAK_LOG, Blocks.GLOWSTONE, Blocks.WHITE_WOOL
    };
    private static final int Y = -60;

    static void register() {
        BuildingHarness.register("render", RenderHarness::build);
    }

    private static void build(final BuildingHarness.Script s) {
        final Runnable demo = RenderHarness::submitDemo;
        s.log("render: showcase")
            .command("time set noon")
            .command("weather clear")
            .command("gamerule doDaylightCycle false")
            .command("fill -16 -61 2 16 -61 18 minecraft:smooth_stone")
            .command("fill -9 -61 -15 12 -61 -5 minecraft:stone_bricks")
            .command("setblock -8 -61 -8 minecraft:grass_block")
            .command("setblock -8 -60 -8 minecraft:short_grass")
            .command("fill -3 -60 -12 -1 -59 -11 minecraft:stone_bricks")
            .command("setblock 6 -60 -10 minecraft:stone")
            .command("setblock 7 -60 -10 minecraft:stone")
            .command("setblock 8 -60 -10 minecraft:cobblestone");
        server(s, RenderHarness::placeShowcase);
        s.command("item replace entity @s hotbar.0 with minecraft:oak_stairs 64")
            .command(item(1, "stairs", "stone"))
            .command(item(2, "vertical_slab", "oak_planks"))
            .command(item(3, "wall", "grass_block"))
            .command(item(4, "fence", "glass"))
            .command(item(5, "step", "glowstone"))
            .command(item(6, "post", "oak_log"))
            .command(item(7, "panel", "white_wool"))
            .command("item replace entity @s hotbar.8 with slate_building:slab")
            .run(() -> {
                final Minecraft mc = Minecraft.getInstance();
                mc.levelRenderer.allChanged();
                if (mc.player != null) mc.player.getInventory().selected = 1;
                mc.getToasts().clear();
                PreviewSettings.current().enabled = false;   // the showcase shots show blocks only
            })
            .wait(60);
        camera(s, 0.5, -53, 26.5, new Vec3(0.5, Y, 10)).wait(40).screenshot("render-overview");
        camera(s, 17.5, -55, -2.5, new Vec3(0.5, Y, 10)).wait(40).screenshot("render-back");
        camera(s, -7.5, -54, 19.5, new Vec3(-7.5, Y, 10)).wait(40).screenshot("render-closeup-left");
        s.run(RenderHarness::breakParticles).wait(4).screenshot("render-particles").wait(40);
        camera(s, -10.5, -57, 21.5, new Vec3(-10.5, Y + 0.5, 18.5)).wait(30).screenshot("render-adjacent");
        camera(s, 6.5, -54, 19.5, new Vec3(6.5, Y, 10)).wait(40).screenshot("render-closeup-right");
        camera(s, -3.5, -57, 1.5, new Vec3(-3.5, Y, 8)).wait(40).screenshot("render-closeup-front");
        s.run(() -> SlateEvents.HUD_RENDER.register(ITEMS_SHEET)).wait(10).screenshot("render-items")
            .run(() -> SlateEvents.HUD_RENDER.unregister(ITEMS_SHEET));

        s.log("render: ghosts and overlays")
            .run(() -> {
                final Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.getInventory().selected = 0;
                PreviewSettings.current().enabled = true;
                ClientModeState.setSymmetry(new ClientModeState.Symmetry(BuildModes.MIRROR_MODE,
                    ModeParams.defaults(BuildModes.MIRROR_MODE).set("axis", "X"), new BlockPos(0, Y, -9)));
                BuildingRender.FRAME.register(demo);
            });
        camera(s, 1.5, -59, -4.5, new Vec3(2.5, Y, -7.5)).wait(30).screenshot("render-ghost");
        s.run(() -> {
            final Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.getInventory().selected = 1;
        }).wait(20).screenshot("render-ghost-shape")
            .skin("VANILLA").wait(20).screenshot("render-ghost-vanilla").skin("DARK")
            .run(() -> {
                ClientModeState.setSymmetry(null);
                PreviewSettings.current().enabled = false;
                plan = true;
            });
        camera(s, 7.5, -57, -4.5, new Vec3(7.0, -59.5, -10.0)).wait(30).screenshot("render-plan");
        s.run(() -> GhostRenderer.forceVanillaPath = true).wait(5).screenshot("render-plan-fallback")
            .run(() -> GhostRenderer.forceVanillaPath = false);
        s.run(() -> PreviewSettings.current().maxBlocks = 10).wait(10).screenshot("render-hull")
            .run(() -> {
                PreviewSettings.current().maxBlocks = new PreviewSettings().maxBlocks;
                PreviewSettings.current().enabled = true;
                plan = false;
                final Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.getInventory().selected = 0;
            });
        camera(s, -7.5, -60, -4.5, new Vec3(-7.5, -59.6, -7.5)).wait(25).screenshot("render-grass");
        s.run(() -> {
            BuildingRender.FRAME.unregister(demo);
        }).log("render: done");
    }

    /** Every shape (columns, FULL first) in every material (rows), on the smooth stone platform. */
    private static void placeShowcase(final ServerLevel level) {
        final Shape[] shapes = Shape.values();
        for (int r = 0; r < MATERIALS.length; r++) {
            final BlockState material = MATERIALS[r].defaultBlockState();
            for (int c = 0; c < shapes.length; c++) {
                final BlockPos pos = new BlockPos(-13 + 2 * c, Y, 4 + 2 * r);
                if (shapes[c] == Shape.FULL) {
                    level.setBlock(pos, material, Block.UPDATE_CLIENTS);
                    continue;
                }
                final RegistryRef<? extends Block> ref = BuildingBlocks.forShape(shapes[c]);
                if (ref == null || !ref.isBound()) continue;
                placeShape(level, pos, showcaseState(ref.get(), shapes[c]), material);
            }
        }
        // Adjacency (face culling between neighbours): glass | glass stairs | glass slab | glass, and stone stairs on stone.
        final BlockState glass = Blocks.GLASS.defaultBlockState();
        final RegistryRef<? extends Block> stairs = BuildingBlocks.forShape(Shape.STAIRS);
        final RegistryRef<? extends Block> slab = BuildingBlocks.forShape(Shape.SLAB);
        if (stairs != null && stairs.isBound() && slab != null && slab.isBound()) {
            level.setBlock(new BlockPos(-13, Y, 18), glass, Block.UPDATE_CLIENTS);
            placeShape(level, new BlockPos(-12, Y, 18), with(stairs.get().defaultBlockState(), "facing", "west"), glass);
            placeShape(level, new BlockPos(-11, Y, 18), with(slab.get().defaultBlockState(), "type", "bottom"), glass);
            level.setBlock(new BlockPos(-10, Y, 18), glass, Block.UPDATE_CLIENTS);
            level.setBlock(new BlockPos(-8, Y, 18), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            placeShape(level, new BlockPos(-8, Y + 1, 18), with(stairs.get().defaultBlockState(), "facing", "north"), Blocks.STONE.defaultBlockState());
            placeShape(level, new BlockPos(-7, Y, 18), with(slab.get().defaultBlockState(), "type", "top"), Blocks.STONE.defaultBlockState());
        }
    }

    /** Sets a shape block exactly as given (no neighbour updates reshaping it) and gives it its material. */
    private static void placeShape(final ServerLevel level, final BlockPos pos, final BlockState shape, final BlockState material) {
        level.setBlock(pos, shape, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        if (level.getBlockEntity(pos) instanceof ShapeBlockEntity be) be.setMaterial(material);
    }

    /** Breaking particles of a few shapes (grass stairs, glowstone slab, log vertical slab): they must show the material. */
    private static void breakParticles() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (final BlockPos pos : List.of(new BlockPos(-11, Y, 8), new BlockPos(-9, Y, 14), new BlockPos(-7, Y, 12), new BlockPos(-11, Y, 16))) {
            mc.particleEngine.destroy(pos, mc.level.getBlockState(pos));
        }
    }

    /** A readable state per shape, set by property name so it works with the placeholders and the real blocks. */
    private static BlockState showcaseState(final Block block, final Shape shape) {
        final BlockState s = block.defaultBlockState();
        return switch (shape) {
            case STAIRS -> with(s, "facing", "east", "half", "bottom");
            case SLAB -> with(s, "type", "bottom");
            case WALL -> with(s, "up", "true", "east", "low", "west", "low");
            case FENCE, PANE -> with(s, "east", "true", "west", "true");
            case FENCE_GATE -> with(s, "facing", "south");
            case VERTICAL_SLAB, VERTICAL_STAIRS, STEP -> with(s, "facing", "north");
            case LAYER -> with(s, "layers", "3");
            default -> s;
        };
    }

    private static BlockState with(final BlockState state, final String... pairs) {
        BlockState out = state;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            final var p = out.getBlock().getStateDefinition().getProperty(pairs[i]);
            if (p != null) out = set(out, p, pairs[i + 1]);
        }
        return out;
    }

    private static <T extends Comparable<T>> BlockState set(final BlockState s, final net.minecraft.world.level.block.state.properties.Property<T> p,
                                                            final String v) {
        return p.getValue(v).map(val -> s.setValue(p, val)).orElse(s);
    }

    // ---- the per-frame demo (overlays + planned ghosts) ----

    /** Whether the planned-result ghosts are part of the demo (the plan and hull shots). */
    private static boolean plan;

    private static void submitDemo() {
        final int accent = OverlayRenderer.accent();
        // An area selection around the little stone-brick structure, its size label, an anchor and a guide line.
        final AABB selection = new AABB(-3, Y, -12, 0, Y + 2, -10);
        OverlayRenderer.box(selection, accent, true);
        OverlayRenderer.label(new Vec3(-1.5, Y + 2.6, -11), Component.literal("3 × 2 × 2 · 12 blocks"), 0xFFFFFFFF);
        OverlayRenderer.marker(new BlockPos(3, Y, -12), accent);
        OverlayRenderer.line(new Vec3(3.5, Y + 0.5, -11.5), new Vec3(0, Y + 1, -11), Colors.withAlpha(accent, 0xC0));
        // The mirror plane of the active symmetry (through the middle of the centre block).
        if (ClientModeState.symmetry() != null) {
            OverlayRenderer.plane(Direction.Axis.X, 0.5, new AABB(0.5, Y, -14, 0.5, Y + 4, -5), Colors.withAlpha(accent, 0xFF));
        }
        if (plan) GhostRenderer.submitCached("harness-plan", 1, RenderHarness::planGhosts);
    }

    /** A planned result in every style: a plank wall (place), replacements, a removal, a blocked cell, shaped ghosts. */
    private static List<GhostRenderer.Ghost> planGhosts() {
        final List<GhostRenderer.Ghost> out = new ArrayList<>();
        final BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        for (int x = 5; x <= 9; x++) {
            for (int y = Y + 1; y <= Y + 2; y++) out.add(GhostRenderer.Ghost.of(new BlockPos(x, y, -10), planks, GhostRenderer.Style.PLACE));
        }
        out.add(GhostRenderer.Ghost.of(new BlockPos(5, Y, -10), planks, GhostRenderer.Style.PLACE));
        out.add(GhostRenderer.Ghost.of(new BlockPos(6, Y, -10), Blocks.GLASS.defaultBlockState(), GhostRenderer.Style.REPLACE));
        out.add(GhostRenderer.Ghost.of(new BlockPos(7, Y, -10), planks, GhostRenderer.Style.REPLACE));
        out.add(GhostRenderer.Ghost.of(new BlockPos(8, Y, -10), Blocks.COBBLESTONE.defaultBlockState(), GhostRenderer.Style.REMOVE));
        out.add(GhostRenderer.Ghost.of(new BlockPos(9, Y, -10), planks, GhostRenderer.Style.INVALID));
        final RegistryRef<? extends Block> stairs = BuildingBlocks.forShape(Shape.STAIRS);
        final RegistryRef<? extends Block> slab = BuildingBlocks.forShape(Shape.SLAB);
        if (stairs != null && stairs.isBound() && slab != null && slab.isBound()) {
            final BlockState shape = with(stairs.get().defaultBlockState(), "facing", "west");
            out.add(new GhostRenderer.Ghost(new BlockPos(5, Y, -8), shape, Blocks.GLASS.defaultBlockState(), GhostRenderer.Style.PLACE));
            out.add(new GhostRenderer.Ghost(new BlockPos(6, Y, -8), shape, Blocks.GRASS_BLOCK.defaultBlockState(), GhostRenderer.Style.PLACE));
            out.add(new GhostRenderer.Ghost(new BlockPos(7, Y, -8), with(slab.get().defaultBlockState(), "type", "bottom"),
                Blocks.WHITE_WOOL.defaultBlockState(), GhostRenderer.Style.PLACE));
        }
        return out;
    }

    // ---- item sheet (HUD) ----

    private static final Block[] SHEET_MATERIALS = {Blocks.STONE, Blocks.OAK_PLANKS, Blocks.GRASS_BLOCK, Blocks.GLASS, Blocks.OAK_LOG};

    /** Every shape item in a few materials (and without one), drawn large on the HUD, for the item-model check. */
    private static final SlateEvents.HudRender ITEMS_SHEET = (g, partialTick) -> {
        final Shape[] shapes = Shape.values();
        for (int row = 0; row <= SHEET_MATERIALS.length; row++) {
            for (int c = 1; c < shapes.length; c++) {
                final var ref = BuildingItems.forShape(shapes[c]);
                if (ref == null || !ref.isBound()) continue;
                final ItemStack stack = new ItemStack(ref.get());
                if (row < SHEET_MATERIALS.length) stack.set(BuildingComponents.MATERIAL.get(), BuiltInRegistries.BLOCK.getKey(SHEET_MATERIALS[row]));
                g.pose().pushPose();
                g.pose().translate(8 + (c - 1) * 36, 8 + row * 36, 0);
                g.pose().scale(2F, 2F, 2F);
                g.renderItem(stack, 0, 0);
                g.pose().popPose();
            }
        }
    };

    // ---- helpers ----

    /**
     * Puts the player at {@code (x, feetY, z)} looking at {@code target}, standing on an (invisible) barrier so it
     * cannot fall; {@code feetY} is a whole number (the top of the barrier or of the ground).
     */
    private static BuildingHarness.Script camera(final BuildingHarness.Script s, final double x, final int feetY, final double z, final Vec3 target) {
        final double dx = target.x - x, dy = target.y - (feetY + 1.62), dz = target.z - z;
        final double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        final double pitch = Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        final BlockPos below = BlockPos.containing(x, feetY - 1, z);
        return s.command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:barrier keep", below.getX(), below.getY(), below.getZ()))
            .command(String.format(Locale.ROOT, "tp @s %.3f %d %.3f %.2f %.2f", x, feetY, z, yaw, pitch));
    }

    private static String item(final int slot, final String shape, final String material) {
        return "item replace entity @s hotbar." + slot + " with slate_building:" + shape + "[slate_building:material=\"minecraft:" + material + "\"] 64";
    }

    /** Runs {@code action} on the integrated server's thread and waits for it. */
    private static void server(final BuildingHarness.Script s, final Consumer<ServerLevel> action) {
        final AtomicReference<CompletableFuture<Void>> pending = new AtomicReference<>();
        s.run(() -> {
            final IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null) throw new IllegalStateException("no integrated server");
            pending.set(server.submit(() -> {
                try {
                    action.accept(server.overworld());
                } catch (final RuntimeException e) {
                    SlateBuilding.LOGGER.error("[BuildingHarness] render: server step failed", e);
                }
            }));
        }).waitUntil(() -> pending.get() != null && pending.get().isDone(), 400);
    }

    private RenderHarness() {}
}
