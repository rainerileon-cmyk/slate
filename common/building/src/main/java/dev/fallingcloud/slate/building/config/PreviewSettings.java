package dev.fallingcloud.slate.building.config;

import dev.fallingcloud.slate.building.SlateBuilding;

/**
 * {@code building.json → preview}: the placement ghost and the planned-result ghosts of building modes (design §6).
 * The renderers read it every frame through {@link #current()}, so edits (the Slate Config tab, or a hand-edited
 * file after a reload) apply live. Out-of-range values from a hand-edited file are clamped where they are read
 * ({@link #opacity()}, {@link #saturation()}, {@link #maxBlocks()}), never rewritten.
 *
 * <p>Owner: B (render).
 */
public final class PreviewSettings {

    /** Lowest opacity the renderer uses: a fainter ghost is effectively invisible, which reads as a bug. */
    public static final double MIN_OPACITY = 0.05;

    /** Show a ghost of what the held variant would place. */
    public boolean enabled = true;
    /** Also show it for any block item (full blocks, torches, flowers, ...), not just variants. */
    public boolean allBlocks = false;
    /** Ghost opacity, 0..1. */
    public double opacity = 0.45;
    /** Ghost colour saturation, 0 (grey) .. 1 (true colour). */
    public double saturation = 0.6;
    /** Draw an outline around ghosts. */
    public boolean outline = true;
    /** Gently pulse the ghost's alpha. */
    public boolean pulse = true;
    /** Above this many ghost blocks only outlines (the hull of the plan) are drawn. */
    public int maxBlocks = 4096;
    /** Show the mirrored copies while symmetry is active. */
    public boolean showMirrored = true;

    /** {@link #opacity} clamped to {@code [MIN_OPACITY, 1]}. */
    public float opacity() {
        return (float) clamp(opacity, MIN_OPACITY, 1.0);
    }

    /** {@link #saturation} clamped to {@code [0, 1]}. */
    public float saturation() {
        return (float) clamp(saturation, 0.0, 1.0);
    }

    /** {@link #maxBlocks}, never negative. */
    public int maxBlocks() {
        return Math.max(0, maxBlocks);
    }

    /** The live section of the client config (repaired when a hand-edited file says {@code "preview": null}). */
    public static PreviewSettings current() {
        final BuildingConfig config = SlateBuilding.config();
        if (config.preview == null) config.preview = new PreviewSettings();
        return config.preview;
    }

    private static double clamp(final double v, final double lo, final double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
