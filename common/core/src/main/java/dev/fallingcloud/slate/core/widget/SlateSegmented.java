package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * A row of mutually exclusive options with a sliding highlight (2-5 options; use a dropdown for more).
 * Segments share the width exactly (leftover pixels are spread, so the last one ends on the border).
 * Single options can be {@link #disable disabled} (dimmed, skipped by clicks and the keyboard) and carry their own
 * {@link #optionTip tooltip}, so a row can say why a choice is not available.
 */
public class SlateSegmented<T> extends SlateWidget {

    private final List<T> options;
    private final Function<T, Component> labeler;
    private int index;
    private final Consumer<T> onChange;
    private final Anim slide = new Anim(0, 180, Ease.OUT_CUBIC);
    private int hoverIdx = -1;
    private Predicate<T> disabled = t -> false;
    private Function<T, Component> optionTip = t -> null;

    public SlateSegmented(final int x, final int y, final int width, final List<T> options, final T value,
                          final Function<T, Component> labeler, final Consumer<T> onChange) {
        super(x, y, width, 20, Component.empty());
        this.options = new ArrayList<>(options);
        this.labeler = labeler;
        this.index = Math.max(0, options.indexOf(value));
        this.slide.snap(index);
        this.onChange = onChange;
    }

    public T value() { return options.get(index); }

    /** Options the predicate accepts cannot be chosen (they stay visible, dimmed). */
    public SlateSegmented<T> disable(final Predicate<T> p) { this.disabled = p == null ? t -> false : p; return this; }

    /** A tooltip per option (null for none), shown while that segment is hovered; disabled options explain themselves this way. */
    public SlateSegmented<T> optionTip(final Function<T, Component> f) { this.optionTip = f == null ? t -> null : f; return this; }

    public boolean isDisabled(final T option) { return disabled.test(option); }

    public int index() { return index; }

    public SlateSegmented<T> setValue(final T v) {
        final int i = options.indexOf(v);
        if (i >= 0) { index = i; slide.set(i); }
        return this;
    }

    private int count() { return Math.max(1, options.size()); }

    /** Left edge of segment i (i == count gives the right edge of the last). */
    private int segX(final int i) { return getX() + i * getWidth() / count(); }

    private int segW(final int i) { return segX(i + 1) - segX(i); }

    private int segAt(final double mx) {
        final int i = (int) ((mx - getX()) * count() / Math.max(1, getWidth()));
        return i < 0 || i >= options.size() ? -1 : i;
    }

    private void choose(final int i) {
        if (i < 0 || i >= options.size() || i == index || disabled.test(options.get(i))) return;
        index = i;
        slide.set(i);
        SlateSounds.tick();
        if (onChange != null) onChange.accept(options.get(i));
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        choose(segAt(mouseX));
    }

    @Override
    public void playDownSound(final net.minecraft.client.sounds.SoundManager handler) {}

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible) return false;
        if (keyCode == 263) { choose(nextEnabled(index, -1)); return true; }
        if (keyCode == 262) { choose(nextEnabled(index, 1)); return true; }
        if (keyCode == 268) { choose(nextEnabled(-1, 1)); return true; }
        if (keyCode == 269) { choose(nextEnabled(options.size(), -1)); return true; }
        return false;
    }

    /** The first enabled option after {@code from} in direction {@code dir}, or -1. */
    private int nextEnabled(final int from, final int dir) {
        for (int i = from + dir; i >= 0 && i < options.size(); i += dir) if (!disabled.test(options.get(i))) return i;
        return -1;
    }

    /** Requests the hovered option's tooltip, if it has one. */
    private void hoverTip() {
        if (hoverIdx < 0) return;
        final Component tip = optionTip.apply(options.get(hoverIdx));
        if (tip != null) SlateTooltips.request(tip, this);
    }

    /** Highlight geometry between two segments for the slide animation. */
    private int[] highlightRect() {
        final float s = Math.max(0, Math.min(options.size() - 1, slide.get()));
        final int i0 = (int) Math.floor(s), i1 = Math.min(options.size() - 1, i0 + 1);
        final float f = s - i0;
        final int hx = Math.round(segX(i0) + (segX(i1) - segX(i0)) * f);
        final int hw = Math.round(segW(i0) + (segW(i1) - segW(i0)) * f);
        return new int[] { hx, hw };
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        hoverIdx = this.isHovered() && this.active ? segAt(mouseX) : -1;
        hoverTip();
        SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(p.bg2(), a), t.radius());
        SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(this.active ? p.border() : Colors.withAlpha(p.border(), 0x80), a), t.radius());
        if (hoverIdx >= 0 && hoverIdx != index && !disabled.test(options.get(hoverIdx))) {
            SlateDraw.pixelRound(g, segX(hoverIdx) + 2, y + 2, segW(hoverIdx) - 4, h - 4, Colors.scaleAlpha(p.surfaceHover(), a * 0.6f), Math.max(0, t.radius() - 1));
        }
        final int[] hl = highlightRect();
        final int inner = Math.max(0, t.radius() - 1);
        SlateDraw.pixelRound(g, hl[0] + 2, y + 2, hl[1] - 4, h - 4, Colors.scaleAlpha(this.active ? p.surfaceActive() : p.surface(), a), inner);
        SlateDraw.outline(g, hl[0] + 2, y + 2, hl[1] - 4, h - 4, Colors.scaleAlpha(this.active ? p.borderStrong() : p.border(), a), inner);
        SlateDraw.rect(g, hl[0] + 2 + inner, y + h - 3, hl[1] - 4 - inner * 2, 1, Colors.scaleAlpha(p.accent(), a * (this.active ? 1f : 0.4f)));
        for (int i = 0; i < options.size(); i++) {
            final int sx = segX(i), sw = segW(i);
            final boolean off = disabled.test(options.get(i));
            final int fg = !this.active || off ? Colors.withAlpha(p.textDim(), off ? 0xA0 : 0xFF) : i == index ? p.text() : i == hoverIdx ? p.textMuted() : p.textDim();
            final FormattedCharSequence seq = SlateDraw.truncate(labeler.apply(options.get(i)), sw - 8);
            g.drawString(SlateDraw.font(), seq, sx + (sw - SlateDraw.width(seq)) / 2, SlateDraw.textY(y, h), Colors.scaleAlpha(fg, a), false);
        }
        SlateDraw.focusRing(g, x, y, w, h, focus() * a);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final int x = getX(), y = getY() + enterOffset(), h = getHeight();
        hoverIdx = this.isHovered() && this.active ? segAt(mouseX) : -1;
        hoverTip();
        for (int i = 0; i < options.size(); i++) {
            final int sx = segX(i), sw = segW(i);
            final boolean off = disabled.test(options.get(i));
            final float lift = !this.active || off ? 0f : i == index ? 1f : i == hoverIdx ? 0.6f : (i == index && focus() > 0 ? focus() : 0f);
            SlateDraw.vanillaButton(g, sx, y, sw, h, lift, this.active && !off, a);
            if (i == index) g.fill(sx + 1, y + 1, sx + sw - 1, y + h - 1, Colors.scaleAlpha(0x40000000, a));
            final FormattedCharSequence seq = SlateDraw.truncate(labeler.apply(options.get(i)), sw - 8);
            final int fg = !this.active || off ? 0xFFA0A0A0 : i == index ? 0xFFFFFFA0 : 0xFFE0E0E0;
            g.drawString(SlateDraw.font(), seq, sx + (sw - SlateDraw.width(seq)) / 2, SlateDraw.textY(y, h), Colors.scaleAlpha(fg, a), true);
        }
        if (focus() > 0.5f) SlateDraw.outline(g, segX(index), y, segW(index), h, Colors.scaleAlpha(0xFFFFFFFF, a), 0);
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.button", labeler.apply(options.get(index))));
        if (this.active) {
            out.add(NarratedElementType.USAGE, Component.translatable(this.isFocused() ? "narration.slider.usage.focused" : "narration.slider.usage.hovered"));
        }
    }
}
