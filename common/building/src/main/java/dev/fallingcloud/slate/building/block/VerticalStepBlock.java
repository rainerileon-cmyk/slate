package dev.fallingcloud.slate.building.block;

import com.mojang.serialization.MapCodec;
import dev.fallingcloud.slate.building.variant.Shape;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * VERTICAL_STEP: an 8 x 16 x 8 quarter column in the corner {@link #FACING} names ({@code facing} and
 * {@code facing.getClockWise()}, see {@link ShapeBoxes#cornerFacing}).
 *
 * <p>Placement: the corner the cursor points at inside the new block, so clicking a wall's side puts the column
 * against that wall on the side of the click.
 */
public class VerticalStepBlock extends CustomShapeBlock {

    public static final MapCodec<VerticalStepBlock> CODEC = simpleCodec(VerticalStepBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public VerticalStepBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<VerticalStepBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public Shape shape() {
        return Shape.VERTICAL_STEP;
    }

    @Override
    protected List<AABB> computeBoxes(final BlockState state) {
        return List.of(ShapeBoxes.cornerColumn(state.getValue(FACING)));
    }

    @Override
    protected @Nullable BlockState placementShape(final BlockPlaceContext ctx) {
        final BlockPos pos = ctx.getClickedPos();
        final Vec3 hit = ctx.getClickLocation();
        final double fx = Mth.clamp(hit.x - pos.getX(), 0, 1);
        final double fz = Mth.clamp(hit.z - pos.getZ(), 0, 1);
        return defaultBlockState().setValue(FACING, ShapeBoxes.cornerAt(fx, fz));
    }

    @Override
    protected BlockState rotate(final BlockState state, final Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(final BlockState state, final Mirror mirror) {
        return state.setValue(FACING, ShapeBoxes.mirrorCorner(state.getValue(FACING), mirror));
    }
}
