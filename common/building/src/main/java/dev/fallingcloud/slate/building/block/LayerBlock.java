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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * LAYER: 1..8 layers of 2 px growing in the {@link #FACING} direction from the opposite side (UP = lying on the
 * floor, like snow); n layers are worth n units. Placed against the clicked face; clicking the growing face with the
 * same material adds a layer.
 */
public class LayerBlock extends CustomShapeBlock {

    public static final MapCodec<LayerBlock> CODEC = simpleCodec(LayerBlock::new);
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** Pixels per layer. */
    public static final int LAYER_HEIGHT = 2;
    public static final int MAX_LAYERS = 8;

    public LayerBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LAYERS, 1).setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<LayerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LAYERS, FACING);
    }

    @Override
    public Shape shape() {
        return Shape.LAYER;
    }

    @Override
    public int units(final BlockState state) {
        return state.getValue(LAYERS);
    }

    @Override
    protected List<AABB> computeBoxes(final BlockState state) {
        return List.of(ShapeBoxes.plate(state.getValue(FACING), LAYER_HEIGHT * state.getValue(LAYERS)));
    }

    @Override
    protected @Nullable BlockState placementShape(final BlockPlaceContext ctx) {
        final BlockState existing = ctx.getLevel().getBlockState(ctx.getClickedPos());
        if (existing.is(this)) return existing.setValue(LAYERS, Math.min(MAX_LAYERS, existing.getValue(LAYERS) + 1));
        return defaultBlockState().setValue(FACING, ctx.getClickedFace());
    }

    @Override
    protected boolean canBeReplaced(final BlockState state, final BlockPlaceContext ctx) {
        return state.getValue(LAYERS) < MAX_LAYERS
            && ctx.getItemInHand().is(asItem())
            && ctx.getClickedFace() == state.getValue(FACING)
            && ShapeBehaviour.sameMaterial(ctx, ctx.getClickedPos());
    }

    @Override
    protected boolean isPathfindable(final BlockState state, final PathComputationType type) {
        if (type == PathComputationType.LAND) return state.getValue(FACING) == Direction.UP && state.getValue(LAYERS) < 5;
        return super.isPathfindable(state, type);
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
