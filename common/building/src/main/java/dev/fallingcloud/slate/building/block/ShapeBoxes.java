package dev.fallingcloud.slate.building.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Geometry helpers for shape blocks. Every box is in block space 0..16 (the space {@code ShapeBlock.renderBoxes}
 * uses); {@link #toShape} converts to a vanilla {@link VoxelShape} (0..1).
 *
 * <p>Horizontal orientation: {@link Direction#get2DDataValue()} grows clockwise seen from above (S, W, N, E), so the
 * number of clockwise quarter turns from one facing to another is the difference of their 2D values.
 */
public final class ShapeBoxes {

    private ShapeBoxes() {}

    public static AABB box(final double x0, final double y0, final double z0, final double x1, final double y1, final double z1) {
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    /** {@code b} turned clockwise (seen from above) by {@code quarterTurns} around the block's vertical centre line. */
    public static AABB rotateY(final AABB b, final int quarterTurns) {
        AABB r = b;
        for (int i = Math.floorMod(quarterTurns, 4); i > 0; i--) {
            // (x, z) -> (16 - z, x): the north edge becomes the east edge.
            r = new AABB(16 - r.maxZ, r.minY, r.minX, 16 - r.minZ, r.maxY, r.maxX);
        }
        return r;
    }

    /** Clockwise quarter turns that bring horizontal {@code from} onto horizontal {@code to}. */
    public static int turns(final Direction from, final Direction to) {
        return Math.floorMod(to.get2DDataValue() - from.get2DDataValue(), 4);
    }

    /** Boxes modelled for horizontal facing {@code base}, turned to face {@code facing}. */
    public static List<AABB> rotate(final List<AABB> boxes, final Direction base, final Direction facing) {
        final int t = turns(base, facing);
        if (t == 0) return boxes;
        final List<AABB> out = new ArrayList<>(boxes.size());
        for (final AABB b : boxes) out.add(rotateY(b, t));
        return out;
    }

    /** {@code b} shifted down (negative) or up in block units. */
    public static AABB shiftY(final AABB b, final double dy) {
        return new AABB(b.minX, b.minY + dy, b.minZ, b.maxX, b.maxY + dy, b.maxZ);
    }

    /**
     * A plate of thickness {@code t} lying against the side OPPOSITE {@code facing}: {@code facing} is the direction
     * its free face looks (UP = lying on the floor of the block space). Panels and layers use this.
     */
    public static AABB plate(final Direction facing, final double t) {
        return switch (facing) {
            case UP -> box(0, 0, 0, 16, t, 16);
            case DOWN -> box(0, 16 - t, 0, 16, 16, 16);
            case NORTH -> box(0, 0, 16 - t, 16, 16, 16);
            case SOUTH -> box(0, 0, 0, 16, 16, t);
            case WEST -> box(16 - t, 0, 0, 16, 16, 16);
            case EAST -> box(0, 0, 0, t, 16, 16);
        };
    }

    /**
     * Corner convention shared by the vertical step and the vertical stairs: horizontal facing {@code f} names the
     * corner between {@code f} and {@code f.getClockWise()} (NORTH = the north-east corner).
     */
    public static Direction cornerFacing(final Direction a, final Direction b) {
        if (a.getClockWise() == b) return a;
        if (b.getClockWise() == a) return b;
        throw new IllegalArgumentException("not a corner: " + a + "/" + b);
    }

    /** The corner facing for the quadrant a point (fractions 0..1 inside the block) lies in. */
    public static Direction cornerAt(final double fx, final double fz) {
        final Direction ew = fx >= 0.5 ? Direction.EAST : Direction.WEST;
        final Direction ns = fz >= 0.5 ? Direction.SOUTH : Direction.NORTH;
        return cornerFacing(ew, ns);
    }

    /** The corner facing after mirroring (rotation needs nothing special: {@code rotation.rotate(facing)}). */
    public static Direction mirrorCorner(final Direction facing, final Mirror mirror) {
        return cornerFacing(mirror.mirror(facing), mirror.mirror(facing.getClockWise()));
    }

    /** The 8 x 16 x 8 column in the corner named by {@code facing} (see {@link #cornerFacing}). */
    public static AABB cornerColumn(final Direction facing) {
        return rotateY(box(8, 0, 0, 16, 16, 8), turns(Direction.NORTH, facing));
    }

    /**
     * Joins boxes that share a whole face into one, repeatedly, so the renderer crops as few quads as possible and
     * no internal faces are left between pieces of one flat surface.
     */
    public static List<AABB> merge(final List<AABB> boxes) {
        final List<AABB> list = new ArrayList<>(boxes);
        boolean changed = true;
        while (changed) {
            changed = false;
            outer:
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    final AABB joined = join(list.get(i), list.get(j));
                    if (joined != null) {
                        list.set(i, joined);
                        list.remove(j);
                        changed = true;
                        break outer;
                    }
                }
            }
        }
        return List.copyOf(list);
    }

    private static AABB join(final AABB a, final AABB b) {
        final boolean sx = a.minX == b.minX && a.maxX == b.maxX;
        final boolean sy = a.minY == b.minY && a.maxY == b.maxY;
        final boolean sz = a.minZ == b.minZ && a.maxZ == b.maxZ;
        if (sy && sz && (a.maxX == b.minX || b.maxX == a.minX)) return new AABB(Math.min(a.minX, b.minX), a.minY, a.minZ, Math.max(a.maxX, b.maxX), a.maxY, a.maxZ);
        if (sx && sz && (a.maxY == b.minY || b.maxY == a.minY)) return new AABB(a.minX, Math.min(a.minY, b.minY), a.minZ, a.maxX, Math.max(a.maxY, b.maxY), a.maxZ);
        if (sx && sy && (a.maxZ == b.minZ || b.maxZ == a.minZ)) return new AABB(a.minX, a.minY, Math.min(a.minZ, b.minZ), a.maxX, a.maxY, Math.max(a.maxZ, b.maxZ));
        return null;
    }

    /** The union of 0..16 boxes as a vanilla shape (0..1), optimised. */
    public static VoxelShape toShape(final List<AABB> boxes) {
        VoxelShape shape = Shapes.empty();
        for (final AABB b : boxes) {
            shape = Shapes.joinUnoptimized(shape, Shapes.box(b.minX / 16, b.minY / 16, b.minZ / 16, b.maxX / 16, b.maxY / 16, b.maxZ / 16), BooleanOp.OR);
        }
        return shape.optimize();
    }
}
