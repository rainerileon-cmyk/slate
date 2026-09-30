package dev.fallingcloud.slate.core.stage;

/**
 * How a finished scene is put on screen: what makes a stage look like a shot from a trailer instead of a viewport.
 * Bright things glow ({@link #bloom}), colours are a little fuller and warmer, the corners fall off into shade.
 * A stage without a finish is shown exactly as it was rendered.
 *
 * <p>Mutable on purpose: a screen may ease a value (the glow of a hovered thing, a fade) from frame to frame.</p>
 */
public final class StageFinish {

    /** How much of the blurred bright parts is added back (0 = no glow). */
    public float bloom = 0.42f;
    /** How bright something must be to glow (0..1). */
    public float threshold = 0.8f;
    /** How wide the glow spreads, in steps of the blur. */
    public float spread = 1.6f;
    /** How dark the corners get (0 = none). */
    public float vignette = 0.34f;
    /** 1 = as rendered. */
    public float contrast = 1.07f;
    /** 1 = as rendered. */
    public float saturation = 1.12f;
    /** A shift towards warm light (red up, blue down); 0 = none. */
    public float warmth = 0.012f;

    /** The look of the Overhaul layout's scenes. */
    public static StageFinish cinematic() {
        return new StageFinish();
    }

    /** Glow only: for small stages inside a panel, where dark corners would read as dirt. */
    public static StageFinish glow() {
        final StageFinish f = new StageFinish();
        f.vignette = 0f;
        f.bloom = 0.34f;
        return f;
    }

    public StageFinish bloom(final float amount) { this.bloom = amount; return this; }

    public StageFinish vignette(final float amount) { this.vignette = amount; return this; }

    public StageFinish warmth(final float amount) { this.warmth = amount; return this; }
}
