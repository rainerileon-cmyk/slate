package dev.fallingcloud.slate.building.ops.plan;

import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown by {@link PlanBuilder#want()} when a planner visits more positions than the player's volume limit allows,
 * so no planner can walk (and allocate for) an unbounded region whatever the request says. {@code Planners} turns it
 * into a too-many error plan. No stack trace: this is flow control, not a bug.
 */
public final class PlanOverflow extends RuntimeException {

    private final int count;
    private final int max;
    private final @Nullable AABB bounds;

    PlanOverflow(final int count, final int max, final @Nullable AABB bounds) {
        super("plan exceeds " + max + " positions", null, false, false);
        this.count = count;
        this.max = max;
        this.bounds = bounds;
    }

    /** Positions visited when it gave up (a lower bound of what the selection would touch). */
    public int count() {
        return count;
    }

    public int max() {
        return max;
    }

    /** The selection box of the plan, when the planner set one. */
    public @Nullable AABB bounds() {
        return bounds;
    }
}
