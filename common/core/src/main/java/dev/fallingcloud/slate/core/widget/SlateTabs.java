package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** A horizontal tab bar with an animated underline (dark) or raised tab buttons (vanilla). Height 22. */
public class SlateTabs extends SlateWidget {

    public record Tab(Component label, @Nullable Icon icon, int badge) {
        public Tab(final Component label) { this(label, null, 0); }
        public Tab(final Component label, final Icon icon) { this(label, icon, 0); }
    }

    private final List<Tab> tabs = new ArrayList<>();
    private int index;
    private final IntConsumer onChange;
    private final Anim slide = new Anim(0, 200, Ease.OUT_CUBIC);
    private boolean stretch = true;

    public SlateTabs(final int x, final int y, final int width, final List<Tab> tabs, final int selected, final IntConsumer onChange) {
        super(x, y, width, 22, Component.empty());
        this.tabs.addAll(tabs);
        this.index = Math.max(0, Math.min(tabs.size() - 1, selected));
        this.slide.snap(index);
        this.onChange = onChange;
    }

    /** Tabs sized to their labels instead of sharing the width. */
    public SlateTabs compact() { this.stretch = false; return this; }

    public int index() { return index; }

    public void setBadge(final int i, final int count) {
        if (i >= 0 && i < tabs.size()) tabs.set(i, new Tab(tabs.get(i).label, tabs.get(i).icon, count));
    }

    public void select(final int i) {
        if (i < 0 || i >= tabs.size() || i == index) return;
        index = i;
        slide.set(i);
        SlateSounds.tick();
        if (onChange != null) onChange.accept(i);
    }

    private int tabWidth(final int i) {
        if (stretch) return getWidth() / Math.max(1, tabs.size());
        final Tab t = tabs.get(i);
        return SlateDraw.width(t.label) + 16 + (t.icon != null ? 14 : 0) + (t.badge > 0 ? 16 : 0);
    }

    private int tabX(final int i) {
        int x = getX();
        for (int k = 0; k < i; k++) x += tabWidth(k);
        return x;
    }

    private int tabAt(final double mx) {
        for (int i = 0; i < tabs.size(); i++) {
            final int tx = tabX(i);
            if (mx >= tx && mx < tx + tabWidth(i)) return i;
        }
        return -1;
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        select(tabAt(mouseX));
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible) return false;
        if (keyCode == 263) { select(index - 1); return true; }
        if (keyCode == 262) { select(index + 1); return true; }
        return false;
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        final int y = getY() + enterOffset(), h = getHeight();
        final int hov = this.isHovered() ? tabAt(mouseX) : -1;
        SlateDraw.hline(g, getX(), y + h - 1, getWidth(), Colors.scaleAlpha(p.border(), a));
        for (int i = 0; i < tabs.size(); i++) {
            final Tab tab = tabs.get(i);
            final int tx = tabX(i), tw = tabWidth(i);
            final int fg = i == index ? p.text() : i == hov ? p.textMuted() : p.textDim();
            if (i == hov && i != index) SlateDraw.pixelRound(g, tx + 2, y + 2, tw - 4, h - 6, Colors.scaleAlpha(p.surfaceHover(), a), t.radius());
            int cx = tx + (tw - (SlateDraw.width(tab.label) + (tab.icon != null ? 14 : 0) + (tab.badge > 0 ? 14 : 0))) / 2;
            if (tab.icon != null) { Icons.draw(g, tab.icon, cx, y + (h - 10) / 2 - 1, 10, Colors.scaleAlpha(fg, a)); cx += 14; }
            g.drawString(SlateDraw.font(), tab.label, cx, y + (h - 9) / 2, Colors.scaleAlpha(fg, a), false);
            if (tab.badge > 0) SlateBadge.drawCount(g, tab.badge, cx + SlateDraw.width(tab.label) + 4, y + (h - 10) / 2 - 1);
        }
        // Sliding underline
        final float s = slide.get();
        final int i0 = (int) Math.floor(s), i1 = Math.min(tabs.size() - 1, i0 + 1);
        final float f = s - i0;
        final int ux = Math.round(tabX(i0) + (tabX(i1) - tabX(i0)) * f) + 4;
        final int uw = Math.round(tabWidth(i0) + (tabWidth(i1) - tabWidth(i0)) * f) - 8;
        SlateDraw.rect(g, ux, y + h - 2, uw, 2, Colors.scaleAlpha(p.accent(), a));
        SlateDraw.focusRing(g, tabX(index), y, tabWidth(index), h - 2, focus() * a);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        final int y = getY() + enterOffset(), h = getHeight();
        final int hov = this.isHovered() ? tabAt(mouseX) : -1;
        for (int i = 0; i < tabs.size(); i++) {
            final Tab tab = tabs.get(i);
            final int tx = tabX(i), tw = tabWidth(i);
            final boolean sel = i == index;
            SlateDraw.vanillaButton(g, tx, y + (sel ? 0 : 2), tw, h - (sel ? 0 : 2), i == hov ? 0.7f : 0f, true, a);
            if (!sel) g.fill(tx + 1, y + 3, tx + tw - 1, y + h - 1, Colors.scaleAlpha(0x50000000, a));
            int cx = tx + (tw - (SlateDraw.width(tab.label) + (tab.icon != null ? 14 : 0))) / 2;
            final int fg = Colors.scaleAlpha(sel ? 0xFFFFFFFF : 0xFFC0C0C0, a);
            if (tab.icon != null) { Icons.draw(g, tab.icon, cx, y + (h - 10) / 2, 10, fg); cx += 14; }
            g.drawString(SlateDraw.font(), tab.label, cx, y + (h - 9) / 2 + 1, fg, true);
            if (tab.badge > 0) SlateBadge.drawCount(g, tab.badge, tx + tw - 16, y + 3);
        }
    }
}
