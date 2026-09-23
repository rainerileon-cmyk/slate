package dev.fallingcloud.slate.building.config;

/**
 * {@code building.json → preview}: the placement ghost (design §6).
 *
 * <p>Owner: B (render). Skeleton declares the fields and defaults of design §10.
 */
public final class PreviewSettings {

    /** Show a ghost of what the held variant would place. */
    public boolean enabled = true;
    /** Also show it for any block item, not just variants. */
    public boolean allBlocks = false;
    /** Ghost opacity, 0..1. */
    public double opacity = 0.45;
    /** Ghost colour saturation, 0 (grey) .. 1 (true colour). */
    public double saturation = 0.6;
    /** Draw an outline around ghosts. */
    public boolean outline = true;
    /** Gently pulse the ghost's alpha. */
    public boolean pulse = true;
    /** Above this many ghost blocks only outlines are drawn. */
    public int maxBlocks = 4096;
    /** Show the mirrored copies while symmetry is active. */
    public boolean showMirrored = true;
}
