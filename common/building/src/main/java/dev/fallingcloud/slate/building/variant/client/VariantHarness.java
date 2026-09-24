package dev.fallingcloud.slate.building.variant.client;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.block.LayerBlock;
import dev.fallingcloud.slate.building.block.ShapeBehaviour;
import dev.fallingcloud.slate.building.block.ShapeBlockEntity;
import dev.fallingcloud.slate.building.block.ShapeBoxes;
import dev.fallingcloud.slate.building.block.VerticalSlabBlock;
import dev.fallingcloud.slate.building.client.BuildingHarness;
import dev.fallingcloud.slate.building.item.ShapeBlockItem;
import dev.fallingcloud.slate.building.mixin.variant.CreativeModeTabsAccessor;
import dev.fallingcloud.slate.building.net.ReshapeTarget;
import dev.fallingcloud.slate.building.net.SwapHeld;
import dev.fallingcloud.slate.building.registry.BuildingBlocks;
import dev.fallingcloud.slate.building.registry.BuildingItems;
import dev.fallingcloud.slate.building.registry.RegistryRef;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantActions;
import dev.fallingcloud.slate.building.variant.VariantCreativeTab;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Dev-harness scenario {@code variants} ({@code -PbuildingHarness=variants}): places every shape in eight materials
 * as a grid (shapes along x, materials along z) plus connection / merge demos, runs the variant checks through the
 * real code paths (item placement, loot, survival breaking, recipes, swap and reshape handlers) and logs one
 * {@code PASS}/{@code FAIL} line per check and a summary, then takes two screenshots.
 */
final class VariantHarness {

    private static final Block[] MATERIALS = {
        Blocks.STONE, Blocks.OAK_PLANKS, Blocks.GRASS_BLOCK, Blocks.GLASS, Blocks.OAK_LOG, Blocks.GLOWSTONE, Blocks.DIRT, Blocks.DEEPSLATE_TILES};
    private static final int Y = -60;
    private static final int X0 = -13;
    private static final int Z0 = 3;
    private static final int DEMO_Z = Z0 + 2 * MATERIALS.length + 1;

    private static final AtomicInteger PASSED = new AtomicInteger();
    private static final AtomicInteger FAILED = new AtomicInteger();
    private static final List<String> FAILURES = java.util.Collections.synchronizedList(new ArrayList<>());
    private static volatile @Nullable CompletableFuture<Void> pending;

    private record Ctx(IntegratedServer server, ServerLevel level, ServerPlayer player) {}

    private VariantHarness() {}

    static void register() {
        BuildingHarness.register("variants", VariantHarness::script);
    }

    private static void script(final BuildingHarness.Script s) {
        s.log("variants: preparing the test floor")
            .command("time set noon")
            .command("weather clear")
            .command("gamemode creative")
            .command("fill -16 -61 -12 16 -61 24 minecraft:smooth_stone")
            .command("fill -16 -60 -12 16 -44 24 minecraft:air")
            .command("tp @s 0 -60 -8 0 0");
        onServer(s, VariantHarness::buildGrid);
        s.wait(10);
        onServer(s, VariantHarness::registryChecks);
        onServer(s, VariantHarness::dropChecks);
        onServer(s, VariantHarness::actionChecks);
        onServer(s, VariantHarness::geometryChecks);
        onServer(s, VariantHarness::regressionChecks);
        s.run(VariantHarness::deleteNativesChecks)
            .run(VariantHarness::summary)
            .command("gamemode creative")
            .command("tp @s 0 -51 -5 0 40")
            .run(() -> Minecraft.getInstance().options.hideGui = true)
            .wait(90)
            .screenshot("variants")
            .command("tp @s -8 -57 0 20 45")
            .wait(40)
            .screenshot("variants-close")
            .run(() -> Minecraft.getInstance().options.hideGui = false);
    }

