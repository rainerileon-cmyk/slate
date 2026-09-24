package dev.fallingcloud.slate.building.block;

import com.mojang.serialization.MapCodec;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * STAIRS: vanilla stairs (placement, corner shapes, waterlogging, rotation, {@code #minecraft:stairs}) made of the
 * material in the block entity. The base state vanilla requires is stone; everything material-dependent is
 * delegated through {@link ShapeBehaviour} instead.
 */
public class ShapeStairBlock extends StairBlock implements ShapeBlock, EntityBlock {

    public static final MapCodec<ShapeStairBlock> CODEC = simpleCodec(ShapeStairBlock::new);

    /** Vanilla's {@code StairBlock.SHAPE_BY_STATE}: upper quadrants (1 NW, 2 NE, 4 SW, 8 SE) per shape*4 + facing2D. */
    private static final int[] QUADRANTS = {12, 5, 3, 10, 14, 13, 7, 11, 13, 7, 11, 14, 8, 4, 1, 2, 4, 1, 2, 8};
    private static final List<List<AABB>> BOTTOM_BOXES = boxes(false);
    private static final List<List<AABB>> TOP_BOXES = boxes(true);

    public ShapeStairBlock(final BlockBehaviour.Properties properties) {
        super(Blocks.STONE.defaultBlockState(), ShapeBehaviour.refine(properties));
    }

    private static List<List<AABB>> boxes(final boolean top) {
        final double s0 = top ? 8 : 0, s1 = top ? 16 : 8, q0 = top ? 0 : 8, q1 = top ? 8 : 16;
        final List<List<AABB>> out = new ArrayList<>(QUADRANTS.length);
        for (final int bits : QUADRANTS) {
            final List<AABB> boxes = new ArrayList<>(4);
            boxes.add(ShapeBoxes.box(0, s0, 0, 16, s1, 16));
            if ((bits & 1) != 0) boxes.add(ShapeBoxes.box(0, q0, 0, 8, q1, 8));
            if ((bits & 2) != 0) boxes.add(ShapeBoxes.box(8, q0, 0, 16, q1, 8));
            if ((bits & 4) != 0) boxes.add(ShapeBoxes.box(0, q0, 8, 8, q1, 16));
            if ((bits & 8) != 0) boxes.add(ShapeBoxes.box(8, q0, 8, 16, q1, 16));
            out.add(ShapeBoxes.merge(boxes));
        }
        return List.copyOf(out);
    }

    @Override
    public MapCodec<ShapeStairBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ShapeBehaviour.LIGHT, ShapeBehaviour.OPAQUE);
    }

    @Override
    public Shape shape() {
        return Shape.STAIRS;
    }

    @Override
    public List<AABB> renderBoxes(final BlockState state) {
        final int index = state.getValue(SHAPE).ordinal() * 4 + state.getValue(FACING).get2DDataValue();
        return (state.getValue(HALF) == Half.TOP ? TOP_BOXES : BOTTOM_BOXES).get(index);
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
