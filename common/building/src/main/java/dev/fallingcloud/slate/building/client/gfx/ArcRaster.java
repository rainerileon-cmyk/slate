package dev.fallingcloud.slate.building.client.gfx;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A thin ring (radii {@code r0..r1}) rasterised on the GUI pixel grid with each pixel's angle precomputed, so an
 * animated arc of it ({@link #draw} from any angle to any angle) costs one pass over a few hundred pixels per frame
 * and stays crisp while it slides. Used for the wheel's selection indicator and progress rings. Cached by radii.
 *
 * <p>Angles are radians in screen space (0 = right, clockwise positive, as {@code Math.atan2(dy, dx)}).</p>
 */
public final class ArcRaster {

    private static final Map<Long, ArcRaster> CACHED = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<Long, ArcRaster> eldest) {
            return size() > 8;
        }
    };

    /** Pixels in row-major order: x, y (relative to the centre). */
    private final int[] xy;
    /** Angle of each pixel in [0, 2π). */
    private final float[] angle;

    private ArcRaster(final int r0, final int r1) {
        final float in2 = (r0 - 0.5f) * (r0 - 0.5f), out2 = (r1 + 0.5f) * (r1 + 0.5f);
        int n = 0;
        final int[] tmpXy = new int[(2 * r1 + 1) * (2 * r1 + 1) * 2];
        final float[] tmpA = new float[(2 * r1 + 1) * (2 * r1 + 1)];
        for (int y = -r1; y <= r1; y++) {
            for (int x = -r1; x <= r1; x++) {
                final float d2 = x * x + y * y;
                if (d2 > out2 || d2 <= in2) continue;
                tmpXy[n * 2] = x;
                tmpXy[n * 2 + 1] = y;
                tmpA[n] = norm((float) Math.atan2(y, x));
                n++;
            }
        }
        this.xy = java.util.Arrays.copyOf(tmpXy, n * 2);
        this.angle = java.util.Arrays.copyOf(tmpA, n);
    }

    public static synchronized ArcRaster of(final int r0, final int r1) {
        final int a = Math.max(0, r0), b = Math.max(a, r1);
        return CACHED.computeIfAbsent(((long) a << 32) | b, k -> new ArcRaster(a, b));
    }

    private static float norm(final float a) {
        final float tau = (float) (Math.PI * 2);
        return ((a % tau) + tau) % tau;
    }

    /**
     * Draws the part of the ring between {@code from} and {@code to} (clockwise from {@code from}; a span of 2π or
     * more draws the whole ring) centred on (cx, cy). Consecutive pixels of a row are merged into spans.
     */
    public void draw(final PixelCanvas c, final int cx, final int cy, final float from, final float to, final int argb) {
        final float span = to - from;
        if (span <= 0) return;
        final boolean full = span >= Math.PI * 2 - 1e-4;
        final float start = norm(from);
        int runY = Integer.MIN_VALUE, runX0 = 0, runX1 = 0;
        for (int i = 0; i < angle.length; i++) {
            if (!full) {
                float d = angle[i] - start;
                if (d < 0) d += (float) (Math.PI * 2);
                if (d > span) continue;
            }
            final int x = xy[i * 2], y = xy[i * 2 + 1];
            if (y == runY && x == runX1 + 1) {
                runX1 = x;
                continue;
            }
            if (runY != Integer.MIN_VALUE) c.span(cy + runY, cx + runX0, cx + runX1, argb);
            runY = y;
            runX0 = runX1 = x;
        }
        if (runY != Integer.MIN_VALUE) c.span(cy + runY, cx + runX0, cx + runX1, argb);
    }

    /** The whole ring. */
    public void drawFull(final PixelCanvas c, final int cx, final int cy, final int argb) {
        draw(c, cx, cy, 0f, (float) (Math.PI * 2), argb);
    }
}
