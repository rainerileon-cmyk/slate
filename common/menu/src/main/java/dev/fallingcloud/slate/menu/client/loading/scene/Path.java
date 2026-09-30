package dev.fallingcloud.slate.menu.client.loading.scene;

/**
 * The way a thing takes along a belt, seen from the side: from point to point, measured in blocks of belt. Where the
 * way bends, a thing on it turns over a short stretch instead of all at once.
 */
final class Path {

    /** Over how much of the way before and after a bend a thing turns. */
    private static final float BEND = 0.3f;

    private final float[] x, y, s, slope;

    /** @param points x and y of every point, in the order they are passed */
    Path(final float... points) {
        final int n = points.length / 2;
        x = new float[n];
        y = new float[n];
        s = new float[n];
        slope = new float[n - 1];
        for (int i = 0; i < n; i++) {
            x[i] = points[i * 2];
            y[i] = points[i * 2 + 1];
            if (i == 0) continue;
            final float dx = x[i] - x[i - 1], dy = y[i] - y[i - 1];
            s[i] = s[i - 1] + (float) Math.sqrt(dx * dx + dy * dy);
            slope[i - 1] = (float) Math.toDegrees(Math.atan2(dy, dx));
        }
    }

    float length() {
        return s[s.length - 1];
    }

    /** How far along the way its point number {@code i} is. */
    float to(final int i) {
        return s[i];
    }

    /** Where a thing that has come {@code along} the way is: {@code out} takes x, y and the slope under it in degrees. */
    void at(final float along, final float[] out) {
        final float a = Math.max(0f, Math.min(length(), along));
        int i = 0;
        while (i < slope.length - 1 && a > s[i + 1]) i++;
        final float span = s[i + 1] - s[i], t = span <= 0 ? 0 : (a - s[i]) / span;
        out[0] = x[i] + (x[i + 1] - x[i]) * t;
        out[1] = y[i] + (y[i + 1] - y[i]) * t;
        float turn = slope[i];
        if (i > 0 && a - s[i] < BEND) turn = mix(slope[i - 1], slope[i], 0.5f + 0.5f * (a - s[i]) / BEND);
        else if (i < slope.length - 1 && s[i + 1] - a < BEND) turn = mix(slope[i], slope[i + 1], 0.5f - 0.5f * (s[i + 1] - a) / BEND);
        out[2] = turn;
    }

    private static float mix(final float a, final float b, final float t) {
        return a + (b - a) * t;
    }
}
