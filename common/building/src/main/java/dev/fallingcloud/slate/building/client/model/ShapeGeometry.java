package dev.fallingcloud.slate.building.client.model;

import dev.fallingcloud.slate.building.variant.ShapeBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Where the renderer gets a shape state's boxes (block pixels, 0..16): the block's own
 * {@link ShapeBlock#renderBoxes}. Everything that draws a shape (chunk models, items, ghosts, ghost outlines) goes
 * through here, so there is one source of truth.
 *
 * <p>Dev stand-ins ({@link #useStandIns}): while the shape blocks are still the skeleton placeholders (which report
 * a full cube for every shape), the render harness switches on stand-in geometry that follows design §2, so the
 * cropping, culling and item code can be looked at before the real blocks exist. Stand-ins apply only to a block
 * that returns the {@link ShapeBlock#FULL_CUBE} placeholder list itself; real geometry always wins. Never on in
 * normal play.
 */
public final class ShapeGeometry {

    private static volatile boolean standIns;
    private static final Map<BlockState, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    /** The render boxes of {@code state} in block pixels; a full cube for anything that is not a shape block. */
    public static List<AABB> boxes(final BlockState state) {
        if (!(state.getBlock() instanceof ShapeBlock shape)) return ShapeBlock.FULL_CUBE;
        final List<AABB> boxes = shape.renderBoxes(state);
        if (standIns && boxes == ShapeBlock.FULL_CUBE) {
            final List<AABB> standIn = standIn(state);
            if (standIn != null) return standIn;
        }
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

    /** Dev only (render harness): switch the design-§2 stand-in geometry for placeholder blocks on or off. */
    public static void useStandIns(final boolean on) {
        if (standIns == on) return;
        standIns = on;
        ShapeQuadBaker.clearCaches();
        ShapeItemModels.clear();
    }

    /** Drops cached outline shapes (called with the quad caches). */
    static void clear() {
        SHAPES.clear();
    }

    // ---- stand-ins (design §2 geometry; vanilla-derived shapes use the vanilla block's own shape) ----

    private static @Nullable List<AABB> standIn(final BlockState state) {
        final ShapeBlock block = (ShapeBlock) state.getBlock();
        if ("double".equals(valueName(state, "type"))) return null;   // a double (vertical) slab is a full cube anyway
        return switch (block.shape()) {
            case STAIRS, SLAB, WALL, PANE -> fromShape(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            case FENCE -> fence(state);
            case FENCE_GATE -> gate(state);
            case VERTICAL_SLAB -> rotate(List.of(box(0, 0, 0, 16, 16, 8)), facing(state, Direction.NORTH));
            case VERTICAL_STAIRS -> rotate(List.of(box(0, 0, 0, 8, 16, 16), box(8, 0, 8, 16, 16, 16)), facing(state, Direction.NORTH));
            case STEP -> {
                final boolean top = state.hasProperty(BlockStateProperties.HALF)
                    && state.getValue(BlockStateProperties.HALF) == net.minecraft.world.level.block.state.properties.Half.TOP;
                yield rotate(List.of(top ? box(0, 8, 8, 16, 16, 16) : box(0, 0, 8, 16, 8, 16)),
                    facing(state, Direction.SOUTH).getOpposite());
            }
            case PANEL -> List.of(box(0, 0, 0, 16, 3, 16));
            case VERTICAL_STEP -> List.of(box(8, 0, 0, 16, 16, 8));
            case POST -> List.of(box(4, 0, 4, 12, 16, 12));
            case LAYER -> {
                final String layers = valueName(state, "layers");
                yield List.of(box(0, 0, 0, 16, 2 * (layers == null ? 3 : Integer.parseInt(layers)), 16));
            }
            case FULL -> null;
        };
    }

    private static List<AABB> fence(final BlockState state) {
        final List<AABB> out = new ArrayList<>();
        out.add(box(6, 0, 6, 10, 16, 10));
        addRails(out, state, BlockStateProperties.NORTH, Direction.NORTH);
        addRails(out, state, BlockStateProperties.EAST, Direction.EAST);
        addRails(out, state, BlockStateProperties.SOUTH, Direction.SOUTH);
        addRails(out, state, BlockStateProperties.WEST, Direction.WEST);
        return out;
    }

    private static void addRails(final List<AABB> out, final BlockState state, final Property<Boolean> prop, final Direction dir) {
        if (!state.hasProperty(prop) || !state.getValue(prop)) return;
        // Two rails towards NORTH, turned to dir.
        out.addAll(rotate(List.of(box(7, 12, 0, 9, 15, 6), box(7, 6, 0, 9, 9, 6)), dir));
    }

    private static List<AABB> gate(final BlockState state) {
        final double drop = state.hasProperty(FenceGateBlock.IN_WALL) && state.getValue(FenceGateBlock.IN_WALL) ? 3 : 0;
        final List<AABB> south = List.of(
            box(0, 5 - drop, 7, 2, 16 - drop, 9), box(14, 5 - drop, 7, 16, 16 - drop, 9),
            box(6, 6 - drop, 7, 10, 15 - drop, 9),
            box(2, 6 - drop, 7, 6, 9 - drop, 9), box(2, 12 - drop, 7, 6, 15 - drop, 9),
            box(10, 6 - drop, 7, 14, 9 - drop, 9), box(10, 12 - drop, 7, 14, 15 - drop, 9));
        return rotate(south, facing(state, Direction.SOUTH).getOpposite());
    }

    /** The serialized value of the property called {@code name}, or null when the state has none. */
    private static @Nullable String valueName(final BlockState state, final String name) {
        final Property<?> p = state.getBlock().getStateDefinition().getProperty(name);
        return p == null ? null : nameOf(state, p);
    }

    private static <T extends Comparable<T>> String nameOf(final BlockState state, final Property<T> p) {
        return p.getName(state.getValue(p));
    }

    private static Direction facing(final BlockState state, final Direction fallback) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return fallback;
    }

    /** Boxes modelled for NORTH turned to {@code dir} (horizontal), about the block centre. */
    private static List<AABB> rotate(final List<AABB> north, final Direction dir) {
        final int turns = switch (dir) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        final List<AABB> out = new ArrayList<>(north.size());
        for (final AABB b : north) {
            AABB r = b;
            for (int i = 0; i < turns; i++) r = new AABB(16 - r.maxZ, r.minY, r.minX, 16 - r.minZ, r.maxY, r.maxX);
            out.add(r);
        }
        return out;
    }

    private static List<AABB> fromShape(final VoxelShape shape) {
        final List<AABB> out = new ArrayList<>();
        for (final AABB b : shape.toAabbs()) out.add(new AABB(b.minX * 16, b.minY * 16, b.minZ * 16, b.maxX * 16, b.maxY * 16, b.maxZ * 16));
        return out.isEmpty() ? ShapeBlock.FULL_CUBE : out;
    }

    private static AABB box(final double x0, final double y0, final double z0, final double x1, final double y1, final double z1) {
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    private ShapeGeometry() {}
}
