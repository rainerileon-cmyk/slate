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
import dev.fallingcloud.slate.core.widget.SlateWidget;
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
 * animation. Used by the Config hub, the Multiplayer hub and Menu's options.
 */
public abstract class SidebarScreen extends SlateScreen {

    public static final int NAV_W = 118, NAV_W_NARROW = 30, ROW_H = 20;

    private final List<SidebarPage> pages = new ArrayList<>();
    private final List<AbstractWidget> pageWidgets = new ArrayList<>();
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

    protected int navWidth() { return narrow() ? NAV_W_NARROW : NAV_W; }

    public Rect navRect() { return new Rect(0, HEADER_H, navWidth(), height - HEADER_H); }

    /** Where pages build their widgets. */
    public Rect pageRect() {
        final Rect c = contentRect();
        return new Rect(navWidth() + PAD, c.y() + 4, width - navWidth() - PAD * 2, c.h() - 4);
    }

    @Override
    protected void build() {
        if (pages.isEmpty()) definePages(pages);
        if (current >= pages.size()) current = 0;
        navHighlight.snap(current);
        pageWidgets.clear();
        final SidebarPage page = currentPage();
        if (page != null) page.build(this, pageRect());
    }

    /** Pages call this to add their widgets. */
    public <T extends GuiEventListener & Renderable & NarratableEntry> T addPageWidget(final T widget) {
        if (widget instanceof AbstractWidget w) pageWidgets.add(w);
        return addRenderableWidget(widget);
    }

    public void showPage(final int index) {
        if (index < 0 || index >= pages.size() || index == current) return;
        final SidebarPage old = currentPage();
        if (old != null) old.onHide();
        Popups.closeAll();
        for (final AbstractWidget w : pageWidgets) removeWidget(w);
        pageWidgets.clear();
        current = index;
        LAST_PAGE.put(rememberKey, index);
        navHighlight.set(index);
        SlateSounds.tick();
        final SidebarPage page = currentPage();
        if (page != null) page.build(this, pageRect());
        int i = 0;
        for (final AbstractWidget w : pageWidgets) if (w instanceof SlateWidget sw) sw.playEntrance(Math.min(160, i++ * 14));
        setFocused(null);
    }

    public void showPage(final String id) {
        for (int i = 0; i < pages.size(); i++) if (pages.get(i).id().equals(id)) { showPage(i); return; }
    }

    /** Rebuild the current page in place (after a data change). */
    public void refreshPage() {
        for (final AbstractWidget w : pageWidgets) removeWidget(w);
        pageWidgets.clear();
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
        final Rect nav = navRect();
        if (!nav.contains(mx, my)) return -1;
        final int i = (int) ((my - nav.y() - 6) / ROW_H);
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
        // Ctrl+Tab / Ctrl+Shift+Tab cycle pages.
        if (keyCode == 258 && hasControlDown()) {
            showPage(((current + (hasShiftDown() ? -1 : 1)) % pages.size() + pages.size()) % pages.size());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        hoverRow = rowAt(mouseX, mouseY);
        renderBackground(g, mouseX, mouseY, partialTick);
        renderNav(g, mouseX, mouseY);
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
        final boolean icons = narrow();
        if (t.isVanilla()) {
            SlateDraw.vanillaListBackground(g, nav.x(), nav.y(), nav.w(), nav.h(), this.minecraft.level != null);
            SlateDraw.vline(g, nav.right() - 1, nav.y(), nav.h(), 0xFF000000);
        } else {
            g.fill(nav.x(), nav.y(), nav.right(), nav.bottom(), p.bg2());
            SlateDraw.vline(g, nav.right() - 1, nav.y(), nav.h(), p.border());
        }
        // Sliding highlight
        final float s = navHighlight.get();
        final int hy = nav.y() + 6 + Math.round(s * ROW_H);
        if (t.isVanilla()) {
            g.fill(nav.x() + 3, hy, nav.right() - 4, hy + ROW_H, 0x60FFFFFF);
            SlateDraw.outline(g, nav.x() + 3, hy, nav.w() - 7, ROW_H, 0xFFFFFFFF, 0);
        } else {
            SlateDraw.pixelRound(g, nav.x() + 4, hy, nav.w() - 8, ROW_H, p.surfaceActive(), t.radius());
            SlateDraw.rect(g, nav.x() + 4, hy + 4, 2, ROW_H - 8, p.accent());
        }
        for (int i = 0; i < pages.size(); i++) {
            final SidebarPage page = pages.get(i);
            final int ry = nav.y() + 6 + i * ROW_H;
            final boolean sel = i == current, hov = i == hoverRow;
            if (hov && !sel && !t.isVanilla()) SlateDraw.pixelRound(g, nav.x() + 4, ry, nav.w() - 8, ROW_H, p.surfaceHover(), t.radius());
            final int fg = sel ? p.text() : hov ? p.textMuted() : (t.isVanilla() ? 0xFFC0C0C0 : p.textDim());
            Icons.draw(g, page.icon(), nav.x() + 10, ry + (ROW_H - 12) / 2, 12, sel && !t.isVanilla() ? p.accent() : fg);
            if (!icons) {
                g.drawString(font, SlateDraw.truncate(page.title(), nav.w() - 40), nav.x() + 26, ry + (ROW_H - 9) / 2 + 1, fg, t.isVanilla());
                if (page.badge() > 0) SlateBadge.drawCount(g, page.badge(), nav.right() - 18, ry + (ROW_H - 10) / 2);
            } else if (page.badge() > 0) {
                SlateDraw.rect(g, nav.x() + 20, ry + 3, 3, 3, p.accent());
            }
        }
        // Page title in the content area
        final SidebarPage page = currentPage();
        if (page != null && !t.isVanilla()) {
            final Rect pr = pageRect();
            g.drawString(font, Fonts.heading(page.title()), pr.x(), HEADER_H - 22, Colors.withAlpha(p.textDim(), 0), false);
        }
    }
}
