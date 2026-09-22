package dev.fallingcloud.slate.core.widget.popup;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * HSV picker: a saturation/value square, a hue strip, the accent presets as swatches, and a readout of
 * the picked colour next to the one the picker opened with (click the old one to revert).
 */
public class ColorPickerPopup implements Popup {

    private static final int SQ = 64, HUE_W = 10, PAD = 6, SWATCH = 10, SWATCH_GAP = 2, READOUT_H = 14;

    private final int x, y, w, h;
    private final float[] hsv;
    private final int original;
    private final IntConsumer onChange;
    private final Anim open = new Anim(0, 140, Ease.OUT_CUBIC);
    private int dragging;   // 0 none, 1 square, 2 hue
    private boolean closing;

    public ColorPickerPopup(final int atX, final int atY, final int color, final IntConsumer onChange) {
        this.hsv = Colors.rgbToHsv(color);
        this.original = color | 0xFF000000;
        this.onChange = onChange;
        this.w = PAD * 3 + SQ + HUE_W;
        this.h = PAD * 4 + SQ + swatchRows() * (SWATCH + SWATCH_GAP) - SWATCH_GAP + READOUT_H;
        final int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth(), sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        this.x = Math.max(2, Math.min(atX, sw - w - 2));
        this.y = atY + h > sh - 2 ? Math.max(2, atY - h - 24) : atY;
        this.open.snap(0);
        this.open.set(1);
    }

    private int swatchesPerRow() { return (w - PAD * 2 + SWATCH_GAP) / (SWATCH + SWATCH_GAP); }

    private int swatchRows() { return (Palette.ACCENTS.size() + swatchesPerRow() - 1) / swatchesPerRow(); }

    private int sqX() { return x + PAD; }
    private int sqY() { return y + PAD; }
    private int hueX() { return x + PAD * 2 + SQ; }
    private int swatchY() { return y + PAD * 2 + SQ; }
    private int readoutY() { return swatchY() + swatchRows() * (SWATCH + SWATCH_GAP) - SWATCH_GAP + PAD; }

    private int current() { return Colors.hsvToRgb(hsv[0], hsv[1], hsv[2]); }

