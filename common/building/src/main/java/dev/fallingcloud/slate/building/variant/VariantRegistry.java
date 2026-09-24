package dev.fallingcloud.slate.building.variant;

import dev.fallingcloud.slate.building.block.ShapeBehaviour;
import dev.fallingcloud.slate.building.block.ShapeBlockEntity;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.ServerVariants;
import dev.fallingcloud.slate.building.item.ShapeBlockItem;
import dev.fallingcloud.slate.building.mixin.variant.BlockItemInvoker;
import dev.fallingcloud.slate.building.registry.BuildingItems;
import dev.fallingcloud.slate.building.registry.RegistryRef;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Knows, for every block and item, which material and shape it is, and which shapes each material has. Built
 * deterministically from the registries on BOTH sides (no sync needed): vanilla {@code BlockFamilies}, every
 * {@code StairBlock}'s base state, name-based sibling discovery, server-config overrides, re-pointed items (see
 * {@link VariantIndex} for the rules and tie-breaks).
 *
 * <p>The snapshot is rebuilt lazily: after {@link #invalidate()} (tag reloads on both sides, world join/start) and
 * whenever the variant rules in effect change (the synced server rules on a client, the local file on a server;
 * detected by a fingerprint, so live edits apply). All methods are thread-safe and cheap enough to call per frame.
 *
 * <p>Economy (design §1): every variant item and freshly placed block is ONE material unit; {@link #units} says what
 * a block in the world is worth (double slabs 2, layers n).
 */
public final class VariantRegistry {

    private static final VariantRegistry INSTANCE = new VariantRegistry();
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final ServerVariants DEFAULT_RULES = new ServerVariants();
    private static volatile @Nullable Boolean physicalClient;

    private volatile @Nullable VariantIndex index;

    private VariantRegistry() {}

    /** The registry for the current registries, tags and rules. */
    public static VariantRegistry get() {
        return INSTANCE;
    }

    /** Drops the snapshot; the next call rebuilds (tags/datapack reload, world start/join). */
    public static void invalidate() {
        GENERATION.incrementAndGet();
    }

    /** A placement: where the block goes (merging may pick the clicked block) and in which state. */
    public record Placement(BlockPos pos, BlockState state, BlockPlaceContext context) {}

    // ------------------------------------------------------------------------------------------------ identification

    /** planks → (planks, FULL); oak_stairs → (planks, STAIRS); a shape item → its material component. */
    public Optional<Variant> identify(final ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        final Item item = stack.getItem();
        if (item instanceof ShapeBlockItem shapeItem) {
            final Block material = ShapeBlockItem.material(stack);
            return material == null ? Optional.empty() : Optional.of(new Variant(material, shapeItem.shape()));
        }
        final VariantIndex idx = index();
        final Variant nativeVariant = idx.nativeItemVariant(item);
        if (nativeVariant != null) return Optional.of(nativeVariant);
        if (item instanceof BlockItem blockItem && idx.isMaterial(blockItem.getBlock())) return Optional.of(Variant.full(blockItem.getBlock()));
        return Optional.empty();
    }

    public Optional<Variant> identify(final BlockState state, final @Nullable BlockEntity be) {
        final Block block = state.getBlock();
        if (block instanceof ShapeBlock shape) {
            final BlockState material = be instanceof ShapeBlockEntity s ? s.material() : null;
            return material == null ? Optional.empty() : Optional.of(new Variant(material.getBlock(), shape.shape()));
        }
        final VariantIndex idx = index();
        final Variant nativeVariant = idx.nativeVariant(block);
        if (nativeVariant != null) return Optional.of(nativeVariant);
        return idx.isMaterial(block) ? Optional.of(Variant.full(block)) : Optional.empty();
    }

    public Optional<Variant> identify(final BlockGetter level, final BlockPos pos) {
        return identify(level.getBlockState(pos), level.getBlockEntity(pos));
    }

    /** The variant a NATIVE block (oak_stairs, a DiagonalFences twin) stands for; null for materials, our shapes and the rest. */
    public @Nullable Variant nativeVariant(final Block block) {
        return index().nativeVariant(block);
    }

    // ------------------------------------------------------------------------------------------------ materials and shapes

    /** Whether {@code block} is an eligible full block (design §2: full cube, model, own item, no block entity, not tagged out). */
    public boolean isMaterial(final Block block) {
        return index().isMaterial(block);
    }

    /** Shapes available for {@code material} (native or custom), in wheel order, FULL first; empty for non-materials. */
    public List<Shape> shapesFor(final Block material) {
        final VariantIndex idx = index();
        return idx.shapes(material, s -> isAvailable(idx, material, s));
    }

    public boolean isAvailable(final Block material, final Shape shape) {
        return isAvailable(index(), material, shape);
    }

    private static boolean isAvailable(final VariantIndex idx, final Block material, final Shape shape) {
        if (shape == Shape.FULL) return material.asItem() != Items.AIR && (idx.isMaterial(material) || idx.hasRealisations(material));
        if (idx.realisation(material, shape) != null) return true;
        return idx.customShapes && idx.isMaterial(material);
    }

    /** The native block realising ({@code material}, {@code shape}) if one exists, e.g. oak_stairs for (oak_planks, STAIRS). */
    public @Nullable Block nativeBlock(final Block material, final Shape shape) {
        return shape == Shape.FULL ? material : index().realisation(material, shape);
    }

    /**
     * The stack for ({@code material}, {@code shape}): the material's own item for FULL; the native item whenever one
     * exists; else Slate Building's shape item carrying the material. Empty when that variant is not available.
     *
     * <p>Natives win even with {@code deleteNativeVariants}: deleting only hides them (creative tabs, search, JEI) and
     * removes the recipes that MAKE them. Every recipe that CONSUMES them ({@code #wooden_slabs} in a barrel, a stone
     * slab in a grindstone, Create's seats ...) keeps working because the swap wheel, build menu, chisel and pick-block
     * still hand out the native item. Our shape items only stand in for (material, shape) pairs with no native.
     */
    public ItemStack stackFor(final Block material, final Shape shape, final int count) {
        if (count <= 0) return ItemStack.EMPTY;
        final VariantIndex idx = index();
        if (!isAvailable(idx, material, shape)) return ItemStack.EMPTY;
        if (shape == Shape.FULL) return new ItemStack(material, count);
        final Block nativeBlock = idx.realisation(material, shape);
        if (nativeBlock != null) return new ItemStack(nativeBlock.asItem(), count);
        final RegistryRef<ShapeBlockItem> item = BuildingItems.forShape(shape);
        if (item == null || !idx.customShapes || !idx.isMaterial(material)) return ItemStack.EMPTY;
        return ShapeBlockItem.withMaterial(new ItemStack(item.get(), count), material);
    }

    /**
     * The entry that stands in for a HIDDEN native in listings (creative tabs, JEI) while {@code deleteNativeVariants}
     * is on: our shape item carrying the material, so "Oak Planks Stairs" stays findable once oak_stairs is hidden.
     * Placing it still places the native ({@link ShapeBlockItem}); everything else keeps using {@link #stackFor}.
     * Empty when our shape cannot stand in (custom shapes off, or not an eligible material).
     */
    public ItemStack listingStackFor(final Block material, final Shape shape) {
        if (shape == Shape.FULL) return ItemStack.EMPTY;
        final VariantIndex idx = index();
        final RegistryRef<ShapeBlockItem> item = BuildingItems.forShape(shape);
        if (item == null || !idx.customShapes || !idx.isMaterial(material)) return ItemStack.EMPTY;
        return ShapeBlockItem.withMaterial(new ItemStack(item.get()), material);
    }

    /** Material units the block at hand is worth: 1, 2 for double slabs, n for layers. */
    public int units(final BlockState state, final @Nullable BlockEntity be) {
        if (state.getBlock() instanceof ShapeBlock shape) return shape.units(state);
        if (state.getBlock() instanceof SlabBlock && state.hasProperty(SlabBlock.TYPE) && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) return 2;
        return 1;
    }

    /** Every native variant item (what "delete native variants" hides and what recipes are rebalanced for). */
    public Set<Item> nativeVariantItems() {
        return index().nativeItems();
    }

    /** Whether native variants are being replaced by the unified shapes right now ({@code unify} and {@code deleteNativeVariants}). */
    public boolean deletesNatives() {
        final VariantIndex idx = index();
        return idx.unify && idx.deleteNatives;
    }

    /** Whether native variants are unified at all ({@code unify}). */
    public boolean unifies() {
        return index().unify;
    }

    // ------------------------------------------------------------------------------------------------ placement

    /**
     * The state to place for {@code v} from {@code ctx}; goes through the ITEM {@link #stackFor} hands out (the native
     * item where one exists) so DiagonalBlocks' re-pointed items and merging slabs behave exactly as for a player
     * holding it. Null when it cannot be placed there.
     */
    public @Nullable BlockState placementState(final Variant v, final BlockPlaceContext ctx) {
        final Placement p = placement(v, ctx);
        return p == null ? null : p.state();
    }

    /** Like {@link #placementState} but also says WHERE (merging into the clicked slab changes the position). */
    public @Nullable Placement placement(final Variant v, final BlockPlaceContext ctx) {
        final ItemStack stack = stackFor(v.material(), v.shape(), 1);
        if (!(stack.getItem() instanceof BlockItem item)) return null;
        BlockPlaceContext placeCtx = ItemStack.isSameItemSameComponents(stack, ctx.getItemInHand()) ? ctx : withStack(ctx, stack);
        if (!placeCtx.canPlace()) return null;
        placeCtx = item.updatePlacementContext(placeCtx);
        if (placeCtx == null) return null;
        BlockState state = ((BlockItemInvoker) item).slateBuilding$getPlacementState(placeCtx);
        if (state == null) return null;
        if (state.getBlock() instanceof ShapeBlock) state = ShapeBehaviour.withMaterial(state, v.material().defaultBlockState());
        return new Placement(placeCtx.getClickedPos(), state, placeCtx);
    }

    /** {@code ctx} as if {@code stack} were in hand (same click; whether the clicked block is replaced is re-evaluated). */
    public static BlockPlaceContext withStack(final BlockPlaceContext ctx, final ItemStack stack) {
        final Direction face = ctx.getClickedFace();
        final BlockPos hitPos = ctx.replacingClickedOnBlock() ? ctx.getClickedPos() : ctx.getClickedPos().relative(face.getOpposite());
        final BlockHitResult hit = new BlockHitResult(ctx.getClickLocation(), face, hitPos, ctx.isInside());
        return new BlockPlaceContext(ctx.getLevel(), ctx.getPlayer(), ctx.getHand(), stack, hit);
    }

    // ------------------------------------------------------------------------------------------------ reshaping

    /** The material state of a block in the world: our shape's stored material, a material itself, or a native's material. */
    public @Nullable BlockState materialState(final BlockState state, final @Nullable BlockEntity be) {
        if (state.getBlock() instanceof ShapeBlock) return be instanceof ShapeBlockEntity s ? s.material() : null;
        return identify(state, be).map(v -> v.isFull() ? state : v.material().defaultBlockState()).orElse(null);
    }

    /**
     * The state that turns the variant at {@code pos} (state {@code from}, block entity {@code be}) into shape
     * {@code target}, keeping the material, carrying orientation, half and water over where they map, connected to
     * its neighbours. For our shapes the caller sets the block entity's material afterwards
     * ({@link ShapeBehaviour#orientMaterial} of {@link #materialState}). Null when not a variant / not available.
     */
    public @Nullable BlockState reshape(final LevelAccessor level, final BlockPos pos, final BlockState from, final @Nullable BlockEntity be, final Shape target) {
        final Optional<Variant> v = identify(from, be);
        if (v.isEmpty() || !isAvailable(v.get().material(), target)) return null;
        final BlockState material = materialState(from, be);
        if (material == null) return null;
        final ItemStack stack = stackFor(v.get().material(), target, 1);
        final BlockState base;
        if (target == Shape.FULL) {
            base = material;
        } else if (stack.getItem() instanceof BlockItem item) {
            base = item.getBlock().defaultBlockState();
        } else {
            return null;
        }
        BlockState next = ShapeConversion.carryOver(from, base, level.getFluidState(pos));
        next = Block.updateFromNeighbourShapes(next, level, pos);
        if (next.getBlock() instanceof ShapeBlock) next = ShapeBehaviour.withMaterial(next, material);
        return next;
    }

    // ------------------------------------------------------------------------------------------------ snapshot

    /**
     * The current snapshot. Hot path (every identify: the drops mixin, the economy per slot, planners per position), so
     * the staleness check is three boolean compares plus an identity-memoised hash of the rule lists
     * ({@link VariantIndex#matches}), never a walk over the config.
     */
    private VariantIndex index() {
        final ServerVariants rules = rules();
        final int generation = GENERATION.get();
        VariantIndex idx = index;
        if (idx != null && idx.matches(rules, generation)) return idx;
        synchronized (this) {
            idx = index;
            if (idx != null && idx.matches(rules, generation)) return idx;
            idx = VariantIndex.build(rules, generation, VariantIndex.fingerprint(rules));
            index = idx;
            return idx;
        }
    }

    /** The variant rules in effect on this side: the connected server's on a client, the local file otherwise. */
    static ServerVariants rules() {
        ServerVariants rules = null;
        try {
            rules = isPhysicalClient() ? ClientSide.rules() : BuildingServerSettings.local().variants();
        } catch (final RuntimeException e) {
            // Too early for the client (no Minecraft instance yet): the local file is what applies.
            rules = BuildingServerSettings.local().variants();
        }
        return rules != null ? rules : DEFAULT_RULES;
    }

    private static boolean isPhysicalClient() {
        Boolean client = physicalClient;
        if (client == null) physicalClient = client = SlatePlatform.get().isClient();
        return client;
    }

    /** Client-only references live here so a dedicated server never resolves them. */
    private static final class ClientSide {
        static ServerVariants rules() {
            return dev.fallingcloud.slate.building.client.ServerSettingsClient.get().variants();
        }
    }
}
