package dev.fallingcloud.slate.building.block;

import dev.fallingcloud.slate.building.item.ShapeBlockItem;
import dev.fallingcloud.slate.building.mixin.variant.BlockPropertiesAccessor;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HayBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * What every shape block shares: two hidden state properties mirrored from the material, and the delegation of
 * everything material-dependent to the material stored in the block entity (design §2).
 *
 * <p><b>Why {@link #LIGHT} and {@link #OPAQUE} are state properties.</b> Light emission and light/face occlusion are
 * read by the light engines on worker threads, where a server level hands out no block entities, and by vanilla
 * caches keyed by state. Carrying them in the state keeps lighting and face culling exact on both loaders (a roof of
 * stone slabs is dark underneath, glass stairs let light through, glowstone steps glow). They also change the
 * property count of the wall, fence and pane shapes, which is what keeps DiagonalWalls/Fences/Windows (DF pack) from
 * registering twins of them ({@code DiagonalBlockTypeImpl.isTarget} compares the property count with vanilla's), so
 * no separate always-true "shaped" property is needed. Every placement path sets them: shape items via
 * {@code getStateForPlacement}, {@link VariantRegistry#placementState}, and {@code ShapeBlockEntity.setMaterial}.
 */
public final class ShapeBehaviour {

    /** The material's light emission (0..15). */
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
    /** Whether the material occludes (culls neighbour faces, blocks light by shape). Unset shapes count as opaque. */
    public static final BooleanProperty OPAQUE = BooleanProperty.create("opaque");

    private ShapeBehaviour() {}

    /**
     * Refines the shared base properties for a shape block: occlusion back on (decided per state by {@link #OPAQUE}),
     * no dynamic shape (shapes depend on the state alone, so vanilla caches them and derives solidity from them),
     * light from {@link #LIGHT}.
     */
    public static BlockBehaviour.Properties refine(final BlockBehaviour.Properties properties) {
        final BlockPropertiesAccessor access = (BlockPropertiesAccessor) (Object) properties;
        access.slateBuilding$setCanOcclude(true);
        access.slateBuilding$setDynamicShape(false);
        return properties.lightLevel(ShapeBehaviour::lightOf);
    }

    private static int lightOf(final BlockState state) {
        return state.hasProperty(LIGHT) ? state.getValue(LIGHT) : 0;
    }

    /** {@code state} with {@link #LIGHT} and {@link #OPAQUE} taken from {@code material} (null = unset look: dark, opaque). */
    public static BlockState withMaterial(final BlockState state, final @Nullable BlockState material) {
        if (!state.hasProperty(LIGHT) || !state.hasProperty(OPAQUE)) return state;
        final int light = material == null ? 0 : Mth.clamp(material.getLightEmission(), 0, 15);
        final boolean opaque = material == null || material.canOcclude();
        return state.setValue(LIGHT, light).setValue(OPAQUE, opaque);
    }

    /**
     * The material state to store for a shape state: the material's default state, oriented where the shape has an
     * axis of its own (a log post runs along the post).
     */
    public static BlockState orientMaterial(final BlockState material, final BlockState shapeState) {
        if (shapeState.hasProperty(BlockStateProperties.AXIS) && material.hasProperty(BlockStateProperties.AXIS)) {
            return material.setValue(BlockStateProperties.AXIS, shapeState.getValue(BlockStateProperties.AXIS));
        }
        return material;
    }

    // ------------------------------------------------------------------------------------------------ placement

    /** The material being placed from {@code ctx}: a shape item's component, else whatever the stack identifies as. */
    public static @Nullable BlockState placingMaterial(final BlockPlaceContext ctx) {
        final ItemStack stack = ctx.getItemInHand();
        final Block component = ShapeBlockItem.material(stack);
        if (component != null) return component.defaultBlockState();
        final Optional<Variant> v = VariantRegistry.get().identify(stack);
        return v.map(variant -> variant.material().defaultBlockState()).orElse(null);
    }

    /** The placement state {@code base} carrying the material being placed from {@code ctx}. */
    public static @Nullable BlockState forPlacement(final @Nullable BlockState base, final BlockPlaceContext ctx) {
        return base == null ? null : withMaterial(base, placingMaterial(ctx));
    }

    /** Whether the shape at {@code pos} is made of the same material as the stack being placed (merging rule). */
    public static boolean sameMaterial(final BlockPlaceContext ctx, final BlockPos pos) {
        final BlockState there = ShapeBlock.material(ctx.getLevel(), pos);
        final BlockState placing = placingMaterial(ctx);
        return there != null && placing != null && there.getBlock() == placing.getBlock();
    }

    /**
     * After placement: makes sure the block entity holds the material even when a caller placed the block without
     * applying the stack's components, and orients it (posts). Runs on both sides.
     */
    public static void placed(final Level level, final BlockPos pos, final BlockState state, final ItemStack stack) {
        if (!(level.getBlockEntity(pos) instanceof ShapeBlockEntity be)) return;
        BlockState material = be.material();
        if (material == null) {
            final Block fromStack = ShapeBlockItem.material(stack);
            if (fromStack == null) return;
            material = fromStack.defaultBlockState();
        }
        be.setMaterial(orientMaterial(material, state));
    }

    // ------------------------------------------------------------------------------------------------ economy

    /**
     * Drops of a shape block: its material's item, one per unit, and only when the material could be harvested by the
     * breaking player's tool (explosions follow vanilla's "survives explosion" odds instead). An unset shape drops
     * nothing.
     */
    public static List<ItemStack> drops(final ShapeBlock shape, final BlockState state, final LootParams.Builder params) {
        final BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        final BlockState material = be instanceof ShapeBlockEntity s ? s.material() : null;
        if (material == null) return List.of();
        final Item item = material.getBlock().asItem();
        if (item == Items.AIR) return List.of();
        final ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
        final Entity breaker = params.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (material.requiresCorrectToolForDrops()) {
            final boolean toolBreak = breaker instanceof Player || (tool != null && !tool.isEmpty());
            if (toolBreak && (tool == null || !tool.isCorrectToolForDrops(material))) return List.of();
        }
        final Float radius = params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS);
        if (radius != null && radius > 1F && params.getLevel().getRandom().nextFloat() > 1F / radius) return List.of();
        return List.of(new ItemStack(item, Math.max(1, shape.units(state))));
    }

    /** Pick block: the variant stack (a native item where one exists), else this shape's item with the material. */
    public static ItemStack cloneStack(final ShapeBlock shape, final Block self, final LevelReader level, final BlockPos pos) {
        final BlockState material = ShapeBlock.material(level, pos);
        if (material == null) return new ItemStack(self);
        final ItemStack stack = VariantRegistry.get().stackFor(material.getBlock(), shape.shape(), 1);
        return stack.isEmpty() ? ShapeBlockItem.withMaterial(new ItemStack(self), material.getBlock()) : stack;
    }

    /**
     * KleeSlabs (DF pack) splits a double slab by dropping {@code new ItemStack(Item.byBlock(block))} (our shape item
     * WITHOUT a material) and then setting the single state. That drop is turned into the material here, the moment
     * the state shrinks: the item entity already exists (it is spawned first), is brand new, and sits in the block.
     */
    public static void healSplitDrops(final Level level, final BlockPos pos, final BlockState oldState, final BlockState newState) {
        if (level.isClientSide() || !newState.is(oldState.getBlock()) || !(oldState.getBlock() instanceof ShapeBlock shape)) return;
        if (shape.units(newState) >= shape.units(oldState)) return;
        final BlockState material = ShapeBlock.material(level, pos);
        if (material == null || material.getBlock().asItem() == Items.AIR) return;
        for (final ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(0.5),
            e -> e.tickCount == 0 && e.getItem().getItem() instanceof ShapeBlockItem && ShapeBlockItem.material(e.getItem()) == null)) {
            drop.setItem(new ItemStack(material.getBlock().asItem(), drop.getItem().getCount()));
        }
    }

    /**
     * Break effects: the material's particles and break sound (vanilla sends the shape's own state id, whose
     * sound and particles know nothing about the material). The block entity still exists at this point.
     */
    public static void destroyEffects(final Level level, final @Nullable Player player, final BlockPos pos, final BlockState state) {
        final BlockState material = ShapeBlock.material(level, pos);
        level.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(material != null ? material : state));
    }

    // ------------------------------------------------------------------------------------------------ material delegation

    /** Mining progress of the material at this position (tool, speed, harvest level); NaN when unset. */
    public static float destroyProgress(final Player player, final BlockGetter level, final BlockPos pos) {
        final BlockState material = ShapeBlock.material(level, pos);
        return material == null ? Float.NaN : material.getDestroyProgress(player, level, pos);
    }

    public static boolean canHarvest(final BlockGetter level, final BlockPos pos, final Player player) {
        final BlockState material = ShapeBlock.material(level, pos);
        return material == null || !material.requiresCorrectToolForDrops() || player.getMainHandItem().isCorrectToolForDrops(material);
    }

    public static SoundType sound(final BlockState state, final LevelReader level, final BlockPos pos) {
        final BlockState material = ShapeBlock.material(level, pos);
        return material != null ? material.getSoundType() : state.getSoundType();
    }

    public static float explosionResistance(final Block self, final BlockGetter level, final BlockPos pos) {
        final BlockState material = ShapeBlock.material(level, pos);
        return material != null ? material.getBlock().getExplosionResistance() : self.getExplosionResistance();
    }

    public static float friction(final Block self, final LevelReader level, final BlockPos pos) {
        final BlockState material = ShapeBlock.material(level, pos);
        return material != null ? material.getBlock().getFriction() : self.getFriction();
    }

    public static MapColor mapColor(final BlockGetter level, final BlockPos pos, final MapColor fallback) {
        final BlockState material = ShapeBlock.material(level, pos);
        return material != null ? material.getMapColor(level, pos) : fallback;
    }

    /**
     * Walking on a shape made of magma burns like magma. Only a vetted set of materials is delegated: many blocks'
     * {@code stepOn} rewrite the block at the position (redstone ore lights up), which would replace the shape.
     */
    public static void stepOn(final Level level, final BlockPos pos, final Entity entity) {
        final BlockState material = ShapeBlock.material(level, pos);
        if (material != null && material.getBlock() instanceof MagmaBlock magma) magma.stepOn(level, pos, material, entity);
    }

    /** Landing on a slime or hay shape softens the fall like the material; false = use the default. */
    public static boolean fallOn(final Level level, final BlockPos pos, final Entity entity, final float distance) {
        final BlockState material = ShapeBlock.material(level, pos);
        if (material == null) return false;
        final Block block = material.getBlock();
        if (block instanceof SlimeBlock || block instanceof HayBlock) {
            block.fallOn(level, material, pos, entity, distance);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ light and occlusion

    /** Occlusion: the shape itself for opaque materials, nothing for see-through ones (glass, leaves, ice). */
    public static VoxelShape occlusion(final BlockState state, final VoxelShape shape) {
        return state.getValue(OPAQUE) ? shape : Shapes.empty();
    }

    /** Sky light passes unless an opaque material fills the whole block (or a fluid is inside). */
    public static boolean propagatesSkylight(final BlockState state, final VoxelShape shape) {
        if (!state.getFluidState().isEmpty()) return false;
        return !state.getValue(OPAQUE) || !Block.isShapeFullBlock(shape);
    }
}
