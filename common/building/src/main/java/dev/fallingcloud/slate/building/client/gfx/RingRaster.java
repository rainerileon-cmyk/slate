package dev.fallingcloud.slate.building.client.gfx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A radial menu rasterised on the GUI pixel grid: {@code count} annular sectors (slices) between radii
 * {@code rIn..rOut} with straight, parallel-sided gaps of {@code gap} px between them, plus a centre disc of radius
 * {@code rCenter}. Every pixel belongs to one slice, the disc, or nothing; each shape's 1 px edge is the set of its
 * pixels with a 4-neighbour outside it, so fills and outlines match pixel for pixel (the Slate "modern but pixel"
 * look, like {@code SlateDraw.pixelRound} + {@code outline}).
 *
 * <p>The result is stored as horizontal runs per shape (relative to the centre pixel), so drawing is a handful of
 * spans per row. Instances are immutable and cached by geometry ({@link #of}); rasterising is only needed when the
 * geometry changes (resize, slice count), never per frame.</p>
 *
 * <p>Angles: slice 0 is centred straight up, slices continue clockwise (screen space, y down).</p>
 */
public final class RingRaster {

    /** Id of the centre disc in {@link #draw}. */
    public static final int CENTER = -2;

    private static final int CACHE = 8;
    private static final Map<Key, RingRaster> CACHED = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<Key, RingRaster> eldest) {
            return size() > CACHE;
        }
    };

    private record Key(int rIn, int rOut, int count, int gap, int rCenter) {}

    private final Key key;
    /** Per slice (index count = centre): runs as {y, x0, x1} triples. */
    private final int[][] body;
    private final int[][] edge;
    private final int[][] rim;

    private RingRaster(final Key key) {
        this.key = key;
        final int n = key.count;
        final int r = Math.max(key.rOut, key.rCenter);
        final int size = 2 * r + 1;
        final int[] ids = new int[size * size];
        final float step = n > 0 ? (float) (Math.PI * 2 / n) : 0f;
        final float start = (float) (-Math.PI / 2);
        final float outer2 = (key.rOut + 0.5f) * (key.rOut + 0.5f);
        final float inner2 = (key.rIn - 0.5f) * (key.rIn - 0.5f);
        final float centre2 = (key.rCenter + 0.5f) * (key.rCenter + 0.5f);
        final float halfGap = key.gap / 2f;
        for (int y = -r; y <= r; y++) {
            for (int x = -r; x <= r; x++) {
                final float d2 = x * x + y * y;
                int id = -1;
                if (key.rCenter > 0 && d2 <= centre2) {
                    id = n;
                } else if (n > 0 && d2 <= outer2 && d2 > inner2) {
                    id = sliceAt(x, y, n, step, start, halfGap);
                }
                ids[(y + r) * size + (x + r)] = id;
            }
        }
        this.body = new int[n + 1][];
        this.edge = new int[n + 1][];
        this.rim = new int[n + 1][];
        // Pixel classes: 0 body, 1 edge (a 4-neighbour is outside the shape), 2 rim (a 4-neighbour is edge).
        final byte[] cls = new byte[size * size];
        for (int y = -r; y <= r; y++) {
            for (int x = -r; x <= r; x++) {
                final int id = ids[(y + r) * size + (x + r)];
                if (id < 0) continue;
                if (at(ids, size, r, x - 1, y) != id || at(ids, size, r, x + 1, y) != id
                    || at(ids, size, r, x, y - 1) != id || at(ids, size, r, x, y + 1) != id) cls[(y + r) * size + (x + r)] = 1;
            }
        }
        for (int y = -r; y <= r; y++) {
            for (int x = -r; x <= r; x++) {
                final int i = (y + r) * size + (x + r);
                if (ids[i] < 0 || cls[i] != 0) continue;
                if (cls(cls, size, r, x - 1, y) == 1 || cls(cls, size, r, x + 1, y) == 1
                    || cls(cls, size, r, x, y - 1) == 1 || cls(cls, size, r, x, y + 1) == 1) cls[i] = 2;
            }
        }
        final List<List<List<Integer>>> runs = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            final List<List<Integer>> perId = new ArrayList<>();
            for (int i = 0; i <= n; i++) perId.add(new ArrayList<>());
            runs.add(perId);
        }
        for (int y = -r; y <= r; y++) {
            int runId = -1, runCls = 0, runStart = 0;
            for (int x = -r; x <= r + 1; x++) {
                int id = -1, c = 0;
                if (x <= r) {
                    id = ids[(y + r) * size + (x + r)];
                    c = cls[(y + r) * size + (x + r)];
                }
                if (id != runId || c != runCls) {
                    if (runId >= 0) {
                        final List<Integer> list = runs.get(runCls).get(runId);
                        list.add(y);
                        list.add(runStart);
                        list.add(x - 1);
                    }
                    runId = id;
                    runCls = c;
                    runStart = x;
                }
            }
        }
        for (int i = 0; i <= n; i++) {
            body[i] = toArray(runs.get(0).get(i));
            edge[i] = toArray(runs.get(1).get(i));
            rim[i] = toArray(runs.get(2).get(i));
        }
    }

    private static int cls(final byte[] cls, final int size, final int r, final int x, final int y) {
        if (x < -r || x > r || y < -r || y > r) return -1;
        return cls[(y + r) * size + (x + r)];
    }

    private static int sliceAt(final int x, final int y, final int n, final float step, final float start, final float halfGap) {
        float a = (float) Math.atan2(y, x) - start + step / 2f;
        final float tau = (float) (Math.PI * 2);
        a = ((a % tau) + tau) % tau;
        int i = (int) (a / step);
        if (i >= n) i = n - 1;
        if (n == 1 || halfGap <= 0) return i;
        // Parallel-sided gaps: the perpendicular distance to both bounding rays must clear half the gap.
        final float b0 = start - step / 2f + i * step;
        final float b1 = b0 + step;
        final float d0 = Math.abs(x * (float) Math.sin(b0) - y * (float) Math.cos(b0));
        final float d1 = Math.abs(x * (float) Math.sin(b1) - y * (float) Math.cos(b1));
        return d0 < halfGap || d1 < halfGap ? -1 : i;
    }

    private static int at(final int[] ids, final int size, final int r, final int x, final int y) {
        if (x < -r || x > r || y < -r || y > r) return -1;
        return ids[(y + r) * size + (x + r)];
    }

    private static int[] toArray(final List<Integer> list) {
        final int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    /**
     * The raster for this geometry (cached).
     *
     * @param rIn     inner radius of the ring (px)
     * @param rOut    outer radius of the ring (px)
     * @param count   number of slices (0 = only the disc)
     * @param gap     gap between slices (px)
     * @param rCenter radius of the centre disc (0 = none)
     */
    public static synchronized RingRaster of(final int rIn, final int rOut, final int count, final int gap, final int rCenter) {
        final Key k = new Key(Math.max(0, rIn), Math.max(rIn, rOut), Math.max(0, count), Math.max(0, gap), Math.max(0, rCenter));
        return CACHED.computeIfAbsent(k, RingRaster::new);
    }

    public int count() { return key.count; }

    public int rIn() { return key.rIn; }

    public int rOut() { return key.rOut; }

    public int rCenter() { return key.rCenter; }

    private int index(final int id) {
        return id == CENTER ? key.count : id;
    }

    /**
     * Draws shape {@code id} (a slice index or {@link #CENTER}) centred on pixel (cx, cy): its body in
     * {@code bodyColor} and its 1 px edge in {@code edgeColor} (0 = no edge; the body colour fills it then). The
     * inner rim (the pixels just inside the edge) is part of the body here; {@link #drawRim} recolours it.
     */
    public void draw(final PixelCanvas c, final int id, final int cx, final int cy, final int bodyColor, final int edgeColor) {
        final int i = index(id);
        if (i < 0 || i > key.count) return;
        spans(c, body[i], cx, cy, bodyColor);
        spans(c, rim[i], cx, cy, bodyColor);
        spans(c, edge[i], cx, cy, edgeColor == 0 ? bodyColor : edgeColor);
    }

    /** Draws only the edge of shape {@code id}. */
    public void drawEdge(final PixelCanvas c, final int id, final int cx, final int cy, final int edgeColor) {
        final int i = index(id);
        if (i < 0 || i > key.count) return;
        spans(c, edge[i], cx, cy, edgeColor);
    }

    /** Draws the inner rim of shape {@code id} (a bevel / glow line just inside its edge). */
    public void drawRim(final PixelCanvas c, final int id, final int cx, final int cy, final int rimColor) {
        final int i = index(id);
        if (i < 0 || i > key.count) return;
        spans(c, rim[i], cx, cy, rimColor);
    }

    private static void spans(final PixelCanvas c, final int[] runs, final int cx, final int cy, final int color) {
        for (int k = 0; k < runs.length; k += 3) c.span(cy + runs[k], cx + runs[k + 1], cx + runs[k + 2], color);
    }

    /** Draws the inside of shape {@code id} (body and rim, not the edge) with a tiled texture tinted {@code tint}. */
    public void drawTextured(final PixelCanvas.Textured t, final int id, final int cx, final int cy, final int tint) {
        final int i = index(id);
        if (i < 0 || i > key.count) return;
        for (final int[] runs : new int[][] {body[i], rim[i]}) {
            for (int k = 0; k < runs.length; k += 3) {
                final int y = cy + runs[k], x0 = cx + runs[k + 1], x1 = cx + runs[k + 2];
                t.rect(x0, y, x1 + 1, y + 1, x0, y, tint);
            }
        }
    }

    /** Mid-angle of slice {@code i} in radians (screen space: 0 = right, clockwise positive). */
    public static float sliceAngle(final int i, final int count) {
        if (count <= 0) return (float) (-Math.PI / 2);
        return (float) (-Math.PI / 2 + i * (Math.PI * 2 / count));
    }

    /** The slice under an offset (dx, dy) from the centre by angle alone (ignores radii and gaps). */
    public static int sliceAtAngle(final double dx, final double dy, final int count) {
        if (count <= 0) return -1;
        final double step = Math.PI * 2 / count;
        double a = Math.atan2(dy, dx) + Math.PI / 2 + step / 2;
        final double tau = Math.PI * 2;
        a = ((a % tau) + tau) % tau;
        return Math.min(count - 1, (int) (a / step));
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof RingRaster other && other.key.equals(key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
