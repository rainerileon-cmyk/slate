package dev.fallingcloud.slate.core.layout.ui;

/** An integer rectangle with the small set of operations UI layout needs. */
public record Rect(int x, int y, int w, int h) {

    public static Rect of(final int x, final int y, final int w, final int h) { return new Rect(x, y, w, h); }

    public int right() { return x + w; }
    public int bottom() { return y + h; }
    public int centerX() { return x + w / 2; }
    public int centerY() { return y + h / 2; }

    public boolean contains(final double px, final double py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    public Rect inset(final int all) { return new Rect(x + all, y + all, w - all * 2, h - all * 2); }

    public Rect inset(final int horizontal, final int vertical) {
        return new Rect(x + horizontal, y + vertical, w - horizontal * 2, h - vertical * 2);
    }

    public Rect withX(final int nx) { return new Rect(nx, y, w, h); }
    public Rect withY(final int ny) { return new Rect(x, ny, w, h); }
    public Rect withW(final int nw) { return new Rect(x, y, nw, h); }
    public Rect withH(final int nh) { return new Rect(x, y, w, nh); }
    public Rect move(final int dx, final int dy) { return new Rect(x + dx, y + dy, w, h); }

    /** Slice {@code px} off the top; returns {top, rest}. */
    public Rect[] splitTop(final int px) {
        return new Rect[] { new Rect(x, y, w, px), new Rect(x, y + px, w, h - px) };
    }

    public Rect[] splitLeft(final int px) {
        return new Rect[] { new Rect(x, y, px, h), new Rect(x + px, y, w - px, h) };
    }

    public Rect[] splitBottom(final int px) {
        return new Rect[] { new Rect(x, y + h - px, w, px), new Rect(x, y, w, h - px) };
    }

    /** A {@code w x h} box centred in this rect. */
    public Rect centered(final int cw, final int ch) {
        return new Rect(x + (w - cw) / 2, y + (h - ch) / 2, cw, ch);
    }

    public Rect intersect(final Rect o) {
        final int nx = Math.max(x, o.x), ny = Math.max(y, o.y);
        final int nr = Math.min(right(), o.right()), nb = Math.min(bottom(), o.bottom());
        return new Rect(nx, ny, Math.max(0, nr - nx), Math.max(0, nb - ny));
    }
}
