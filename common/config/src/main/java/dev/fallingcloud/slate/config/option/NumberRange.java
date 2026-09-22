package dev.fallingcloud.slate.config.option;

/**
 * Bounds and step of a numeric option. A step of 0 means "continuous" (the slider picks a sensible
 * step from the span). Unbounded numbers (no range) render as a validated text field instead.
 */
public record NumberRange(double min, double max, double step) {

    public static NumberRange of(final double min, final double max, final double step) {
        return new NumberRange(min, max, step);
    }

    public static NumberRange ints(final int min, final int max) {
        return new NumberRange(min, max, 1);
    }

    public static NumberRange unit() {
        return new NumberRange(0, 1, 0.01);
    }

    /** A usable slider step: the given one, or 1/100 of the span. */
    public double effectiveStep() {
        if (step > 0) return step;
        final double span = max - min;
        return span <= 0 ? 1 : span / 100.0;
    }

    public boolean isBounded() {
        return Double.isFinite(min) && Double.isFinite(max) && max > min && (max - min) < 1.0e9;
    }

    public double clamp(final double v) {
        return Math.max(min, Math.min(max, v));
    }
}