    @Override
    public boolean contains(final double mx, final double my) {
        return !closing && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void apply() {
        onChange.accept(current());
    }

    private void pick(final double mx, final double my) {
        if (dragging == 1) {
            hsv[1] = (float) Math.max(0, Math.min(1, (mx - sqX()) / (SQ - 1)));
            hsv[2] = (float) Math.max(0, Math.min(1, 1 - (my - sqY()) / (SQ - 1)));
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
            final int sy = swatchY();
            final int per = swatchesPerRow();
            final int rows = swatchRows();
            if (my >= sy && my < sy + rows * (SWATCH + SWATCH_GAP)) {
                final int col = (int) ((mx - x - PAD) / (SWATCH + SWATCH_GAP));
                final int row = (int) ((my - sy) / (SWATCH + SWATCH_GAP));
                final int i = row * per + col;
                if (col >= 0 && col < per && i >= 0 && i < Palette.ACCENTS.size()) {
                    final float[] p = Colors.rgbToHsv(Palette.ACCENTS.get(i).color());
                    System.arraycopy(p, 0, hsv, 0, 3);
                    apply();
                }
            } else if (my >= readoutY() && my < readoutY() + READOUT_H && mx >= x + w - PAD - 30 && mx < x + w - PAD - 15) {
                // Click the "old" swatch to revert.
                final float[] p = Colors.rgbToHsv(original);
                System.arraycopy(p, 0, hsv, 0, 3);
                apply();
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
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        // Arrows nudge saturation/value; Shift+Up/Down nudge hue.
        final float step = 0.05f;
        switch (keyCode) {
            case 263 -> hsv[1] = Math.max(0, hsv[1] - step);
            case 262 -> hsv[1] = Math.min(1, hsv[1] + step);
            case 265 -> { if ((modifiers & 1) != 0) hsv[0] = (hsv[0] - step + 1) % 1f; else hsv[2] = Math.min(1, hsv[2] + step); }
            case 264 -> { if ((modifiers & 1) != 0) hsv[0] = (hsv[0] + step) % 1f; else hsv[2] = Math.max(0, hsv[2] - step); }
            case 257, 335 -> { Popups.close(this); return true; }
            default -> { return false; }
        }
        apply();
        return true;
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float o = Math.max(0f, Math.min(1f, open.get()));
        final int a = Math.round(255 * Math.min(1, o * 1.4f));
        if (a <= 2) return;
        g.pose().pushPose();
        g.pose().translate(0, Math.round((1 - o) * -4), 0);
        if (t.isVanilla()) {
            g.fill(x, y, x + w, y + h, Colors.withAlpha(0x000000, Math.min(a, 0xF0)));
            SlateDraw.outline(g, x, y, w, h, Colors.withAlpha(0xFFFFFF, a), 0);
        } else {
            SlateDraw.shadow(g, x, y, w, h, 0.6f * o);
            SlateDraw.pixelRound(g, x, y, w, h, Colors.withAlpha(p.surface(), a), t.radius());
            SlateDraw.outline(g, x, y, w, h, Colors.withAlpha(p.borderStrong(), a), t.radius());
        }
        // SV square in 4 px cells (cheap, pixel-looking)
        for (int j = 0; j < SQ; j += 4) {
            for (int i = 0; i < SQ; i += 4) {
                g.fill(sqX() + i, sqY() + j, sqX() + i + 4, sqY() + j + 4, Colors.withAlpha(Colors.hsvToRgb(hsv[0], (i + 2f) / SQ, 1 - (j + 2f) / SQ), a));
            }
        }
        final int cx = sqX() + Math.round(hsv[1] * (SQ - 1)), cy = sqY() + Math.round((1 - hsv[2]) * (SQ - 1));
        SlateDraw.outline(g, cx - 3, cy - 3, 7, 7, Colors.withAlpha(0xFFFFFF, a), 0);
        SlateDraw.outline(g, cx - 2, cy - 2, 5, 5, Colors.withAlpha(0x000000, a), 0);
        // Hue strip
        for (int j = 0; j < SQ; j += 2) g.fill(hueX(), sqY() + j, hueX() + HUE_W, sqY() + j + 2, Colors.withAlpha(Colors.hsvToRgb((j + 1f) / SQ, 1, 1), a));
        final int hy = sqY() + Math.round(hsv[0] * SQ);
        g.fill(hueX() - 1, hy - 1, hueX() + HUE_W + 1, hy + 1, Colors.withAlpha(0xFFFFFF, a));
        g.fill(hueX() - 1, hy - 2, hueX() + HUE_W + 1, hy - 1, Colors.withAlpha(0x000000, a));
        g.fill(hueX() - 1, hy + 1, hueX() + HUE_W + 1, hy + 2, Colors.withAlpha(0x000000, a));
        // Presets
        final int per = swatchesPerRow();
        final int cur = current();
        for (int i = 0; i < Palette.ACCENTS.size(); i++) {
            final int sx = x + PAD + (i % per) * (SWATCH + SWATCH_GAP);
            final int sy = swatchY() + (i / per) * (SWATCH + SWATCH_GAP);
            final int c = Palette.ACCENTS.get(i).color();
            SlateDraw.pixelRound(g, sx, sy, SWATCH, SWATCH, Colors.withAlpha(c, a), 2);
            if ((c & 0xFFFFFF) == (cur & 0xFFFFFF)) SlateDraw.outline(g, sx - 1, sy - 1, SWATCH + 2, SWATCH + 2, Colors.withAlpha(p.text(), a), 2);
        }
        // Readout: hex + new/old swatches
        final int ry = readoutY();
        g.drawString(SlateDraw.font(), Colors.toHex(cur), x + PAD, ry + 3, Colors.withAlpha(p.text(), a), t.isVanilla());
        SlateDraw.pixelRound(g, x + w - PAD - 30, ry, 15, READOUT_H, Colors.withAlpha(original, a), 0);
        SlateDraw.pixelRound(g, x + w - PAD - 15, ry, 15, READOUT_H, Colors.withAlpha(cur, a), 0);
        SlateDraw.outline(g, x + w - PAD - 30, ry, 30, READOUT_H, Colors.withAlpha(p.borderStrong(), a), 0);
        g.pose().popPose();
    }

    @Override
    public boolean beginClose() {
        if (Theme.current().motion() <= 0) return false;
        closing = true;
        open.set(0f, 100);
        return true;
    }

    @Override
    public boolean closeFinished() { return open.get() <= 0.02f; }

    /** Narration-friendly title (unused visually). */
    public Component title() { return Component.translatable("slate.color"); }
}
