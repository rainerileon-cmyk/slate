package dev.fallingcloud.slate.core.layout.ui;

import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Tiny cursor-based layout: place widgets in a row or column with a gap, no measuring pass. Enough for
 * settings pages and toolbars; screens with real grids compute positions themselves.
 */
public final class Flow {

    private final boolean vertical;
    private final int gap;
    private int x, y;
    private int maxX, maxY;

    private Flow(final int x, final int y, final int gap, final boolean vertical) {
        this.x = x; this.y = y; this.gap = gap; this.vertical = vertical;
        this.maxX = x; this.maxY = y;
    }

    public static Flow column(final int x, final int y, final int gap) { return new Flow(x, y, gap, true); }

    public static Flow row(final int x, final int y, final int gap) { return new Flow(x, y, gap, false); }

    /** Positions the widget at the cursor and advances. */
    public <T extends AbstractWidget> T place(final T w) {
        w.setX(x);
        w.setY(y);
        advance(w.getWidth(), w.getHeight());
        return w;
    }

    /** Advances by a blank of the given size (spacer). */
    public Flow skip(final int px) {
        if (vertical) y += px; else x += px;
        return this;
    }

    private void advance(final int w, final int h) {
        maxX = Math.max(maxX, x + w);
        maxY = Math.max(maxY, y + h);
        if (vertical) y += h + gap; else x += w + gap;
    }

    public int x() { return x; }
    public int y() { return y; }
    /** Extent used so far (right/bottom edge). */
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
}
