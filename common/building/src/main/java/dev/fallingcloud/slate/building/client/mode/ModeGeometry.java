package dev.fallingcloud.slate.building.client.mode;

import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.Clipboard;
import dev.fallingcloud.slate.building.ops.ModeKind;
import dev.fallingcloud.slate.building.ops.ModeParams;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The shape of a selection as the player sees it, before (or without) a plan: the box to outline, its size, the
 * radius/height of round modes, the A→B distance, and an estimate of how many positions the mode will touch (checked
 * against the player's limits before anything is planned, so a huge selection never stalls the client). Also the
 * editing gestures: pushing a face (Ctrl+scroll) and changing a radius (Shift+scroll on round modes).
 *
 * <p>Anchor conventions sent to the server ({@code ApplyOp.anchors}): AREA/MEASURE {@code [A, B]} (sphere: A centre,
 * B on the surface; cylinder: A base centre, B radius + height), POINT {@code [point]}, MOVE {@code [A, B, D]} where D
 * is the block the moved selection's minimum corner lands on.
 */
final class ModeGeometry {

    /** How a mode's anchors turn into a shape. */
    enum Kind { BOX, LINE, SPHERE, CYLINDER, POINT, NONE }

    /**
     * A selection's visible geometry.
     *
     * @param box      block-aligned outline (world space), null when there is nothing to outline yet
     * @param radius   sphere / cylinder radius (0 otherwise)
     * @param height   cylinder height (0 otherwise)
     * @param distance distance between the first two anchors' centres (0 with fewer)
     * @param estimate positions the mode will roughly touch
     * @param span     longest edge of the box
     */
    record Shape(@Nullable AABB box, int radius, int height, double distance, long estimate, int span) {
        static final Shape NONE = new Shape(null, 0, 0, 0, 0, 0);

        int sizeX() { return box == null ? 0 : (int) Math.round(box.getXsize()); }
        int sizeY() { return box == null ? 0 : (int) Math.round(box.getYsize()); }
        int sizeZ() { return box == null ? 0 : (int) Math.round(box.getZsize()); }
        long volume() { return (long) sizeX() * sizeY() * sizeZ(); }
    }

    static Kind kind(final BuildMode mode) {
        if (mode.kind() == ModeKind.TOGGLE) return Kind.NONE;
        if (mode.kind() == ModeKind.POINT) return Kind.POINT;
        if (mode == BuildModes.LINE) return Kind.LINE;
        if (mode == BuildModes.SPHERE) return Kind.SPHERE;
        if (mode == BuildModes.CYLINDER) return Kind.CYLINDER;
        return Kind.BOX;
    }

    /**
     * The shape of {@code anchors} (committed anchors plus the live one that follows the crosshair) in {@code mode}.
     * {@code clipboard} sizes paste and move previews.
     */
    static Shape shape(final BuildMode mode, final ModeParams params, final List<BlockPos> anchors, final @Nullable Clipboard clipboard) {
        if (anchors.isEmpty()) return Shape.NONE;
        final BlockPos a = anchors.get(0);
        final BlockPos b = anchors.size() > 1 ? anchors.get(1) : a;
        final double distance = anchors.size() > 1 ? Math.sqrt(a.distSqr(b)) : 0;
        return switch (kind(mode)) {
            case NONE -> Shape.NONE;
            case POINT -> {
                if (mode == BuildModes.PASTE && clipboard != null) {
                    final Vec3i size = rotatedSize(clipboard.size(), params);
                    final AABB box = new AABB(a.getX(), a.getY(), a.getZ(), a.getX() + size.getX(), a.getY() + size.getY(), a.getZ() + size.getZ());
                    yield new Shape(box, 0, 0, 0, clipboard.entries().size(), max(size));
                }
                yield new Shape(new AABB(a), 0, 0, 0, 1, 1);
            }
            case LINE -> {
                final AABB box = boxOf(a, b);
                final int length = Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ()))) + 1;
                final int t = Math.max(1, params.getInt("thickness"));
                yield new Shape(box, 0, 0, distance, (long) length * t * t, spanOf(box));
            }
            case SPHERE -> {
                final int r = (int) Math.round(distance);
                final AABB box = new AABB(a.getX() - r, a.getY() - r, a.getZ() - r, a.getX() + r + 1, a.getY() + r + 1, a.getZ() + r + 1);
                final boolean hollow = params.getBool("hollow");
                final double half = "FULL".equals(params.getChoice("part")) ? 1.0 : 0.5;
                final long est = (long) Math.ceil(half * (hollow ? 4 * Math.PI * (r + 1) * (r + 1) : 4.0 / 3.0 * Math.PI * Math.pow(r + 1, 3)));
                yield new Shape(box, r, 0, distance, Math.max(1, est), 2 * r + 1);
            }
            case CYLINDER -> {
                final int dx = b.getX() - a.getX();
                final int dz = b.getZ() - a.getZ();
                final int r = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
                final int y0 = Math.min(a.getY(), b.getY());
                final int h = Math.abs(b.getY() - a.getY()) + 1;
                final AABB box = new AABB(a.getX() - r, y0, a.getZ() - r, a.getX() + r + 1, y0 + h, a.getZ() + r + 1);
                final long est = (long) Math.ceil(params.getBool("hollow") ? 2 * Math.PI * (r + 1) * h : Math.PI * (r + 1) * (r + 1) * h);
                yield new Shape(box, r, h, distance, Math.max(1, est), Math.max(2 * r + 1, h));
            }
            case BOX -> {
                final AABB box = boxOf(a, b);
                final Shape base = new Shape(box, 0, 0, distance, 0, spanOf(box));
                yield new Shape(box, 0, 0, distance, estimate(mode, params, base, anchors, clipboard), base.span());
            }
        };
    }

    /** The destination box of a move ({@code [A, B, D]}): the source size, rotated, with its minimum corner on D. */
    static @Nullable AABB moveDestination(final ModeParams params, final List<BlockPos> anchors) {
        if (anchors.size() < 3) return null;
        final AABB src = boxOf(anchors.get(0), anchors.get(1));
        final Vec3i size = rotatedSize(new Vec3i((int) src.getXsize(), (int) src.getYsize(), (int) src.getZsize()), params);
        final BlockPos d = anchors.get(2);
        return new AABB(d.getX(), d.getY(), d.getZ(), d.getX() + size.getX(), d.getY() + size.getY(), d.getZ() + size.getZ());
    }

    /** Positions a box-shaped mode touches, roughly (exact for fill / walls / hollow / outline). */
    private static long estimate(final BuildMode mode, final ModeParams params, final Shape s, final List<BlockPos> anchors,
                                 final @Nullable Clipboard clipboard) {
        final long x = s.sizeX();
        final long y = s.sizeY();
        final long z = s.sizeZ();
        final long volume = x * y * z;
        final int t = Math.max(1, params.getInt("thickness"));
        if (mode == BuildModes.WALLS) return volume - Math.max(0, x - 2L * t) * y * Math.max(0, z - 2L * t);
        if (mode == BuildModes.HOLLOW_BOX) return volume - Math.max(0, x - 2L * t) * Math.max(0, y - 2L * t) * Math.max(0, z - 2L * t);
        if (mode == BuildModes.OUTLINE) return Math.min(volume, Math.max(1, 4 * (x + y + z) - 16));
        if (mode == BuildModes.OVERLAY) return x * z * Math.max(1, params.getInt("depth"));
        if (mode == BuildModes.STACK) return volume * Math.max(1, params.getInt("count"));
        if (mode == BuildModes.MOVE) return volume * 2;
        return volume;
    }

    // ---- editing ----

    /**
     * Anchors with face {@code face} of the selection pushed outwards ({@code delta > 0}) or pulled in. Box modes move
     * the side of the A-B box; spheres change the radius; cylinders change the height (top/bottom) or the radius
     * (sides). Returns null when the selection cannot shrink further.
     */
    static @Nullable List<BlockPos> pushFace(final BuildMode mode, final List<BlockPos> anchors, final Direction face, final int delta) {
        if (anchors.size() < 2) return null;
        final BlockPos a = anchors.get(0);
        final BlockPos b = anchors.get(1);
        final List<BlockPos> out = new ArrayList<>(anchors);
        switch (kind(mode)) {
            case SPHERE -> {
                final int r = (int) Math.round(Math.sqrt(a.distSqr(b))) + delta;
                if (r < 0) return null;
                out.set(1, radiusPoint(a, b, r, false));
            }
            case CYLINDER -> {
                if (face.getAxis() == Direction.Axis.Y) {
                    final int up = b.getY() >= a.getY() ? 1 : -1;
                    final int h = Math.abs(b.getY() - a.getY()) + delta;
                    if (h < 0) return null;
                    out.set(1, new BlockPos(b.getX(), a.getY() + up * h, b.getZ()));
                } else {
                    final int dx = b.getX() - a.getX();
                    final int dz = b.getZ() - a.getZ();
                    final int r = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz)) + delta;
                    if (r < 0) return null;
                    final BlockPos p = radiusPoint(a, new BlockPos(b.getX(), a.getY(), b.getZ()), r, true);
                    out.set(1, new BlockPos(p.getX(), b.getY(), p.getZ()));
                }
            }
            default -> {
                final Direction.Axis axis = face.getAxis();
                final boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
                final int ca = a.get(axis);
                final int cb = b.get(axis);
                // The anchor holding this side's coordinate moves; a flat box grows from the second corner.
                final int idx = ca == cb ? 1 : (positive == (ca > cb) ? 0 : 1);
                final BlockPos moving = out.get(idx);
                final int step = positive ? delta : -delta;
                final int next = moving.get(axis) + step;
                final int other = out.get(1 - idx).get(axis);
                if (delta < 0 && (ca == cb || (positive ? next < other : next > other))) return null;
                out.set(idx, with(moving, axis, next));
            }
        }
        return out;
    }

    /** A point {@code r} blocks from {@code a} along the dominant axis of {@code a→towards} (or +X / horizontal +X). */
    private static BlockPos radiusPoint(final BlockPos a, final BlockPos towards, final int r, final boolean horizontal) {
        final int dx = towards.getX() - a.getX();
        final int dy = horizontal ? 0 : towards.getY() - a.getY();
        final int dz = towards.getZ() - a.getZ();
        final int ax = Math.abs(dx);
        final int ay = Math.abs(dy);
        final int az = Math.abs(dz);
        if (ax == 0 && ay == 0 && az == 0) return a.offset(r, 0, 0);
        if (ax >= ay && ax >= az) return a.offset(Integer.signum(dx) * r, 0, 0);
        if (ay >= az) return a.offset(0, Integer.signum(dy) * r, 0);
        return a.offset(0, 0, Integer.signum(dz) * r);
    }

    private static BlockPos with(final BlockPos p, final Direction.Axis axis, final int value) {
        return switch (axis) {
            case X -> new BlockPos(value, p.getY(), p.getZ());
            case Y -> new BlockPos(p.getX(), value, p.getZ());
            case Z -> new BlockPos(p.getX(), p.getY(), value);
        };
    }

    // ---- helpers ----

    static AABB boxOf(final BlockPos a, final BlockPos b) {
        return new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
            Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    /** The block-aligned box around every position of {@code changes}, or null. */
    static @Nullable AABB boundsOf(final Iterable<BlockPos> positions) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean any = false;
        for (final BlockPos p : positions) {
            any = true;
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        return any ? new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1) : null;
    }

    private static int spanOf(final AABB box) {
        return (int) Math.round(Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())));
    }

    private static int max(final Vec3i v) {
        return Math.max(v.getX(), Math.max(v.getY(), v.getZ()));
    }

    /** {@code size} after the {@code rotation} parameter (90 / 270 swap X and Z). */
    static Vec3i rotatedSize(final Vec3i size, final ModeParams params) {
        final String rot = params.getChoice("rotation");
        return "90".equals(rot) || "270".equals(rot) ? new Vec3i(size.getZ(), size.getY(), size.getX()) : size;
    }

    /** Centre of the top face of {@code box}, slightly above it (label position). */
    static Vec3 labelPoint(final AABB box) {
        return new Vec3((box.minX + box.maxX) / 2, box.maxY + 0.35, (box.minZ + box.maxZ) / 2);
    }

    private ModeGeometry() {}
}
