package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * A left-aligned tab strip that never runs out of room. When the tabs are wider than the strip it scrolls
 * sideways (mouse wheel, the arrow buttons at both ends, and automatically to the selected tab) and a
 * "more" button at the right end drops down a list of every tab. Complements {@link SlateTabs}, which
 * stretches or overflows.
 *
 * <p>Two looks: {@link Style#UNDERLINE} for primary tabs (sliding accent underline on the dark skin, raised
 * tab sprites on a header separator on the vanilla skin) and {@link Style#PILLS} for secondary tabs (a
 * sliding filled pill; small stone buttons on the vanilla skin). Left/Right/Home/End select when focused.
 * Every movement goes through {@link Anim}, so {@code Theme.motion()} is honoured. {@link #animateFrom}
 * lets a screen that rebuilds its widgets on a tab change keep the underline sliding from the old tab.</p>
 */
public class SlateTabStrip extends SlateWidget {

    public enum Style { UNDERLINE, PILLS }

    public static final int HEIGHT = 22, PILL_HEIGHT = 16;
    private static final int ARROW_W = 14, MORE_W = 16, PILL_GAP = 4;

    private final List<SlateTabs.Tab> tabs = new ArrayList<>();
    private final List<Anim> tabHover = new ArrayList<>();
    private int index;
    @Nullable private final IntConsumer onChange;
    private Style style = Style.UNDERLINE;
    private final Anim slide = new Anim(0, 220, Ease.OUT_CUBIC);
    private final Anim scroll = new Anim(0, 220, Ease.OUT_CUBIC);
    private final Anim leftHover = new Anim(0, 120, Ease.OUT_CUBIC);
    private final Anim rightHover = new Anim(0, 120, Ease.OUT_CUBIC);
    private final Anim moreHover = new Anim(0, 120, Ease.OUT_CUBIC);

    public SlateTabStrip(final int x, final int y, final int width, final List<SlateTabs.Tab> tabs, final int selected, @Nullable final IntConsumer onChange) {
        super(x, y, width, HEIGHT, Component.empty());
        this.tabs.addAll(tabs);
        for (int i = 0; i < tabs.size(); i++) tabHover.add(new Anim(0, 140, Ease.OUT_CUBIC));
        this.index = Math.max(0, Math.min(tabs.size() - 1, selected));
        this.onChange = onChange;
        this.slide.snap(index);
        ensureVisible(index, false);
    }

    // ------------------------------------------------------------------ configuration

    public SlateTabStrip style(final Style s) {
        this.style = s;
        this.height = s == Style.PILLS ? PILL_HEIGHT : HEIGHT;
        ensureVisible(index, false);
        return this;
    }

    public Style style() { return style; }

    public int index() { return index; }

    public List<SlateTabs.Tab> tabs() { return tabs; }

    public void setBadge(final int i, final int count) {
        if (i < 0 || i >= tabs.size()) return;
        final SlateTabs.Tab t = tabs.get(i);
        tabs.set(i, new SlateTabs.Tab(t.label(), t.icon(), count));
    }

    /** Select a tab as the user would (tick sound, callback). */
    public void select(final int i) {
        if (i < 0 || i >= tabs.size() || i == index) return;
        setIndex(i);
        SlateSounds.tick();
        if (onChange != null) onChange.accept(i);
    }

    /** Move the selection without the callback (the owner already knows). */
    public void setIndex(final int i) {
        if (i < 0 || i >= tabs.size()) return;
        index = i;
        slide.set(i);
        ensureVisible(i, true);
    }

    /**
     * Start the selection indicator at {@code fromIndex} and the scroll at {@code fromScroll}, then animate
     * both to the current tab: used when a strip is rebuilt right after a tab change.
     */
    public SlateTabStrip animateFrom(final int fromIndex, final double fromScroll) {
        if (fromIndex >= 0 && fromIndex < tabs.size()) slide.snap(fromIndex);
        slide.set(index);
        scroll.snap((float) clampScroll(fromScroll));
        ensureVisible(index, true);
        return this;
    }

    /** Current horizontal scroll in px (pass it to {@link #animateFrom} of a rebuilt strip). */
    public double scrollOffset() { return scroll.target(); }

    /** Width of all tabs laid out side by side. */
    public int contentWidth() {
        int w = 0;
        for (int i = 0; i < tabs.size(); i++) w += tabW(i) + (i > 0 ? gap() : 0);
        return w;
    }

    public boolean overflows() { return contentWidth() > getWidth(); }

    // ------------------------------------------------------------------ geometry

    private int gap() { return style == Style.PILLS ? PILL_GAP : 0; }

    private static int badgeW(final int badge) {
        return badge <= 0 ? 0 : Math.max(10, SlateDraw.width(badge > 99 ? "99+" : Integer.toString(badge)) + 6) + 4;
    }

    private int tabW(final int i) {
        final SlateTabs.Tab t = tabs.get(i);
        final boolean pills = style == Style.PILLS;
        return SlateDraw.width(t.label()) + (pills ? 14 : 18) + (t.icon() != null ? (pills ? 12 : 14) : 0) + badgeW(t.badge());
    }

    /** Left edge of tab i in content space (0 = first tab). */
    private int tabCx(final int i) {
        int x = 0;
        for (int k = 0; k < i; k++) x += tabW(k) + gap();
        return x;
    }

    private int viewX0() { return getX() + (overflows() ? ARROW_W + 2 : 0); }

    private int viewX1() { return getX() + getWidth() - (overflows() ? ARROW_W + MORE_W + 4 : 0); }

    private int viewW() { return Math.max(1, viewX1() - viewX0()); }

    private int maxScroll() { return Math.max(0, contentWidth() - viewW()); }

    private double clampScroll(final double s) { return Math.max(0, Math.min(maxScroll(), s)); }

    private int tabX(final int i) { return viewX0() + tabCx(i) - Math.round(scroll.get()); }

    private int rightArrowX() { return viewX1() + 2; }

    private int moreX() { return getX() + getWidth() - MORE_W; }

    private void ensureVisible(final int i, final boolean animate) {
        if (i < 0 || i >= tabs.size()) return;
        final int cx = tabCx(i), w = tabW(i), vw = viewW();
        double s = scroll.target();
        if (cx - 8 < s) s = cx - 8;
        else if (cx + w + 8 > s + vw) s = cx + w + 8 - vw;
        s = clampScroll(s);
        if (animate) scroll.set((float) s);
        else scroll.snap((float) s);
    }

    private int tabAt(final double mx, final double my) {
        if (my < getY() || my >= getY() + getHeight() || mx < viewX0() || mx >= viewX1()) return -1;
        for (int i = 0; i < tabs.size(); i++) {
            final int tx = tabX(i);
            if (mx >= tx && mx < tx + tabW(i)) return i;
        }
        return -1;
    }

    private boolean overLeft(final double mx) { return overflows() && mx >= getX() && mx < getX() + ARROW_W; }

    private boolean overRight(final double mx) { return overflows() && mx >= rightArrowX() && mx < rightArrowX() + ARROW_W; }

    private boolean overMore(final double mx) { return overflows() && mx >= moreX() && mx < moreX() + MORE_W; }

    // ------------------------------------------------------------------ input

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        if (overLeft(mouseX)) { scrollPage(-1); return; }
        if (overRight(mouseX)) { scrollPage(1); return; }
        if (overMore(mouseX)) { openMore(); return; }
        select(tabAt(mouseX, mouseY));
    }

    private void scrollPage(final int dir) {
        final double to = clampScroll(scroll.target() + dir * Math.max(40, viewW() * 0.6));
        if (to == scroll.target()) return;
        scroll.set((float) to);
        SlateSounds.tick();
    }

    private void openMore() {
        final List<MenuPopup.Item> items = new ArrayList<>();
        for (int i = 0; i < tabs.size(); i++) {
            final int k = i;
            items.add(MenuPopup.Item.checked(tabs.get(i).label(), i == index, () -> select(k)));
        }
        SlateSounds.tick();
        Popups.open(new MenuPopup(Math.max(2, getX() + getWidth() - 130), getY() + getHeight() + 1, items, 130));
    }

    @Override
    public void playDownSound(final net.minecraft.client.sounds.SoundManager handler) {}

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (!this.visible || !this.active || !overflows() || !isMouseOver(mouseX, mouseY)) return false;
        final double d = scrollX != 0 ? scrollX : scrollY;
        scroll.set((float) clampScroll(scroll.target() - d * 28));
        return true;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible || tabs.isEmpty()) return false;
        switch (keyCode) {
            case 263 -> select(Math.max(0, index - 1));
            case 262 -> select(Math.min(tabs.size() - 1, index + 1));
            case 268 -> select(0);
            case 269 -> select(tabs.size() - 1);
            default -> { return false; }
        }
        return true;
    }

    // ------------------------------------------------------------------ render

    private int hoveredTab(final int mouseX, final int mouseY, final GuiGraphics g) {
        return this.isHovered() && this.active && g.containsPointInScissor(mouseX, mouseY) ? tabAt(mouseX, mouseY) : -1;
    }

    /** Selection indicator position: interpolated between the two tabs the slide animation is between. */
    private int[] indicator() {
        final float s = Math.max(0, Math.min(tabs.size() - 1, slide.get()));
        final int i0 = (int) Math.floor(s), i1 = Math.min(tabs.size() - 1, i0 + 1);
        final float f = s - i0;
        final int x = Math.round(tabX(i0) + (tabX(i1) - tabX(i0)) * f);
        final int w = Math.round(tabW(i0) + (tabW(i1) - tabW(i0)) * f);
        return new int[] { x, w };
    }

    private void drawTab(final GuiGraphics g, final SlateTabs.Tab tab, final int tx, final int tw, final int y, final int h, final int fg, final int iconColor, final boolean shadow) {
        final boolean pills = style == Style.PILLS;
        final int iconSize = pills ? 8 : 10;
        final int iconW = tab.icon() != null ? iconSize + 4 : 0;
        final int badgeW = badgeW(tab.badge());
        final FormattedCharSequence label = SlateDraw.truncate(tab.label(), Math.max(0, tw - 8 - iconW - badgeW));
        final int labelW = SlateDraw.width(label);
        int cx = tx + (tw - (iconW + labelW + badgeW)) / 2;
        if (tab.icon() != null) { Icons.draw(g, tab.icon(), cx, y + (h - iconSize) / 2, iconSize, iconColor); cx += iconW; }
        g.drawString(SlateDraw.font(), label, cx, SlateDraw.textY(y, h), fg, shadow);
        cx += labelW;
        if (tab.badge() > 0) SlateBadge.drawCount(g, tab.badge(), cx + 4, y + (h - 10) / 2);
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        if (a <= 0.004f || tabs.isEmpty()) return;
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final int y = getY() + enterOffset(), h = getHeight();
        final int hov = hoveredTab(mouseX, mouseY, g);
        final boolean pills = style == Style.PILLS;
        if (!pills) SlateDraw.hline(g, getX(), y + h - 1, getWidth(), Colors.scaleAlpha(p.border(), a));
        SlateDraw.scissor(g, viewX0(), y - 1, viewW(), h + 2);
        if (pills) {
            final int[] ind = indicator();
            final int fill = Colors.lerp(p.surfaceActive(), p.accent(), 0.2f);
            for (int i = 0; i < tabs.size(); i++) {
                final Anim ha = tabHover.get(i);
                ha.set(i == hov && i != index);
                final float hv = ha.get();
                final int tx = tabX(i), tw = tabW(i);
                if (tx + tw < viewX0() || tx > viewX1()) continue;
                if (hv > 0.01f) SlateDraw.pixelRound(g, tx, y, tw, h, Colors.scaleAlpha(p.surfaceHover(), a * hv), t.radius());
            }
            SlateDraw.pixelRound(g, ind[0], y, ind[1], h, Colors.scaleAlpha(fill, a), t.radius());
            SlateDraw.outline(g, ind[0], y, ind[1], h, Colors.scaleAlpha(Colors.withAlpha(p.accent(), 0x90), a), t.radius());
            for (int i = 0; i < tabs.size(); i++) {
                final int tx = tabX(i), tw = tabW(i);
                if (tx + tw < viewX0() || tx > viewX1()) continue;
                final float hv = tabHover.get(i).get();
                final int fg = !this.active ? p.textDim() : i == index ? p.text() : Colors.lerp(p.textMuted(), p.text(), hv);
                drawTab(g, tabs.get(i), tx, tw, y, h, Colors.scaleAlpha(fg, a), Colors.scaleAlpha(i == index ? p.accent() : fg, a), false);
            }
            SlateDraw.focusRing(g, tabX(index), y, tabW(index), h, focus() * a);
        } else {
            for (int i = 0; i < tabs.size(); i++) {
                final Anim ha = tabHover.get(i);
                ha.set(i == hov && i != index);
                final float hv = ha.get();
                final int tx = tabX(i), tw = tabW(i);
                if (tx + tw < viewX0() || tx > viewX1()) continue;
                if (hv > 0.01f) SlateDraw.pixelRound(g, tx + 2, y + 2, tw - 4, h - 6, Colors.scaleAlpha(p.surfaceHover(), a * hv), t.radius());
                final int fg = !this.active ? p.textDim() : i == index ? p.text() : Colors.lerp(p.textDim(), p.textMuted(), Math.max(hv, 0.35f));
                drawTab(g, tabs.get(i), tx, tw, y, h - 2, Colors.scaleAlpha(fg, a), Colors.scaleAlpha(i == index ? p.accent() : fg, a), false);
            }
            final int[] ind = indicator();
            SlateDraw.rect(g, ind[0] + 5, y + h - 2, Math.max(2, ind[1] - 10), 2, Colors.scaleAlpha(this.active ? p.accent() : p.textDim(), a));
            SlateDraw.focusRing(g, tabX(index) + 1, y + 1, tabW(index) - 2, h - 4, focus() * a);
        }
        SlateDraw.unscissor(g);
        if (overflows()) {
            final int s = Math.round(scroll.get());
            final int shade = Colors.scaleAlpha(Colors.withAlpha(p.bg(), 0xC0), a);
            if (s > 0) SlateDraw.hgradient(g, viewX0(), y, 12, h - (pills ? 0 : 1), shade, 0);
            if (s < maxScroll()) SlateDraw.hgradient(g, viewX1() - 12, y, 12, h - (pills ? 0 : 1), 0, shade);
            darkButton(g, getX(), y, ARROW_W, h, Icon.CHEVRON_LEFT, leftHover, overLeft(mouseX) && isHovered(), s > 0, a, p, t);
            darkButton(g, rightArrowX(), y, ARROW_W, h, Icon.CHEVRON_RIGHT, rightHover, overRight(mouseX) && isHovered(), s < maxScroll(), a, p, t);
            darkButton(g, moreX(), y, MORE_W, h, Icon.CHEVRON_DOWN, moreHover, overMore(mouseX) && isHovered(), true, a, p, t);
        }
    }

    private void darkButton(final GuiGraphics g, final int x, final int y, final int w, final int h, final Icon icon, final Anim hoverA,
                            final boolean hovered, final boolean enabled, final float a, final Palette p, final Theme t) {
        hoverA.set(hovered && enabled);
        final float hv = hoverA.get();
        final int bh = style == Style.PILLS ? h : h - 4;
        final int by = style == Style.PILLS ? y : y + 1;
        if (hv > 0.01f) SlateDraw.pixelRound(g, x, by, w, bh, Colors.scaleAlpha(p.surfaceHover(), a * hv), t.radius());
        final int c = !enabled ? Colors.withAlpha(p.textDim(), 0x70) : Colors.lerp(p.textMuted(), p.text(), hv);
        Icons.draw(g, icon, x + (w - 8) / 2, by + (bh - 8) / 2, 8, Colors.scaleAlpha(c, a));
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        if (a <= 0.004f || tabs.isEmpty()) return;
        final int y = getY() + enterOffset(), h = getHeight();
        final int hov = hoveredTab(mouseX, mouseY, g);
        final boolean pills = style == Style.PILLS;
        if (!pills) {
            g.setColor(1f, 1f, 1f, a);
            SlateDraw.vanillaSeparator(g, getX(), y + h - 2, getWidth(), true, Minecraft.getInstance().level != null);
            g.setColor(1f, 1f, 1f, 1f);
        }
        SlateDraw.scissor(g, viewX0(), y - 1, viewW(), h + 2);
        for (int i = 0; i < tabs.size(); i++) {
            final Anim ha = tabHover.get(i);
            ha.set(i == hov && i != index);
            final float hv = ha.get();
            final int tx = tabX(i), tw = tabW(i);
            if (tx + tw < viewX0() || tx > viewX1()) continue;
            final boolean sel = i == index;
            if (pills) {
                SlateDraw.vanillaButton(g, tx, y, tw, h, sel ? 1f : hv, this.active, a);
                final int fg = !this.active ? 0xFFA0A0A0 : sel ? 0xFFFFFFA0 : Colors.lerp(0xFFE0E0E0, 0xFFFFFFFF, hv);
                drawTab(g, tabs.get(i), tx, tw, y, h, Colors.scaleAlpha(fg, a), Colors.scaleAlpha(fg, a), true);
                if (sel) {
                    final int lw = Math.min(SlateDraw.width(tabs.get(i).label()), tw - 8);
                    SlateDraw.rect(g, tx + (tw - lw) / 2, y + h - 3, lw, 1, Colors.scaleAlpha(0xFFFFFFA0, a * 0.8f));
                }
            } else {
                final float lift = sel ? Math.max(0.6f, focus()) : hv * 0.6f;
                SlateDraw.vanillaTab(g, tx, y + (sel ? 0 : 2), tw, h - (sel ? 0 : 2), sel, lift, a);
                final int fg = Colors.scaleAlpha(!this.active ? 0xFFA0A0A0 : sel ? 0xFFFFFFFF : Colors.lerp(0xFFC0C0C0, 0xFFFFFFFF, hv), a);
                drawTab(g, tabs.get(i), tx, tw, y + (sel ? 0 : 2), h - (sel ? 0 : 2), fg, fg, true);
            }
        }
        if (!pills && focus() > 0.5f) {
            final int uw = Math.min(SlateDraw.width(tabs.get(index).label()), tabW(index) - 4);
            SlateDraw.rect(g, tabX(index) + (tabW(index) - uw) / 2, y + h - 4, uw, 1, Colors.scaleAlpha(0xFFFFFFFF, a));
        }
        SlateDraw.unscissor(g);
        if (overflows()) {
            final int s = Math.round(scroll.get());
            vanillaArrow(g, getX(), y, ARROW_W, h, Icon.CHEVRON_LEFT, leftHover, overLeft(mouseX) && isHovered(), s > 0, a);
            vanillaArrow(g, rightArrowX(), y, ARROW_W, h, Icon.CHEVRON_RIGHT, rightHover, overRight(mouseX) && isHovered(), s < maxScroll(), a);
            vanillaArrow(g, moreX(), y, MORE_W, h, Icon.CHEVRON_DOWN, moreHover, overMore(mouseX) && isHovered(), true, a);
        }
    }

    private void vanillaArrow(final GuiGraphics g, final int x, final int y, final int w, final int h, final Icon icon, final Anim hoverA,
                              final boolean hovered, final boolean enabled, final float a) {
        hoverA.set(hovered && enabled);
        final int bh = style == Style.PILLS ? h : h - 4;
        final int by = style == Style.PILLS ? y : y + 1;
        SlateDraw.vanillaButton(g, x, by, w, bh, hoverA.get(), enabled, a);
        Icons.draw(g, icon, x + (w - 8) / 2, by + (bh - 8) / 2, 8, Colors.scaleAlpha(enabled ? 0xFFFFFFFF : 0xFFA0A0A0, a));
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {
        if (tabs.isEmpty()) return;
        out.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.tab", tabs.get(index).label()));
        out.add(NarratedElementType.POSITION, Component.translatable("narrator.position.tab", index + 1, tabs.size()));
        if (this.active) {
            out.add(NarratedElementType.USAGE, Component.translatable(this.isFocused() ? "narration.slider.usage.focused" : "narration.slider.usage.hovered"));
        }
    }
}
