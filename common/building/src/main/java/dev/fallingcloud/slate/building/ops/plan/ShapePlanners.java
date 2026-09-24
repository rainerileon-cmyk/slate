package dev.fallingcloud.slate.building.ops.plan;

import dev.fallingcloud.slate.building.ops.Palette;
import dev.fallingcloud.slate.building.ops.Plan;
import dev.fallingcloud.slate.building.ops.PlanContext;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * Geometric modes: line, cylinder and sphere. Circles and spheres use the "radius + ½" rule ({@code d² < (r + 0.5)²}),
 * which gives round, symmetric outlines at every size; hollow variants keep the positions with at least one neighbour
 * outside, a watertight one-block shell.
 */
public final class ShapePlanners {

    /**
     * A 3D line from corner A to corner B (both included), {@code thickness} blocks across: every step of the longest
     * axis gets one position, widened in the two other axes for thickness 2–3.
     */
    public static Plan line(final PlanContext ctx) {
        final BlockPos a = ctx.anchor(0);
        final BlockPos b = ctx.anchor(1);
        final Box box = Box.of(a, b);
        final int t = ctx.mode().param("thickness") != null ? Math.max(1, ctx.params().getInt("thickness")) : 1;
        final int dx = b.getX() - a.getX(), dy = b.getY() - a.getY(), dz = b.getZ() - a.getZ();
        final int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        Component err = Plans.span(box, ctx.limits());
        if (err == null) err = Plans.tooMany((steps + 1L) * t * t, ctx.limits());
        if (err != null) return Plans.error(err, box.aabb());
        if (ctx.palette().isEmpty()) return Plans.error(PlanErrors.noBlock(), box.aabb());
        final Direction.Axis major = Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= Math.abs(dz) ? Direction.Axis.X
            : Math.abs(dy) >= Math.abs(dz) ? Direction.Axis.Y : Direction.Axis.Z;
        final int lo = -(t - 1) / 2;
        final int hi = t / 2;
        final LongLinkedOpenHashSet points = new LongLinkedOpenHashSet();
        for (int i = 0; i <= steps; i++) {
            final double f = steps == 0 ? 0 : i / (double) steps;
            final int x = a.getX() + (int) Math.round(dx * f);
            final int y = a.getY() + (int) Math.round(dy * f);
            final int z = a.getZ() + (int) Math.round(dz * f);
            for (int u = lo; u <= hi; u++) {
                for (int v = lo; v <= hi; v++) {
                    switch (major) {
                        case X -> points.add(BlockPos.asLong(x, y + u, z + v));
                        case Y -> points.add(BlockPos.asLong(x + u, y, z + v));
                        case Z -> points.add(BlockPos.asLong(x + u, y + v, z));
                    }
                }
            }
        }
        final PlanBuilder pb = new PlanBuilder(ctx).bounds(box.aabb().inflate(Math.max(0, hi)));
        final ReplacePolicy policy = ReplacePolicy.of(ctx.params(), ReplacePolicy.REPLACEABLE);
        final long seed = ctx.seed();
        for (final LongIterator it = points.iterator(); it.hasNext(); ) {
            final BlockPos pos = BlockPos.of(it.nextLong());
            pb.want();
            if (!pb.open(pos, policy)) continue;
            final Palette.WeightedEntry entry = ctx.palette().pick(pos, seed);
            if (entry != null) pb.place(pos, Placement.of(ctx, entry, pos, ctx.face()), entry.variant(), policy);
        }
        return pb.build();
    }

