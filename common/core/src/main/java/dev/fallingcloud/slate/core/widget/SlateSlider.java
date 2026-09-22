package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A slider over a double range with an optional step and a value formatter. Label left, value right,
 * track underneath (dark) or the vanilla slider sprite (vanilla). Arrow keys nudge by one step,
 * Home/End jump to the ends. {@code onChange} fires continuously; {@link #onCommit} once per drag.
 */
public class SlateSlider extends SlateWidget {

    private static final int KNOB = 8, TRACK_H = 4;

    private final double min, max, step;
    private double value;
    private final DoubleFunction<String> format;
    private final DoubleConsumer onChange;
    private DoubleConsumer onCommit;
    private boolean dragging;
    private boolean compact;
    private boolean dirty;

    public SlateSlider(final int x, final int y, final int width, final Component label, final double min, final double max,
                       final double step, final double value, final DoubleFunction<String> format, final DoubleConsumer onChange) {
        super(x, y, width, 30, label);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = clamp(value);
        this.format = format != null ? format : v -> step >= 1 ? Integer.toString((int) Math.round(v)) : "%.2f".formatted(v);
        this.onChange = onChange;
    }

    /** Single-line: label + value on top of the track (height 20). Default is two rows (height 30). */
    public SlateSlider compact(final boolean compact) { this.compact = compact; setHeight(compact ? 20 : 30); return this; }

    /** Called once when a drag ends or a key changed the value (save-on-release for expensive settings). */
    public SlateSlider onCommit(final DoubleConsumer c) { this.onCommit = c; return this; }

    public double value() { return value; }

    public SlateSlider setValue(final double v) { value = clamp(v); return this; }

    public double min() { return min; }

    public double max() { return max; }

    private double clamp(double v) {
        if (step > 0) v = Math.round((v - min) / step) * step + min;
        return Mth.clamp(v, min, max);
    }

    private float fraction() { return max <= min ? 0 : (float) ((value - min) / (max - min)); }

    private void change(final double nv) {
        if (nv == value) return;
        value = nv;
        dirty = true;
        if (onChange != null) onChange.accept(value);
    }

    private void commit() {
        if (!dirty) return;
        dirty = false;
        if (onCommit != null) onCommit.accept(value);
    }

    private void setFromMouse(final double mouseX) {
        final int tx = getX(), tw = getWidth();
        final double f = Mth.clamp((mouseX - tx - KNOB / 2.0) / Math.max(1, tw - KNOB), 0, 1);
        change(clamp(min + f * (max - min)));
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
        commit();
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible) return false;
        final double s = step > 0 ? step : (max - min) / 100.0;
        switch (keyCode) {
            case 263 -> change(clamp(value - s));      // left
            case 262 -> change(clamp(value + s));      // right
            case 268 -> change(min);                   // home
            case 269 -> change(max);                   // end
            default -> { return false; }
        }
        commit();
        return true;
    }

    @Override
    public void playDownSound(final net.minecraft.client.sounds.SoundManager handler) {}

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        final int fg = Colors.scaleAlpha(this.active ? p.text() : p.textDim(), a);
        final String val = format.apply(value);
        final int valW = SlateDraw.width(val);
        // Two rows: text row (9 px) at the top, track centred in the remaining space; compact: text in the top 10 px.
        final int trackY = compact ? y + h - TRACK_H - 3 : y + 10 + (h - 10 - TRACK_H) / 2;
        final int textY = compact ? y : y + 1;
        g.drawString(SlateDraw.font(), SlateDraw.truncate(getMessage(), w - valW - 8), x, textY, fg, false);
        g.drawString(SlateDraw.font(), val, x + w - valW, textY, Colors.scaleAlpha(this.active ? p.textMuted() : p.textDim(), a), false);
        // Track + fill + knob
        final int fillW = KNOB / 2 + Math.round((w - KNOB) * fraction());
        SlateDraw.pixelRound(g, x, trackY, w, TRACK_H, Colors.scaleAlpha(this.active ? p.surfaceActive() : Colors.withAlpha(p.surfaceActive(), 0x80), a), 1);
        final int fillColor = this.active ? Colors.lerp(p.accent(), p.accentHover(), hover()) : p.textDim();
        SlateDraw.pixelRound(g, x, trackY, Math.max(KNOB / 2, fillW), TRACK_H, Colors.scaleAlpha(fillColor, a), 1);
        final int ks = KNOB + (this.active ? Math.round(2 * Math.max(hover(), press())) : 0);
        final int kcx = x + fillW;
        SlateDraw.pixelRound(g, kcx - ks / 2, trackY + TRACK_H / 2 - ks / 2, ks, ks, Colors.scaleAlpha(this.active ? p.text() : p.textMuted(), a), 2);
        if (dragging || press() > 0.01f) SlateDraw.outline(g, kcx - ks / 2, trackY + TRACK_H / 2 - ks / 2, ks, ks, Colors.scaleAlpha(p.accent(), a), 2);
        SlateDraw.focusRing(g, x, trackY - 2, w, TRACK_H + 4, focus() * a);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final int x = getX(), y = getY() + enterOffset(), w = getWidth();
        final int sh = 20;
        final int sy = compact ? y : y + (getHeight() - sh) / 2;
        final float lift = this.active ? Math.max(hover(), focus()) : 0f;
        final int kx = x + Math.round((w - KNOB) * fraction());
        SlateDraw.vanillaSlider(g, x, sy, w, sh, kx, lift, this.active, a);
        final String val = format.apply(value);
        final int fg = Colors.scaleAlpha(this.active ? 0xFFFFFFFF : 0xFFA0A0A0, a);
        final Component text = Component.empty().append(getMessage()).append(": " + val);
        SlateDraw.drawScrollingTextCentered(g, text, x + 2, SlateDraw.textY(sy, sh), w - 4, fg, true);
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.slider",
            Component.empty().append(getMessage()).append(": " + format.apply(value))));
        if (this.active) {
            out.add(NarratedElementType.USAGE, Component.translatable(this.isFocused() ? "narration.slider.usage.focused" : "narration.slider.usage.hovered"));
        }
    }
}
