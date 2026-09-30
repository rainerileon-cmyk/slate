package dev.fallingcloud.slate.menu.client.loading.scene;

/**
 * The one texture the scene is drawn from: the scene's own tiles ({@link Atlas}) in its top left corner, and under
 * them whatever textures were read from a mod, each with a rim of its own colours round it so that no face ever takes
 * a texel of its neighbour.
 */
final class Sheet {

    static final int SIDE = 1024;

    /** A texture on the sheet, in pixels. */
    record Region(int x, int y, int w, int h, boolean sheer) {

        /** {@code u} from 0 to 16 across the texture, as the sheet's coordinate. */
        float u(final float u) {
            return (x + u / 16f * w) / SIDE;
        }

        float v(final float v) {
            return (y + v / 16f * h) / SIDE;
        }
    }

    private final int[] px = new int[SIDE * SIDE];
    /** The shelf that is being filled: where the next texture goes, and how high the shelf has become. */
    private int nextX, shelfY = Atlas.SIDE + 1, shelfH;
    private boolean changed = true;

    Sheet() {
        final int[] own = Atlas.paint();
        for (int y = 0; y < Atlas.SIDE; y++) System.arraycopy(own, y * Atlas.SIDE, px, y * SIDE, Atlas.SIDE);
    }

    /**
     * Puts a picture on the sheet.
     *
     * @param argb its pixels, row by row from the top
     * @return where it is, or null when the sheet is full
     */
    Region add(final int[] argb, final int w, final int h) {
        if (w + 2 > SIDE) return null;
        if (nextX + w + 2 > SIDE) {
            shelfY += shelfH;
            nextX = 0;
            shelfH = 0;
        }
        if (shelfY + h + 2 > SIDE) return null;
        final int x0 = nextX + 1, y0 = shelfY + 1;
        boolean sheer = false;
        for (int y = -1; y <= h; y++) {
            for (int x = -1; x <= w; x++) {
                final int sx = Math.max(0, Math.min(w - 1, x)), sy = Math.max(0, Math.min(h - 1, y));
                final int c = argb[sy * w + sx];
                px[(y0 + y) * SIDE + x0 + x] = c;
                final int a = c >>> 24;
                if (a > 12 && a < 243) sheer = true;
            }
        }
        nextX += w + 2;
        shelfH = Math.max(shelfH, h + 2);
        changed = true;
        return new Region(x0, y0, w, h, sheer);
    }

    /** The pixels, ARGB, row by row from the top. */
    int[] pixels() {
        return px;
    }

    /** Whether something was added since this was asked the last time: the texture has to be sent again. */
    boolean takeChanged() {
        final boolean was = changed;
        changed = false;
        return was;
    }
}
