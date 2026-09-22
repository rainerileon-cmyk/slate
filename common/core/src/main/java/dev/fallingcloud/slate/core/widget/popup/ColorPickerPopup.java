package dev.fallingcloud.slate.core.widget.popup;

import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** HSV picker: a saturation/value square, a hue strip, and the accent presets as swatches. */
public class ColorPickerPopup implements Popup {

    private static final int SQ = 64, HUE_W = 10, PAD = 6;

    private final int x, y, w, h;
    private final float[] hsv;
    private final IntConsumer onChange;
    private int dragging;   // 0 none, 1 square, 2 hue

    public ColorPickerPopup(final int atX, final int atY, final int color, final IntConsumer onChange) {
        this.hsv = Colors.rgbToHsv(color);
        this.onChange = onChange;
        this.w = PAD * 3 + SQ + HUE_W;
        this.h = PAD * 3 + SQ + 12;
        final int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth(), sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        this.x = Math.min(atX, sw - w - 2);
        this.y = atY + h > sh - 2 ? Math.max(2, atY - h - 22) : atY;
    }

    private int sqX() { return x + PAD; }
    private int sqY() { return y + PAD; }
    private int hueX() { return x + PAD * 2 + SQ; }

    @Override
    public boolean contains(final double mx, final double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void apply() {
        onChange.accept(Colors.hsvToRgb(hsv[0], hsv[1], hsv[2]));
    }

    private void pick(final double mx, final double my) {
        if (dragging == 1) {
            hsv[1] = (float) Math.max(0, Math.min(1, (mx - sqX()) / SQ));
            hsv[2] = (float) Math.max(0, Math.min(1, 1 - (my - sqY()) / SQ));
            apply();
        } else if (dragging == 2) {
            hsv[0] = (float) Math.max(0, Math.min(0.999f, (my - sqY()) / SQ));
            apply();
        }
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        if (mx >= sqX() && mx < sqX() + SQ && my >= sqY() && my < sqY() + SQ) dragging = 1;
        else if (mx >= hueX() && mx < hueX() + HUE_W && my >= sqY() && my < sqY() + SQ) dragging = 2;
        else {
            // Preset swatches row
            final int sy = y + PAD * 2 + SQ;
            if (my >= sy && my < sy + 10) {
                final int i = (int) ((mx - x - PAD) / 12);
                if (i >= 0 && i < Palette.ACCENTS.size()) {
                    final float[] p = Colors.rgbToHsv(Palette.ACCENTS.get(i).color());
                    System.arraycopy(p, 0, hsv, 0, 3);
                    apply();
                }
            }
            return true;
        }
        pick(mx, my);
        return true;
    }

    @Override
    public boolean mouseDragged(final double mx, final double my, final int button, final double dx, final double dy) {
        if (dragging == 0) return false;
        pick(mx, my);
        return true;
    }

    @Override
    public boolean mouseReleased(final double mx, final double my, final int button) {
        dragging = 0;
        return true;
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        SlateDraw.shadow(g, x, y, w, h, 0.6f);
        SlateDraw.pixelRound(g, x, y, w, h, p.surface(), t.radius());
        SlateDraw.outline(g, x, y, w, h, p.borderStrong(), t.radius());
        // SV square in 4 px cells (cheap, pixel-looking)
        for (int j = 0; j < SQ; j += 4) {
            for (int i = 0; i < SQ; i += 4) {
                g.fill(sqX() + i, sqY() + j, sqX() + i + 4, sqY() + j + 4, Colors.hsvToRgb(hsv[0], (i + 2f) / SQ, 1 - (j + 2f) / SQ));
            }
        }
        final int cx = sqX() + Math.round(hsv[1] * SQ), cy = sqY() + Math.round((1 - hsv[2]) * SQ);
        SlateDraw.outline(g, cx - 3, cy - 3, 6, 6, 0xFFFFFFFF, 0);
        SlateDraw.outline(g, cx - 2, cy - 2, 4, 4, 0xFF000000, 0);
        // Hue strip
        for (int j = 0; j < SQ; j += 2) g.fill(hueX(), sqY() + j, hueX() + HUE_W, sqY() + j + 2, Colors.hsvToRgb((j + 1f) / SQ, 1, 1));
        final int hy = sqY() + Math.round(hsv[0] * SQ);
        g.fill(hueX() - 1, hy - 1, hueX() + HUE_W + 1, hy + 1, 0xFFFFFFFF);
        // Presets
        final int sy = y + PAD * 2 + SQ;
        for (int i = 0; i < Palette.ACCENTS.size(); i++) {
            final int sx = x + PAD + i * 12;
            if (sx + 10 > x + w - PAD) break;
            SlateDraw.pixelRound(g, sx, sy, 10, 10, Palette.ACCENTS.get(i).color(), 2);
        }
    }
}
