package dev.fallingcloud.slate.building.variant;

import dev.fallingcloud.slate.building.block.ShapeBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by every Slate Building shape block (one block per {@link Shape}, the material lives in the block
 * entity). Rendering crops the material's model to {@link #renderBoxes}; the economy counts {@link #units}.
 *
 * <p>Owner: A (variants). The skeleton declares it (design §2) and implements {@link #material}; A implements it on
 * the block classes in {@code building.block}.
 */
public interface ShapeBlock {

    /** The whole block in the 0..16 space {@link #renderBoxes} uses. */
    List<AABB> FULL_CUBE = List.of(new AABB(0, 0, 0, 16, 16, 16));

    Shape shape();

    /** Boxes in block space 0..16 used for rendering (quad cropping) and for the ghost. */
    List<AABB> renderBoxes(BlockState state);

    /** Units this state is worth (double slab 2, layers n, everything else 1). */
    int units(BlockState state);

    /** The material stored at {@code pos} (the full block this shape is made of), or null when unset / not ours. */
    static @Nullable BlockState material(final BlockGetter level, final BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ShapeBlockEntity be ? be.material() : null;
    }
}
