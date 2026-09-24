package dev.fallingcloud.slate.building.config;

import com.google.gson.JsonObject;

/**
 * {@code building.json → modes}: how building modes feel in the world and what survives a restart (design §7
 * client flow, §10). Read and written by {@code client.mode.ClientModeState}; the Building settings tab may edit the
 * plain fields directly (they apply live, nothing caches them).
 *
 * <p>Owner: D2 (ops client).
 */
public final class ModeSettings {

    /** Id of the last active mode (re-selected by its keybind / the menu), empty for none. */
    public String lastMode = "";
    /** Parameter values per mode: {@code {"fill": {"replace": "AIR", ...}, ...}} (see {@code ModeParams.toJson}). */
    public JsonObject params = new JsonObject();
    /** Distance in blocks of a corner placed on air (the scroll wheel adjusts it while aiming at air). */
    public int airDistance = 4;
    /**
     * A third right-click applies a pending selection. When off, only the confirm key applies (right-click still
     * applies while the confirm key is unbound, so a selection can never get stuck).
     */
    public boolean confirmWithRightClick = true;
    /** Plan and show the ghost preview while the box still follows the crosshair (small selections only). */
    public boolean livePreview = true;
    /** Size labels ("12 × 4 × 8") above the selection box. */
    public boolean labels = true;
    /** Arrow keys (and Page Up / Page Down) nudge the selection, the paste point or the symmetry centre. */
    public boolean arrowNudge = true;
    /** Esc clears a pending selection before it opens the pause menu. */
    public boolean escapeCancels = true;
    /** Right-clicking a container (chest, barrel, ...) with nothing selected opens it instead of starting a selection. */
    public boolean openContainers = true;

    /** {@link #airDistance} kept inside 1..{@code maxReach}. */
    public int airDistance(final int maxReach) {
        return Math.max(1, Math.min(Math.max(1, maxReach), airDistance));
    }
}
