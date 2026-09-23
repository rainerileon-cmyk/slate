package dev.fallingcloud.slate.building.ops;

/**
 * Turns a selection into a {@link Plan}. Must be deterministic and side-effect free: the client runs it for the
 * ghost preview and the server runs it again for the real thing, so the preview matches what happens.
 */
@FunctionalInterface
public interface ModePlanner {
    Plan plan(PlanContext ctx);
}
