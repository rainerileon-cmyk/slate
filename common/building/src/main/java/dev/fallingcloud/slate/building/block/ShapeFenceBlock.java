package dev.fallingcloud.slate.building.block;

import com.mojang.serialization.MapCodec;
import dev.fallingcloud.slate.building.variant.RotatedBox;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * FENCE: a vanilla fence (leads, gates, 1.5-block collision, {@code #minecraft:fences}) made of the material in the
 * block entity. It joins every fence, wooden or not, and every fence joins it (FenceBlockMixin), since its
 * material is per position. Render boxes follow the vanilla fence models: a 4 x 16 x 4 post and two 2 x 3 bars per
 * connected side.
 */
public class ShapeFenceBlock extends FenceBlock implements ShapeBlock, EntityBlock {

    public static final MapCodec<ShapeFenceBlock> CODEC = simpleCodec(ShapeFenceBlock::new);

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    /** Keyed by the connection bits (N 1, E 2, S 4, W 8). */
    private static final Map<Integer, List<AABB>> BOXES = new ConcurrentHashMap<>();
    /**
     * The arm as it points north (the two bars): what a diagonal arm is cut from before its turn ({@link DiagonalShapes}).
     * It runs to the block centre so its near end sits inside the post, never poking out beside the post's corner.
     */
    private static final List<AABB> NORTH_ARM = List.of(ShapeBoxes.box(7, 12, 0, 9, 15, 8), ShapeBoxes.box(7, 6, 0, 9, 9, 8));
    /** The arm as vanilla's outline draws it (one 2 x 9 box, like a fence side's selection box): the turned outline box. */
    private static final List<AABB> OUTLINE_ARM = List.of(ShapeBoxes.box(7, 6, 0, 9, 15, 8));
    private final DiagonalShapes.ShapeCache diagonalShapes = new DiagonalShapes.ShapeCache(1, 6, 15, OUTLINE_ARM);
    private final DiagonalShapes.ShapeCache diagonalCollisions = new DiagonalShapes.ShapeCache(1, 0, 24);

    public ShapeFenceBlock(final BlockBehaviour.Properties properties) {
        super(DiagonalShapes.properties(ShapeBehaviour.refine(properties).forceSolidOn(), DiagonalShapes.Kind.FENCE));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public MapCodec<FenceBlock> codec() {
        // FenceBlock.codec() is typed MapCodec<FenceBlock>; the codec really builds this subclass.
        return (MapCodec) CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ShapeBehaviour.LIGHT, ShapeBehaviour.OPAQUE);
    }

    @Override
    public boolean connectsTo(final BlockState neighbour, final boolean sturdy, final Direction direction) {
        return neighbour.is(BlockTags.FENCES) || super.connectsTo(neighbour, sturdy, direction);
    }

    @Override
    public Shape shape() {
        return Shape.FENCE;
    }

    @Override
    public List<AABB> renderBoxes(final BlockState state) {
        int bits = 0;
        for (int i = 0; i < HORIZONTAL.length; i++) if (state.getValue(PROPERTY_BY_DIRECTION.get(HORIZONTAL[i]))) bits |= 1 << i;
        return BOXES.computeIfAbsent(bits, ShapeFenceBlock::computeBoxes);
    }

    @Override
    public List<RotatedBox> renderRotatedBoxes(final BlockState state, final int diagonals) {
        return DiagonalShapes.rotated(diagonals, NORTH_ARM);
    }

    // ------------------------------------------------------------------------------------------------ diagonal arms

    @Override
    protected BlockState updateShape(final BlockState state, final Direction direction, final BlockState neighbourState, final LevelAccessor level,
                                     final BlockPos pos, final BlockPos neighbourPos) {
        final BlockState updated = super.updateShape(state, direction, neighbourState, level, pos, neighbourPos);
        DiagonalShapes.refresh(updated, level, pos, DiagonalShapes.Kind.FENCE);
        return updated;
    }

    @Override
    public void updateIndirectNeighbourShapes(final BlockState state, final LevelAccessor level, final BlockPos pos, final int flags, final int recursionLeft) {
        super.updateIndirectNeighbourShapes(state, level, pos, flags, recursionLeft);
        DiagonalShapes.refreshAround(state, level, pos, DiagonalShapes.Kind.FENCE);
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return diagonalShapes.get(state, DiagonalShapes.mask(level, pos), super.getShape(state, level, pos, context));
    }

    @Override
    protected VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return diagonalCollisions.get(state, DiagonalShapes.mask(level, pos), super.getCollisionShape(state, level, pos, context));
    }

    private static List<AABB> computeBoxes(final int bits) {
        final List<AABB> out = new ArrayList<>(9);
        out.add(ShapeBoxes.box(6, 0, 6, 10, 16, 10));
        for (int i = 0; i < HORIZONTAL.length; i++) {
            if ((bits & (1 << i)) == 0) continue;
            final int turns = ShapeBoxes.turns(Direction.NORTH, HORIZONTAL[i]);
            out.add(ShapeBoxes.rotateY(ShapeBoxes.box(7, 12, 0, 9, 15, 6), turns));
            out.add(ShapeBoxes.rotateY(ShapeBoxes.box(7, 6, 0, 9, 9, 6), turns));
        }
        return ShapeBoxes.merge(out);
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
