package dev.fallingcloud.slate.menu.client.overhaul.create;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateSounds;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The tabs of the world creation screen, as its sketch draws them: a line across the screen, and the tabs hanging
 * from it side by side in the middle. The chosen one is filled and carries the accent along its foot; the fill
 * slides over when another is chosen. Left and right walk them from the keyboard.
 */
final class HangingTabs extends SlateWidget {

    static final int HEIGHT = 19;

    private final List<Component> labels;
    private final IntConsumer onChange;
    private final List<Anim> hovers = new ArrayList<>();
    private final Anim slide;
    private final int tabW, tabsX;
    private int index;

    /** @param width the length of the line; the tabs take what they need of its middle */
    HangingTabs(final int x, final int y, final int width, final List<Component> labels, final int selected, final IntConsumer onChange) {
        super(x, y, width, HEIGHT, Component.empty());
        this.labels = List.copyOf(labels);
        this.onChange = onChange;
        this.index = Mth.clamp(selected, 0, Math.max(0, labels.size() - 1));
        this.slide = new Anim(index, 220, Ease.OUT_CUBIC);
        int widest = 40;
        for (final Component c : labels) widest = Math.max(widest, SlateDraw.font().width(Fonts.heading(c)) + 22);
        final int n = Math.max(1, labels.size());
        this.tabW = Math.min(widest, Math.max(30, (width - 8) / n));
        this.tabsX = x + (width - tabW * n) / 2;
        for (int i = 0; i < n; i++) hovers.add(new Anim(0, 150, Ease.OUT_CUBIC));
        silent();
    }

    int index() { return index; }

    private int tabAt(final double mx, final double my) {
        if (my < getY() || my >= getY() + HEIGHT || mx < tabsX) return -1;
        final int i = (int) ((mx - tabsX) / tabW);
        return i >= 0 && i < labels.size() ? i : -1;
    }

    private void choose(final int i) {
        if (i < 0 || i >= labels.size() || i == index) return;
        index = i;
        slide.set(i);
        SlateSounds.tick();
        onChange.accept(i);
    }

    @Override
    public boolean isMouseOver(final double mouseX, final double mouseY) {
        return this.active && this.visible && tabAt(mouseX, mouseY) >= 0;
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        choose(tabAt(mouseX, mouseY));
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!isFocused()) return false;
        if (keyCode == 263) { choose(Math.max(0, index - 1)); return true; }
        if (keyCode == 262) { choose(Math.min(labels.size() - 1, index + 1)); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, mouseX, mouseY, false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, mouseX, mouseY, true);
    }

    private void draw(final GuiGraphics g, final int mouseX, final int mouseY, final boolean van) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final var font = SlateDraw.font();
        final int y = getY() + enterOffset(), h = HEIGHT - 1;
        final int line = van ? 0xFF6F6F6F : p.border();
        // The line they hang from, fading out towards both ends.
        final int lx = getX(), lw = getWidth();
        SlateDraw.hgradient(g, lx, y, lw / 5, 1, Colors.scaleAlpha(Colors.withAlpha(line, 0), a), Colors.scaleAlpha(line, a));
        SlateDraw.rect(g, lx + lw / 5, y, lw - 2 * (lw / 5), 1, Colors.scaleAlpha(line, a));
        SlateDraw.hgradient(g, lx + lw - lw / 5, y, lw / 5, 1, Colors.scaleAlpha(line, a), Colors.scaleAlpha(Colors.withAlpha(line, 0), a));

        final int hovered = isHovered() ? tabAt(mouseX, mouseY) : -1;
        for (int i = 0; i < labels.size(); i++) {
            final int x = tabsX + i * tabW;
            final Anim hover = hovers.get(i);
            hover.set(i == hovered && i != index);
            final float hv = hover.get();
            if (van) {
                g.fill(x + 1, y + 1, x + tabW - 1, y + h, Colors.scaleAlpha(Colors.lerp(0x60000000, 0x80303030, hv), a));
                SlateDraw.outline(g, x, y, tabW, h + 1, Colors.scaleAlpha(0xFF000000, a), 0);
            } else {
                SlateDraw.rect(g, x + 1, y + 1, tabW - 2, h - 1, Colors.scaleAlpha(Colors.lerp(Colors.withAlpha(p.bg2(), 0xC0), Colors.withAlpha(p.surfaceHover(), 0xE0), hv), a));
                SlateDraw.vline(g, x, y + 1, h - 2, Colors.scaleAlpha(line, a));
                SlateDraw.vline(g, x + tabW - 1, y + 1, h - 2, Colors.scaleAlpha(line, a));
                SlateDraw.hline(g, x + 1, y + h - 1, tabW - 2, Colors.scaleAlpha(line, a));
            }
        }
        // The chosen tab's fill, wherever it is on its way.
        final int sx = tabsX + Math.round(slide.get() * tabW);
        final int accent = van ? 0xFFFFFFFF : p.accent();
        if (van) {
            g.fill(sx + 1, y + 1, sx + tabW - 1, y + h, Colors.scaleAlpha(0x70FFFFFF, a));
        } else {
            SlateDraw.vgradient(g, sx + 1, y + 1, tabW - 2, h - 1, Colors.scaleAlpha(Colors.withAlpha(accent, 0x18), a), Colors.scaleAlpha(Colors.withAlpha(accent, 0x58), a));
        }
        SlateDraw.rect(g, sx + 1, y + h - 2, tabW - 2, 2, Colors.scaleAlpha(accent, a));

        for (int i = 0; i < labels.size(); i++) {
            final int x = tabsX + i * tabW;
            final boolean sel = i == index;
            final Component label = Fonts.heading(labels.get(i));
            final int fg = sel ? (van ? 0xFFFFFFFF : p.text()) : Colors.lerp(van ? 0xFFC0C0C0 : p.textMuted(), van ? 0xFFFFFFFF : p.text(), hovers.get(i).get());
            final var cut = SlateDraw.truncate(label, tabW - 8);
            g.drawString(font, cut, x + (tabW - font.width(cut)) / 2, y + (h - 8) / 2, Colors.scaleAlpha(fg, a), van);
        }
        if (isFocused()) SlateDraw.focusRing(g, tabsX + index * tabW, y, tabW, h, focus() * a);
    }
}
