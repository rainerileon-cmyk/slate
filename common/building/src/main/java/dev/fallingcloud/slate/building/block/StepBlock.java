package dev.fallingcloud.slate.building.block;

import com.mojang.serialization.MapCodec;
import dev.fallingcloud.slate.building.variant.Shape;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * STEP (Create's copycat step): a quarter block, full width, half height, half depth, on the {@link #FACING} side
 * ({@code (0,0,8)-(16,8,16)} for SOUTH / BOTTOM).
 *
 * <p>Placement exactly as Create: facing = the player's horizontal look direction (the step lands on the far half),
 * TOP when clicking a block's underside or the upper half of a side.
 */
public class StepBlock extends CustomShapeBlock {

    public static final MapCodec<StepBlock> CODEC = simpleCodec(StepBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;

    public StepBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(HALF, Half.BOTTOM));
    }

    @Override
    protected MapCodec<StepBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, HALF);
    }

    @Override
    public Shape shape() {
        return Shape.STEP;
    }

    @Override
    protected List<AABB> computeBoxes(final BlockState state) {
        AABB box = ShapeBoxes.rotateY(ShapeBoxes.box(0, 0, 8, 16, 8, 16), ShapeBoxes.turns(Direction.SOUTH, state.getValue(FACING)));
        if (state.getValue(HALF) == Half.TOP) box = ShapeBoxes.shiftY(box, 8);
        return List.of(box);
    }

    @Override
    protected @Nullable BlockState placementShape(final BlockPlaceContext ctx) {
        final Direction face = ctx.getClickedFace();
        final double y = ctx.getClickLocation().y - ctx.getClickedPos().getY();
        final boolean top = face == Direction.DOWN || (face != Direction.UP && y > 0.5);
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection()).setValue(HALF, top ? Half.TOP : Half.BOTTOM);
    }

    @Override
    protected BlockState rotate(final BlockState state, final Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(final BlockState state, final Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
