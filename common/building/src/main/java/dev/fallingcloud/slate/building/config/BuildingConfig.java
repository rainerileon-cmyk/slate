package dev.fallingcloud.slate.building.config;

/**
 * Client preferences, {@code config/slate/building.json} ({@code SlateBuilding.config()}). One nested section per
 * area, each in its own class so owners never edit the same file. Gson fills the POJOs: a missing key keeps its
 * field default, so adding fields is always safe; renaming one loses the user's value.
 *
 * <p>Save after editing with {@code SlateBuilding.configFile().save()} (or {@code update(...)}). The Slate Config
 * "Building" tab (owner C) edits these live through a reload hook.
 */
public final class BuildingConfig {

    /** Alt swap wheel and pick-block behaviour. Owner: C. */
    public WheelSettings wheel = new WheelSettings();
    /** Placement ghost. Owner: B. */
    public PreviewSettings preview = new PreviewSettings();
    /** Mode HUD chip, action-bar results, sounds. Owner: C. */
    public HudSettings hud = new HudSettings();
    /** Last mode, per-mode parameters, selection feel. Owner: D2. */
    public ModeSettings modes = new ModeSettings();
    /** Accurate placement and fast breaking ({@code client.place.AccuratePlacement}). */
    public PlacementSettings placement = new PlacementSettings();
}
