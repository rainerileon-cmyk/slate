package dev.fallingcloud.slate.building.variant;

import dev.fallingcloud.slate.building.block.VerticalSlabBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * Carries orientation from one shape's state to another's when a block is reshaped in the world (hammer, reshape
 * mode), through vanilla's shared properties, so e.g. top stairs facing east become a top slab, an east vertical slab
 * becomes an east step, a floor panel becomes a floor layer, a post keeps its axis. Works for native and Slate
 * Building states alike (they use the same property objects).
 *
 * <p>Model: every source yields a horizontal facing, the side the shape hugs ("attach": DOWN for bottom halves and
 * floor plates, UP for top halves, the facing side of a vertical slab), a vertical half, an axis and water, each when
 * it has one; the target takes whatever it has properties for.
 */
public final class ShapeConversion {

    private ShapeConversion() {}

    /** {@code target} (a default or material state) with {@code from}'s orientation and water applied. */
    public static BlockState carryOver(final BlockState from, final BlockState target, final FluidState fluidHere) {
        Direction horizontal = null;
        Direction attach = null;
        Half half = null;
        Direction.Axis axis = null;

        if (from.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) horizontal = from.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (from.hasProperty(BlockStateProperties.FACING)) {
            final Direction facing = from.getValue(BlockStateProperties.FACING);
            attach = facing.getOpposite();
            if (facing.getAxis().isHorizontal()) horizontal = attach;
        }
        if (from.getBlock() instanceof VerticalSlabBlock && from.getValue(VerticalSlabBlock.SINGLE)) attach = horizontal;
        if (from.hasProperty(BlockStateProperties.HALF)) half = from.getValue(BlockStateProperties.HALF);
        if (from.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            final SlabType type = from.getValue(BlockStateProperties.SLAB_TYPE);
            half = type == SlabType.TOP ? Half.TOP : type == SlabType.BOTTOM ? Half.BOTTOM : null;
        }
        if (half != null && attach == null) attach = half == Half.TOP ? Direction.UP : Direction.DOWN;
        if (half == null && attach != null && attach.getAxis() == Direction.Axis.Y) half = attach == Direction.UP ? Half.TOP : Half.BOTTOM;
        if (from.hasProperty(BlockStateProperties.AXIS)) axis = from.getValue(BlockStateProperties.AXIS);
        final boolean water = (from.hasProperty(BlockStateProperties.WATERLOGGED) && from.getValue(BlockStateProperties.WATERLOGGED))
            || fluidHere.getType() == Fluids.WATER;

        BlockState out = target;
        if (horizontal != null && out.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) out = out.setValue(BlockStateProperties.HORIZONTAL_FACING, horizontal);
        if (attach != null && out.hasProperty(BlockStateProperties.FACING)) out = out.setValue(BlockStateProperties.FACING, attach.getOpposite());
        if (half != null && out.hasProperty(BlockStateProperties.HALF)) out = out.setValue(BlockStateProperties.HALF, half);
        if (out.hasProperty(BlockStateProperties.SLAB_TYPE)) out = out.setValue(BlockStateProperties.SLAB_TYPE, half == Half.TOP ? SlabType.TOP : SlabType.BOTTOM);
        if (axis != null && out.hasProperty(BlockStateProperties.AXIS)) out = out.setValue(BlockStateProperties.AXIS, axis);
        if (out.hasProperty(BlockStateProperties.WATERLOGGED)) out = out.setValue(BlockStateProperties.WATERLOGGED, water);
        return out;
    }

    /** The horizontal facing a state carries, if any (for callers that want to orient a preview). */
    public static @Nullable Direction horizontalFacing(final BlockState state) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (state.hasProperty(BlockStateProperties.FACING)) {
            final Direction facing = state.getValue(BlockStateProperties.FACING);
            return facing.getAxis().isHorizontal() ? facing.getOpposite() : null;
        }
        return null;
    }
}
