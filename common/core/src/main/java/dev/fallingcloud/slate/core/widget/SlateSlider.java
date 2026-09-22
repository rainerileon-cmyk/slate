package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A slider over a double range with an optional step and a value formatter. Label left, value right,
 * track underneath (dark) or the vanilla slider sprite (vanilla). Arrow keys nudge by one step.
 */
public class SlateSlider extends SlateWidget {

    private final double min, max, step;
    private double value;
    private final DoubleFunction<String> format;
    private final DoubleConsumer onChange;
    private boolean dragging;
    private boolean compact;

    public SlateSlider(final int x, final int y, final int width, final Component label, final double min, final double max,
                       final double step, final double value, final DoubleFunction<String> format, final DoubleConsumer onChange) {
        super(x, y, width, 20, label);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = clamp(value);
        this.format = format != null ? format : v -> step >= 1 ? Integer.toString((int) Math.round(v)) : "%.2f".formatted(v);
        this.onChange = onChange;
    }

    /** Single-line: label + value on top of the track (height 20). Default is two rows (height 30). */
    public SlateSlider compact(final boolean compact) { this.compact = compact; setHeight(compact ? 20 : 30); return this; }

    public double value() { return value; }

    public SlateSlider setValue(final double v) { value = clamp(v); return this; }

    private double clamp(double v) {
        if (step > 0) v = Math.round((v - min) / step) * step + min;
        return Mth.clamp(v, min, max);
    }

    private float fraction() { return max <= min ? 0 : (float) ((value - min) / (max - min)); }

    private void setFromMouse(final double mouseX) {
        final int tx = getX(), tw = getWidth();
        final double f = Mth.clamp((mouseX - tx - 4) / Math.max(1, tw - 8), 0, 1);
        final double nv = clamp(min + f * (max - min));
        if (nv != value) { value = nv; if (onChange != null) onChange.accept(value); }
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        dragging = true;
        setFromMouse(mouseX);
    }

    @Override
    protected void onDrag(final double mouseX, final double mouseY, final double dragX, final double dragY) {
        if (dragging) setFromMouse(mouseX);
    }

    @Override
    public void onRelease(final double mouseX, final double mouseY) {
        super.onRelease(mouseX, mouseY);
        dragging = false;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible) return false;
        final double s = step > 0 ? step : (max - min) / 100.0;
        if (keyCode == 263) { setValue(value - s); if (onChange != null) onChange.accept(value); return true; }
        if (keyCode == 262) { setValue(value + s); if (onChange != null) onChange.accept(value); return true; }
        return false;
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        final int fg = Colors.scaleAlpha(this.active ? p.text() : p.textDim(), a);
        final String val = format.apply(value);
        final int trackY = compact ? y + h - 6 : y + 20;
        final int textY = compact ? y + 1 : y + 2;
        g.drawString(SlateDraw.font(), SlateDraw.truncate(getMessage(), w - SlateDraw.width(val) - 8), x, textY, fg, false);
        g.drawString(SlateDraw.font(), val, x + w - SlateDraw.width(val), textY, Colors.scaleAlpha(p.textMuted(), a), false);
        // Track + fill + knob
        final int th = 4;
        SlateDraw.pixelRound(g, x, trackY, w, th, Colors.scaleAlpha(p.surfaceActive(), a), 1);
        final int fillW = Math.round((w - 8) * fraction()) + 4;
        SlateDraw.pixelRound(g, x, trackY, Math.max(4, fillW), th, Colors.scaleAlpha(Colors.lerp(p.accent(), p.accentHover(), hover()), a), 1);
        final int kx = x + fillW - 4;
        final int ks = 8 + Math.round(2 * Math.max(hover(), press()));
        SlateDraw.pixelRound(g, kx + 4 - ks / 2, trackY + th / 2 - ks / 2, ks, ks, Colors.scaleAlpha(p.text(), a), 2);
        SlateDraw.focusRing(g, x, trackY, w, th, focus() * a);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        final int x = getX(), y = getY() + enterOffset(), w = getWidth();
        final int sh = 20;
        final int sy = compact ? y : y + 10;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.setColor(1, 1, 1, a);
        g.blitSprite(hover() > 0.5f || focus() > 0.5f ? SlateDraw.SLIDER_HIGHLIGHTED : SlateDraw.SLIDER, x, sy, w, sh);
        final int kx = x + Math.round((w - 8) * fraction());
        g.blitSprite(hover() > 0.5f || focus() > 0.5f ? SlateDraw.SLIDER_HANDLE_HIGHLIGHTED : SlateDraw.SLIDER_HANDLE, kx, sy, 8, sh);
        g.setColor(1, 1, 1, 1);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        final String val = format.apply(value);
        final int fg = Colors.scaleAlpha(this.active ? 0xFFFFFFFF : 0xFFA0A0A0, a);
        final Component text = Component.literal(getMessage().getString() + ": " + val);
        SlateDraw.textCentered(g, text, x + w / 2, sy + 6, fg);
    }
}
