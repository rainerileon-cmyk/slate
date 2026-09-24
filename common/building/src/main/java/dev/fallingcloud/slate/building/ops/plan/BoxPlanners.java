package dev.fallingcloud.slate.building.ops.plan;

import dev.fallingcloud.slate.building.ops.Palette;
import dev.fallingcloud.slate.building.ops.Plan;
import dev.fallingcloud.slate.building.ops.PlanContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Box modes: fill, walls, hollow box and outline. All place the palette into (parts of) the selection box, bottom
 * layer first so gravity blocks rest on what was placed before them, under the {@code replace} policy. The number of
 * positions a part covers is known exactly up front ({@link Part#count}), so an oversized request is refused before
 * anything is walked, and rows skip straight over the hollow middle of walls, shells and outlines.
 */
public final class BoxPlanners {

    /** Which positions of the box a mode fills. */
    private interface Part {
        boolean contains(Box b, int x, int y, int z, int t);

        /** Positions of box {@code b} (sizes x, y, z) in this part (the client's estimate formula; exact or above). */
        long count(long x, long y, long z, int t);

        /** The next x to look at after {@code x}, which is not in the part (skips a row's hollow middle). */
        default int next(final Box b, final int x, final int t) {
            return x + 1;
        }
    }

    private static final Part ALL = new Part() {
        @Override public boolean contains(final Box b, final int x, final int y, final int z, final int t) { return true; }

        @Override public long count(final long x, final long y, final long z, final int t) { return x * y * z; }
    };

    /** The four vertical sides, {@code t} thick. */
    private static final Part WALLS = new Part() {
        @Override public boolean contains(final Box b, final int x, final int y, final int z, final int t) {
            return x - b.minX() < t || b.maxX() - x < t || z - b.minZ() < t || b.maxZ() - z < t;
        }

        @Override public long count(final long x, final long y, final long z, final int t) {
            return x * y * z - Math.max(0, x - 2L * t) * y * Math.max(0, z - 2L * t);
        }

        @Override public int next(final Box b, final int x, final int t) {
            return Math.max(x + 1, b.maxX() - t + 1);
        }
    };

    /** All six sides, {@code t} thick. */
    private static final Part SHELL = new Part() {
        @Override public boolean contains(final Box b, final int x, final int y, final int z, final int t) {
            return WALLS.contains(b, x, y, z, t) || y - b.minY() < t || b.maxY() - y < t;
        }

        @Override public long count(final long x, final long y, final long z, final int t) {
            return x * y * z - Math.max(0, x - 2L * t) * Math.max(0, y - 2L * t) * Math.max(0, z - 2L * t);
        }

        @Override public int next(final Box b, final int x, final int t) {
            return Math.max(x + 1, b.maxX() - t + 1);
        }
    };

    /** The twelve edges: at least two coordinates on the box boundary. */
    private static final Part EDGES = new Part() {
        @Override public boolean contains(final Box b, final int x, final int y, final int z, final int t) {
            int on = 0;
            if (x == b.minX() || x == b.maxX()) on++;
            if (y == b.minY() || y == b.maxY()) on++;
            if (z == b.minZ() || z == b.maxZ()) on++;
            return on >= 2;
        }

        @Override public long count(final long x, final long y, final long z, final int t) {
            return Math.min(x * y * z, Math.max(1, 4 * (x + y + z) - 16));
        }

        @Override public int next(final Box b, final int x, final int t) {
            return Math.max(x + 1, b.maxX());
        }
    };

    public static Plan fill(final PlanContext ctx) {
        return box(ctx, ALL);
    }

    public static Plan walls(final PlanContext ctx) {
        return box(ctx, WALLS);
    }

    public static Plan hollow(final PlanContext ctx) {
        return box(ctx, SHELL);
    }

    public static Plan outline(final PlanContext ctx) {
        return box(ctx, EDGES);
    }

    private static Plan box(final PlanContext ctx, final Part part) {
        final Box box = Plans.box(ctx);
        final int t = ctx.mode().param("thickness") != null ? Math.max(1, ctx.params().getInt("thickness")) : 1;
        Component err = Plans.span(box, ctx.limits());
        if (err == null) err = Plans.tooMany(part.count(box.sizeX(), box.sizeY(), box.sizeZ(), t), ctx.limits());
        if (err != null) return Plans.error(err, box.aabb());
        final Palette palette = ctx.palette();
        if (palette.isEmpty()) return Plans.error(PlanErrors.noBlock(), box.aabb());
        final ReplacePolicy policy = ReplacePolicy.of(ctx.params(), ReplacePolicy.REPLACEABLE);
        final long seed = ctx.seed();
        final PlanBuilder b = new PlanBuilder(ctx).bounds(box.aabb());
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); ) {
                    if (!part.contains(box, x, y, z, t)) {
                        x = part.next(box, x, t);
                        continue;
                    }
                    b.want();
                    p.set(x, y, z);
                    x++;
                    if (!b.open(p, policy)) continue;
                    final Palette.WeightedEntry entry = palette.pick(p, seed);
                    if (entry == null) continue;
                    final BlockPos pos = p.immutable();
                    b.place(pos, Placement.of(ctx, entry, pos, ctx.face()), entry.variant(), policy);
                }
            }
        }
        return b.build();
    }

    private BoxPlanners() {}
}
