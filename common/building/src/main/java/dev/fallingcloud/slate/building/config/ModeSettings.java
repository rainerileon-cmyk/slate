package dev.fallingcloud.slate.building.config;

import com.google.gson.JsonObject;

/**
 * {@code building.json → modes}: building-mode state that survives restarts (design §7 client flow).
 *
 * <p>Owner: D2 (ops client). Skeleton declares the fields and defaults of design §10; {@code ClientModeState} reads
 * and writes them.
 */
public final class ModeSettings {

    /** Id of the last active mode (re-selected by its keybind / the menu), empty for none. */
    public String lastMode = "";
    /** Parameter values per mode: {@code {"fill": {"replace": "AIR", ...}, ...}} (see {@code ModeParams.toJson}). */
    public JsonObject params = new JsonObject();
    /** Distance of a corner placed on air (scroll-adjustable while selecting). */
    public int airDistance = 4;
    /** A third right-click applies a pending selection (the confirm key always works). */
    public boolean confirmWithRightClick = true;
}
