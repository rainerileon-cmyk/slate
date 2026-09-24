package dev.fallingcloud.slate.building.block;

import com.mojang.serialization.MapCodec;
import dev.fallingcloud.slate.building.variant.Shape;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
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
 * VERTICAL_STAIRS: a full-height L, the whole block minus the quarter column in the corner {@link #FACING} names
 * ({@code facing} and {@code facing.getClockWise()}, see {@link ShapeBoxes#cornerFacing}). A vertical step with the
 * same facing fills exactly the notch.
 *
 * <p>Placement: the notch opens towards the placing player (the L wraps around the far side of the block, like an
 * inner wall corner seen from inside).
 */
public class VerticalStairsBlock extends CustomShapeBlock {

    public static final MapCodec<VerticalStairsBlock> CODEC = simpleCodec(VerticalStairsBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public VerticalStairsBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<VerticalStairsBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public Shape shape() {
        return Shape.VERTICAL_STAIRS;
    }

    @Override
    protected List<AABB> computeBoxes(final BlockState state) {
        final Direction notch = state.getValue(FACING);
        final List<AABB> columns = new ArrayList<>(3);
        for (final Direction d : HORIZONTAL) if (d != notch) columns.add(ShapeBoxes.cornerColumn(d));
        return columns;
    }

    @Override
    protected @Nullable BlockState placementShape(final BlockPlaceContext ctx) {
        final BlockPos pos = ctx.getClickedPos();
        final Player player = ctx.getPlayer();
        final Vec3 towards = player != null ? player.getEyePosition() : ctx.getClickLocation();
        final double fx = towards.x - pos.getX();
        final double fz = towards.z - pos.getZ();
        final Direction notch = Math.abs(fx - 0.5) < 1.0E-4 && Math.abs(fz - 0.5) < 1.0E-4
            ? ctx.getHorizontalDirection().getOpposite()
            : ShapeBoxes.cornerAt(fx, fz);
        return defaultBlockState().setValue(FACING, notch);
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
