package dev.fallingcloud.slate.building.block;

import com.mojang.serialization.MapCodec;
import dev.fallingcloud.slate.building.variant.RotatedBox;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import java.util.ArrayList;
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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * WALL: a vanilla wall (posts, low/tall sides, connections to {@code #minecraft:walls}, panes, gates and solid faces,
 * 1.5-block collision) made of the material in the block entity.
 *
 * <p>Vanilla's wall keeps its outline and collision shapes in maps keyed by the states that existed with default
 * values of any extra property; {@link #shapeKey} maps a state onto that key before asking. The render boxes follow
 * the vanilla wall models (post 8 x 16 x 8, sides 6 wide, 14 high or 16 when tall), each side ending at the post.
 */
public class ShapeWallBlock extends WallBlock implements ShapeBlock, EntityBlock {

    public static final MapCodec<ShapeWallBlock> CODEC = simpleCodec(ShapeWallBlock::new);

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private volatile @Nullable Map<BlockState, List<AABB>> boxes;
    /**
     * The low arm as it points north: what a diagonal arm is cut from before its turn ({@link DiagonalShapes}). It runs to
     * the block centre so its near end sits inside the post, never poking out beside the post's corner.
     */
    private static final List<AABB> NORTH_ARM = List.of(ShapeBoxes.box(5, 0, 0, 11, 14, 8));
    private final DiagonalShapes.ShapeCache diagonalShapes = new DiagonalShapes.ShapeCache(3, 0, 14, NORTH_ARM);
    private final DiagonalShapes.ShapeCache diagonalCollisions = new DiagonalShapes.ShapeCache(3, 0, 24);

    public ShapeWallBlock(final BlockBehaviour.Properties properties) {
        super(DiagonalShapes.properties(ShapeBehaviour.refine(properties).forceSolidOn(), DiagonalShapes.Kind.WALL));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public MapCodec<WallBlock> codec() {
        // WallBlock.codec() is typed MapCodec<WallBlock>; the codec really builds this subclass.
        return (MapCodec) CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ShapeBehaviour.LIGHT, ShapeBehaviour.OPAQUE);
    }

    /** The state vanilla's shape maps were built with (the material properties at their defaults). */
    private static BlockState shapeKey(final BlockState state) {
        return state.setValue(ShapeBehaviour.LIGHT, 0).setValue(ShapeBehaviour.OPAQUE, true);
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return diagonalShapes.get(state, DiagonalShapes.mask(level, pos), super.getShape(shapeKey(state), level, pos, context));
    }

    @Override
    protected VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return diagonalCollisions.get(state, DiagonalShapes.mask(level, pos), super.getCollisionShape(shapeKey(state), level, pos, context));
    }

    // ------------------------------------------------------------------------------------------------ diagonal arms

    @Override
    public List<RotatedBox> renderRotatedBoxes(final BlockState state, final int diagonals) {
        return DiagonalShapes.rotated(diagonals, NORTH_ARM);
    }

    @Override
    protected BlockState updateShape(final BlockState state, final Direction direction, final BlockState neighbourState, final LevelAccessor level,
                                     final BlockPos pos, final BlockPos neighbourPos) {
        final BlockState updated = super.updateShape(state, direction, neighbourState, level, pos, neighbourPos);
        DiagonalShapes.refresh(updated, level, pos, DiagonalShapes.Kind.WALL);
        return updated;
    }

    @Override
    public void updateIndirectNeighbourShapes(final BlockState state, final LevelAccessor level, final BlockPos pos, final int flags, final int recursionLeft) {
        super.updateIndirectNeighbourShapes(state, level, pos, flags, recursionLeft);
        DiagonalShapes.refreshAround(state, level, pos, DiagonalShapes.Kind.WALL);
    }

    @Override
    public Shape shape() {
        return Shape.WALL;
    }

    @Override
    public List<AABB> renderBoxes(final BlockState state) {
        Map<BlockState, List<AABB>> map = boxes;
        if (map == null) map = buildBoxes();
        return map.get(state);
    }

    private synchronized Map<BlockState, List<AABB>> buildBoxes() {
        Map<BlockState, List<AABB>> map = boxes;
        if (map != null) return map;
        map = new IdentityHashMap<>();
        for (final BlockState state : getStateDefinition().getPossibleStates()) map.put(state, computeBoxes(state));
        boxes = map;
        return map;
    }

    private static List<AABB> computeBoxes(final BlockState state) {
        final boolean post = state.getValue(UP);
        final List<AABB> out = new ArrayList<>(5);
        if (post) out.add(ShapeBoxes.box(4, 0, 4, 12, 16, 12));
        for (final Direction dir : HORIZONTAL) {
            final WallSide side = state.getValue(sideProperty(dir));
            if (side == WallSide.NONE) continue;
            final double height = side == WallSide.TALL ? 16 : 14;
            final AABB north = ShapeBoxes.box(5, 0, 0, 11, height, post ? 4 : 8);
            out.add(ShapeBoxes.rotateY(north, ShapeBoxes.turns(Direction.NORTH, dir)));
        }
        if (out.isEmpty()) out.add(ShapeBoxes.box(4, 0, 4, 12, 16, 12));
        return ShapeBoxes.merge(out);
    }

    private static EnumProperty<WallSide> sideProperty(final Direction dir) {
        return switch (dir) {
            case NORTH -> NORTH_WALL;
            case EAST -> EAST_WALL;
            case SOUTH -> SOUTH_WALL;
            default -> WEST_WALL;
        };
    }

    @Override
    public int units(final BlockState state) {
        return 1;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ShapeBlockEntity(pos, state);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext ctx) {
        return ShapeBehaviour.forPlacement(super.getStateForPlacement(ctx), ctx);
    }

    @Override
    public void setPlacedBy(final Level level, final BlockPos pos, final BlockState state, final @Nullable LivingEntity placer, final ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        ShapeBehaviour.placed(level, pos, state, stack);
    }

    @Override
    protected VoxelShape getOcclusionShape(final BlockState state, final BlockGetter level, final BlockPos pos) {
        return ShapeBehaviour.occlusion(state, super.getOcclusionShape(state, level, pos));
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
