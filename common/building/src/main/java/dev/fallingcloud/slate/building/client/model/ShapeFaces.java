package dev.fallingcloud.slate.building.client.model;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * The visible faces of a shape, computed once per shape state from its render boxes: for every box and every side,
 * the part of that side that is not covered by another box of the same shape. Two boxes touching (a stair's slab
 * and its upper half) or overlapping (an L made of two overlapping halves) would otherwise leave coplanar faces
 * inside the shape, which show through translucent materials (glass stairs) and z-fight. What is left is split into
 * rectangles ({@link Region}s), each tagged {@link Region#boundary()} when it lies on the block boundary (culled
 * against the neighbour like any full-block face) or internal (never culled).
 *
 * <p>Coordinates are in block units (0..1). Each region spans the two in-plane axes of its direction in XYZ order:
 * X faces span (Y, Z), Y faces (X, Z), Z faces (X, Y).
 */
final class ShapeFaces {

    /** Two coordinates closer than this are the same plane (boxes are given in 1/16 steps, so this is generous). */
    static final double EPS = 1.0E-5;

    /**
     * One rectangle of visible face.
     *
     * @param dir      the direction the face points to
     * @param depth    its position on {@code dir}'s axis
     * @param a0       lower bound on the first in-plane axis
     * @param b0       lower bound on the second in-plane axis
     * @param a1       upper bound on the first in-plane axis
     * @param b1       upper bound on the second in-plane axis
     * @param boundary whether the face lies on the block boundary (culled against the neighbour)
     */
    record Region(Direction dir, double depth, double a0, double b0, double a1, double b1, boolean boundary) {

        /** How far this face sits from the full block's face of the same direction (0 on the boundary, negative inside). */
        double move() {
            return dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? depth - 1.0 : depth;
        }
    }

    private static final Direction[] DIRECTIONS = Direction.values();

    private final List<Region>[] byDir;
    /** Bit {@code dir.ordinal()} set: the shape has a boundary / an internal region facing that way. */
    private final int boundaryMask;
    private final int internalMask;

    private ShapeFaces(final List<Region>[] byDir) {
        this.byDir = byDir;
        int boundary = 0, internal = 0;
        for (final List<Region> regions : byDir) {
            for (final Region r : regions) {
                if (r.boundary()) boundary |= 1 << r.dir().ordinal();
                else internal |= 1 << r.dir().ordinal();
            }
        }
        this.boundaryMask = boundary;
        this.internalMask = internal;
    }

    /** The visible regions facing {@code dir}. */
    List<Region> facing(final Direction dir) {
        return byDir[dir.ordinal()];
    }

    /** Whether some visible region facing {@code dir} lies on the block boundary (culled against the neighbour). */
    boolean hasBoundary(final Direction dir) {
        return (boundaryMask >> dir.ordinal() & 1) != 0;
    }

    /** Whether some visible region facing {@code dir} lies inside the block (a slab top, a stair riser: never culled). */
    boolean hasInternal(final Direction dir) {
        return (internalMask >> dir.ordinal() & 1) != 0;
    }

    /** Whether the shape covers the whole face on {@code dir}'s side of the block (one full boundary region). */
    boolean fullFace(final Direction dir) {
        double area = 0;
        for (final Region r : byDir[dir.ordinal()]) if (r.boundary()) area += (r.a1() - r.a0()) * (r.b1() - r.b0());
        return area > 1.0 - 1.0E-4;
    }

