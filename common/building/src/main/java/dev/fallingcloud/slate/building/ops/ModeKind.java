package dev.fallingcloud.slate.building.ops;

/**
 * How a {@link BuildMode} is driven in the world (design §7, client flow):
 * <ul>
 *   <li>AREA: right-click corner A, the box follows the crosshair, right-click corner B, ghosts show the plan, a
 *       third right-click (or the confirm key) applies.</li>
 *   <li>POINT: one click shows the ghost, the next applies (extend, paste).</li>
 *   <li>MOVE: select an area, then click a destination.</li>
 *   <li>TOGGLE: stays on and changes how normal placing/breaking works (mirror, radial).</li>
 *   <li>MEASURE: selection only, never changes the world.</li>
 * </ul>
 */
public enum ModeKind {
    AREA, POINT, MOVE, TOGGLE, MEASURE
}
