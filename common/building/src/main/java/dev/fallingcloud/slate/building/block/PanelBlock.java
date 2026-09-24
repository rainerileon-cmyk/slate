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
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * PANEL (Create's copycat panel): a 3 px plate; {@link #FACING} is the direction its free face looks, so it lies
 * against the opposite side ({@code (0,0,0)-(16,3,16)} for UP).
 *
 * <p>Placement exactly as Create: facing = the opposite of the nearest look direction, i.e. the plate lies against
 * the clicked face.
 */
public class PanelBlock extends CustomShapeBlock {

    public static final MapCodec<PanelBlock> CODEC = simpleCodec(PanelBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** Plate thickness in pixels. */
    public static final int THICKNESS = 3;

    public PanelBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<PanelBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public Shape shape() {
        return Shape.PANEL;
    }

    @Override
    protected List<AABB> computeBoxes(final BlockState state) {
        return List.of(ShapeBoxes.plate(state.getValue(FACING), THICKNESS));
    }

    @Override
    protected @Nullable BlockState placementShape(final BlockPlaceContext ctx) {
        // Without a player (machines, building operations) there is no look direction: lie against the clicked face.
        final Direction look = ctx.getPlayer() != null ? ctx.getNearestLookingDirection() : ctx.getClickedFace().getOpposite();
        return defaultBlockState().setValue(FACING, look.getOpposite());
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
