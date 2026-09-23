package dev.fallingcloud.slate.building.ops;

/**
 * Size and speed limits for one player's operations, derived from their toolbox (tool tiers + upgrades) and the
 * server rules ({@code Capabilities.limits(settings)}).
 *
 * @param maxVolume     most positions one operation may touch
 * @param maxSpan       longest edge of a selection
 * @param reachBonus    extra anchor reach beyond {@code blockInteractionRange()}
 * @param blocksPerTick execution speed of one operation
 * @param undoDepth     operations kept in the undo history
 */
public record Limits(int maxVolume, int maxSpan, int reachBonus, int blocksPerTick, int undoDepth) {

    /** Nothing allowed (no toolbox, or building disabled). */
    public static final Limits NONE = new Limits(0, 0, 0, 0, 0);
}
