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
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/** A horizontal tab bar with an animated underline (dark) or raised tab buttons (vanilla). Height 22. */
public class SlateTabs extends SlateWidget {

    public record Tab(Component label, @Nullable Icon icon, int badge) {
        public Tab(final Component label) { this(label, null, 0); }
        public Tab(final Component label, final Icon icon) { this(label, icon, 0); }
    }

    public static final int HEIGHT = 22;

    private final List<Tab> tabs = new ArrayList<>();
    private int index;
    private final IntConsumer onChange;
    private final Anim slide = new Anim(0, 200, Ease.OUT_CUBIC);
    private boolean stretch = true;

    public SlateTabs(final int x, final int y, final int width, final List<Tab> tabs, final int selected, final IntConsumer onChange) {
        super(x, y, width, HEIGHT, Component.empty());
        this.tabs.addAll(tabs);
        this.index = Math.max(0, Math.min(tabs.size() - 1, selected));
        this.slide.snap(index);
        this.onChange = onChange;
    }

    /** Tabs sized to their labels instead of sharing the width. */
    public SlateTabs compact() { this.stretch = false; return this; }

    public int index() { return index; }

    public List<Tab> tabs() { return tabs; }

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

    /** Total width of the compact layout (so screens can right-align other things). */
    public int contentWidth() {
        int w = 0;
        for (int i = 0; i < tabs.size(); i++) w += tabWidth(i);
        return w;
    }

    private int badgeWidth(final int badge) {
        return badge <= 0 ? 0 : Math.max(10, SlateDraw.width(badge > 99 ? "99+" : Integer.toString(badge)) + 6) + 4;
    }

    private int tabWidth(final int i) {
        if (stretch) return getX() + (i + 1) * getWidth() / Math.max(1, tabs.size()) - tabX(i);
        final Tab t = tabs.get(i);
        return SlateDraw.width(t.label) + 16 + (t.icon != null ? 14 : 0) + badgeWidth(t.badge);
    }

    private int tabX(final int i) {
        if (stretch) return getX() + i * getWidth() / Math.max(1, tabs.size());
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
    public void playDownSound(final net.minecraft.client.sounds.SoundManager handler) {}

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible) return false;
        if (keyCode == 263) { select(index - 1); return true; }
        if (keyCode == 262) { select(index + 1); return true; }
        if (keyCode == 268) { select(0); return true; }
        if (keyCode == 269) { select(tabs.size() - 1); return true; }
        return false;
    }

    /** Draws icon + label + badge centred in the tab; shared by both skins. */
    private void drawTab(final GuiGraphics g, final Tab tab, final int tx, final int tw, final int y, final int h, final int fg, final int iconColor, final boolean shadow, final float a) {
        final int iconW = tab.icon != null ? 14 : 0;
        final int badgeW = badgeWidth(tab.badge);
        final int avail = Math.max(0, tw - 8 - iconW - badgeW);
        final FormattedCharSequence label = SlateDraw.truncate(tab.label, avail);
        final int labelW = SlateDraw.width(label);
        int cx = tx + (tw - (iconW + labelW + badgeW)) / 2;
        if (tab.icon != null) { Icons.draw(g, tab.icon, cx, y + (h - 10) / 2, 10, iconColor); cx += 14; }
        g.drawString(SlateDraw.font(), label, cx, SlateDraw.textY(y, h), fg, shadow);
        cx += labelW;
        if (tab.badge > 0) SlateBadge.drawCount(g, tab.badge, cx + 4, y + (h - 10) / 2);
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final int y = getY() + enterOffset(), h = getHeight();
        final int hov = this.isHovered() && this.active ? tabAt(mouseX) : -1;
        SlateDraw.hline(g, getX(), y + h - 1, getWidth(), Colors.scaleAlpha(p.border(), a));
        for (int i = 0; i < tabs.size(); i++) {
            final Tab tab = tabs.get(i);
            final int tx = tabX(i), tw = tabWidth(i);
            final int fg = !this.active ? p.textDim() : i == index ? p.text() : i == hov ? p.textMuted() : p.textDim();
            if (i == hov && i != index) SlateDraw.pixelRound(g, tx + 2, y + 2, tw - 4, h - 6, Colors.scaleAlpha(p.surfaceHover(), a), t.radius());
            drawTab(g, tab, tx, tw, y, h - 2, Colors.scaleAlpha(fg, a), Colors.scaleAlpha(i == index ? p.accent() : fg, a), false, a);
        }
        // Sliding underline
        final float s = Math.max(0, Math.min(tabs.size() - 1, slide.get()));
        final int i0 = (int) Math.floor(s), i1 = Math.min(tabs.size() - 1, i0 + 1);
        final float f = s - i0;
        final int ux = Math.round(tabX(i0) + (tabX(i1) - tabX(i0)) * f) + 4;
        final int uw = Math.round(tabWidth(i0) + (tabWidth(i1) - tabWidth(i0)) * f) - 8;
        SlateDraw.rect(g, ux, y + h - 2, uw, 2, Colors.scaleAlpha(this.active ? p.accent() : p.textDim(), a));
        SlateDraw.focusRing(g, tabX(index) + 1, y + 1, tabWidth(index) - 2, h - 4, focus() * a);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final int y = getY() + enterOffset(), h = getHeight();
        final int hov = this.isHovered() && this.active ? tabAt(mouseX) : -1;
        for (int i = 0; i < tabs.size(); i++) {
            final Tab tab = tabs.get(i);
            final int tx = tabX(i), tw = tabWidth(i);
            final boolean sel = i == index;
            final float lift = sel ? Math.max(0.6f, focus()) : i == hov ? 0.6f : 0f;
            SlateDraw.vanillaTab(g, tx, y + (sel ? 0 : 2), tw, h - (sel ? 0 : 2), sel, lift, a);
            final int fg = Colors.scaleAlpha(!this.active ? 0xFFA0A0A0 : sel ? 0xFFFFFFFF : 0xFFC0C0C0, a);
            drawTab(g, tab, tx, tw, y + (sel ? 0 : 2), h - (sel ? 0 : 2), fg, fg, true, a);
        }
        if (focus() > 0.5f) {
            final int uw = Math.min(SlateDraw.width(tabs.get(index).label), tabWidth(index) - 4);
            SlateDraw.rect(g, tabX(index) + (tabWidth(index) - uw) / 2, y + h - 2, uw, 1, Colors.scaleAlpha(0xFFFFFFFF, a));
        }
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {
        if (tabs.isEmpty()) return;
        out.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.tab", tabs.get(index).label));
        out.add(NarratedElementType.POSITION, Component.translatable("narrator.position.tab", index + 1, tabs.size()));
        if (this.active) {
            out.add(NarratedElementType.USAGE, Component.translatable(this.isFocused() ? "narration.slider.usage.focused" : "narration.slider.usage.hovered"));
        }
    }
}