    /**
     * A vertical cylinder: A is the centre of the base, B sets the radius (its horizontal distance from A) and the
     * height (its Y; below A grows downwards). {@code hollow} leaves only the wall.
     */
    public static Plan cylinder(final PlanContext ctx) {
        final BlockPos a = ctx.anchor(0);
        final BlockPos b = ctx.anchor(1);
        final int dxB = b.getX() - a.getX(), dzB = b.getZ() - a.getZ();
        final int r = (int) Math.round(Math.sqrt((double) dxB * dxB + (double) dzB * dzB));
        final int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
        final Box box = new Box(a.getX() - r, y0, a.getZ() - r, a.getX() + r, y1, a.getZ() + r);
        final boolean hollow = ctx.params().getBool("hollow");
        final int h = y1 - y0 + 1;
        // The client's estimate (ModeGeometry): above the real count, so an honest request always passes.
        final long est = (long) Math.ceil(hollow ? 2 * Math.PI * (r + 1) * h : Math.PI * (r + 1) * (r + 1) * h);
        Component err = Plans.span(box, ctx.limits());
        if (err == null) err = Plans.tooMany(Math.max(1, est), ctx.limits());
        if (err != null) return Plans.error(err, box.aabb());
        if (ctx.palette().isEmpty()) return Plans.error(PlanErrors.noBlock(), box.aabb());
        final ReplacePolicy policy = ReplacePolicy.of(ctx.params(), ReplacePolicy.REPLACEABLE);
        final long seed = ctx.seed();
        final PlanBuilder pb = new PlanBuilder(ctx).bounds(box.aabb());
        for (int y = y0; y <= y1; y++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (!inCircle(dx, dz, r)) continue;
                    if (hollow && inCircle(dx + 1, dz, r) && inCircle(dx - 1, dz, r) && inCircle(dx, dz + 1, r) && inCircle(dx, dz - 1, r)) continue;
                    place(ctx, pb, new BlockPos(a.getX() + dx, y, a.getZ() + dz), policy, seed);
                }
            }
        }
        return pb.build();
    }

    /**
     * A sphere around A through B (radius = the distance A–B, rounded). {@code part}: FULL, DOME (the upper half,
     * centre layer included) or BOWL (the lower half). {@code hollow} leaves a one-block shell.
     */
    public static Plan sphere(final PlanContext ctx) {
        final BlockPos a = ctx.anchor(0);
        final BlockPos b = ctx.anchor(1);
        final int r = radius(a, b);
        final String part = ctx.params().getChoice("part");
        final int yLo = "DOME".equals(part) ? 0 : -r;
        final int yHi = "BOWL".equals(part) ? 0 : r;
        final Box box = new Box(a.getX() - r, a.getY() + yLo, a.getZ() - r, a.getX() + r, a.getY() + yHi, a.getZ() + r);
        final Box full = new Box(a.getX() - r, a.getY() - r, a.getZ() - r, a.getX() + r, a.getY() + r, a.getZ() + r);
        final boolean hollow = ctx.params().getBool("hollow");
        final double half = "FULL".equals(part) ? 1.0 : 0.5;
        final long est = (long) Math.ceil(half * (hollow ? 4 * Math.PI * (r + 1) * (r + 1) : 4.0 / 3.0 * Math.PI * Math.pow(r + 1, 3)));
        Component err = Plans.span(full, ctx.limits());
        if (err == null) err = Plans.tooMany(Math.max(1, est), ctx.limits());
        if (err != null) return Plans.error(err, box.aabb());
        if (ctx.palette().isEmpty()) return Plans.error(PlanErrors.noBlock(), box.aabb());
        final ReplacePolicy policy = ReplacePolicy.of(ctx.params(), ReplacePolicy.REPLACEABLE);
        final long seed = ctx.seed();
        final PlanBuilder pb = new PlanBuilder(ctx).bounds(box.aabb());
        for (int dy = yLo; dy <= yHi; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (!inSphere(dx, dy, dz, r)) continue;
                    if (hollow && inSphere(dx + 1, dy, dz, r) && inSphere(dx - 1, dy, dz, r) && inSphere(dx, dy + 1, dz, r)
                        && inSphere(dx, dy - 1, dz, r) && inSphere(dx, dy, dz + 1, r) && inSphere(dx, dy, dz - 1, r)) continue;
                    place(ctx, pb, a.offset(dx, dy, dz), policy, seed);
                }
            }
        }
        return pb.build();
    }

    /** Sphere radius for centre {@code a} and surface point {@code b}. */
    public static int radius(final BlockPos a, final BlockPos b) {
        return (int) Math.round(Math.sqrt(a.distSqr(b)));
    }

    /** Whether offset (dx, dz) lies in the disc of radius {@code r}. */
    public static boolean inCircle(final int dx, final int dz, final int r) {
        final double rr = r + 0.5;
        return dx * dx + dz * dz < rr * rr;
    }

    /** Whether offset (dx, dy, dz) lies in the ball of radius {@code r}. */
    public static boolean inSphere(final int dx, final int dy, final int dz, final int r) {
        final double rr = r + 0.5;
        return dx * dx + dy * dy + dz * dz < rr * rr;
    }

    private static void place(final PlanContext ctx, final PlanBuilder pb, final BlockPos pos, final ReplacePolicy policy, final long seed) {
        pb.want();
        if (!pb.open(pos, policy)) return;
        final Palette.WeightedEntry entry = ctx.palette().pick(pos, seed);
        if (entry != null) pb.place(pos, Placement.of(ctx, entry, pos, ctx.face()), entry.variant(), policy);
    }

    private ShapePlanners() {}
}
