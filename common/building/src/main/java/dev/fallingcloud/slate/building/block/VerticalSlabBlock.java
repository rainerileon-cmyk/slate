package dev.fallingcloud.slate.building.block;

import com.mojang.serialization.MapCodec;
import dev.fallingcloud.slate.building.variant.Shape;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * VERTICAL_SLAB: half a block standing against its {@link #FACING} side; two of the same material merge into a full
 * block worth 2 units.
 *
 * <p>The double state is a boolean {@code single_slab} (not an enum "type") and the facing is vanilla's
 * {@code HORIZONTAL_FACING}: exactly the layout KleeSlabs' vertical-slab converter understands, so with the shipped
 * {@code kleeslabs:vertical_slabs/enchanted_vertical_slabs} tag, breaking a double one only removes the half you
 * look at, like KleeSlabs does for horizontal slabs.
 *
 * <p>Placement: clicking a block's side stands the slab against that block; clicking a top or bottom face puts it
 * on the half the cursor points at. Clicking the open face of a single slab with the same material completes it.
 */
public class VerticalSlabBlock extends CustomShapeBlock {

    public static final MapCodec<VerticalSlabBlock> CODEC = simpleCodec(VerticalSlabBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** true = one half, false = both halves (a full block, 2 units). */
    public static final BooleanProperty SINGLE = BooleanProperty.create("single_slab");

    public VerticalSlabBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(SINGLE, true));
    }

    @Override
    protected MapCodec<VerticalSlabBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, SINGLE);
    }

    @Override
    public Shape shape() {
        return Shape.VERTICAL_SLAB;
    }

    @Override
    public int units(final BlockState state) {
        return state.getValue(SINGLE) ? 1 : 2;
    }

    @Override
    protected List<AABB> computeBoxes(final BlockState state) {
        if (!state.getValue(SINGLE)) return List.of(ShapeBoxes.box(0, 0, 0, 16, 16, 16));
        return ShapeBoxes.rotate(List.of(ShapeBoxes.box(0, 0, 0, 16, 16, 8)), Direction.NORTH, state.getValue(FACING));
    }

    @Override
    protected @Nullable BlockState placementShape(final BlockPlaceContext ctx) {
        final BlockState existing = ctx.getLevel().getBlockState(ctx.getClickedPos());
        if (existing.is(this) && existing.getValue(SINGLE)) return existing.setValue(SINGLE, false);
        return defaultBlockState().setValue(FACING, facingFor(ctx));
    }

    /** Against the clicked block for side clicks, else the half of the block the cursor points at. */
    static Direction facingFor(final BlockPlaceContext ctx) {
        final Direction face = ctx.getClickedFace();
        if (face.getAxis().isHorizontal()) return face.getOpposite();
        final BlockPos pos = ctx.getClickedPos();
        final Vec3 hit = ctx.getClickLocation();
        final double dx = hit.x - pos.getX() - 0.5;
        final double dz = hit.z - pos.getZ() - 0.5;
        if (Math.abs(dx) < 0.05 && Math.abs(dz) < 0.05) return ctx.getHorizontalDirection();
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    @Override
    protected boolean canBeReplaced(final BlockState state, final BlockPlaceContext ctx) {
        if (!state.getValue(SINGLE) || !ctx.getItemInHand().is(asItem()) || !ShapeBehaviour.sameMaterial(ctx, ctx.getClickedPos())) return false;
        // Clicking the slab itself merges only through its open face; placing into it from a neighbour always does.
        return !ctx.replacingClickedOnBlock() || ctx.getClickedFace() == state.getValue(FACING).getOpposite();
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
