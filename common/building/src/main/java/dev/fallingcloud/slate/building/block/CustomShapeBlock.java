package dev.fallingcloud.slate.building.block;

import dev.fallingcloud.slate.building.variant.ShapeBlock;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Base of Slate Building's own shapes (vertical slab, vertical stairs, step, panel, vertical step, post, layer):
 * waterloggable, geometry from boxes (collision = outline = render boxes, computed once per state), and the material
 * delegation of {@link ShapeBehaviour}. Subclasses declare their properties, boxes, placement and transforms.
 */
public abstract class CustomShapeBlock extends Block implements ShapeBlock, EntityBlock, SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private volatile @Nullable Map<BlockState, Geometry> geometry;

    /** The boxes of one state and their union as a vanilla shape. */
    protected record Geometry(List<AABB> boxes, VoxelShape shape, boolean full) {}

    protected CustomShapeBlock(final BlockBehaviour.Properties properties) {
        super(ShapeBehaviour.refine(properties));
        registerDefaultState(stateDefinition.any()
            .setValue(WATERLOGGED, false)
            .setValue(ShapeBehaviour.LIGHT, 0)
            .setValue(ShapeBehaviour.OPAQUE, true));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, ShapeBehaviour.LIGHT, ShapeBehaviour.OPAQUE);
    }

    /** Boxes (0..16) of {@code state}; only geometry properties matter. Called once per state, may be slow. */
    protected abstract List<AABB> computeBoxes(BlockState state);

    /** The placement state from {@code ctx} ignoring water and material (both added by the caller), or null. */
    protected abstract @Nullable BlockState placementShape(BlockPlaceContext ctx);

    protected final Geometry geometry(final BlockState state) {
        Map<BlockState, Geometry> map = geometry;
        if (map == null) map = buildGeometry();
        return map.get(state);
    }

    private synchronized Map<BlockState, Geometry> buildGeometry() {
        Map<BlockState, Geometry> map = geometry;
        if (map != null) return map;
        map = new IdentityHashMap<>();
        final Map<List<AABB>, Geometry> shared = new HashMap<>();
        for (final BlockState state : getStateDefinition().getPossibleStates()) {
            final List<AABB> boxes = ShapeBoxes.merge(computeBoxes(state));
            map.put(state, shared.computeIfAbsent(boxes, b -> {
                final VoxelShape shape = ShapeBoxes.toShape(b);
                return new Geometry(b, shape, Block.isShapeFullBlock(shape));
            }));
        }
        geometry = map;
        return map;
    }

    /** Whether {@code state} fills the whole block (double vertical slab, 8 layers): not waterloggable. */
    protected final boolean isFull(final BlockState state) {
        return geometry(state).full();
    }

    // ------------------------------------------------------------------------------------------------ ShapeBlock

    @Override
    public List<AABB> renderBoxes(final BlockState state) {
        return geometry(state).boxes();
    }

    @Override
    public int units(final BlockState state) {
        return 1;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ShapeBlockEntity(pos, state);
    }

    // ------------------------------------------------------------------------------------------------ shape and light

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return geometry(state).shape();
    }

    @Override
    protected VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return geometry(state).shape();
    }

    @Override
    protected VoxelShape getOcclusionShape(final BlockState state, final BlockGetter level, final BlockPos pos) {
        return ShapeBehaviour.occlusion(state, geometry(state).shape());
    }

    @Override
    protected boolean useShapeForLightOcclusion(final BlockState state) {
        return true;
    }

    @Override
    protected boolean propagatesSkylightDown(final BlockState state, final BlockGetter level, final BlockPos pos) {
        return ShapeBehaviour.propagatesSkylight(state, geometry(state).shape());
    }

    @Override
    protected boolean isPathfindable(final BlockState state, final PathComputationType type) {
        return type == PathComputationType.WATER && state.getFluidState().is(Fluids.WATER);
    }

    // ------------------------------------------------------------------------------------------------ placement and water

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext ctx) {
        BlockState state = placementShape(ctx);
        if (state == null) return null;
        final boolean water = ctx.getLevel().getFluidState(ctx.getClickedPos()).getType() == Fluids.WATER;
        state = state.setValue(WATERLOGGED, water && !isFull(state));
        return ShapeBehaviour.forPlacement(state, ctx);
    }

    @Override
    public void setPlacedBy(final Level level, final BlockPos pos, final BlockState state, final @Nullable LivingEntity placer, final ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        ShapeBehaviour.placed(level, pos, state, stack);
    }

    @Override
    protected FluidState getFluidState(final BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(final BlockState state, final Direction direction, final BlockState neighbour, final LevelAccessor level,
                                     final BlockPos pos, final BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    public boolean canPlaceLiquid(final @Nullable Player player, final BlockGetter level, final BlockPos pos, final BlockState state, final Fluid fluid) {
        return !isFull(state) && SimpleWaterloggedBlock.super.canPlaceLiquid(player, level, pos, state, fluid);
    }

    @Override
    public boolean placeLiquid(final LevelAccessor level, final BlockPos pos, final BlockState state, final FluidState fluid) {
        return !isFull(state) && SimpleWaterloggedBlock.super.placeLiquid(level, pos, state, fluid);
    }

    // ------------------------------------------------------------------------------------------------ material delegation

    @Override
    protected float getDestroyProgress(final BlockState state, final Player player, final BlockGetter level, final BlockPos pos) {
        final float progress = ShapeBehaviour.destroyProgress(player, level, pos);
        return Float.isNaN(progress) ? super.getDestroyProgress(state, player, level, pos) : progress;
    }

    @Override
    protected List<ItemStack> getDrops(final BlockState state, final LootParams.Builder params) {
        return ShapeBehaviour.drops(this, state, params);
    }

    @Override
    public ItemStack getCloneItemStack(final LevelReader level, final BlockPos pos, final BlockState state) {
        return ShapeBehaviour.cloneStack(this, this, level, pos);
    }

    @Override
    protected void spawnDestroyParticles(final Level level, final Player player, final BlockPos pos, final BlockState state) {
        ShapeBehaviour.destroyEffects(level, player, pos, state);
    }

    @Override
    public void stepOn(final Level level, final BlockPos pos, final BlockState state, final Entity entity) {
        ShapeBehaviour.stepOn(level, pos, entity);
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void fallOn(final Level level, final BlockState state, final BlockPos pos, final Entity entity, final float distance) {
        if (!ShapeBehaviour.fallOn(level, pos, entity, distance)) super.fallOn(level, state, pos, entity, distance);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos, final BlockState newState, final boolean moved) {
        ShapeBehaviour.healSplitDrops(level, pos, state, newState);
        super.onRemove(state, level, pos, newState, moved);
    }

    // NeoForge IBlockExtension methods: overrides there, plain methods on Fabric (no @Override on purpose).

    public SoundType getSoundType(final BlockState state, final LevelReader level, final BlockPos pos, final @Nullable Entity entity) {
        return ShapeBehaviour.sound(state, level, pos);
    }

    public float getExplosionResistance(final BlockState state, final BlockGetter level, final BlockPos pos, final Explosion explosion) {
        return ShapeBehaviour.explosionResistance(this, level, pos);
    }

    public float getFriction(final BlockState state, final LevelReader level, final BlockPos pos, final @Nullable Entity entity) {
        return ShapeBehaviour.friction(this, level, pos);
    }

    public MapColor getMapColor(final BlockState state, final BlockGetter level, final BlockPos pos, final MapColor fallback) {
        return ShapeBehaviour.mapColor(level, pos, fallback);
    }

    public boolean canHarvestBlock(final BlockState state, final BlockGetter level, final BlockPos pos, final Player player) {
        return ShapeBehaviour.canHarvest(level, pos, player);
    }
}
