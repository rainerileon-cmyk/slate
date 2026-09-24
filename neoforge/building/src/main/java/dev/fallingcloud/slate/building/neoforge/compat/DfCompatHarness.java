package dev.fallingcloud.slate.building.neoforge.compat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.block.DiagonalShapes;
import dev.fallingcloud.slate.building.block.VerticalSlabBlock;
import dev.fallingcloud.slate.building.client.BuildingHarness;
import dev.fallingcloud.slate.building.client.input.BuildKeys;
import dev.fallingcloud.slate.building.client.input.ExclusiveKeys;
import dev.fallingcloud.slate.building.client.menu.BuildMenuScreen;
import dev.fallingcloud.slate.building.client.render.RenderCompat;
import dev.fallingcloud.slate.building.client.wheel.WheelOverlay;
import dev.fallingcloud.slate.building.compat.BetterInventoryBridge;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.net.OpResult;
import dev.fallingcloud.slate.building.net.ReshapeTarget;
import dev.fallingcloud.slate.building.ops.server.OpsServer;
import dev.fallingcloud.slate.building.platform.BuildingPlatform;
import dev.fallingcloud.slate.building.registry.BuildingBlocks;
import dev.fallingcloud.slate.building.registry.BuildingItems;
import dev.fallingcloud.slate.building.registry.RegistryRef;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantActions;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * DF-pack compatibility scenarios for the dev harness, run in {@code :dev:runClientCompat} (whose {@code run-compat/mods}
 * holds copies of the pack's production jars, see {@code tools/compat/populate-run-compat.sh}):
 * <pre>
 *   -PbuildingHarness=compat            every scenario below, in order
 *   compat-env       mods + versions, who is bound to Left Alt / Right Alt / R, BetterInventory key migration, Iris state
 *   compat-render    Sodium meshing of every shape in tinted / cutout / translucent / emissive / axis materials, the
 *                    NeoForge model-data path Sodium uses, day + night + ghost screenshots (first person)
 *   compat-diagonal  DiagonalFences/Walls/Windows: no twins of our blocks; re-pointed vanilla items still identify and drop
 *   compat-klee      KleeSlabs half-breaking our double slab / double vertical slab / a native double slab
 *   compat-jei       our items in JEI, natives hidden with deleteNativeVariants
 *   compat-bi        BetterInventory 1.1.5 toolbox slot, bridge, quick-move, the screen
 *   compat-keys      Left Alt shared with Create's toolbelt, Shoulder Surfing free look, Relics; Right Alt; R; pick vector
 *   compat-opac      Open Parties and Claims: a claim (non-ally mode) denies our operations
 * </pre>
 * Every check logs {@code [BuildingHarness] PASS|FAIL <what>} (plus {@code INFO} lines for observations) and each
 * scenario ends with a summary line. Screenshots: {@code run-compat/screenshots/building-compat-*.png}.
 */
final class DfCompatHarness {

    private static final String[] MODS = {"neoforge", "slate", "slate_building", "slate_config", "sodium", "sodium_extra", "iris",
        "create", "flywheel", "ponder", "diagonalfences", "diagonalwalls", "diagonalwindows", "diagonalblocks", "puzzleslib",
        "kleeslabs", "balm", "jei", "curios", "openpartiesandclaims", "shouldersurfing", "relics", "octolib", "architectury",
        "betterinventory"};

    private static final AtomicInteger PASSED = new AtomicInteger();
    private static final AtomicInteger FAILED = new AtomicInteger();
    private static final List<String> FAILURES = java.util.Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger TOTAL_PASSED = new AtomicInteger();
    private static final List<String> TOTAL_FAILURES = java.util.Collections.synchronizedList(new ArrayList<>());
    private static volatile @Nullable CompletableFuture<Void> pending;
    /** Values carried from a server step to a later client step. */
    private static final Map<String, Object> NOTES = new java.util.concurrent.ConcurrentHashMap<>();

    private record Ctx(IntegratedServer server, ServerLevel level, ServerPlayer player) {}

    static void register() {
        BuildingHarness.register("compat-env", DfCompatHarness::env);
        BuildingHarness.register("compat-render", DfCompatHarness::render);
        BuildingHarness.register("compat-diagonal", DfCompatHarness::diagonal);
        BuildingHarness.register("compat-klee", DfCompatHarness::klee);
        BuildingHarness.register("compat-jei", DfCompatHarness::jei);
        BuildingHarness.register("compat-bi", DfCompatHarness::betterInventory);
        BuildingHarness.register("compat-keys", DfCompatHarness::keys);
        BuildingHarness.register("compat-opac", DfCompatHarness::opac);
        BuildingHarness.register("compat", s -> {
            env(s);
            render(s);
            diagonal(s);
            klee(s);
            jei(s);
            betterInventory(s);
            keys(s);
            opac(s);
            s.run(() -> SlateBuilding.LOGGER.info("[BuildingHarness] compat total: {} passed, {} failed{}", TOTAL_PASSED.get(),
                TOTAL_FAILURES.size(), TOTAL_FAILURES.isEmpty() ? "" : " -> " + String.join("; ", TOTAL_FAILURES)));
        });
    }

    // ================================================================================================ compat-env

    private static void env(final BuildingHarness.Script s) {
        begin(s, "compat-env");
        s.run(() -> {
            final Minecraft mc = Minecraft.getInstance();
            for (final String id : MODS) {
                final String version = ModList.get().getModContainerById(id).map(c -> c.getModInfo().getVersion().toString()).orElse("ABSENT");
                info("mod " + id + " " + version);
            }
            final Map<String, List<String>> byKey = new LinkedHashMap<>();
            for (final String k : List.of("key.keyboard.left.alt", "key.keyboard.right.alt", "key.keyboard.r")) byKey.put(k, new ArrayList<>());
            for (final KeyMapping m : mc.options.keyMappings) {
                final List<String> list = byKey.get(m.getKey().getName());
                if (list != null) list.add(m.getName() + "(" + m.getKeyConflictContext() + ", " + m.getKeyModifier() + ")");
            }
            byKey.forEach((k, v) -> info("bound to " + k + ": " + v));

            final KeyMapping bi = mapping("key.betterinventory.offhand_selector");
            check("BetterInventory offhand carousel bound to Right Alt", bi != null && "key.keyboard.right.alt".equals(bi.getKey().getName()),
                bi == null ? "mapping missing" : bi.getKey().getName());
            final String saved = optionsValue(mc, "key_key.betterinventory.offhand_selector");
            check("options.txt stores the carousel on Right Alt", "key.keyboard.right.alt".equals(saved), saved);
            check("Slate swap key on Left Alt", "key.keyboard.left.alt".equals(BuildKeys.SWAP.getKey().getName()), BuildKeys.SWAP.getKey().getName());
            check("Slate build-menu key on R", "key.keyboard.r".equals(BuildKeys.BUILD_MENU.getKey().getName()), BuildKeys.BUILD_MENU.getKey().getName());
            check("BetterInventory toolbox API bridged", BetterInventoryBridge.available(), "bridge unavailable");
            final boolean irisPack = RenderCompat.shaderPackInUse();
            info("Iris shader pack in use: " + irisPack + " (iris.properties: " + irisProperties(mc) + ")");
            NOTES.put("irisPack", irisPack);
            // Which of the loaded mods' own stairs / slabs / walls / fences / gates unify (design: every native variant).
            final VariantRegistry reg = VariantRegistry.get();
            final Map<String, int[]> perMod = new java.util.TreeMap<>();
            final Map<String, List<String>> missed = new java.util.TreeMap<>();
            for (final Block block : BuiltInRegistries.BLOCK) {
                if (block instanceof ShapeBlock) continue;
                if (!(block instanceof net.minecraft.world.level.block.StairBlock || block instanceof SlabBlock
                    || block instanceof net.minecraft.world.level.block.WallBlock || block instanceof net.minecraft.world.level.block.FenceBlock
                    || block instanceof net.minecraft.world.level.block.FenceGateBlock)) continue;
                final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                final int[] n = perMod.computeIfAbsent(id.getNamespace(), k -> new int[2]);
                n[1]++;
                if (reg.identify(block.defaultBlockState(), null).isPresent() || reg.identify(new ItemStack(block)).isPresent()) n[0]++;
                else missed.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(id.getPath());
            }
            perMod.forEach((ns, n) -> info("variant coverage " + ns + ": " + n[0] + "/" + n[1] + " stairs/slab/wall/fence/gate blocks identify"
                + (missed.containsKey(ns) ? ", not: " + missed.get(ns).subList(0, Math.min(12, missed.get(ns).size()))
                + (missed.get(ns).size() > 12 ? " ... (" + missed.get(ns).size() + ")" : "") : "")));
            final int[] create = perMod.getOrDefault("create", new int[2]);
            check("Create's stairs / slabs / walls identify as variants (" + create[0] + "/" + create[1] + ")",
                create[1] > 0 && create[0] == create[1], missed.getOrDefault("create", List.of()));
        });
        end(s);
    }

    // ================================================================================================ compat-render

    private static final Block[] RENDER_MATERIALS = {Blocks.GRASS_BLOCK, Blocks.OAK_LEAVES, Blocks.GLASS, Blocks.WHITE_STAINED_GLASS,
        Blocks.GLOWSTONE, Blocks.OAK_LOG, Blocks.STONE_BRICKS};
    private static final int RX = 21;
    private static final int RZ = 4;
    private static final int RY = -60;

    private static BlockPos renderPos(final int material, final int shapeIndex) {
        return new BlockPos(RX + 2 * shapeIndex, RY, RZ + 2 * material);
    }

    private static void render(final BuildingHarness.Script s) {
        begin(s, "compat-render");
        s.run(() -> perspective("FIRST_PERSON"))
            .command("time set noon")
            .command("weather clear")
            .command("fill 18 -61 1 48 -61 24 minecraft:smooth_stone")
            .command("fill 18 -60 1 48 -52 24 minecraft:air");
        onServer(s, c -> {
            final VariantRegistry reg = VariantRegistry.get();
            for (int m = 0; m < RENDER_MATERIALS.length; m++) {
                if (!reg.isMaterial(RENDER_MATERIALS[m])) {
                    info("not a material, row skipped: " + name(RENDER_MATERIALS[m]));
                    continue;
                }
                int i = 0;
                for (final Shape shape : Shape.values()) {
                    if (shape == Shape.FULL) continue;
                    if (placeVariant(c.level(), RENDER_MATERIALS[m], shape, renderPos(m, i)) == null) {
                        check("place " + name(RENDER_MATERIALS[m]) + " " + shape.id(), false, "refused");
                    }
                    i++;
                }
            }
            // Face culling between neighbours: our stone brick vertical slabs merged into full blocks next to each other
            // and next to a real block; glass steps side by side.
            for (int x = 22; x <= 24; x++) {
                final BlockPos p = new BlockPos(x, RY, 20);
                placeVariant(c.level(), Blocks.STONE_BRICKS, Shape.VERTICAL_SLAB, p);
                mergeVerticalSlab(c.level(), Blocks.STONE_BRICKS, p);
            }
            c.level().setBlock(new BlockPos(25, RY, 20), Blocks.STONE_BRICKS.defaultBlockState(), 3);
            for (int x = 28; x <= 31; x++) placeVariant(c.level(), Blocks.GLASS, Shape.STEP, new BlockPos(x, RY, 20));
            for (int x = 34; x <= 37; x++) placeVariant(c.level(), Blocks.GRASS_BLOCK, Shape.SLAB, new BlockPos(x, RY, 20));
            final BlockPos glowStep = renderPos(4, Shape.STEP.ordinal() - 1);
            NOTES.put("glowStep", glowStep);
        });
        s.wait(40).run(() -> {
            final Minecraft mc = Minecraft.getInstance();
            check("Sodium is the chunk renderer", ModList.get().isLoaded("sodium"), "sodium absent");
            final RandomSource random = RandomSource.create(42L);
            for (int m = 0; m < RENDER_MATERIALS.length; m++) {
                final Block material = RENDER_MATERIALS[m];
                int i = 0;
                final List<String> wrong = new ArrayList<>();
                int checked = 0;
                for (final Shape shape : Shape.values()) {
                    if (shape == Shape.FULL) continue;
                    final BlockPos pos = renderPos(m, i++);
                    final BlockState state = mc.level.getBlockState(pos);
                    if (!(state.getBlock() instanceof ShapeBlock)) continue;   // realised by a native block
                    checked++;
                    final String problem = modelProblem(mc, pos, state, material, random);
                    if (problem != null) wrong.add(shape.id() + ": " + problem);
                }
                check("model data path (" + checked + " " + name(material) + " shapes): material quads for the material's render types",
                    wrong.isEmpty(), wrong);
            }
            final BlockPos glowStep = (BlockPos) NOTES.get("glowStep");
            if (glowStep != null) {
                final int light = mc.level.getBrightness(LightLayer.BLOCK, glowStep.above());
                check("glowstone step lights its surroundings (block light above = " + light + ")", light >= 13, light);
            }
        });
        s.run(() -> {
            final Minecraft mc = Minecraft.getInstance();
            mc.player.getAbilities().flying = true;   // hold the camera positions in the air
            mc.player.onUpdateAbilities();
            mc.options.hideGui = true;
            mc.gui.getChat().clearMessages(false);
        })
            .command("setblock 33 -55 -4 minecraft:barrier").command("setblock 26 -59 1 minecraft:barrier").command("setblock 30 -59 16 minecraft:barrier")
            .command("tp @s 33.5 -54 -3.5 0 32").wait(80).screenshot("compat-render-day")
            .command("tp @s 26.5 -58 1.5 0 22").wait(40).screenshot("compat-render-close")
            .command("tp @s 30.5 -58 16.5 0 25").wait(40).screenshot("compat-render-culling")
            .command("time set 18000").command("tp @s 33.5 -54 -3.5 0 32").wait(100)
            .run(() -> info("night shot: client day time " + Minecraft.getInstance().level.getDayTime()))
            .screenshot("compat-render-night")
            .command("time set noon")
            .run(() -> Minecraft.getInstance().options.hideGui = false);
        // The placement ghost with Iris present (with or without a shader pack, whatever this run has).
        s.run(() -> {
            final Minecraft mc = Minecraft.getInstance();
            final IntegratedServer server = mc.getSingleplayerServer();
            final UUID id = mc.player.getUUID();
            pending = server.submit(() -> {
                final ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) {
                    p.getInventory().selected = 0;
                    p.getInventory().setItem(0, VariantRegistry.get().stackFor(Blocks.OAK_PLANKS, Shape.VERTICAL_SLAB, 16));
                }
            });
        }).waitUntil(() -> pending != null && pending.isDone(), 200)
            .command("tp @s 44.5 -60 -1.5 0 45").wait(40)
            .run(() -> {
                final Minecraft mc = Minecraft.getInstance();
                info("ghost: crosshair on " + describe(mc.hitResult) + ", Iris shader pack in use: " + RenderCompat.shaderPackInUse()
                    + " (ghosts then use the vanilla translucent path)");
            })
            .screenshot("compat-ghost")
            .run(() -> perspective("SHOULDER_SURFING")).wait(30).screenshot("compat-ghost-shoulder")
            .run(() -> perspective("FIRST_PERSON"));
        end(s);
    }

    /** What is wrong with the model Sodium meshes for our block at {@code pos} (null = fine). */
    private static @Nullable String modelProblem(final Minecraft mc, final BlockPos pos, final BlockState state, final Block material, final RandomSource random) {
        final BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        final ModelData data = model.getModelData(mc.level, pos, state, mc.level.getModelData(pos));
        final BlockState materialState = ShapeBlock.material(mc.level, pos);
        if (materialState == null) return "client block entity has no material";
        if (!materialState.is(material)) return "material " + materialState;
        final var types = model.getRenderTypes(state, random, data);
        final var expected = mc.getBlockRenderer().getBlockModel(materialState).getRenderTypes(materialState, random, ModelData.EMPTY);
        if (!types.asList().containsAll(expected.asList())) return "render types " + types.asList() + " miss " + expected.asList();
        int quads = 0;
        boolean materialSprite = false;
        final String materialPath = BuiltInRegistries.BLOCK.getKey(material).getPath();
        for (final RenderType type : types) {
            for (final Direction side : new Direction[] {null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                for (final BakedQuad q : model.getQuads(state, side, random, data, type)) {
                    quads++;
                    final String sprite = q.getSprite().contents().name().getPath();
                    if (sprite.contains(materialPath)) materialSprite = true;
                }
            }
        }
        if (quads == 0) return "no quads";
        if (!materialSprite) return "no quad uses a " + materialPath + " sprite";
        return null;
    }

    // ================================================================================================ compat-diagonal

    private static void diagonal(final BuildingHarness.Script s) {
        begin(s, "compat-diagonal");
        s.command("fill -34 -61 -2 -18 -61 12 minecraft:smooth_stone")
            .command("fill -34 -60 -2 -18 -55 12 minecraft:air")
            .command("tp @s -26 -60 -4 0 20");
        onServer(s, c -> {
            if (!ModList.get().isLoaded("diagonalblocks")) {
                check("DiagonalBlocks loaded", false, "absent");
                return;
            }
            final Collection<?> types = diagonalTypes();
            check("DiagonalBlocks types found (fences, walls, windows)", types.size() >= 3, types.size());
            final List<String> ours = new ArrayList<>();
            int conversions = 0;
            for (final Object type : types) {
                final Map<?, ?> map = conversions(type);
                conversions += map.size();
                map.forEach((from, to) -> { if (from instanceof ShapeBlock || to instanceof ShapeBlock) ours.add(from + " -> " + to); });
                for (final Block block : new Block[] {BuildingBlocks.FENCE.get(), BuildingBlocks.WALL.get(),
                    BuildingBlocks.PANE.get()}) {
                    if (isTarget(type, BuiltInRegistries.BLOCK.getKey(block), block)) ours.add("isTarget(" + BuiltInRegistries.BLOCK.getKey(block) + ")");
                }
            }
            info("diagonal conversions registered: " + conversions);
            check("no diagonal twins of our fence / wall / pane blocks", ours.isEmpty(), ours);
            final List<String> twinsNamedOurs = new ArrayList<>();
            for (final ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
                if (id.getNamespace().startsWith("diagonal") && id.getPath().contains("slate_building")) twinsNamedOurs.add(id.toString());
            }
            check("no diagonal* registry entries for slate_building", twinsNamedOurs.isEmpty(), twinsNamedOurs);

            nativeTwin(c, Items.OAK_FENCE, Blocks.OAK_FENCE, Blocks.OAK_PLANKS, Shape.FENCE, new BlockPos(-30, -60, 2), new ItemStack(Items.DIAMOND_AXE));
            nativeTwin(c, Items.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE, Shape.WALL, new BlockPos(-27, -60, 2), new ItemStack(Items.DIAMOND_PICKAXE));
            // Glass panes are not a unified variant (6 glass -> 16 panes is not 1:1; our PANE shape is the unified one):
            // only check the twin is placed and that our own pane block got no twin (above).
            final Block paneTwin = ((BlockItem) Items.GLASS_PANE).getBlock();
            info("glass_pane item places " + BuiltInRegistries.BLOCK.getKey(paneTwin) + ", identifies as "
                + VariantRegistry.get().identify(new ItemStack(Items.GLASS_PANE)) + " (not unified by design)");
            check("glass_pane item is re-pointed to a diagonal twin", paneTwin != Blocks.GLASS_PANE, BuiltInRegistries.BLOCK.getKey(paneTwin));
            final BlockPos ourPane = new BlockPos(-24, -60, 2);
            placeVariant(c.level(), Blocks.GLASS, Shape.PANE, ourPane);
            placeItem(c.level(), new ItemStack(Items.GLASS_PANE), ourPane.east());
            check("our glass pane (no twin) connects to the diagonal glass pane", c.level().getBlockState(ourPane).getBlock() instanceof ShapeBlock
                && c.level().getBlockState(ourPane).getValue(BlockStateProperties.EAST), c.level().getBlockState(ourPane));

            // Our fences / walls next to the twins: both sides connect.
            final ServerLevel level = c.level();
            placeVariant(level, Blocks.DIRT, Shape.FENCE, new BlockPos(-31, -60, 6));
            placeItem(level, new ItemStack(Items.OAK_FENCE), new BlockPos(-30, -60, 6));
            placeVariant(level, Blocks.DIRT, Shape.FENCE, new BlockPos(-29, -60, 6));
            final BlockState ourFence = level.getBlockState(new BlockPos(-31, -60, 6));
            final BlockState twinFence = level.getBlockState(new BlockPos(-30, -60, 6));
            info("our dirt fence " + ourFence + " | twin " + twinFence);
            check("our fence connects to the diagonal oak fence", ourFence.hasProperty(BlockStateProperties.EAST) && ourFence.getValue(BlockStateProperties.EAST), ourFence);
            check("diagonal oak fence connects to our fences", twinFence.hasProperty(BlockStateProperties.EAST) && twinFence.getValue(BlockStateProperties.EAST)
                && twinFence.getValue(BlockStateProperties.WEST), twinFence);
            placeVariant(level, Blocks.STONE, Shape.WALL, new BlockPos(-27, -60, 6));
            placeItem(level, new ItemStack(Items.COBBLESTONE_WALL), new BlockPos(-26, -60, 6));
            placeVariant(level, Blocks.STONE, Shape.WALL, new BlockPos(-25, -60, 6));
            info("our stone wall " + level.getBlockState(new BlockPos(-27, -60, 6)) + " | twin " + level.getBlockState(new BlockPos(-26, -60, 6)));
            // Diagonal connections between our fences (they are plain FenceBlocks without twins: straight only).
            placeVariant(level, Blocks.DIRT, Shape.FENCE, new BlockPos(-22, -60, 6));
            placeVariant(level, Blocks.DIRT, Shape.FENCE, new BlockPos(-21, -60, 7));
            placeItem(level, new ItemStack(Items.OAK_FENCE), new BlockPos(-20, -60, 6));
            placeItem(level, new ItemStack(Items.OAK_FENCE), new BlockPos(-19, -60, 7));

            // Our own shapes take diagonal arms too (kept in the block entity), with each other and with the twins both ways.
            final ItemStack stoneFence = VariantRegistry.get().stackFor(Blocks.STONE, Shape.FENCE, 1);
            final BlockPos fa = new BlockPos(-24, -60, 6), fb = new BlockPos(-23, -60, 5), ft = new BlockPos(-25, -60, 7);
            placeItem(level, stoneFence.copy(), fa);   // place() consumes the stack
            placeItem(level, stoneFence.copy(), fb);
            check("our fence is registered as the library bridge (implements DiagonalBlock)", isLibDiagonal(level.getBlockState(fa).getBlock()),
                level.getBlockState(fa).getBlock().getClass().getName());
            check("our stone fences connect diagonally to each other (NE / SW)",
                DiagonalShapes.has(level, fa, DiagonalShapes.Diagonal.NORTH_EAST) && DiagonalShapes.has(level, fb, DiagonalShapes.Diagonal.SOUTH_WEST),
                DiagonalShapes.mask(level, fa) + "/" + DiagonalShapes.mask(level, fb));
            placeItem(level, new ItemStack(Items.OAK_FENCE), ft);
            check("our fence takes an arm towards a vanilla (twin) fence placed diagonally", DiagonalShapes.has(level, fa, DiagonalShapes.Diagonal.SOUTH_WEST),
                DiagonalShapes.mask(level, fa));
            check("the vanilla twin takes an arm towards our fence", DiagonalShapes.has(level, ft, DiagonalShapes.Diagonal.NORTH_EAST), level.getBlockState(ft));
            final ItemStack stoneWall = VariantRegistry.get().stackFor(Blocks.STONE, Shape.WALL, 1);
            final BlockPos wa = new BlockPos(-24, -60, 10), wb = new BlockPos(-23, -60, 9);
            placeItem(level, stoneWall.copy(), wa);
            placeItem(level, stoneWall.copy(), wb);
            check("our stone walls connect diagonally to each other",
                DiagonalShapes.has(level, wa, DiagonalShapes.Diagonal.NORTH_EAST) && DiagonalShapes.has(level, wb, DiagonalShapes.Diagonal.SOUTH_WEST),
                DiagonalShapes.mask(level, wa) + "/" + DiagonalShapes.mask(level, wb));
            check("a diagonal arm is part of the collision shape", !level.getBlockState(fa).getCollisionShape(level, fa).isEmpty()
                && level.getBlockState(fa).getCollisionShape(level, fa).bounds().maxX > 0.75, level.getBlockState(fa).getCollisionShape(level, fa).bounds());

            // Reshape a twin in the world, and reshape planks into a fence: which block comes out?
            final BlockPos r = new BlockPos(-30, -60, 9);
            placeItem(level, new ItemStack(Items.OAK_FENCE), r);
            c.player().teleportTo(r.getX() + 1.5, r.getY(), r.getZ() - 1.5);   // within reach of both
            VariantActions.reshapeTarget(new ReshapeTarget(r, "stairs"), c.player());
            check("reshape a diagonal oak fence -> oak_stairs", level.getBlockState(r).is(Blocks.OAK_STAIRS), level.getBlockState(r));
            final BlockPos r2 = new BlockPos(-28, -60, 9);
            level.setBlock(r2, Blocks.OAK_PLANKS.defaultBlockState(), 3);
            VariantActions.reshapeTarget(new ReshapeTarget(r2, "fence"), c.player());
            final Block reshaped = level.getBlockState(r2).getBlock();
            info("reshape oak planks -> fence gives " + BuiltInRegistries.BLOCK.getKey(reshaped));
            check("reshape oak planks -> fence gives the block the oak fence item places (the diagonal twin)",
                reshaped == ((BlockItem) Items.OAK_FENCE).getBlock(), BuiltInRegistries.BLOCK.getKey(reshaped));
        });
        s.command("tp @s -26 -57.5 -1.5 0 35").wait(40).screenshot("compat-diagonal");
        end(s);
    }

    private static void nativeTwin(final Ctx c, final Item item, final Block vanilla, final Block material, final Shape shape,
                                   final BlockPos pos, final ItemStack tool) {
        final String what = BuiltInRegistries.ITEM.getKey(item).getPath();
        final Block placesBlock = ((BlockItem) item).getBlock();
        info(what + " item places " + BuiltInRegistries.BLOCK.getKey(placesBlock));
        check(what + " item is re-pointed to a diagonal twin", placesBlock != vanilla, BuiltInRegistries.BLOCK.getKey(placesBlock));
        final Optional<Variant> v = VariantRegistry.get().identify(new ItemStack(item));
        check(what + " item identifies as (" + name(material) + ", " + shape.id() + ")",
            v.isPresent() && v.get().material() == material && v.get().shape() == shape, v);
        final ItemStack realised = VariantRegistry.get().stackFor(material, shape, 1);
        check(name(material) + " " + shape.id() + " is realised by the " + what + " item", realised.is(item), realised);

        final ServerLevel level = c.level();
        final BlockPos placed = placeItem(level, new ItemStack(item), pos);
        final BlockState state = level.getBlockState(pos);
        check(what + " places its twin " + BuiltInRegistries.BLOCK.getKey(placesBlock), placed != null && state.is(placesBlock), state);
        final Optional<Variant> inWorld = VariantRegistry.get().identify(level, pos);
        check("placed " + what + " twin identifies as (" + name(material) + ", " + shape.id() + ")",
            inWorld.isPresent() && inWorld.get().material() == material && inWorld.get().shape() == shape, inWorld);
        final List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), c.player(), tool);
        // Shapes drop themselves: the twin's loot is the re-pointed item, which identifies as the same variant.
        check(what + " twin loot = 1 " + what, drops.size() == 1 && drops.get(0).is(item) && drops.get(0).getCount() == 1, drops);
        final Map<Item, Integer> dropped = survivalBreak(c, pos, tool);
        check(what + " twin broken in survival drops 1 " + what, dropped.size() == 1 && Objects.equals(dropped.get(item), 1), dropped);
        placeItem(level, new ItemStack(item), pos);
    }

    private static Collection<?> diagonalTypes() {
        try {
            final Class<?> api = Class.forName("fuzs.diagonalblocks.api.v2.DiagonalBlockType");
            return (Collection<?>) api.getField("TYPES").get(null);
        } catch (final ReflectiveOperationException | LinkageError | ClassCastException e) {
            info("DiagonalBlockType.TYPES unavailable: " + e);
            return List.of();
        }
    }

    private static Map<?, ?> conversions(final Object type) {
        try {
            final Class<?> api = Class.forName("fuzs.diagonalblocks.api.v2.DiagonalBlockType");
            return (Map<?, ?>) api.getMethod("getBlockConversions").invoke(type);
        } catch (final ReflectiveOperationException | LinkageError | ClassCastException e) {
            return Map.of();
        }
    }

    private static boolean isTarget(final Object type, final ResourceLocation id, final Block block) {
        try {
            final Class<?> api = Class.forName("fuzs.diagonalblocks.api.v2.DiagonalBlockType");
            return (boolean) api.getMethod("isTarget", ResourceLocation.class, Block.class).invoke(type, id, block);
        } catch (final ReflectiveOperationException | LinkageError | ClassCastException e) {
            return false;
        }
    }

    // ================================================================================================ compat-klee

    private static void klee(final BuildingHarness.Script s) {
        begin(s, "compat-klee");
        s.command("fill -34 -61 18 -18 -61 30 minecraft:smooth_stone")
            .command("fill -34 -60 18 -18 -55 30 minecraft:air")
            .command("tp @s -26 -60 16 0 20");
        onServer(s, c -> {
            final ServerLevel level = c.level();
            // Our horizontal double slab (terracotta: no native slab, needs a pickaxe).
            final BlockPos a = new BlockPos(-30, -60, 22);
            doubleSlab(level, Blocks.TERRACOTTA, a);
            final BlockState before = level.getBlockState(a);
            check("our terracotta slabs merge into a double", before.getBlock() instanceof SlabBlock && before.getValue(SlabBlock.TYPE) == SlabType.DOUBLE, before);
            Map<Item, Integer> dropped = kleeBreak(c, a, new Vec3(a.getX() + 0.5, a.getY() + 0.8, a.getZ()), new ItemStack(Items.DIAMOND_PICKAXE));
            BlockState after = level.getBlockState(a);
            check("KleeSlabs: looking at the top half of our double slab leaves the bottom half",
                after.getBlock() == before.getBlock() && after.getValue(SlabBlock.TYPE) == SlabType.BOTTOM, after);
            check("KleeSlabs: the remaining half keeps its material", isMaterial(level, a, Blocks.TERRACOTTA), ShapeBlock.material(level, a));
            final Item terracottaSlab = VariantRegistry.get().stackFor(Blocks.TERRACOTTA, Shape.SLAB, 1).getItem();
            check("KleeSlabs: the broken half drops 1 terracotta slab", dropped.size() == 1 && Objects.equals(dropped.get(terracottaSlab), 1), dropped);

            // By hand: terracotta needs a pickaxe, so nothing may drop (KleeSlabs asks our block, which has no tool rule).
            final BlockPos b = new BlockPos(-28, -60, 22);
            KleeSlabsCompat.enabled = false;
            doubleSlab(level, Blocks.TERRACOTTA, b);
            dropped = kleeBreak(c, b, new Vec3(b.getX() + 0.5, b.getY() + 0.8, b.getZ()), ItemStack.EMPTY);
            info("without KleeSlabsCompat, half of our terracotta double slab mined BY HAND drops " + dropped);
            KleeSlabsCompat.enabled = true;
            final BlockPos b2 = new BlockPos(-26, -60, 22);
            doubleSlab(level, Blocks.TERRACOTTA, b2);
            dropped = kleeBreak(c, b2, new Vec3(b2.getX() + 0.5, b2.getY() + 0.8, b2.getZ()), ItemStack.EMPTY);
            check("KleeSlabs by hand on our terracotta double slab drops nothing", dropped.isEmpty(), dropped);

            // Our vertical double slab (enchanted-vertical-slab layout, tagged for KleeSlabs).
            final BlockPos v = new BlockPos(-22, -60, 22);
            placeVariant(level, Blocks.TERRACOTTA, Shape.VERTICAL_SLAB, v);
            mergeVerticalSlab(level, Blocks.TERRACOTTA, v);
            final BlockState vBefore = level.getBlockState(v);
            check("our vertical slabs merge into a double", vBefore.getBlock() instanceof VerticalSlabBlock && !vBefore.getValue(VerticalSlabBlock.SINGLE), vBefore);
            // Look at the NORTH face: the north half goes, the south half stays.
            dropped = kleeBreak(c, v, new Vec3(v.getX() + 0.5, v.getY() + 0.5, v.getZ()), new ItemStack(Items.DIAMOND_PICKAXE));
            final BlockState vAfter = level.getBlockState(v);
            info("vertical double slab " + vBefore + " -> " + vAfter);
            check("KleeSlabs: looking at the north half of our double vertical slab leaves a single slab",
                vAfter.getBlock() instanceof VerticalSlabBlock && vAfter.getValue(VerticalSlabBlock.SINGLE), vAfter);
            check("KleeSlabs: the remaining vertical half is the far (south) one",
                vAfter.getBlock() instanceof VerticalSlabBlock && vAfter.getValue(VerticalSlabBlock.FACING) == Direction.SOUTH, vAfter);
            check("KleeSlabs: the remaining vertical half keeps its material", isMaterial(level, v, Blocks.TERRACOTTA), ShapeBlock.material(level, v));
            final Item terracottaVSlab = VariantRegistry.get().stackFor(Blocks.TERRACOTTA, Shape.VERTICAL_SLAB, 1).getItem();
            check("KleeSlabs: the broken vertical half drops 1 terracotta vertical slab", dropped.size() == 1 && Objects.equals(dropped.get(terracottaVSlab), 1), dropped);

            // A native double oak slab: the half should come out as the material, like any broken variant.
            final BlockPos n = new BlockPos(-19, -60, 22);
            KleeSlabsCompat.enabled = false;
            level.setBlock(n, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 3);
            dropped = kleeBreak(c, n, new Vec3(n.getX() + 0.5, n.getY() + 0.8, n.getZ()), new ItemStack(Items.DIAMOND_AXE));
            info("without KleeSlabsCompat, half of a native double oak slab drops " + dropped);
            KleeSlabsCompat.enabled = true;
            level.setBlock(n, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 3);
            dropped = kleeBreak(c, n, new Vec3(n.getX() + 0.5, n.getY() + 0.8, n.getZ()), new ItemStack(Items.DIAMOND_AXE));
            check("KleeSlabs: half of a native double oak slab drops 1 oak_planks", dropped.size() == 1 && Objects.equals(dropped.get(Items.OAK_PLANKS), 1), dropped);

            // Our permission probe must not break anything (BuildingPlatform.canBreak posts the BreakEvent KleeSlabs acts on).
            final BlockPos p = new BlockPos(-30, -60, 26);
            level.setBlock(p, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 3);
            final BlockState probeBefore = level.getBlockState(p);
            c.player().setGameMode(GameType.SURVIVAL);
            final boolean allowed;
            try {
                allowed = BuildingPlatform.get().canBreak(c.player(), level, p, probeBefore);
            } finally {
                c.player().setGameMode(GameType.CREATIVE);
            }
            final BlockState probeAfter = level.getBlockState(p);
            final Map<Item, Integer> probeDrops = collectDrops(level, p);
            check("BuildingPlatform.canBreak on a double slab is side-effect free", probeAfter == probeBefore && probeDrops.isEmpty(),
                "allowed=" + allowed + " state " + probeBefore + " -> " + probeAfter + " drops " + probeDrops);
            check("BuildingPlatform.canBreak allows breaking an unclaimed double slab", allowed, "denied");
            // ...which is what made VariantHarness' "reshape native double oak slab" fail in this pack:
            final BlockPos q = new BlockPos(-28, -60, 26);
            c.player().teleportTo(q.getX() + 0.5, q.getY(), q.getZ() - 1.5);
            level.setBlock(q, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), 3);
            VariantActions.reshapeTarget(new ReshapeTarget(q, "stairs"), c.player());
            check("reshape a native double oak slab -> oak_stairs (creative, KleeSlabs installed)", level.getBlockState(q).is(Blocks.OAK_STAIRS), level.getBlockState(q));
        });
        s.command("tp @s -26 -58 17 0 30").wait(30).screenshot("compat-klee");
        end(s);
    }

    private static void doubleSlab(final ServerLevel level, final Block material, final BlockPos pos) {
        placeVariant(level, material, Shape.SLAB, pos);
        final ItemStack stack = VariantRegistry.get().stackFor(material, Shape.SLAB, 1);
        ((BlockItem) stack.getItem()).place(new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, stack,
            new BlockHitResult(Vec3.atBottomCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false)));
    }

    /** A survival break of {@code pos} by the server player looking at {@code look} from 2.5 blocks north of it. */
    private static Map<Item, Integer> kleeBreak(final Ctx c, final BlockPos pos, final Vec3 look, final ItemStack tool) {
        final ServerPlayer player = c.player();
        final Vec3 eyeFrom = new Vec3(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() - 2.0);
        player.moveTo(eyeFrom.x, eyeFrom.y, eyeFrom.z, 0f, 0f);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, look);
        return survivalBreak(c, pos, tool);
    }

    private static boolean isMaterial(final ServerLevel level, final BlockPos pos, final Block material) {
        final BlockState m = ShapeBlock.material(level, pos);
        return m != null && m.is(material);
    }

    // ================================================================================================ compat-jei

    private static void jei(final BuildingHarness.Script s) {
        begin(s, "compat-jei");
        if (!ModList.get().isLoaded("jei")) {
            s.run(() -> check("JEI loaded", false, "absent"));
            end(s);
            return;
        }
        s.waitUntil(() -> JeiProbe.runtime() != null, 600)
            .run(() -> {
                check("JEI runtime reached our plugin", JeiProbe.runtime() != null, "no runtime");
                check("JEI lists the Builder's Toolbox", JeiProbe.has(BuildingItems.TOOLBOX.get()), "missing");
                final List<String> missing = new ArrayList<>();
                final Map<String, java.util.Set<String>> materials = new LinkedHashMap<>();
                for (final Shape shape : Shape.values()) {
                    final RegistryRef<? extends Item> ref = BuildingItems.forShape(shape);
                    if (ref == null) continue;
                    final boolean showcased = VariantRegistry.get().stackFor(Blocks.OAK_PLANKS, shape, 1).is(ref.get())
                        || VariantRegistry.get().stackFor(Blocks.STONE_BRICKS, shape, 1).is(ref.get());   // the creative showcase materials
                    if (showcased && !JeiProbe.has(ref.get())) missing.add(shape.id());
                    materials.put(shape.id(), JeiProbe.materials(ref.get()));
                }
                check("JEI lists every shape item the creative showcase uses", missing.isEmpty(), missing);
                info("JEI stacks per shape item (materials): " + materials);
                final java.util.Set<String> vslab = materials.getOrDefault("vertical_slab", java.util.Set.of());
                check("JEI keeps the showcase materials apart (vertical slab in oak planks AND stone bricks)",
                    vslab.contains("minecraft:oak_planks") && vslab.contains("minecraft:stone_bricks"), vslab);
                check("oak_stairs listed while deleteNativeVariants is off", JeiProbe.has(Items.OAK_STAIRS), "missing");
            });
        s.run(() -> {
            final Minecraft mc = Minecraft.getInstance();
            NOTES.put("jeiDelete", SlateBuilding.serverConfig().variants.deleteNativeVariants);
            setDeleteNatives(mc, true);
        }).waitUntil(() -> !JeiProbe.has(Items.OAK_STAIRS), 400)
            .run(() -> {
                check("deleteNativeVariants: oak_stairs hidden from JEI", !JeiProbe.has(Items.OAK_STAIRS), "still listed");
                check("deleteNativeVariants: stone_brick_wall hidden from JEI", !JeiProbe.has(Items.STONE_BRICK_WALL), "still listed");
                check("deleteNativeVariants: oak_planks still in JEI", JeiProbe.has(Items.OAK_PLANKS), "missing");
                check("deleteNativeVariants: a stairs item is still in JEI (ours in place of the hidden oak_stairs)", JeiProbe.has(BuildingItems.forShape(Shape.STAIRS).get()), "missing: JEI has no stairs at all now");
                JeiProbe.filter("stairs");
                Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player));
            })
            .wait(40).screenshot("compat-jei-natives-hidden")
            .run(() -> Minecraft.getInstance().setScreen(null))
            .run(() -> setDeleteNatives(Minecraft.getInstance(), Boolean.TRUE.equals(NOTES.get("jeiDelete"))))
            .waitUntil(() -> JeiProbe.has(Items.OAK_STAIRS), 400)
            .run(() -> {
                check("oak_stairs back in JEI after deleteNativeVariants off", JeiProbe.has(Items.OAK_STAIRS), "missing");
                Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player));
            })
            .wait(40).screenshot("compat-jei-natives-shown")
            .run(() -> {
                JeiProbe.filter("");
                Minecraft.getInstance().setScreen(null);
            });
        end(s);
    }

    private static void setDeleteNatives(final Minecraft mc, final boolean delete) {
        final IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            SlateBuilding.serverConfig().variants.deleteNativeVariants = delete;
            BuildingServerSettings.broadcast(server);
        });
    }

    // ================================================================================================ compat-bi

    private static void betterInventory(final BuildingHarness.Script s) {
        begin(s, "compat-bi");
        onServer(s, c -> {
            final ServerPlayer player = c.player();
            check("BetterInventory hasToolboxSlot()", Boolean.TRUE.equals(biCall("hasToolboxSlot", new Class<?>[0])), "false");
            final ItemStack toolbox = new ItemStack(BuildingItems.TOOLBOX.get());
            check("setToolbox refuses stone", Boolean.FALSE.equals(biCall("setToolbox", new Class<?>[] {net.minecraft.world.entity.player.Player.class, ItemStack.class},
                player, new ItemStack(Items.STONE))), "accepted");
            check("setToolbox accepts our toolbox", Boolean.TRUE.equals(biCall("setToolbox", new Class<?>[] {net.minecraft.world.entity.player.Player.class, ItemStack.class},
                player, toolbox)), "refused");
            final ToolboxAccess.Located at = ToolboxAccess.locate(player);
            check("ToolboxAccess finds the toolbox in the BetterInventory slot (server)", at != null && at.betterInventory(), at);
            // Take it out again; the quick-move below has to put it back.
            biCall("setToolbox", new Class<?>[] {net.minecraft.world.entity.player.Player.class, ItemStack.class}, player, ItemStack.EMPTY);
            player.getInventory().setItem(9, toolbox.copy());
            player.setGameMode(GameType.SURVIVAL);
        });
        s.wait(10)
            .run(() -> Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player)))
            .waitUntil(() -> Minecraft.getInstance().screen != null && Minecraft.getInstance().screen.getClass().getName().contains("betterinventory"), 200)
            .run(() -> {
                final Minecraft mc = Minecraft.getInstance();
                info("inventory screen: " + (mc.screen == null ? "none" : mc.screen.getClass().getName()));
                check("survival inventory is BetterInventory's screen", mc.screen != null && mc.screen.getClass().getName().contains("betterinventory"),
                    mc.screen == null ? "none" : mc.screen.getClass().getName());
                final Slot slot = biToolboxSlot(mc.player.containerMenu);
                check("BetterInventory menu has an active toolbox slot", slot != null && slot.isActive(), slot);
                if (slot != null) {
                    check("toolbox slot accepts our toolbox", slot.mayPlace(new ItemStack(BuildingItems.TOOLBOX.get())), "refused");
                    check("toolbox slot refuses a stone block", !slot.mayPlace(new ItemStack(Items.STONE)), "accepted");
                    check("toolbox slot refuses a hammer", !slot.mayPlace(new ItemStack(BuildingItems.tool(dev.fallingcloud.slate.building.ops.ToolType.HAMMER,
                        dev.fallingcloud.slate.building.toolbox.ToolTier.IRON).get())), "accepted");
                }
            });
        onServer(s, c -> {
            final ServerPlayer player = c.player();
            final AbstractContainerMenu menu = player.containerMenu;
            int from = -1;
            for (int i = 0; i < menu.slots.size(); i++) {
                final Slot sl = menu.slots.get(i);
                if (sl.getItem() == player.getInventory().getItem(9)) from = i;   // BetterInventory wraps the inventory in its own slots
            }
            check("BetterInventory menu shows inventory slot 9", from >= 0, from);
            if (from >= 0) menu.quickMoveStack(player, from);
            final Object inSlot = biCall("toolbox", new Class<?>[] {net.minecraft.world.entity.player.Player.class}, player);
            check("shift-click moves the toolbox into the toolbox slot", inSlot instanceof ItemStack st && ToolboxAccess.isToolbox(st),
                inSlot + " / inv9=" + player.getInventory().getItem(9));
            final ToolboxAccess.Located at = ToolboxAccess.locate(player);
            check("ToolboxAccess.locate -> BetterInventory slot after the shift-click (server)", at != null && at.betterInventory(), at);
        });
        s.waitUntil(() -> {
            final ToolboxAccess.Located at = ToolboxAccess.locate(Minecraft.getInstance().player);
            return at != null && at.betterInventory();
        }, 200)
            .run(() -> {
                final ToolboxAccess.Located at = ToolboxAccess.locate(Minecraft.getInstance().player);
                check("ToolboxAccess finds the BetterInventory toolbox on the client (synced)", at != null && at.betterInventory(), at);
            })
            .wait(20).screenshot("compat-bi-toolbox-slot")
            .run(() -> Minecraft.getInstance().setScreen(null))
            .command("gamemode creative");
        end(s);
    }

    private static @Nullable Slot biToolboxSlot(final AbstractContainerMenu menu) {
        try {
            final Class<?> c = Class.forName("dev.fallingcloud.betterinventory.menu.BetterInventoryMenu");
            if (!c.isInstance(menu)) return null;
            final int index = c.getField("SLOT_TOOLBOX").getInt(null);
            return index < menu.slots.size() ? menu.slots.get(index) : null;
        } catch (final ReflectiveOperationException | LinkageError e) {
            info("BetterInventoryMenu.SLOT_TOOLBOX unavailable: " + e);
            return null;
        }
    }

    private static @Nullable Object biCall(final String method, final Class<?>[] types, final Object... args) {
        try {
            return Class.forName("dev.fallingcloud.betterinventory.api.BetterInventoryApi").getMethod(method, types).invoke(null, args);
        } catch (final ReflectiveOperationException | LinkageError e) {
            info("BetterInventoryApi." + method + " failed: " + e);
            return null;
        }
    }

    // ================================================================================================ compat-keys

    private static void keys(final BuildingHarness.Script s) {
        begin(s, "compat-keys");
        s.run(() -> perspective("FIRST_PERSON"))
            .command("fill -6 -61 -30 8 -61 -16 minecraft:smooth_stone")
            .command("fill -6 -60 -30 8 -55 -16 minecraft:air")
            .command("setblock 0 -60 -18 minecraft:stone_bricks")
            .command("tp @s 0.5 -60 -21.5 0 30")
            .command("item replace entity @s hotbar.0 with minecraft:oak_planks 16")
            .command("item replace entity @s hotbar.1 with minecraft:stone_bricks 16")
            .command("item replace entity @s hotbar.2 with minecraft:air")
            .wait(20);
        logArea(s, "keys setup");
        // 1. Empty hand looking at the sky, no Create toolbox around: Alt is everyone else's, as without Slate Building.
        s.command("tp @s 0.5 -60 -21.5 0 -40").run(() -> Minecraft.getInstance().player.getInventory().selected = 2).wait(6)
            .run(() -> key(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_PRESS)).wait(6)
            .run(() -> {
                info("Alt with an empty hand, crosshair on " + describe(Minecraft.getInstance().hitResult) + ": " + altState());
                check("Alt + empty hand (sky): not claimed by us, wheel shut", !ExclusiveKeys.isHeldExclusively(BuildKeys.SWAP) && !WheelOverlay.INSTANCE.isOpen(), altState());
                check("Alt + empty hand (sky): Shoulder Surfing free look down", down("key.shouldersurfing.free_look"), altState());
                check("Alt + empty hand (sky): Relics ability list down", down("key.relics.active_abilities_list"), altState());
                check("Alt + empty hand (sky): Create toolbelt mapping down", down("create.keyinfo.toolbelt"), altState());
            })
            .run(() -> key(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_RELEASE)).wait(6)
            .run(() -> check("Alt released (empty hand): nothing stuck down", !down("key.shouldersurfing.free_look")
                && !down("key.relics.active_abilities_list") && !down("create.keyinfo.toolbelt"), altState()));
        logArea(s, "keys after step 1 (sky)");
        // 1b. Empty hand looking at a variant-able block: the in-world reshape wheel needs the toolbox or a building tool
        // in hand, so free look / Relics / Create keep Alt here.
        s.command("tp @s 0.5 -60 -21.5 0 30").wait(6)
            .run(() -> key(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_PRESS)).wait(8)
            .run(() -> {
                info("Alt with an empty hand, crosshair on " + describe(Minecraft.getInstance().hitResult) + ": " + altState());
                check("Alt + empty hand looking at a block: not claimed by us, wheel shut", !ExclusiveKeys.isHeldExclusively(BuildKeys.SWAP) && !WheelOverlay.INSTANCE.isOpen(), altState());
                check("Alt + empty hand looking at a block: Shoulder Surfing free look still gets Alt", down("key.shouldersurfing.free_look"), altState());
            })
            .run(() -> key(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_RELEASE)).wait(6);
        logArea(s, "keys after step 1b (empty hand on a block)");
        // 1c. A building tool in hand, same block: the in-world reshape wheel opens and takes Alt for itself.
        s.command("item replace entity @s hotbar.3 with slate_building:copper_hammer")
            .run(() -> Minecraft.getInstance().player.getInventory().selected = 3).wait(6)
            .run(() -> key(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_PRESS)).waitUntil(WheelOverlay.INSTANCE::isOpen, 40)
            .run(() -> {
                info("Alt with a hammer in hand, crosshair on " + describe(Minecraft.getInstance().hitResult) + ": " + altState());
                check("Alt + hammer looking at a block: the reshape wheel opens and holds Alt", WheelOverlay.INSTANCE.isOpen() && ExclusiveKeys.isHeldExclusively(BuildKeys.SWAP), altState());
                check("Alt + hammer looking at a block: Shoulder Surfing free look not down", !down("key.shouldersurfing.free_look"), altState());
            })
            .run(() -> key(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_RELEASE)).wait(6)
            .command("item replace entity @s hotbar.3 with minecraft:air")
            .run(() -> Minecraft.getInstance().player.getInventory().selected = 2).wait(4);
        logArea(s, "keys after step 1c (reshape wheel)");
        probeReshape(s, new BlockPos(0, -61, -19), "stairs");
        logArea(s, "keys after the reshape probe");
        // 2. Holding a block: ours only.
        s.run(() -> Minecraft.getInstance().player.getInventory().selected = 0).wait(4)
            .run(() -> key(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_PRESS)).waitUntil(WheelOverlay.INSTANCE::isOpen, 40)
            .run(() -> {
                info("Alt with a block: " + altState());
                check("Alt + block: our swap key holds Alt exclusively", ExclusiveKeys.isHeldExclusively(BuildKeys.SWAP), altState());
                check("Alt + block: the swap wheel is open", WheelOverlay.INSTANCE.isOpen(), altState());
                check("Alt + block: Shoulder Surfing free look not down", !down("key.shouldersurfing.free_look"), altState());
                check("Alt + block: Relics ability list not down", !down("key.relics.active_abilities_list"), altState());
                check("Alt + block: Create toolbelt mapping not down", !down("create.keyinfo.toolbelt"), altState());
                check("Alt + block: BetterInventory carousel not down", !down("key.betterinventory.offhand_selector"), altState());
            })
            .wait(20).screenshot("compat-keys-alt-block")
            .run(() -> key(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_RELEASE)).wait(6)
            .run(() -> {
                check("Alt released: wheel closed", !WheelOverlay.INSTANCE.isOpen(), "open");
                check("Alt released: held stack unchanged (released in the dead zone)",
                    Minecraft.getInstance().player.getMainHandItem().is(Items.OAK_PLANKS), Minecraft.getInstance().player.getMainHandItem());
                check("Alt released: no mapping stuck down", !down("key.slate_building.swap") && !down("key.shouldersurfing.free_look")
                    && !down("key.relics.active_abilities_list"), altState());
            });
        logArea(s, "keys after step 2 (Alt + block)");
        // 3. Shoulder Surfing perspective (the DF default): the wheel still opens.
        s.run(() -> perspective("SHOULDER_SURFING")).wait(10)
            .run(() -> key(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_PRESS)).waitUntil(WheelOverlay.INSTANCE::isOpen, 40)
            .run(() -> check("shoulder surfing: Alt + block opens the wheel", WheelOverlay.INSTANCE.isOpen(), altState()))
            .wait(20).screenshot("compat-keys-alt-shoulder")
            .run(() -> key(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_RELEASE)).wait(6)
            .run(() -> perspective("FIRST_PERSON")).wait(4);
        logArea(s, "keys after step 3");
        // 4. A Create toolbox in range: its toolbelt radial listens to the raw key event, not the mapping.
        s.command("setblock 2 -60 -21 create:brown_toolbox").wait(30)
            .run(() -> CreateToolbeltCompat.enabled = false)
            .run(() -> key(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_PRESS)).wait(6)
            .run(() -> info("WITHOUT CreateToolbeltCompat, Alt + block next to a Create toolbox: " + altState()))
            .screenshot("compat-keys-alt-create-conflict")
            .run(() -> key(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_RELEASE)).wait(4)
            .run(() -> {
                Minecraft.getInstance().setScreen(null);
                CreateToolbeltCompat.enabled = true;
            }).wait(10)
            .run(() -> key(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_PRESS)).waitUntil(WheelOverlay.INSTANCE::isOpen, 40)
            .run(() -> {
                info("with CreateToolbeltCompat, Alt + block next to a Create toolbox: " + altState());
                check("Create toolbox in range, Alt + block: our wheel opens", WheelOverlay.INSTANCE.isOpen() && ExclusiveKeys.isHeldExclusively(BuildKeys.SWAP), altState());
                check("Create toolbox in range, Alt + block: Create's radial stays shut", !createRadialOpen(), altState());
            })
            .run(() -> key(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_RELEASE)).wait(6)
            .command("tp @s 0.5 -60 -21.5 0 -40").run(() -> Minecraft.getInstance().player.getInventory().selected = 2).wait(6)
            .run(() -> key(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_PRESS)).wait(6)
            .run(() -> check("Create toolbox in range, Alt + empty hand (sky): Create's radial opens", createRadialOpen(), altState()))
            .run(() -> key(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_RELEASE)).wait(4)
            .run(() -> Minecraft.getInstance().setScreen(null))
            .command("setblock 2 -60 -21 minecraft:air").wait(6);
        logArea(s, "keys after step 4 (Create radial)");
        // 5. Right Alt is BetterInventory's, and ours is not on it.
        s.run(() -> Minecraft.getInstance().player.getInventory().selected = 0)
            .run(() -> key(GLFW.GLFW_KEY_RIGHT_ALT, GLFW.GLFW_PRESS)).wait(4)
            .run(() -> {
                check("Right Alt: BetterInventory carousel down", down("key.betterinventory.offhand_selector"), altState());
                check("Right Alt: our wheel stays shut", !WheelOverlay.INSTANCE.isOpen(), "open");
            })
            .run(() -> key(GLFW.GLFW_KEY_RIGHT_ALT, GLFW.GLFW_RELEASE)).wait(4);
        // 6. R with a block opens our menu; the Iris shader reload mapping stays up.
        s.run(() -> key(GLFW.GLFW_KEY_R, GLFW.GLFW_PRESS)).wait(10)
            .run(() -> {
                check("R + block: build menu opens", Minecraft.getInstance().screen instanceof BuildMenuScreen,
                    Minecraft.getInstance().screen == null ? "none" : Minecraft.getInstance().screen.getClass().getName());
                check("R + block: Iris shader reload not down", !down("iris.keybind.reload"), "down");
            })
            .run(() -> key(GLFW.GLFW_KEY_R, GLFW.GLFW_RELEASE)).wait(2)
            .run(() -> Minecraft.getInstance().setScreen(null)).wait(6);
        // 7. What the crosshair targets with Shoulder Surfing's camera pick vector (the DF default perspective).
        s.command("tp @s 0.5 -60 -20.5 0 30").run(() -> perspective("FIRST_PERSON")).wait(10)
            .run(() -> NOTES.put("hitFirst", describe(Minecraft.getInstance().hitResult)))
            .run(() -> perspective("SHOULDER_SURFING")).wait(10)
            .run(() -> info("crosshair target from 0.5 -60 -20.5 looking 0/30: first person " + NOTES.get("hitFirst")
                + ", shoulder surfing " + describe(Minecraft.getInstance().hitResult)))
            .run(() -> perspective("FIRST_PERSON"));
        end(s);
    }


    /** Diagnostics: the items lying around the player and the hotbar, logged from the server. */
    private static void logArea(final BuildingHarness.Script s, final String label) {
        onServer(s, c -> {
            final ServerPlayer p = c.player();
            final Map<Item, Integer> loose = new LinkedHashMap<>();
            for (final ItemEntity e : c.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(12))) {
                loose.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
            }
            final List<String> hotbar = new ArrayList<>();
            for (int i = 0; i < 9; i++) hotbar.add(p.getInventory().getItem(i).toString());
            info(label + ": items on the ground " + loose + ", hotbar " + hotbar);
        });
    }

    /** Diagnostics: why an in-world reshape of {@code pos} is refused in this pack. */
    private static void probeReshape(final BuildingHarness.Script s, final BlockPos pos, final String shape) {
        onServer(s, c -> {
            final ServerPlayer p = c.player();
            final ServerLevel level = c.level();
            final BlockState before = level.getBlockState(pos);
            info("reshape probe at " + pos.toShortString() + " (" + before + "): mayBuild=" + p.mayBuild() + " mayInteract=" + level.mayInteract(p, pos)
                + " canBreak=" + BuildingPlatform.get().canBreak(p, level, pos, before) + " reach=" + p.canInteractWithBlock(pos, 1.0)
                + " identify=" + VariantRegistry.get().identify(level, pos));
            VariantActions.reshapeTarget(new ReshapeTarget(pos, shape), p);
            info("reshape probe -> " + level.getBlockState(pos));
        });
    }
    private static String altState() {
        final Minecraft mc = Minecraft.getInstance();
        return "swapExclusive=" + ExclusiveKeys.isHeldExclusively(BuildKeys.SWAP) + " wheel=" + WheelOverlay.INSTANCE.isOpen()
            + " freeLook=" + down("key.shouldersurfing.free_look") + " relics=" + down("key.relics.active_abilities_list")
            + " createToolbelt=" + down("create.keyinfo.toolbelt") + " biCarousel=" + down("key.betterinventory.offhand_selector")
            + " screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
    }

    private static boolean createRadialOpen() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.screen != null && CreateToolbeltCompat.RADIAL.equals(mc.screen.getClass().getName());
    }

    // ================================================================================================ compat-opac

    private static final BlockPos OPAC_IN = new BlockPos(88, -60, 88);      // chunk (5, 5), claimed
    private static final BlockPos OPAC_OUT = new BlockPos(100, -60, 88);    // chunk (6, 5), not claimed

    private static void opac(final BuildingHarness.Script s) {
        begin(s, "compat-opac");
        if (!ModList.get().isLoaded("openpartiesandclaims")) {
            s.run(() -> check("OPAC loaded", false, "absent"));
            end(s);
            return;
        }
        s.command("fill 80 -61 80 111 -61 95 minecraft:smooth_stone")
            .command("fill 80 -60 80 111 -55 95 minecraft:air")
            .command("tp @s 94.5 -60 88.5 90 20")
            .command("item replace entity @s hotbar.0 with minecraft:stone_bricks 64")
            .run(() -> Minecraft.getInstance().player.getInventory().selected = 0)
            .wait(20)
            .command("execute positioned 88 -60 88 run openpac-claims claim")
            .wait(20);
        onServer(s, c -> {
            final Object claim = opacClaim(c, OPAC_IN);
            info("OPAC claim at " + OPAC_IN + ": " + claim);
            if (claim == null) {
                // The claim command works on the player's own chunk: stand in it.
                c.player().teleportTo(OPAC_IN.getX() + 0.5, OPAC_IN.getY(), OPAC_IN.getZ() + 0.5);
            }
        });
        s.wait(10).command("openpac-claims claim").wait(20);
        onServer(s, c -> {
            final Object claim = opacClaim(c, OPAC_IN);
            check("OPAC: chunk (5,5) claimed by the player", claim != null, "not claimed");
            check("OPAC: chunk (6,5) not claimed", opacClaim(c, OPAC_OUT) == null, opacClaim(c, OPAC_OUT));
            final ServerLevel level = c.level();
            level.setBlock(OPAC_IN.offset(0, 0, 2), Blocks.STONE.defaultBlockState(), 3);
            check("OPAC owner: canBreak in own claim", BuildingPlatform.get().canBreak(c.player(), level, OPAC_IN.offset(0, 0, 2),
                level.getBlockState(OPAC_IN.offset(0, 0, 2))), "denied");
        });
        s.command("openpac-claims non-ally-mode").wait(10);
        onServer(s, c -> {
            final ServerLevel level = c.level();
            final ServerPlayer player = c.player();
            player.teleportTo(94.5, -60, 88.5);
            final boolean protectedPlace = opacProtectsPlace(c, OPAC_IN);
            info("OPAC protection says place denied in claim (non-ally mode): " + protectedPlace);
            check("OPAC non-ally mode active (API denies placing in the claim)", protectedPlace, "not denied");
            final BlockPos stone = OPAC_IN.offset(0, 0, 2);
            check("claim: BuildingPlatform.canBreak denied", !BuildingPlatform.get().canBreak(player, level, stone, level.getBlockState(stone)), "allowed");
            final BlockPos air = OPAC_IN.offset(0, 0, -2);
            final boolean placed = BuildingPlatform.get().tryPlace(player, level, air, Blocks.STONE_BRICKS.defaultBlockState(), Direction.UP, 3);
            check("claim: BuildingPlatform.tryPlace denied and reverted", !placed && level.getBlockState(air).isAir(), "placed=" + placed + " " + level.getBlockState(air));
            VariantActions.reshapeTarget(new ReshapeTarget(stone, "stairs"), player);
            check("claim: in-world reshape refused", level.getBlockState(stone).is(Blocks.STONE), level.getBlockState(stone));
            final BlockPos outside = OPAC_OUT.offset(0, 0, 2);
            level.setBlock(outside, Blocks.STONE.defaultBlockState(), 3);
            VariantActions.reshapeTarget(new ReshapeTarget(outside, "stairs"), player);
            check("outside the claim: in-world reshape works", level.getBlockState(outside).is(Blocks.STONE_STAIRS), level.getBlockState(outside));
            NOTES.put("opacBefore", Optional.ofNullable(OpsServer.lastResult(player)).map(OpResult::op).orElse(-1));
            OpsServer.apply(player, "fill", new CompoundTag(), List.of(new BlockPos(86, -60, 84), new BlockPos(90, -58, 84)), Direction.UP, 0, false);
        });
        s.waitUntil(() -> opResultAfter("opacBefore") != null, 400).wait(20);   // ops.minTicksBetweenOps
        onServer(s, c -> {
            final OpResult r = OpsServer.lastResult(c.player());
            info("fill inside the claim: " + r);
            check("claim: fill places nothing and skips the claimed blocks", r != null && r.placed() == 0 && r.skipped() > 0, r);
            int changed = 0;
            for (final BlockPos p : BlockPos.betweenClosed(new BlockPos(86, -60, 84), new BlockPos(90, -58, 84))) if (!c.level().getBlockState(p).isAir()) changed++;
            check("claim: no block changed inside the claim", changed == 0, changed);
            NOTES.put("opacBefore2", r == null ? -1 : r.op());
            OpsServer.apply(c.player(), "fill", new CompoundTag(), List.of(new BlockPos(98, -60, 84), new BlockPos(100, -58, 84)), Direction.UP, 0, false);
        });
        s.waitUntil(() -> opResultAfter("opacBefore2") != null, 400);
        onServer(s, c -> {
            final OpResult r = OpsServer.lastResult(c.player());
            info("fill outside the claim: " + r);
            check("outside the claim: fill places its 9 blocks", r != null && r.placed() == 9, r);
        });
        s.command("tp @s 94.5 -57 80.5 0 25").wait(30).screenshot("compat-opac")
            .command("openpac-claims non-ally-mode")
            .command("execute positioned 88 -60 88 run openpac-claims unclaim");
        end(s);
    }

    private static @Nullable Object opResultAfter(final String note) {
        final Minecraft mc = Minecraft.getInstance();
        final IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return null;
        final ServerPlayer p = server.getPlayerList().getPlayer(mc.player.getUUID());
        if (p == null) return null;
        final OpResult r = OpsServer.lastResult(p);
        final Object before = NOTES.get(note);
        return r != null && (before == null || r.op() != (int) before) ? r : null;
    }

    private static @Nullable Object opacClaim(final Ctx c, final BlockPos pos) {
        try {
            final Object api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI")
                .getMethod("get", net.minecraft.server.MinecraftServer.class).invoke(null, c.server());
            final Object claims = api.getClass().getMethod("getServerClaimsManager").invoke(api);
            return Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI")
                .getMethod("get", ResourceLocation.class, BlockPos.class).invoke(claims, c.level().dimension().location(), pos);
        } catch (final ReflectiveOperationException | LinkageError e) {
            info("OPAC claims API failed: " + e);
            return null;
        }
    }

    private static boolean opacProtectsPlace(final Ctx c, final BlockPos pos) {
        try {
            final Object api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI")
                .getMethod("get", net.minecraft.server.MinecraftServer.class).invoke(null, c.server());
            final Object protection = api.getClass().getMethod("getChunkProtection").invoke(api);
            return (boolean) Class.forName("xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI")
                .getMethod("onEntityPlaceBlock", net.minecraft.world.entity.Entity.class, ServerLevel.class, BlockPos.class)
                .invoke(protection, c.player(), c.level(), pos);
        } catch (final ReflectiveOperationException | LinkageError e) {
            info("OPAC protection API failed: " + e);
            return false;
        }
    }

    // ================================================================================================ helpers

    private static void begin(final BuildingHarness.Script s, final String scenario) {
        s.run(() -> {
            PASSED.set(0);
            FAILED.set(0);
            FAILURES.clear();
            NOTES.put("scenario", scenario);
            SlateBuilding.LOGGER.info("[BuildingHarness] {}: start", scenario);
        });
    }

    private static void end(final BuildingHarness.Script s) {
        s.run(() -> {
            final Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) mc.setScreen(null);
            SlateBuilding.LOGGER.info("[BuildingHarness] {}: {} passed, {} failed{}", NOTES.get("scenario"), PASSED.get(), FAILED.get(),
                FAILURES.isEmpty() ? "" : " -> " + String.join("; ", FAILURES));
        }).command("gamemode creative");
    }

    private static void check(final String name, final boolean ok, final @Nullable Object detail) {
        if (ok) {
            PASSED.incrementAndGet();
            TOTAL_PASSED.incrementAndGet();
            SlateBuilding.LOGGER.info("[BuildingHarness] PASS {}", name);
        } else {
            FAILED.incrementAndGet();
            FAILURES.add(name);
            TOTAL_FAILURES.add(name);
            SlateBuilding.LOGGER.warn("[BuildingHarness] FAIL {} ({})", name, detail);
        }
    }

    private static void info(final String message) {
        SlateBuilding.LOGGER.info("[BuildingHarness] INFO {}", message);
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
            final UUID id = mc.player.getUUID();
            pending = server.submit(() -> {
                final ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null) {
                    check("server player", false, "missing");
                    return;
                }
                try {
                    task.accept(new Ctx(server, player.serverLevel(), player));
                } catch (final RuntimeException e) {
                    SlateBuilding.LOGGER.error("[BuildingHarness] {}: server step failed", NOTES.get("scenario"), e);
                    check("server step", false, e.toString());
                }
            });
        }).waitUntil(() -> pending != null && pending.isDone(), 1200);
    }

    /** Places ({@code material}, {@code shape}) at {@code pos} through its item, as clicking the floor below would. */
    private static @Nullable BlockPos placeVariant(final ServerLevel level, final Block material, final Shape shape, final BlockPos pos) {
        return placeItem(level, VariantRegistry.get().stackFor(material, shape, 1), pos);
    }

    /** Whether {@code block} implements the DiagonalBlocks API interface (reflective: this class must load without the library). */
    private static boolean isLibDiagonal(final Block block) {
        try {
            return Class.forName("fuzs.diagonalblocks.api.v2.DiagonalBlock").isInstance(block);
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    private static @Nullable BlockPos placeItem(final ServerLevel level, final ItemStack stack, final BlockPos pos) {
        if (!(stack.getItem() instanceof BlockItem item)) return null;
        final BlockPos below = pos.below();
        final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false);
        final BlockPlaceContext ctx = new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, stack, hit);
        try {
            return item.place(ctx).consumesAction() ? ctx.getClickedPos() : null;
        } catch (final RuntimeException e) {
            SlateBuilding.LOGGER.error("[BuildingHarness] placing {} threw", stack, e);
            return null;
        }
    }

    private static void mergeVerticalSlab(final ServerLevel level, final Block material, final BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof VerticalSlabBlock)) return;
        final Direction open = state.getValue(VerticalSlabBlock.FACING).getOpposite();
        final ItemStack stack = VariantRegistry.get().stackFor(material, Shape.VERTICAL_SLAB, 1);
        ((BlockItem) stack.getItem()).place(new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, stack,
            new BlockHitResult(Vec3.atCenterOf(pos), open, pos, false)));
    }

    /** Survival {@code ServerPlayerGameMode.destroyBlock} with {@code tool}; returns (and removes) the items dropped. */
    private static Map<Item, Integer> survivalBreak(final Ctx c, final BlockPos pos, final ItemStack tool) {
        final ServerPlayer player = c.player();
        collectDrops(c.level(), pos);
        final ItemStack held = player.getMainHandItem();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool.copy());
        try {
            player.gameMode.destroyBlock(pos);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            player.setGameMode(GameType.CREATIVE);
        }
        return collectDrops(c.level(), pos);
    }

    private static Map<Item, Integer> collectDrops(final ServerLevel level, final BlockPos pos) {
        final Map<Item, Integer> dropped = new LinkedHashMap<>();
        for (final ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.0))) {
            dropped.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
            e.discard();
        }
        return dropped;
    }

    private static void key(final int glfwKey, final int action) {
        final Minecraft mc = Minecraft.getInstance();
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), glfwKey, GLFW.glfwGetKeyScancode(glfwKey), action, 0);
    }

    private static @Nullable KeyMapping mapping(final String name) {
        for (final KeyMapping m : Minecraft.getInstance().options.keyMappings) if (m.getName().equals(name)) return m;
        return null;
    }

    private static boolean down(final String name) {
        final KeyMapping m = mapping(name);
        return m != null && m.isDown();
    }

    /** Switches the camera through Shoulder Surfing's API when present (it owns the perspective in the DF pack). */
    private static void perspective(final String name) {
        final Minecraft mc = Minecraft.getInstance();
        if (ModList.get().isLoaded("shouldersurfing")) {
            try {
                final Class<?> persp = Class.forName("com.github.exopandora.shouldersurfing.api.client.Perspective");
                final Object instance = Class.forName("com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing").getMethod("getInstance").invoke(null);
                Class.forName("com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing").getMethod("changePerspective", persp)
                    .invoke(instance, persp.getMethod("valueOf", String.class).invoke(null, name));
                return;
            } catch (final ReflectiveOperationException | LinkageError e) {
                info("Shoulder Surfing perspective switch failed: " + e);
            }
        }
        mc.options.setCameraType("FIRST_PERSON".equals(name) ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK);
    }

    private static String describe(final @Nullable HitResult hit) {
        if (hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
            return "block " + b.getBlockPos().toShortString() + " " + BuiltInRegistries.BLOCK.getKey(Minecraft.getInstance().level.getBlockState(b.getBlockPos()).getBlock())
                + " face " + b.getDirection();
        }
        return hit == null ? "nothing" : hit.getType().toString();
    }

    private static String optionsValue(final Minecraft mc, final String key) {
        try {
            for (final String line : Files.readAllLines(mc.gameDirectory.toPath().resolve("options.txt"), StandardCharsets.UTF_8)) {
                if (line.startsWith(key + ":")) return line.substring(key.length() + 1);
            }
        } catch (final java.io.IOException e) {
            return "unreadable: " + e;
        }
        return "absent";
    }

    private static String irisProperties(final Minecraft mc) {
        final Path p = mc.gameDirectory.toPath().resolve("config").resolve("iris.properties");
        try {
            return Files.exists(p) ? String.join(" ", Files.readAllLines(p, StandardCharsets.UTF_8).stream()
                .filter(l -> l.startsWith("enableShaders") || l.startsWith("shaderPack")).toList()) : "absent";
        } catch (final java.io.IOException e) {
            return "unreadable";
        }
    }

    private static String name(final Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    private DfCompatHarness() {}
}
