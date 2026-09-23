package dev.fallingcloud.slate.building.config;

/**
 * {@code building.json → hud}: the mode chip, progress bar and result line (design §5).
 *
 * <p>Owner: C (ui). Skeleton declares the fields and defaults of design §10.
 */
public final class HudSettings {

    /** Show the mode chip while a building mode is active. */
    public boolean enabled = true;
    /** Where the chip sits: a {@code layout.ui.Anchor} name ({@code TOP}, {@code TOP_LEFT}, ...). */
    public String anchor = "TOP";
    /** Size multiplier of the HUD elements. */
    public double scale = 1.0;
    /** Show operation results as an action-bar style line. */
    public boolean actionBar = true;
    /** UI sounds for selections, applies and undo. */
    public boolean sounds = true;
}
