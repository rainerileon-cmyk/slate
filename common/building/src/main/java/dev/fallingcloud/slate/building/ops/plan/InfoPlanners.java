package dev.fallingcloud.slate.building.ops.plan;

import dev.fallingcloud.slate.building.ops.Plan;
import dev.fallingcloud.slate.building.ops.PlanContext;
import dev.fallingcloud.slate.building.ops.SymmetryMath;
import java.util.List;

/**
 * Modes that never change the world through a plan: measure (the box and its volume) and the symmetry toggles
 * (the region the symmetry covers; placing and breaking are mirrored by the server as they happen).
 */
public final class InfoPlanners {

    /** Measure: bounds = the box, requested count = its volume. No limits (measure needs no tool). */
    public static Plan measure(final PlanContext ctx) {
        final Box box = Plans.box(ctx);
        return new Plan(List.of(), box.aabb(), null, (int) Math.min(Integer.MAX_VALUE, box.volume()));
    }

    /** Mirror / radial: bounds = the cube the symmetry covers around the centre ({@code anchors[0]}). */
    public static Plan symmetry(final PlanContext ctx) {
        return new Plan(List.of(), SymmetryMath.region(ctx.anchor(0), SymmetryMath.radius(ctx.player())), null, 0);
    }

    private InfoPlanners() {}
}
