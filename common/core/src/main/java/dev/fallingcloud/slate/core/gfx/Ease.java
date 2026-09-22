package dev.fallingcloud.slate.core.gfx;

/** Easing curves, t in 0..1. */
public enum Ease {
    LINEAR, OUT_CUBIC, IN_OUT_CUBIC, OUT_QUINT, OUT_BACK, OUT_EXPO, SPRING;

    public float apply(final float t) {
        final float x = t <= 0 ? 0 : Math.min(1, t);
        return switch (this) {
            case LINEAR -> x;
            case OUT_CUBIC -> 1 - (1 - x) * (1 - x) * (1 - x);
            case IN_OUT_CUBIC -> x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
            case OUT_QUINT -> 1 - (float) Math.pow(1 - x, 5);
            case OUT_BACK -> {
                final float c1 = 1.70158f, c3 = c1 + 1;
                yield 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
            }
            case OUT_EXPO -> x >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * x);
            case SPRING -> {
                // Lightly damped: overshoots ~8% once, settles by t=1.
                final float d = 1 - x;
                yield 1 - d * d * (float) Math.cos(x * Math.PI * 1.5f);
            }
        };
    }
}
