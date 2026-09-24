package dev.fallingcloud.slate.building.ops.plan;

import dev.fallingcloud.slate.building.ops.Palette;
import dev.fallingcloud.slate.building.ops.Plan;
import dev.fallingcloud.slate.building.ops.PlanContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Box modes: fill, walls, hollow box and outline. All place the palette into (parts of) the selection box, bottom
 * layer first so gravity blocks rest on what was placed before them, under the {@code replace} policy.
 */
public final class BoxPlanners {

    /** Which positions of the box a mode fills. */
    @FunctionalInterface
    private interface Part {
        boolean contains(Box b, int x, int y, int z, int t);
    }

    private static final Part ALL = (b, x, y, z, t) -> true;

    /** The four vertical sides, {@code t} thick. */
    private static final Part WALLS = (b, x, y, z, t) ->
        x - b.minX() < t || b.maxX() - x < t || z - b.minZ() < t || b.maxZ() - z < t;

    /** All six sides, {@code t} thick. */
    private static final Part SHELL = (b, x, y, z, t) ->
        WALLS.contains(b, x, y, z, t) || y - b.minY() < t || b.maxY() - y < t;

    /** The twelve edges: at least two coordinates on the box boundary. */
    private static final Part EDGES = (b, x, y, z, t) -> {
        int on = 0;
        if (x == b.minX() || x == b.maxX()) on++;
        if (y == b.minY() || y == b.maxY()) on++;
        if (z == b.minZ() || z == b.maxZ()) on++;
        return on >= 2;
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
        final Component err = Plans.span(box, ctx.limits());
        if (err != null) return Plans.error(err, box.aabb());
        final Palette palette = ctx.palette();
        if (palette.isEmpty()) return Plans.error(PlanErrors.noBlock(), box.aabb());
        final int t = ctx.mode().param("thickness") != null ? ctx.params().getInt("thickness") : 1;
        final ReplacePolicy policy = ReplacePolicy.of(ctx.params(), ReplacePolicy.REPLACEABLE);
        final long seed = ctx.seed();
        final PlanBuilder b = new PlanBuilder(ctx).bounds(box.aabb());
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    if (!part.contains(box, x, y, z, t)) continue;
                    b.want();
                    p.set(x, y, z);
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
