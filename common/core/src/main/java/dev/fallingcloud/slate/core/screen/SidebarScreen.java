package dev.fallingcloud.slate.core.screen;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateBadge;
import dev.fallingcloud.slate.core.widget.SlateSounds;
import dev.fallingcloud.slate.core.widget.SlateTabStrip;
import dev.fallingcloud.slate.core.widget.SlateTooltips;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A screen with a left navigation column and a page area. The nav collapses to icons when the window is
 * narrow. Pages are {@link SidebarPage}s; switching rebuilds only the page widgets, with an entrance
 * animation. The page title sits at the top of the page area in the heading font, with room on the
 * right for page-level actions ({@link #addPageAction}). Ctrl+Tab / Ctrl+Shift+Tab and Ctrl+1..9 switch
 * pages; the last page is remembered per screen key. Used by the Config hub, the Multiplayer hub and
 * Menu's options.
 */
public abstract class SidebarScreen extends SlateScreen {

    public static final int NAV_W = 118, NAV_W_NARROW = 30, ROW_H = 20, NAV_TOP = 6;
    /** Height of the page title row (title + page actions) above the page area. */
    public static final int PAGE_TITLE_H = 22;

    /** Height of the tab strip in {@link #topNav() top-nav} mode (the strip plus its breathing room). */
    public static final int TOP_NAV_H = 30;

    private final List<SidebarPage> pages = new ArrayList<>();
    private final List<AbstractWidget> pageWidgets = new ArrayList<>();
    private final List<AbstractWidget> pageActions = new ArrayList<>();
    @Nullable private SlateTabStrip topStrip;
    private int current;
    private final Anim navHighlight = new Anim(0, 200, Ease.OUT_CUBIC);
    private int hoverRow = -1;
    private final String rememberKey;
    private static final java.util.Map<String, Integer> LAST_PAGE = new java.util.HashMap<>();

    protected SidebarScreen(final Component title, @Nullable final Screen parent, final String rememberKey) {
        super(title, parent);
        this.rememberKey = rememberKey;
        this.current = LAST_PAGE.getOrDefault(rememberKey, 0);
    }

    /** Subclasses add their pages here (called once, before the first build). */
    protected abstract void definePages(List<SidebarPage> pages);

    public List<SidebarPage> pages() { return pages; }

    public int currentIndex() { return current; }

    @Nullable public SidebarPage currentPage() { return pages.isEmpty() ? null : pages.get(Math.min(current, pages.size() - 1)); }

    protected boolean narrow() { return width < 420; }

    /**
     * Whether the page list is drawn the Overhaul layout's way: no plate under it, the names in the heading font, a
     * bar of accent that slides to the chosen page and throws its light along the row, rows that close up on a low
     * screen so the last page is never cut off. The hubs of the Overhaul layout (settings, friends) answer true.
     */
    protected boolean overhaulNav() { return false; }

    /** What a page is called in the nav column; a screen short of room may shorten a long name. */
    protected Component navTitle(final SidebarPage page) { return page.title(); }

    /** Height of one row of the nav column. */
    protected int navRowHeight() {
        if (!overhaulNav()) return ROW_H;
        final int n = Math.max(1, pages.size());
        return Math.max(16, Math.min(24, (height - HEADER_H - navTopPad() - 4) / n));
    }

    /** Room above the nav column's first row. */
    protected int navTopPad() { return overhaulNav() ? 8 : NAV_TOP; }

    /** The nav row under the pointer, -1 for none: for a screen that draws the column itself. */
    protected int hoveredNavRow() { return hoverRow; }

    /** Where the sliding highlight is right now, in rows (2.5 = half way from the third row to the fourth). */
    protected float navHighlightRow() { return Math.max(0, Math.min(Math.max(0, pages.size() - 1), navHighlight.get())); }

    /**
     * Where the page list sits: the left rail (default) or a strip of tabs across the top, right under the header
     * (the Overhaul settings hub). With the strip the page area spans the whole width and the strip scrolls when
     * the tabs do not fit; Ctrl+Tab and Ctrl+1..9 work the same.
     */
    protected boolean topNav() { return false; }

    /**
     * The nav column's width: as wide as its longest label needs ({@link #NAV_W} at least, at most two fifths of the
     * screen), icons only when {@link #narrow()}. Subclasses may widen it; the labels always get at least what they
     * need within that bound, so "Language &amp; Accessibility" is never cut to "Language &amp; Acces…".
     */
    protected int navWidth() { return narrow() ? NAV_W_NARROW : labelFitWidth(); }

    /** {@link #navWidth()}, but never narrower than the labels need (a subclass may only widen the column). */
    private int navW() {
        if (topNav()) return 0;
        if (narrow()) return NAV_W_NARROW;
        if (overhaulNav()) {
            // As wide as its names need in the heading font; a quarter of the screen at most.
            int widest = 0;
            if (font != null) for (final SidebarPage p : pages) widest = Math.max(widest, font.width(Fonts.heading(navTitle(p))) + (p.badge() > 0 ? 24 : 0));
            return Math.max(96, Math.min(widest + 40, Math.max(NAV_W, width / 4)));
        }
        return Math.max(navWidth(), labelFitWidth());
    }

    /**
     * Width that fits the longest page label next to its icon (and its badge), bounded by {@link #NAV_W} and two
     * fifths of the screen. Measured when the screen builds, so a badge appearing later never shifts the page.
     */
    private int labelFitWidth() {
        if (fitWidthFor != width) {
            fitWidthFor = width;
            int need = NAV_W;
            if (font != null) {
                for (final SidebarPage p : pages) need = Math.max(need, font.width(p.title()) + 32 + (p.badge() > 0 ? 24 : 0));
            }
            fitWidth = Math.min(need, Math.max(NAV_W, width * 2 / 5));
        }
        return fitWidth;
    }

    /** Cache of {@link #labelFitWidth()}: the width it was measured for ({@code -1}: measure again). */
    private int fitWidthFor = -1;
    private int fitWidth = NAV_W;

    public Rect navRect() {
        return topNav() ? new Rect(0, HEADER_H, width, TOP_NAV_H) : new Rect(0, HEADER_H, navW(), height - HEADER_H);
    }

    /** The row holding the page title and the page actions. */
    public Rect pageTitleRect() {
        final int top = topNav() ? HEADER_H + TOP_NAV_H : HEADER_H + 6;
        return new Rect(navW() + PAD, top, width - navW() - PAD * 2, PAGE_TITLE_H);
    }

    /**
     * Whether the page area starts with the page's own title row. A screen that names the page in its header instead
     * (the settings hub: "Settings › Controls") returns false and hands that row's height to the page.
     */
    protected boolean showPageTitle() { return true; }

    /** Where pages build their widgets (below the title row, or right under the header without one). */
    public Rect pageRect() {
        final Rect t = pageTitleRect();
        final int top = showPageTitle() || !pageActions.isEmpty() ? t.bottom() + 6 : t.y();
        return new Rect(t.x(), top, t.w(), height - top - PAD);
    }

    @Override
    protected void build() {
        if (pages.isEmpty()) definePages(pages);
        fitWidthFor = -1;                                   // re-measure the labels (language, badges, resize)
        if (current >= pages.size()) current = 0;
        navHighlight.snap(current);
        pageWidgets.clear();
        pageActions.clear();
        topStrip = null;
        if (topNav() && !pages.isEmpty()) {
            final List<SlateTabStrip.Tab> tabs = new ArrayList<>();
            for (final SidebarPage p : pages) tabs.add(new SlateTabStrip.Tab(p.title(), p.icon(), p.badge()));
            final SlateTabStrip strip = new SlateTabStrip(PAD, HEADER_H + 4, width - PAD * 2, tabs, current, this::showPage)
                .style(SlateTabStrip.Style.UNDERLINE);
            addRenderableWidget(strip);
            topStrip = strip;
        }
        final SidebarPage page = currentPage();
        if (page != null) page.build(this, pageRect());
    }

    /** Pages call this to add their widgets. */
    public <T extends GuiEventListener & Renderable & NarratableEntry> T addPageWidget(final T widget) {
        if (widget instanceof AbstractWidget w) pageWidgets.add(w);
        return addRenderableWidget(widget);
    }

    /** Adds a widget to the right end of the page title row (right-to-left). Removed with the page. */
    public <T extends AbstractWidget> T addPageAction(final T widget) {
        final Rect t = pageTitleRect();
        int x = t.right();
        for (final AbstractWidget a : pageActions) x = a.getX();
        widget.setX(x - widget.getWidth() - (pageActions.isEmpty() ? 0 : 4));
        widget.setY(t.y() + (t.h() - widget.getHeight()) / 2);
        pageActions.add(widget);
        pageWidgets.add(widget);
        addRenderableWidget(widget);
        return widget;
    }

    private void tearDownPage() {
        for (final AbstractWidget w : pageWidgets) removeWidget(w);
        pageWidgets.clear();
        pageActions.clear();
    }

    public void showPage(final int index) {
        if (index < 0 || index >= pages.size() || index == current) return;
        final SidebarPage old = currentPage();
        if (old != null) old.onHide();
        Popups.closeAll();
        tearDownPage();
        current = index;
        LAST_PAGE.put(rememberKey, index);
        navHighlight.set(index);
        if (topStrip != null && topStrip.index() != index) topStrip.setIndex(index);
        SlateSounds.tick();
        final SidebarPage page = currentPage();
        if (page != null) page.build(this, pageRect());
        int i = 0;
        for (final AbstractWidget w : pageWidgets) i = entrance(w, i);
        setFocused(null);
    }

    public void showPage(final String id) {
        for (int i = 0; i < pages.size(); i++) if (pages.get(i).id().equals(id)) { showPage(i); return; }
    }

    /** Rebuild the current page in place (after a data change). */
    public void refreshPage() {
        tearDownPage();
        final SidebarPage page = currentPage();
        if (page != null) page.build(this, pageRect());
    }

    @Override
    public void tick() {
        super.tick();
        final SidebarPage page = currentPage();
        if (page != null) page.tick();
    }

    @Override
    public void removed() {
        super.removed();
        final SidebarPage page = currentPage();
        if (page != null) page.onHide();
    }

    private int rowAt(final double mx, final double my) {
        if (topNav()) return -1;                                 // the strip widget takes the clicks
        final Rect nav = navRect();
        if (!nav.contains(mx, my) || my < nav.y() + navTopPad()) return -1;
        final int i = (int) ((my - nav.y() - navTopPad()) / navRowHeight());
        return i >= 0 && i < pages.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button == 0) {
            final int row = rowAt(mouseX, mouseY);
            if (row >= 0) { showPage(row); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!pages.isEmpty() && hasControlDown()) {
            // Ctrl+Tab / Ctrl+Shift+Tab cycle pages; Ctrl+1..9 jump.
            if (keyCode == 258) {
                showPage(((current + (hasShiftDown() ? -1 : 1)) % pages.size() + pages.size()) % pages.size());
                return true;
            }
            if (keyCode >= 49 && keyCode <= 57 && keyCode - 49 < pages.size()) { showPage(keyCode - 49); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        hoverRow = rowAt(mouseX, mouseY);
        renderBackground(g, mouseX, mouseY, partialTick);
        renderNav(g, mouseX, mouseY);
        renderPageTitle(g);
        final SidebarPage page = currentPage();
        if (page != null) page.render(this, g, pageRect(), mouseX, mouseY, partialTick);
        for (final Renderable r : renderableList()) r.render(g, mouseX, mouseY, partialTick);
        if (showHeader) renderHeader(g);
        renderContent(g, mouseX, mouseY, partialTick);
    }

    protected void renderNav(final GuiGraphics g, final int mouseX, final int mouseY) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final Rect nav = navRect();
        if (topNav()) {
            // The strip widget draws the tabs; the rule under it is the divider between the nav and the page.
            SlateDraw.hline(g, PAD, nav.bottom() - 1, width - PAD * 2, t.isVanilla() ? 0xFF6F6F6F : Colors.withAlpha(p.border(), 0xC0));
            return;
        }
        if (overhaulNav()) {
            renderOverhaulNav(g, nav);
            return;
        }
        final boolean icons = narrow();
        if (t.isVanilla()) {
            SlateDraw.vanillaListBackground(g, nav.x(), nav.y(), nav.w(), nav.h(), this.minecraft.level != null);
            SlateDraw.vline(g, nav.right() - 1, nav.y(), nav.h(), 0xFF000000);
        } else {
            g.fill(nav.x(), nav.y(), nav.right(), nav.bottom(), p.bg2());
            SlateDraw.vline(g, nav.right() - 1, nav.y(), nav.h(), p.border());
        }
        // Sliding highlight
        final float s = Math.max(0, Math.min(Math.max(0, pages.size() - 1), navHighlight.get()));
        final int hy = nav.y() + NAV_TOP + Math.round(s * ROW_H);
        if (t.isVanilla()) {
            g.fill(nav.x() + 3, hy, nav.right() - 4, hy + ROW_H, 0x60FFFFFF);
            SlateDraw.outline(g, nav.x() + 3, hy, nav.w() - 7, ROW_H, 0xFFFFFFFF, 0);
        } else {
            SlateDraw.pixelRound(g, nav.x() + 4, hy, nav.w() - 8, ROW_H, p.surfaceActive(), t.radius());
            SlateDraw.rect(g, nav.x() + 4, hy + 4, 2, ROW_H - 8, p.accent());
        }
        for (int i = 0; i < pages.size(); i++) {
            final SidebarPage page = pages.get(i);
            final int ry = nav.y() + NAV_TOP + i * ROW_H;
            final boolean sel = i == current, hov = i == hoverRow;
            if (hov && !sel && !t.isVanilla()) SlateDraw.pixelRound(g, nav.x() + 4, ry, nav.w() - 8, ROW_H, p.surfaceHover(), t.radius());
            final int fg = sel ? p.text() : hov ? p.textMuted() : (t.isVanilla() ? 0xFFC0C0C0 : p.textDim());
            final int ix = icons ? nav.x() + (nav.w() - 12) / 2 : nav.x() + 10;
            Icons.draw(g, page.icon(), ix, ry + (ROW_H - 12) / 2, 12, sel && !t.isVanilla() ? p.accent() : fg);
            if (!icons) {
                final int badgeW = page.badge() > 0 ? 24 : 0;
                g.drawString(font, SlateDraw.truncate(page.title(), nav.w() - 32 - badgeW), nav.x() + 26, SlateDraw.textY(ry, ROW_H), fg, t.isVanilla());
                if (page.badge() > 0) SlateBadge.drawCount(g, page.badge(), nav.right() - 20, ry + (ROW_H - 10) / 2);
            } else {
                if (page.badge() > 0) SlateDraw.pixelCircle(g, nav.right() - 8, ry + 5, 1, p.accent());
                if (hov) SlateTooltips.request(page.title(), null);
            }
        }
    }

    private final List<Anim> navHover = new ArrayList<>();

    private void renderOverhaulNav(final GuiGraphics g, final Rect nav) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final boolean icons = narrow();
        final int rowH = navRowHeight(), top = nav.y() + navTopPad();
        while (navHover.size() < pages.size()) navHover.add(new Anim(0, 160, Ease.OUT_CUBIC));

        // No plate under the list: the tabs stand on the screen itself, a line that fades out at both ends beside them.
        final int line = van ? 0xFF000000 : p.border();
        SlateDraw.vgradient(g, nav.right() - 1, nav.y(), 1, 18, Colors.withAlpha(line, 0), line);
        SlateDraw.rect(g, nav.right() - 1, nav.y() + 18, 1, Math.max(0, nav.h() - 36), line);
        SlateDraw.vgradient(g, nav.right() - 1, nav.bottom() - 18, 1, 18, line, Colors.withAlpha(line, 0));

        // The chosen tab: a bar of accent on the left, its light running out to the right. It slides.
        final int hy = top + Math.round(navHighlightRow() * rowH);
        final int accent = van ? 0xFFFFFFFF : p.accent();
        SlateDraw.hgradient(g, nav.x() + 3, hy + 2, nav.w() - 8, rowH - 4, Colors.withAlpha(accent, van ? 0x38 : 0x46), Colors.withAlpha(accent, 0));
        SlateDraw.rect(g, nav.x() + 3, hy + 3, 2, rowH - 6, accent);

        for (int i = 0; i < pages.size(); i++) {
            final SidebarPage page = pages.get(i);
            final int ry = top + i * rowH;
            final boolean sel = i == current;
            final Anim hover = navHover.get(i);
            hover.set(i == hoverRow);
            final float h = hover.get();
            final int rest = van ? 0xFFB0B0B0 : p.textDim(), lit = van ? 0xFFFFFFFF : p.text();
            final int fg = sel ? lit : Colors.lerp(rest, van ? 0xFFE0E0E0 : p.textMuted(), h);
            final int push = Math.round(2f * h);
            final int ix = icons ? nav.x() + (nav.w() - 12) / 2 : nav.x() + 11 + push;
            Icons.draw(g, page.icon(), ix, ry + (rowH - 12) / 2, 12, sel && !van ? p.accent() : fg);
            if (icons) {
                if (page.badge() > 0) SlateDraw.pixelCircle(g, nav.right() - 8, ry + 5, 1, accent);
                if (i == hoverRow) SlateTooltips.request(navTitle(page), null);
                continue;
            }
            final int badgeW = page.badge() > 0 ? 24 : 0;
            g.drawString(font, SlateDraw.truncate(Fonts.heading(navTitle(page)), nav.w() - 34 - badgeW), nav.x() + 28 + push, SlateDraw.textY(ry, rowH), fg, van);
            if (page.badge() > 0) SlateBadge.drawCount(g, page.badge(), nav.right() - 22, ry + (rowH - 10) / 2);
        }
    }

    /** The current page's title in the heading font, at the top of the page area. */
    protected void renderPageTitle(final GuiGraphics g) {
        final SidebarPage page = currentPage();
        if (page == null || (!showPageTitle() && pageActions.isEmpty())) return;
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final Rect r = pageTitleRect();
        int right = r.right();
        for (final AbstractWidget a : pageActions) right = Math.min(right, a.getX() - 8);
        g.drawString(font, SlateDraw.truncate(Fonts.heading(page.title()), right - r.x()), r.x(), SlateDraw.textY(r.y(), r.h()),
            t.isVanilla() ? 0xFFFFFFFF : p.text(), t.isVanilla());
        SlateDraw.hline(g, r.x(), r.bottom() + 2, r.w(), t.isVanilla() ? 0xFF6F6F6F : Colors.withAlpha(p.border(), 0xC0));
    }
}