    /** Appends a step that runs {@code task} on the integrated server and waits for it. */
    private static void onServer(final BuildingHarness.Script s, final Consumer<Ctx> task) {
        s.run(() -> {
            final Minecraft mc = Minecraft.getInstance();
            final IntegratedServer server = mc.getSingleplayerServer();
            if (server == null || mc.player == null) {
                check("server available", false, "no integrated server");
                pending = CompletableFuture.completedFuture(null);
                return;
            }
            final java.util.UUID id = mc.player.getUUID();
            pending = server.submit(() -> {
                final ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null) {
                    check("server player", false, "missing");
                    return;
                }
                try {
                    task.accept(new Ctx(server, player.serverLevel(), player));
                } catch (final RuntimeException e) {
                    SlateBuilding.LOGGER.error("[BuildingHarness] variants: server step failed", e);
                    check("server step", false, e.toString());
                }
            });
        }).waitUntil(() -> pending != null && pending.isDone(), 1200);
    }

    // ------------------------------------------------------------------------------------------------ grid

    private static BlockPos gridPos(final int material, final Shape shape) {
        return new BlockPos(X0 + 2 * shape.ordinal(), Y, Z0 + 2 * material);
    }

    private static void buildGrid(final Ctx c) {
        for (int m = 0; m < MATERIALS.length; m++) {
            for (final Shape shape : Shape.values()) {
                final BlockPos pos = gridPos(m, shape);
                if (place(c, MATERIALS[m], shape, pos) == null) check("place " + name(MATERIALS[m]) + " " + shape.id(), false, "placement refused");
            }
        }
        // Connections: our stone walls into native deepslate tile walls, native oak fences into our dirt fences,
        // our glass panes, a stone stair corner, a merged vertical slab and stacked layers.
        for (int x = -13; x <= -11; x++) place(c, Blocks.STONE, Shape.WALL, new BlockPos(x, Y, DEMO_Z));
        for (int x = -10; x <= -9; x++) place(c, Blocks.DEEPSLATE_TILES, Shape.WALL, new BlockPos(x, Y, DEMO_Z));
        for (int x = -7; x <= -6; x++) place(c, Blocks.OAK_PLANKS, Shape.FENCE, new BlockPos(x, Y, DEMO_Z));
        for (int x = -5; x <= -4; x++) place(c, Blocks.DIRT, Shape.FENCE, new BlockPos(x, Y, DEMO_Z));
        for (int x = -2; x <= 0; x++) place(c, Blocks.GLASS, Shape.PANE, new BlockPos(x, Y, DEMO_Z));
        place(c, Blocks.STONE, Shape.STAIRS, new BlockPos(2, Y, DEMO_Z));
        place(c, Blocks.STONE, Shape.STAIRS, new BlockPos(3, Y, DEMO_Z));
        final BlockPos vslab = new BlockPos(5, Y, DEMO_Z);
        place(c, Blocks.GRASS_BLOCK, Shape.VERTICAL_SLAB, vslab);
        mergeVerticalSlab(c, Blocks.GRASS_BLOCK, vslab);
        final BlockPos layers = new BlockPos(7, Y, DEMO_Z);
        for (int i = 0; i < 5; i++) addLayer(c, Blocks.GLOWSTONE, layers);
    }

    /** Places ({@code material}, {@code shape}) at {@code pos} through the item, as clicking the floor below would. */
    private static @Nullable BlockPos place(final Ctx c, final Block material, final Shape shape, final BlockPos pos) {
        return place(c, VariantRegistry.get().stackFor(material, shape, 1), pos);
    }

    /** Places {@code stack} at {@code pos} through its item, as clicking the floor below would. */
    private static @Nullable BlockPos place(final Ctx c, final ItemStack stack, final BlockPos pos) {
        if (!(stack.getItem() instanceof BlockItem item)) return null;
        final BlockPos below = pos.below();
        final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false);
        final BlockPlaceContext ctx = new BlockPlaceContext(c.level(), null, InteractionHand.MAIN_HAND, stack, hit);
        try {
            return item.place(ctx).consumesAction() ? ctx.getClickedPos() : null;
        } catch (final RuntimeException e) {
            SlateBuilding.LOGGER.error("[BuildingHarness] variants: placing {} threw", stack, e);
            return null;
        }
    }

    /** Clicks the open face of the single vertical slab at {@code pos} with the same material. */
    private static void mergeVerticalSlab(final Ctx c, final Block material, final BlockPos pos) {
        final BlockState state = c.level().getBlockState(pos);
        if (!(state.getBlock() instanceof VerticalSlabBlock)) return;
        final Direction open = state.getValue(VerticalSlabBlock.FACING).getOpposite();
        final ItemStack stack = VariantRegistry.get().stackFor(material, Shape.VERTICAL_SLAB, 1);
        final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), open, pos, false);
        ((BlockItem) stack.getItem()).place(new BlockPlaceContext(c.level(), null, InteractionHand.MAIN_HAND, stack, hit));
    }

    /** Clicks the top of the layer at {@code pos} (or the floor under it) with the same material. */
    private static void addLayer(final Ctx c, final Block material, final BlockPos pos) {
        final ItemStack stack = VariantRegistry.get().stackFor(material, Shape.LAYER, 1);
        final BlockState state = c.level().getBlockState(pos);
        final BlockHitResult hit = state.getBlock() instanceof LayerBlock
            ? new BlockHitResult(Vec3.atBottomCenterOf(pos).add(0, 2 * state.getValue(LayerBlock.LAYERS) / 16.0, 0), Direction.UP, pos, false)
            : new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, 0.5, 0), Direction.UP, pos.below(), false);
        ((BlockItem) stack.getItem()).place(new BlockPlaceContext(c.level(), null, InteractionHand.MAIN_HAND, stack, hit));
    }

    // ------------------------------------------------------------------------------------------------ registry and recipes

    private static void registryChecks(final Ctx c) {
        final VariantRegistry reg = VariantRegistry.get();
        final ItemStack oakStairs = reg.stackFor(Blocks.OAK_PLANKS, Shape.STAIRS, 1);
        check("oak_planks -> STAIRS resolves to minecraft:oak_stairs", oakStairs.is(Items.OAK_STAIRS), oakStairs);
        final ItemStack dirtStairs = reg.stackFor(Blocks.DIRT, Shape.STAIRS, 1);
        check("dirt -> STAIRS resolves to slate_building:stairs (dirt)",
            dirtStairs.is(BuildingItems.forShape(Shape.STAIRS).get()) && ShapeBlockItem.material(dirtStairs) == Blocks.DIRT, dirtStairs);
        identifies(Items.OAK_STAIRS, Blocks.OAK_PLANKS, Shape.STAIRS);
        identifies(Items.STONE_STAIRS, Blocks.STONE, Shape.STAIRS);
        identifies(Items.STONE_BRICK_SLAB, Blocks.STONE_BRICKS, Shape.SLAB);
        identifies(Items.SMOOTH_STONE_SLAB, Blocks.SMOOTH_STONE, Shape.SLAB);
        identifies(Items.COBBLESTONE_WALL, Blocks.COBBLESTONE, Shape.WALL);
        identifies(Items.NETHER_BRICK_FENCE, Blocks.NETHER_BRICKS, Shape.FENCE);
        identifies(Items.OAK_FENCE_GATE, Blocks.OAK_PLANKS, Shape.FENCE_GATE);
        identifies(Items.CUT_COPPER_SLAB, Blocks.CUT_COPPER, Shape.SLAB);
        identifies(Items.OAK_PLANKS, Blocks.OAK_PLANKS, Shape.FULL);
        for (final Block m : MATERIALS) check("material " + name(m), reg.isMaterial(m), "not a material");
        for (final Block b : new Block[] {Blocks.BEDROCK, Blocks.BUDDING_AMETHYST, Blocks.CHEST, Blocks.OAK_STAIRS, Blocks.TORCH, Blocks.SOUL_SAND}) {
            check("not a material: " + name(b), !reg.isMaterial(b), "counted as a material");
        }
        check("oak planks offer all 14 shapes", reg.shapesFor(Blocks.OAK_PLANKS).size() == Shape.values().length, reg.shapesFor(Blocks.OAK_PLANKS));
        check("native stone stairs realise (stone, STAIRS)", reg.nativeBlock(Blocks.STONE, Shape.STAIRS) == Blocks.STONE_STAIRS, reg.nativeBlock(Blocks.STONE, Shape.STAIRS));
        check("no native stone wall", reg.nativeBlock(Blocks.STONE, Shape.WALL) == null, reg.nativeBlock(Blocks.STONE, Shape.WALL));
        check("oak_stairs is a native variant item", reg.nativeVariantItems().contains(Items.OAK_STAIRS), "missing");

        recipe(c, "oak_slab", 3);
        recipe(c, "oak_stairs", 6);
        recipe(c, "oak_fence", 4);
        recipe(c, "oak_fence_gate", 2);
        recipe(c, "cobblestone_wall", 6);
        recipe(c, "nether_brick_fence", 4);
        recipe(c, "stone_slab_from_stone_stonecutting", 1);
        recipe(c, "stone_brick_slab_from_stone_bricks_stonecutting", 1);

        final List<ItemStack> showcase = new ArrayList<>();
        VariantCreativeTab.fill(new CreativeModeTab.ItemDisplayParameters(c.level().enabledFeatures(), true, c.level().registryAccess()),
            (stack, visibility) -> showcase.add(stack));
        check("creative showcase: 13 shapes x 2 materials", showcase.size() == 26, showcase.size());
    }

    private static void identifies(final Item item, final Block material, final Shape shape) {
        final Optional<Variant> v = VariantRegistry.get().identify(new ItemStack(item));
        check("identify " + BuiltInRegistries.ITEM.getKey(item).getPath() + " = " + name(material) + " " + shape.id(),
            v.isPresent() && v.get().material() == material && v.get().shape() == shape, v);
    }

    private static void recipe(final Ctx c, final String id, final int expected) {
        final Optional<RecipeHolder<?>> holder = c.server().getRecipeManager().byKey(ResourceLocation.withDefaultNamespace(id));
        final int count = holder.map(h -> h.value().getResultItem(c.level().registryAccess()).getCount()).orElse(-1);
        check("recipe minecraft:" + id + " makes " + expected, count == expected, count);
    }

    // ------------------------------------------------------------------------------------------------ drops

    private static void dropChecks(final Ctx c) {
        final ServerLevel level = c.level();
        final ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        final VariantRegistry reg = VariantRegistry.get();
        for (int m = 0; m < MATERIALS.length; m++) {
            final List<String> wrong = new ArrayList<>();
            int checked = 0;
            for (final Shape shape : Shape.values()) {
                if (shape == Shape.FULL) continue;   // the material itself keeps its vanilla loot
                final BlockPos pos = gridPos(m, shape);
                final BlockState state = level.getBlockState(pos);
                final BlockEntity be = level.getBlockEntity(pos);
                final List<ItemStack> drops = Block.getDrops(state, level, pos, be, c.player(), pickaxe);
                final int units = reg.units(state, be);
                if (!isExactly(drops, MATERIALS[m].asItem(), units)) wrong.add(shape.id() + "=" + drops);
                checked++;
            }
            check("drops of " + checked + " " + name(MATERIALS[m]) + " shapes = material x units", wrong.isEmpty(), wrong);
        }

        final BlockPos vslab = new BlockPos(5, Y, DEMO_Z);
        final BlockState merged = level.getBlockState(vslab);
        check("vertical slab double = 2 units",
            merged.getBlock() instanceof VerticalSlabBlock && !merged.getValue(VerticalSlabBlock.SINGLE) && reg.units(merged, level.getBlockEntity(vslab)) == 2, merged);
        check("vertical slab double drops 2 grass blocks",
            isExactly(Block.getDrops(merged, level, vslab, level.getBlockEntity(vslab), c.player(), pickaxe), Items.GRASS_BLOCK, 2), "wrong drops");
        final BlockPos layers = new BlockPos(7, Y, DEMO_Z);
        final BlockState layerState = level.getBlockState(layers);
        check("five glowstone layers = 5 units", layerState.getBlock() instanceof LayerBlock && reg.units(layerState, null) == 5, layerState);

        final BlockPos stoneVSlab = gridPos(0, Shape.VERTICAL_SLAB);
        check("our stone shape by hand drops nothing",
            Block.getDrops(level.getBlockState(stoneVSlab), level, stoneVSlab, level.getBlockEntity(stoneVSlab), c.player(), ItemStack.EMPTY).isEmpty(), "dropped");

        final BlockState glowSlab = level.getBlockState(gridPos(5, Shape.VERTICAL_SLAB));
        check("glowstone shape emits light 15", glowSlab.getLightEmission() == 15, glowSlab);
        check("glass shape is not opaque", !level.getBlockState(gridPos(3, Shape.STEP)).getValue(ShapeBehaviour.OPAQUE), level.getBlockState(gridPos(3, Shape.STEP)));
        check("stone shape is opaque", level.getBlockState(gridPos(0, Shape.STEP)).getValue(ShapeBehaviour.OPAQUE), level.getBlockState(gridPos(0, Shape.STEP)));

        // Survival-equivalent breaking through ServerPlayerGameMode (loot, harvest rules, loader break events).
        final ServerPlayer player = c.player();
        player.setGameMode(GameType.SURVIVAL);
        try {
            final BlockPos nativeDouble = new BlockPos(9, Y, DEMO_Z);
            level.setBlock(nativeDouble, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 3);
            if (dev.fallingcloud.slate.core.platform.SlatePlatform.get().isModLoaded("kleeslabs")) {
                // KleeSlabs (DF pack) breaks one half of a double slab by design: one unit, and a single slab stays.
                clearItems(level, nativeDouble);
                c.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
                c.player().gameMode.destroyBlock(nativeDouble);
                int planks = 0;
                for (final ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(nativeDouble).inflate(1.0))) {
                    if (e.getItem().is(Items.OAK_PLANKS)) planks += e.getItem().getCount();
                    e.discard();
                }
                // Whichever half it takes (or the whole block, if its ray misses), nothing is lost or made: 2 units in all.
                final BlockState left = level.getBlockState(nativeDouble);
                final int stays = left.is(Blocks.OAK_SLAB) ? VariantRegistry.get().units(left, null) : left.isAir() ? 0 : 99;
                check("survival break with KleeSlabs: a native double oak slab keeps its 2 units (planks dropped + slab left)",
                    planks >= 1 && planks + stays == 2, planks + " planks, " + left);
                level.setBlock(nativeDouble, Blocks.AIR.defaultBlockState(), 3);
            } else {
                survivalBreak(c, nativeDouble, new ItemStack(Items.DIAMOND_AXE), Items.OAK_PLANKS, 2, "native double oak slab");
            }
            survivalBreak(c, gridPos(7, Shape.VERTICAL_STAIRS), pickaxe.copy(), Items.DEEPSLATE_TILES, 1, "deepslate tile vertical stairs");
            survivalBreak(c, gridPos(6, Shape.STAIRS), ItemStack.EMPTY, Items.DIRT, 1, "dirt stairs by hand");
            survivalBreak(c, gridPos(0, Shape.STAIRS), pickaxe.copy(), Items.STONE, 1, "native stone stairs");
            survivalBreak(c, gridPos(0, Shape.PANEL), ItemStack.EMPTY, Items.STONE, 0, "stone panel by hand");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setGameMode(GameType.CREATIVE);
        }
        // Put the broken grid cells back for the screenshots.
        place(c, Blocks.DEEPSLATE_TILES, Shape.VERTICAL_STAIRS, gridPos(7, Shape.VERTICAL_STAIRS));
        place(c, Blocks.DIRT, Shape.STAIRS, gridPos(6, Shape.STAIRS));
        place(c, Blocks.STONE, Shape.STAIRS, gridPos(0, Shape.STAIRS));
        place(c, Blocks.STONE, Shape.PANEL, gridPos(0, Shape.PANEL));
        clearItems(level, gridPos(0, Shape.STAIRS));
    }

    private static void survivalBreak(final Ctx c, final BlockPos pos, final ItemStack tool, final Item expected, final int count, final String what) {
        final ServerLevel level = c.level();
        clearItems(level, pos);
        c.player().setItemInHand(InteractionHand.MAIN_HAND, tool);
        final boolean broken = c.player().gameMode.destroyBlock(pos);
        final Map<Item, Integer> dropped = new LinkedHashMap<>();
        for (final ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.0))) {
            dropped.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
            e.discard();
        }
        final boolean ok = broken && (count == 0 ? dropped.isEmpty() : dropped.size() == 1 && Objects.equals(dropped.get(expected), count));
        check("survival break: " + what + " drops " + count + " " + BuiltInRegistries.ITEM.getKey(expected).getPath(), ok, "broken=" + broken + " " + dropped);
    }

    private static void clearItems(final ServerLevel level, final BlockPos pos) {
        for (final ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.0))) e.discard();
    }

    private static boolean isExactly(final List<ItemStack> drops, final Item item, final int count) {
        return drops.size() == 1 && drops.get(0).is(item) && drops.get(0).getCount() == count;
    }

    // ------------------------------------------------------------------------------------------------ swap, reshape, split

    private static void actionChecks(final Ctx c) {
        final ServerPlayer player = c.player();
        final Inventory inv = player.getInventory();
        final int slot = inv.selected;
        final ItemStack before = inv.getItem(slot).copy();
        try {
            inv.setItem(slot, new ItemStack(Items.OAK_PLANKS, 10));
            VariantActions.swapHeld(new SwapHeld(slot, "slab"), player);
            check("swap oak planks x10 -> oak_slab x10", inv.getItem(slot).is(Items.OAK_SLAB) && inv.getItem(slot).getCount() == 10, inv.getItem(slot));
            VariantActions.swapHeld(new SwapHeld(slot, "vertical_slab"), player);
            final ItemStack vs = inv.getItem(slot);
            check("swap -> oak vertical slab x10 (ours)", vs.is(BuildingItems.forShape(Shape.VERTICAL_SLAB).get()) && vs.getCount() == 10
                && ShapeBlockItem.material(vs) == Blocks.OAK_PLANKS, vs);
            VariantActions.swapHeld(new SwapHeld(slot, "full"), player);
            check("swap back -> oak planks x10", inv.getItem(slot).is(Items.OAK_PLANKS) && inv.getItem(slot).getCount() == 10, inv.getItem(slot));
            inv.setItem(slot, new ItemStack(Items.TORCH, 3));
            VariantActions.swapHeld(new SwapHeld(slot, "slab"), player);
            check("swap refuses a torch", inv.getItem(slot).is(Items.TORCH) && inv.getItem(slot).getCount() == 3, inv.getItem(slot));
        } finally {
            inv.setItem(slot, before);
        }

        final ServerLevel level = c.level();
        final BlockPos r1 = new BlockPos(1, Y, -6);
        level.setBlock(r1, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 3);
        VariantActions.reshapeTarget(new ReshapeTarget(r1, "stairs"), player);
        check("reshape native double oak slab -> oak_stairs (creative)", level.getBlockState(r1).is(Blocks.OAK_STAIRS), level.getBlockState(r1));

        final BlockPos r2 = new BlockPos(-1, Y, -6);
        place(c, Blocks.DIRT, Shape.STAIRS, r2);
        VariantActions.reshapeTarget(new ReshapeTarget(r2, "vertical_slab"), player);
        final BlockState reshaped = level.getBlockState(r2);
        check("reshape dirt stairs -> dirt vertical slab, facing kept",
            reshaped.getBlock() instanceof VerticalSlabBlock && reshaped.getValue(VerticalSlabBlock.FACING) == Direction.NORTH
                && ShapeBlock.material(level, r2) != null && ShapeBlock.material(level, r2).is(Blocks.DIRT), reshaped);

        player.setGameMode(GameType.SURVIVAL);
        try {
            VariantActions.reshapeTarget(new ReshapeTarget(r1, "slab"), player);
            check("survival reshape without a hammer is refused", level.getBlockState(r1).is(Blocks.OAK_STAIRS), level.getBlockState(r1));
        } finally {
            player.setGameMode(GameType.CREATIVE);
        }

        // KleeSlabs-style split: a material-less slab item spawned in the block, then the double shrinks.
        final BlockPos r3 = new BlockPos(3, Y, -6);
        place(c, Blocks.DIRT, Shape.SLAB, r3);
        final ItemStack slabItem = VariantRegistry.get().stackFor(Blocks.DIRT, Shape.SLAB, 1);
        ((BlockItem) slabItem.getItem()).place(new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, slabItem,
            new BlockHitResult(Vec3.atBottomCenterOf(r3).add(0, 0.5, 0), Direction.UP, r3, false)));
        final BlockState doubled = level.getBlockState(r3);
        check("dirt slabs merge into a double", doubled.getBlock() instanceof SlabBlock && doubled.getValue(SlabBlock.TYPE) == SlabType.DOUBLE, doubled);
        clearItems(level, r3);
        final ItemEntity split = new ItemEntity(level, r3.getX() + 0.5, r3.getY() + 0.5, r3.getZ() + 0.5, new ItemStack(BuildingItems.forShape(Shape.SLAB).get()));
        level.addFreshEntity(split);
        level.setBlock(r3, doubled.setValue(SlabBlock.TYPE, SlabType.BOTTOM), 3);
        check("split slab drop becomes the material (KleeSlabs)", split.getItem().is(Items.DIRT) && split.getItem().getCount() == 1, split.getItem());
        split.discard();
    }

    // ------------------------------------------------------------------------------------------------ geometry

    private static void geometryChecks(final Ctx c) {
        for (final RegistryRef<? extends Block> ref : BuildingBlocks.all()) {
            final Block block = ref.get();
            if (!(block instanceof ShapeBlock shape)) continue;
            final boolean subsetOnly = shape.shape() == Shape.FENCE || shape.shape() == Shape.FENCE_GATE;
            final List<String> wrong = new ArrayList<>();
            int states = 0;
            for (final BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (shape.shape() == Shape.FENCE_GATE && state.getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN)) continue;   // open panels swing outside vanilla's outline
                if (shape.shape() == Shape.WALL && !vanillaModelMatchesOutline(state)) continue;
                states++;
                final List<net.minecraft.world.phys.AABB> boxes = shape.renderBoxes(state);
                final VoxelShape render = ShapeBoxes.toShape(boxes);
                final VoxelShape outline = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                final boolean ok = boxes != null && !boxes.isEmpty() && (subsetOnly
                    ? !Shapes.joinIsNotEmpty(render, outline, BooleanOp.ONLY_FIRST)
                    : !Shapes.joinIsNotEmpty(render, outline, BooleanOp.NOT_SAME));
                if (!ok && wrong.size() < 3) wrong.add(state.toString());
            }
            check("geometry " + shape.shape().id() + ": render boxes " + (subsetOnly ? "inside" : "=") + " outline (" + states + " states)", wrong.isEmpty(), wrong);
        }
    }

    /**
     * Wall states where vanilla's own model and outline agree: a post, or a straight post-less run with equal
     * heights. (Post-less single or mixed-height sides never occur from placement, and there vanilla's outline
     * reaches past the model's side ends, which the render boxes follow.)
     */
    private static boolean vanillaModelMatchesOutline(final BlockState state) {
        if (state.getValue(net.minecraft.world.level.block.WallBlock.UP)) return true;
        final var n = state.getValue(net.minecraft.world.level.block.WallBlock.NORTH_WALL);
        final var s = state.getValue(net.minecraft.world.level.block.WallBlock.SOUTH_WALL);
        final var e = state.getValue(net.minecraft.world.level.block.WallBlock.EAST_WALL);
        final var w = state.getValue(net.minecraft.world.level.block.WallBlock.WEST_WALL);
        final var none = net.minecraft.world.level.block.state.properties.WallSide.NONE;
        return (n != none && n == s && e == none && w == none) || (e != none && e == w && n == none && s == none);
    }

    // ------------------------------------------------------------------------------------------------ review regressions

    /** Row of the regression demos, in reach of the harness player (standing at 0.5, -60, -8.5). */
    private static final int REG_Z = -10;

    private static void regressionChecks(final Ctx c) {
        shapeMaterialChecks(c);
        reshapeLootChecks(c);
        legacySlabMergeChecks(c);
        rulesFingerprintChecks();
    }

    /** A shape can never hold another shape as its material (it would delegate to itself until the stack overflows). */
    private static void shapeMaterialChecks(final Ctx c) {
        final ServerLevel level = c.level();
        final Block stairsBlock = BuildingBlocks.forShape(Shape.STAIRS).get();
        final Block slabBlock = BuildingBlocks.forShape(Shape.SLAB).get();

        final ItemStack bad = ShapeBlockItem.withMaterial(new ItemStack(BuildingItems.forShape(Shape.SLAB).get()), stairsBlock);
        check("shape item with a shape material has no material", ShapeBlockItem.material(bad) == null, ShapeBlockItem.material(bad));
        final BlockPos itemPos = new BlockPos(-4, Y, REG_Z);
        check("shape item with a shape material places nothing", place(c, bad, itemPos) == null && level.getBlockState(itemPos).isAir(), level.getBlockState(itemPos));

        final BlockPos pos = new BlockPos(-2, Y, REG_Z);
        level.setBlock(pos, slabBlock.defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof ShapeBlockEntity be)) {
            check("shape slab has its block entity", false, level.getBlockEntity(pos));
            return;
        }
        be.setMaterial(Blocks.DIRT.defaultBlockState());
        be.setMaterial(stairsBlock.defaultBlockState());
        check("setMaterial refuses a shape block", be.material() == null, be.material());
        be.setMaterial(Blocks.DIRT.defaultBlockState());
        // A corrupt save / schematic / data packet: the same through load.
        final CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
        tag.put("material", NbtUtils.writeBlockState(stairsBlock.defaultBlockState()));
        be.loadWithComponents(tag, level.registryAccess());
        check("loading a shape material leaves the shape unset", be.material() == null, be.material());
        String thrown = null;
        try {
            final BlockState state = level.getBlockState(pos);
            state.getDestroyProgress(c.player(), level, pos);
            state.getMapColor(level, pos);
        } catch (final StackOverflowError | RuntimeException e) {
            thrown = e.toString();
        }
        check("mining / map colour of that shape do not recurse", thrown == null, thrown);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** Survival hammer reshape obeys the chisel's loot guard: no free silk touch on natural blocks. */
    private static void reshapeLootChecks(final Ctx c) {
        final ServerLevel level = c.level();
        final ServerPlayer player = c.player();
        final var ops = dev.fallingcloud.slate.building.config.BuildingServerSettings.local().ops();
        final boolean requireToolbox = ops.requireToolbox;
        final BlockPos stone = new BlockPos(0, Y, REG_Z);
        final BlockPos glass = new BlockPos(1, Y, REG_Z - 1);
        final BlockPos bricks = new BlockPos(2, Y, REG_Z);
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(glass, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(bricks, Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
        ops.requireToolbox = false;   // every tool at full tier, still survival (no creative bypass)
        player.setGameMode(GameType.SURVIVAL);
        try {
            VariantActions.reshapeTarget(new ReshapeTarget(stone, "stairs"), player);
            check("survival reshape of natural stone (drops cobblestone) is refused", level.getBlockState(stone).is(Blocks.STONE), level.getBlockState(stone));
            VariantActions.reshapeTarget(new ReshapeTarget(glass, "slab"), player);
            check("survival reshape of glass (drops nothing) is refused", level.getBlockState(glass).is(Blocks.GLASS), level.getBlockState(glass));
            VariantActions.reshapeTarget(new ReshapeTarget(bricks, "stairs"), player);
            check("survival reshape of stone bricks (drop themselves) works", level.getBlockState(bricks).is(Blocks.STONE_BRICK_STAIRS), level.getBlockState(bricks));
        } finally {
            ops.requireToolbox = requireToolbox;
            player.setGameMode(GameType.CREATIVE);
        }
        final boolean nativeStairs = dev.fallingcloud.slate.building.variant.VariantDrops.dropsOwnWorth(level, bricks, level.getBlockState(bricks), null, Blocks.STONE_BRICKS, 1);
        check("a native stair drops its own worth (unified to the material)", nativeStairs, level.getBlockState(bricks));
    }

    /** A leftover shape item of a material with a native slab merges into native half slabs (and still into its own). */
    private static void legacySlabMergeChecks(final Ctx c) {
        final ServerLevel level = c.level();
        final Item ourSlab = BuildingItems.forShape(Shape.SLAB).get();

        final BlockPos nativeHalf = new BlockPos(4, Y, REG_Z);
        level.setBlock(nativeHalf, Blocks.OAK_SLAB.defaultBlockState(), Block.UPDATE_ALL);
        final ItemStack legacy = ShapeBlockItem.withMaterial(new ItemStack(ourSlab, 2), Blocks.OAK_PLANKS);
        clickTop(c, legacy, nativeHalf);
        final BlockState merged = level.getBlockState(nativeHalf);
        check("our oak slab item completes a native oak half slab",
            merged.is(Blocks.OAK_SLAB) && merged.getValue(SlabBlock.TYPE) == SlabType.DOUBLE && level.getBlockState(nativeHalf.above()).isAir(), merged);
        check("that placement used one item", legacy.getCount() == 1, legacy.getCount());

        final BlockPos ourHalf = new BlockPos(6, Y, REG_Z);
        level.setBlock(ourHalf, ourSlabState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(ourHalf) instanceof ShapeBlockEntity be) be.setMaterial(Blocks.OAK_PLANKS.defaultBlockState());
        clickTop(c, ShapeBlockItem.withMaterial(new ItemStack(ourSlab), Blocks.OAK_PLANKS), ourHalf);
        final BlockState ours = level.getBlockState(ourHalf);
        check("our oak slab item still completes our own oak half slab",
            ours.getBlock() == BuildingBlocks.forShape(Shape.SLAB).get() && ours.getValue(SlabBlock.TYPE) == SlabType.DOUBLE
                && ShapeBlock.material(level, ourHalf) != null && ShapeBlock.material(level, ourHalf).is(Blocks.OAK_PLANKS), ours);
    }

    private static BlockState ourSlabState() {
        return BuildingBlocks.forShape(Shape.SLAB).get().defaultBlockState();
    }

    /** Clicks the top face of the bottom half slab at {@code pos} with {@code stack}. */
    private static void clickTop(final Ctx c, final ItemStack stack, final BlockPos pos) {
        final BlockHitResult hit = new BlockHitResult(Vec3.atBottomCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        ((BlockItem) stack.getItem()).place(new BlockPlaceContext(c.level(), null, InteractionHand.MAIN_HAND, stack, hit));
    }

    /** The index follows a rule list replaced wholesale (the fingerprint memo is keyed by the list's identity). */
    private static void rulesFingerprintChecks() {
        final var rules = SlateBuilding.serverConfig().variants;
        final List<String> previous = rules.materialDenylist;
        try {
            rules.materialDenylist = new ArrayList<>(List.of("minecraft:dirt"));
            check("a replaced denylist applies at once", !VariantRegistry.get().isMaterial(Blocks.DIRT), "dirt still a material");
        } finally {
            rules.materialDenylist = previous;
        }
        check("restoring the denylist brings dirt back", VariantRegistry.get().isMaterial(Blocks.DIRT), "dirt not a material");
    }

    // ------------------------------------------------------------------------------------------------ delete natives (client)

    private static void deleteNativesChecks() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        final var rules = SlateBuilding.serverConfig().variants;
        final boolean previous = rules.deleteNativeVariants;
        try {
            rules.deleteNativeVariants = true;
            check("deleteNativeVariants applies at once", VariantRegistry.get().deletesNatives(), "index not rebuilt");
            // Deleting hides natives and removes the recipes that MAKE them; the wheel still hands them out, so every
            // recipe that USES them (#wooden_slabs in a barrel, a stone slab in a grindstone) keeps working.
            final ItemStack stairs = VariantRegistry.get().stackFor(Blocks.OAK_PLANKS, Shape.STAIRS, 1);
            check("deleteNativeVariants: oak stairs still hand out minecraft:oak_stairs", stairs.is(Items.OAK_STAIRS), stairs);
            final ItemStack slab = VariantRegistry.get().stackFor(Blocks.OAK_PLANKS, Shape.SLAB, 1);
            check("deleteNativeVariants: oak slab still minecraft:oak_slab (#wooden_slabs recipes)", slab.is(Items.OAK_SLAB), slab);
            final ItemStack dirtStairs = VariantRegistry.get().stackFor(Blocks.DIRT, Shape.STAIRS, 1);
            check("deleteNativeVariants: dirt stairs (no native) are ours", dirtStairs.is(BuildingItems.forShape(Shape.STAIRS).get()), dirtStairs);
            rebuildTabs(mc);
            check("deleteNativeVariants: oak_stairs hidden from Building Blocks", !tabHas(CreativeModeTabs.BUILDING_BLOCKS, Items.OAK_STAIRS), "still listed");
            check("deleteNativeVariants: oak_stairs hidden from search", !searchHas(Items.OAK_STAIRS), "still searchable");
        } finally {
            rules.deleteNativeVariants = previous;
            rebuildTabs(mc);
        }
        check("natives back after deleteNativeVariants off", tabHas(CreativeModeTabs.BUILDING_BLOCKS, Items.OAK_STAIRS), "missing");
    }

    private static void rebuildTabs(final Minecraft mc) {
        CreativeModeTabsAccessor.slateBuilding$setCachedParameters(null);
        CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
    }

    private static boolean tabHas(final net.minecraft.resources.ResourceKey<CreativeModeTab> key, final Item item) {
        final CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(key);
        return tab != null && tab.getDisplayItems().stream().anyMatch(s -> s.is(item));
    }

    private static boolean searchHas(final Item item) {
        for (final CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (tab.getSearchTabDisplayItems().stream().anyMatch(s -> s.is(item))) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ reporting

    private static void check(final String name, final boolean ok, final @Nullable Object detail) {
        if (ok) {
            PASSED.incrementAndGet();
            SlateBuilding.LOGGER.info("[BuildingHarness] PASS {}", name);
        } else {
            FAILED.incrementAndGet();
            FAILURES.add(name);
            SlateBuilding.LOGGER.warn("[BuildingHarness] FAIL {} ({})", name, detail);
        }
    }

    private static void summary() {
        SlateBuilding.LOGGER.info("[BuildingHarness] variants: {} passed, {} failed{}", PASSED.get(), FAILED.get(),
            FAILURES.isEmpty() ? "" : " -> " + String.join("; ", FAILURES));
    }

    private static String name(final Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }
}
