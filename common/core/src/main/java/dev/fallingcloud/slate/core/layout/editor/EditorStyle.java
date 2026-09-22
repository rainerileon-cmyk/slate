package dev.fallingcloud.slate.core.layout.editor;

import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The editor's look: semi-transparent chrome panels with an accent hairline on top, dashed accent
 * selection with square handles, subtle 1 px grid dots. Everything reads the theme so both skins work;
 * the chrome is deliberately distinct from the screen underneath so it never passes for part of it.
 */
final class EditorStyle {

    /** Z the chrome is drawn at: above the screen's widgets, below popups (300), tooltips and toasts. */
    static final int Z = 150;
    static final int TOOLBAR_H = 24;
    static final int PROPS_W = 186;
    static final int LAYERS_W = 150;
    static final int HANDLE_HIT = 3;

    static Theme theme() { return Theme.current(); }

    static Palette p() { return Theme.current().palette(); }

    static boolean vanilla() { return Theme.current().isVanilla(); }

    static int accent() { return p().accent(); }

    static int text() { return p().text(); }

    static int muted() { return p().textMuted(); }

    static int dim() { return p().textDim(); }

    static int panelFill() { return vanilla() ? 0xF0121212 : Colors.withAlpha(p().surface(), 0xF4); }

    static int panelBorder() { return vanilla() ? 0xFF8B8B8B : p().borderStrong(); }

    static int rowHover() { return vanilla() ? 0x30FFFFFF : p().surfaceHover(); }

    static int rowActive() { return vanilla() ? 0x50FFFFFF : p().surfaceActive(); }

    static int gridDot() { return Colors.withAlpha(vanilla() ? 0xFFFFFF : p().text(), 0x2C); }

    static int hoverOutline() { return Colors.withAlpha(accent(), 0x90); }

    static int guide() { return Colors.withAlpha(accent(), 0xC8); }

    static int bandFill() { return Colors.withAlpha(accent(), 0x28); }

    static int ghost() { return Colors.withAlpha(p().textMuted(), 0x90); }

    static int radius() { return theme().radius(); }

    /** A chrome panel: translucent surface, border, and the accent hairline that marks it as editor UI. */
    static void panel(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        if (vanilla()) {
            g.fill(x, y, x + w, y + h, panelFill());
            SlateDraw.outline(g, x, y, w, h, panelBorder(), 0);
        } else {
            SlateDraw.pixelRound(g, x, y, w, h, panelFill(), radius());
            SlateDraw.outline(g, x, y, w, h, panelBorder(), radius());
        }
        SlateDraw.hline(g, x + 1, y, w - 2, Colors.withAlpha(accent(), 0xA0));
    }

    /** Marching-ants phase (0-4); static when motion is off. */
    static int dashPhase() {
        return theme().motion() <= 0 ? 0 : (int) ((Clock.nowMs() / 90) % 5);
    }

    /** 1 px dashed rectangle (3 on, 2 off), the dashes marching with {@code phase}. */
    static void dashedRect(final GuiGraphics g, final int x, final int y, final int w, final int h, final int color, final int phase) {
        if (w <= 0 || h <= 0) return;
        if (w < 8 || h < 8) { SlateDraw.outline(g, x, y, w, h, color, 0); return; }
        final int dash = 3, period = 5;
        final int per = 2 * (w + h) - 4;
        for (int start = -(phase % period); start < per; start += period) {
            final int end = Math.min(per, start + dash);
            for (int k = Math.max(0, start); k < end; k++) {
                final int px, py;
                if (k < w) { px = x + k; py = y; }
                else if (k < w + h - 1) { px = x + w - 1; py = y + (k - w) + 1; }
                else if (k < 2 * w + h - 2) { px = x + w - 2 - (k - (w + h - 1)); py = y + h - 1; }
                else { px = x; py = y + h - 2 - (k - (2 * w + h - 2)); }
                g.fill(px, py, px + 1, py + 1, color);
            }
        }
    }

    /** The 8 resize handle centres: NW, N, NE, E, SE, S, SW, W. */
    static int[][] handlePoints(final Rect r) {
        final int l = r.x(), t = r.y(), rr = r.right() - 1, b = r.bottom() - 1, cx = r.x() + r.w() / 2, cy = r.y() + r.h() / 2;
        return new int[][] { { l, t }, { cx, t }, { rr, t }, { rr, cy }, { rr, b }, { cx, b }, { l, b }, { l, cy } };
    }

    static void handles(final GuiGraphics g, final Rect r) {
        final int outer = vanilla() ? 0xFF000000 : p().bg();
        for (final int[] pt : handlePoints(r)) {
            g.fill(pt[0] - 2, pt[1] - 2, pt[0] + 3, pt[1] + 3, outer);
            g.fill(pt[0] - 1, pt[1] - 1, pt[0] + 2, pt[1] + 2, accent());
        }
    }

    /** A small label box (element names, live geometry readouts). Returns its width. */
    static int labelPill(final GuiGraphics g, final Component text, final int x, final int y) {
        final FormattedCharSequence seq = SlateDraw.truncate(text, 180);
        final int w = SlateDraw.font().width(seq) + 8, h = 12;
        final int fill = vanilla() ? 0xF0101010 : Colors.withAlpha(p().surfaceActive(), 0xF4);
        final int r = radius() > 0 ? 2 : 0;
        SlateDraw.pixelRound(g, x, y, w, h, fill, r);
        SlateDraw.outline(g, x, y, w, h, Colors.withAlpha(accent(), 0xC0), r);
        g.drawString(SlateDraw.font(), seq, x + 4, y + 2, text(), vanilla());
        return w;
    }

    /** A 1 px dotted grid at {@code spacing}; the step doubles until the dot count is sane. */
    static void grid(final GuiGraphics g, final int w, final int h, final int spacing) {
        int step = Math.max(2, spacing);
        while ((long) (w / step) * (h / step) > 16000) step *= 2;
        final int c = gridDot();
        for (int y = 0; y <= h; y += step) {
            for (int x = 0; x <= w; x += step) g.fill(x, y, x + 1, y + 1, c);
        }
    }

    private EditorStyle() {}
}
