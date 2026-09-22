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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A row of mutually exclusive options with a sliding highlight (2-5 options; use a dropdown for more). */
public class SlateSegmented<T> extends SlateWidget {

    private final List<T> options;
    private final Function<T, Component> labeler;
    private int index;
    private final Consumer<T> onChange;
    private final Anim slide = new Anim(0, 180, Ease.OUT_CUBIC);
    private int hoverIdx = -1;

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

    public SlateSegmented<T> setValue(final T v) {
        final int i = options.indexOf(v);
        if (i >= 0) { index = i; slide.set(i); }
        return this;
    }

    private int segW() { return getWidth() / Math.max(1, options.size()); }

    private void choose(final int i) {
        if (i < 0 || i >= options.size() || i == index) return;
        index = i;
        slide.set(i);
        SlateSounds.tick();
        if (onChange != null) onChange.accept(options.get(i));
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        choose((int) ((mouseX - getX()) / segW()));
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible) return false;
        if (keyCode == 263) { choose(index - 1); return true; }
        if (keyCode == 262) { choose(index + 1); return true; }
        return false;
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight(), sw = segW();
        hoverIdx = this.isHovered() ? (int) ((mouseX - x) / sw) : -1;
        SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(p.bg2(), a), t.radius());
        SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(p.border(), a), t.radius());
        final int hx = x + 2 + Math.round(slide.get() * sw);
        SlateDraw.pixelRound(g, hx, y + 2, sw - 4, h - 4, Colors.scaleAlpha(p.surfaceActive(), a), Math.max(0, t.radius() - 1));
        SlateDraw.outline(g, hx, y + 2, sw - 4, h - 4, Colors.scaleAlpha(p.borderStrong(), a), Math.max(0, t.radius() - 1));
        for (int i = 0; i < options.size(); i++) {
            final int cx = x + i * sw + sw / 2;
            final int fg = i == index ? p.text() : i == hoverIdx ? p.textMuted() : p.textDim();
            final net.minecraft.util.FormattedCharSequence seq = SlateDraw.truncate(labeler.apply(options.get(i)), sw - 6);
            g.drawString(SlateDraw.font(), seq, cx - SlateDraw.font().width(seq) / 2, y + (h - 9) / 2 + 1, Colors.scaleAlpha(fg, a), false);
        }
        SlateDraw.focusRing(g, x, y, w, h, focus() * a);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        final int x = getX(), y = getY() + enterOffset(), h = getHeight(), sw = segW();
        hoverIdx = this.isHovered() ? (int) ((mouseX - x) / sw) : -1;
        for (int i = 0; i < options.size(); i++) {
            final int sx = x + i * sw;
            SlateDraw.vanillaButton(g, sx, y, sw, h, i == index ? 1f : i == hoverIdx ? 0.6f : 0f, this.active, a);
            if (i == index) g.fill(sx + 1, y + 1, sx + sw - 1, y + h - 1, Colors.scaleAlpha(0x40000000, a));
            SlateDraw.textCentered(g, labeler.apply(options.get(i)), sx + sw / 2, y + (h - 9) / 2 + 1,
                Colors.scaleAlpha(i == index ? 0xFFFFFFA0 : 0xFFE0E0E0, a));
        }
    }
}
