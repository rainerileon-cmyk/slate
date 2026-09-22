package dev.fallingcloud.slate.core.layout.ui;

/**
 * Where a layout element hangs from. An element stores an anchor + offsets; its screen position is
 * {@code anchorPoint(screen) + offset}, so layouts survive every window size.
 */
public enum Anchor {
    TOP_LEFT(0, 0), TOP(0.5f, 0), TOP_RIGHT(1, 0),
    LEFT(0, 0.5f), CENTER(0.5f, 0.5f), RIGHT(1, 0.5f),
    BOTTOM_LEFT(0, 1), BOTTOM(0.5f, 1), BOTTOM_RIGHT(1, 1);

    public final float fx, fy;

    Anchor(final float fx, final float fy) { this.fx = fx; this.fy = fy; }

    /** Screen x for an element of width {@code w} with offset {@code ox} from this anchor. */
    public int x(final int screenW, final int ox, final int w) {
        return Math.round(screenW * fx - w * fx) + ox;
    }

    public int y(final int screenH, final int oy, final int h) {
        return Math.round(screenH * fy - h * fy) + oy;
    }

    /** Inverse of {@link #x}: the offset that puts an element at screen x. */
    public int offsetX(final int screenW, final int x, final int w) {
        return x - Math.round(screenW * fx - w * fx);
    }

    public int offsetY(final int screenH, final int y, final int h) {
        return y - Math.round(screenH * fy - h * fy);
    }

    public static Anchor parse(final String s, final Anchor fallback) {
        if (s == null) return fallback;
        try { return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT)); } catch (final IllegalArgumentException e) { return fallback; }
    }

    /** The anchor nearest to a point (used by the editor to pick a sensible default when placing). */
    public static Anchor nearest(final int x, final int y, final int screenW, final int screenH) {
        final float fx = (float) x / Math.max(1, screenW), fy = (float) y / Math.max(1, screenH);
        Anchor best = CENTER;
        float bestD = Float.MAX_VALUE;
        for (final Anchor a : values()) {
            final float d = (a.fx - fx) * (a.fx - fx) + (a.fy - fy) * (a.fy - fy);
            if (d < bestD) { bestD = d; best = a; }
        }
        return best;
    }
}