    /** Computes the faces of {@code boxes}, given in block pixels (0..16) as {@code ShapeBlock.renderBoxes} returns them. */
    @SuppressWarnings("unchecked")
    static ShapeFaces of(final List<AABB> boxes16) {
        final List<AABB> boxes = new ArrayList<>(boxes16.size());
        for (final AABB b : boxes16) {
            final AABB unit = new AABB(b.minX / 16.0, b.minY / 16.0, b.minZ / 16.0, b.maxX / 16.0, b.maxY / 16.0, b.maxZ / 16.0);
            if (unit.getXsize() > EPS && unit.getYsize() > EPS && unit.getZsize() > EPS) boxes.add(unit);
        }
        final List<Region>[] byDir = new List[DIRECTIONS.length];
        for (final Direction d : DIRECTIONS) byDir[d.ordinal()] = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            final AABB box = boxes.get(i);
            for (final Direction d : DIRECTIONS) {
                final Direction.Axis n = d.getAxis();
                final boolean positive = d.getAxisDirection() == Direction.AxisDirection.POSITIVE;
                final double depth = positive ? box.max(n) : box.min(n);
                List<double[]> rects = new ArrayList<>(4);
                rects.add(rect(box, n));
                for (int j = 0; j < boxes.size() && !rects.isEmpty(); j++) {
                    if (j == i) continue;
                    final AABB other = boxes.get(j);
                    // Covered: the other box fills the space right in front of this face ...
                    final double probe = positive ? depth + EPS * 10 : depth - EPS * 10;
                    final boolean inFront = other.min(n) < probe && other.max(n) > probe;
                    // ... or an earlier box already has a face in the same plane pointing the same way (no duplicates).
                    final boolean samePlane = j < i && Math.abs((positive ? other.max(n) : other.min(n)) - depth) < EPS;
                    if (inFront || samePlane) rects = subtract(rects, rect(other, n));
                }
                final boolean boundary = positive ? depth > 1.0 - EPS : depth < EPS;
                for (final double[] r : rects) {
                    final double a0 = Math.max(0, r[0]), b0 = Math.max(0, r[1]), a1 = Math.min(1, r[2]), b1 = Math.min(1, r[3]);
                    if (a1 - a0 > EPS && b1 - b0 > EPS) byDir[d.ordinal()].add(new Region(d, depth, a0, b0, a1, b1, boundary));
                }
            }
        }
        for (int k = 0; k < byDir.length; k++) byDir[k] = List.copyOf(byDir[k]);
        return new ShapeFaces(byDir);
    }

    /** The first in-plane axis of faces on axis {@code n} (XYZ order without {@code n}). */
    static Direction.Axis axisA(final Direction.Axis n) {
        return n == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
    }

    /** The second in-plane axis of faces on axis {@code n}. */
    static Direction.Axis axisB(final Direction.Axis n) {
        return n == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
    }

    /** {@code box} projected onto the plane of axis {@code n}: {a0, b0, a1, b1}. */
    private static double[] rect(final AABB box, final Direction.Axis n) {
        final Direction.Axis a = axisA(n), b = axisB(n);
        return new double[] {box.min(a), box.min(b), box.max(a), box.max(b)};
    }

    /** {@code rects} minus {@code cut}; every rectangle that overlaps {@code cut} is split into up to four pieces. */
    private static List<double[]> subtract(final List<double[]> rects, final double[] cut) {
        final List<double[]> out = new ArrayList<>(rects.size() + 3);
        for (final double[] r : rects) {
            if (cut[0] >= r[2] - EPS || cut[2] <= r[0] + EPS || cut[1] >= r[3] - EPS || cut[3] <= r[1] + EPS) {
                out.add(r);
                continue;
            }
            // Bands below and above the cut span the full width; the sides fill the cut's height only.
            if (cut[1] > r[1] + EPS) out.add(new double[] {r[0], r[1], r[2], cut[1]});
            if (cut[3] < r[3] - EPS) out.add(new double[] {r[0], cut[3], r[2], r[3]});
            final double lo = Math.max(r[1], cut[1]), hi = Math.min(r[3], cut[3]);
            if (cut[0] > r[0] + EPS) out.add(new double[] {r[0], lo, cut[0], hi});
            if (cut[2] < r[2] - EPS) out.add(new double[] {cut[2], lo, r[2], hi});
        }
        return out;
    }
}
