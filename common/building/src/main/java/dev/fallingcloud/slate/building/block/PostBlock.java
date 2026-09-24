package dev.fallingcloud.slate.building.block;

import com.mojang.serialization.MapCodec;
import dev.fallingcloud.slate.building.variant.Shape;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * POST: an 8 x 8 beam through the middle of the block along {@link #AXIS} (the axis of the clicked face). A log
 * material is stored turned to the same axis, so the bark runs along the beam.
 */
public class PostBlock extends CustomShapeBlock {

    public static final MapCodec<PostBlock> CODEC = simpleCodec(PostBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    public PostBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    protected MapCodec<PostBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AXIS);
    }

    @Override
    public Shape shape() {
        return Shape.POST;
    }

    @Override
    protected List<AABB> computeBoxes(final BlockState state) {
        return List.of(switch (state.getValue(AXIS)) {
            case X -> ShapeBoxes.box(0, 4, 4, 16, 12, 12);
            case Y -> ShapeBoxes.box(4, 0, 4, 12, 16, 12);
            case Z -> ShapeBoxes.box(4, 4, 0, 12, 12, 16);
        });
    }

    @Override
    protected @Nullable BlockState placementShape(final BlockPlaceContext ctx) {
        return defaultBlockState().setValue(AXIS, ctx.getClickedFace().getAxis());
    }

    @Override
    protected BlockState rotate(final BlockState state, final Rotation rotation) {
        return RotatedPillarBlock.rotatePillar(state, rotation);
    }
}
