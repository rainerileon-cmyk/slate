package dev.fallingcloud.slate.building.client.model;

import dev.fallingcloud.slate.building.variant.ShapeBlock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where the renderer gets a shape state's boxes (block pixels, 0..16): the block's own
 * {@link ShapeBlock#renderBoxes}. Everything that draws a shape (chunk models, items, ghosts, ghost outlines) goes
 * through here, so there is one source of truth.
 */
public final class ShapeGeometry {

    private static final Map<BlockState, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    /** The render boxes of {@code state} in block pixels; a full cube for anything that is not a shape block. */
    public static List<AABB> boxes(final BlockState state) {
        if (!(state.getBlock() instanceof ShapeBlock shape)) return ShapeBlock.FULL_CUBE;
        final List<AABB> boxes = shape.renderBoxes(state);
        return boxes == null || boxes.isEmpty() ? ShapeBlock.FULL_CUBE : boxes;
    }

    /** The union of {@link #boxes} as a voxel shape in block units (for outlines), cached per state. */
    public static VoxelShape shape(final BlockState state) {
        final VoxelShape hit = SHAPES.get(state);
        if (hit != null) return hit;
        final VoxelShape built = union(state);
        SHAPES.put(state, built);
        return built;
    }

    private static VoxelShape union(final BlockState state) {
        VoxelShape out = Shapes.empty();
        for (final AABB b : boxes(state)) {
            out = Shapes.or(out, Shapes.box(
                b.minX / 16.0, b.minY / 16.0, b.minZ / 16.0, b.maxX / 16.0, b.maxY / 16.0, b.maxZ / 16.0));
        }
        return out.optimize();
    }

    /** Drops cached outline shapes (called with the quad caches). */
    static void clear() {
        SHAPES.clear();
    }

    private ShapeGeometry() {}
}
