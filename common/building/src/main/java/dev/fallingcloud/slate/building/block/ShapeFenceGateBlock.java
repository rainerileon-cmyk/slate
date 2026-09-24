package dev.fallingcloud.slate.building.block;

import com.mojang.serialization.MapCodec;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * FENCE_GATE: a vanilla fence gate (open/close, redstone, sits lower in walls, {@code #minecraft:fence_gates}) made
 * of the material in the block entity. Render boxes follow vanilla's gate templates (closed / open, dropped 3 px when
 * in a wall), modelled facing south like the templates and turned to the facing.
 */
public class ShapeFenceGateBlock extends FenceGateBlock implements ShapeBlock, EntityBlock {

    public static final MapCodec<ShapeFenceGateBlock> CODEC = simpleCodec(ShapeFenceGateBlock::new);

    private static final List<AABB> CLOSED_BOXES = List.of(
        ShapeBoxes.box(0, 5, 7, 2, 16, 9), ShapeBoxes.box(14, 5, 7, 16, 16, 9),
        ShapeBoxes.box(6, 6, 7, 8, 15, 9), ShapeBoxes.box(8, 6, 7, 10, 15, 9),
        ShapeBoxes.box(2, 6, 7, 6, 9, 9), ShapeBoxes.box(2, 12, 7, 6, 15, 9),
        ShapeBoxes.box(10, 6, 7, 14, 9, 9), ShapeBoxes.box(10, 12, 7, 14, 15, 9));
    private static final List<AABB> OPEN_BOXES = List.of(
        ShapeBoxes.box(0, 5, 7, 2, 16, 9), ShapeBoxes.box(14, 5, 7, 16, 16, 9),
        ShapeBoxes.box(0, 6, 13, 2, 15, 15), ShapeBoxes.box(14, 6, 13, 16, 15, 15),
        ShapeBoxes.box(0, 6, 9, 2, 9, 13), ShapeBoxes.box(0, 12, 9, 2, 15, 13),
        ShapeBoxes.box(14, 6, 9, 16, 9, 13), ShapeBoxes.box(14, 12, 9, 16, 15, 13));
    /** [facing2D][open][inWall]. */
    private static final List<List<AABB>> BOXES = computeAll();

    public ShapeFenceGateBlock(final BlockBehaviour.Properties properties) {
        super(WoodType.OAK, ShapeBehaviour.refine(properties).forceSolidOn());
    }

    private static List<List<AABB>> computeAll() {
        final List<List<AABB>> out = new ArrayList<>(16);
        for (int facing = 0; facing < 4; facing++) {
            for (int open = 0; open < 2; open++) {
                for (int inWall = 0; inWall < 2; inWall++) {
                    final List<AABB> boxes = new ArrayList<>();
                    for (AABB b : open == 1 ? OPEN_BOXES : CLOSED_BOXES) {
                        if (inWall == 1) b = ShapeBoxes.shiftY(b, -3);
                        boxes.add(ShapeBoxes.rotateY(b, ShapeBoxes.turns(Direction.SOUTH, Direction.from2DDataValue(facing))));
                    }
                    out.add(ShapeBoxes.merge(boxes));
                }
            }
        }
        return List.copyOf(out);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public MapCodec<FenceGateBlock> codec() {
        // FenceGateBlock.codec() is typed MapCodec<FenceGateBlock>; the codec really builds this subclass.
        return (MapCodec) CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ShapeBehaviour.LIGHT, ShapeBehaviour.OPAQUE);
    }

    @Override
    public Shape shape() {
        return Shape.FENCE_GATE;
    }

    @Override
    public List<AABB> renderBoxes(final BlockState state) {
        final int index = state.getValue(FACING).get2DDataValue() * 4 + (state.getValue(OPEN) ? 2 : 0) + (state.getValue(IN_WALL) ? 1 : 0);
        return BOXES.get(index);
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
