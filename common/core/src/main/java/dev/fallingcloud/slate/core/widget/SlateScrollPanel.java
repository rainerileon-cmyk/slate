package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A clipped, smoothly scrolling container of widgets. Children are added with positions relative to the
 * panel's content origin; every frame (and before every event) they are moved to
 * {@code panel.x + relX, panel.y + relY - scroll}, so ordinary widgets work inside without knowing about
 * scrolling. The scrollbar (4 px, 8 px hit area) is drawn on the right when the content overflows:
 * wheel, thumb drag and track click all scroll; Page Up/Down and Home/End work when the panel has
 * focus. Nested panels chain: an inner panel that cannot scroll further hands the wheel to its parent.
 */
public class SlateScrollPanel extends AbstractContainerWidget {

    private record Child(AbstractWidget widget, int relX, int relY) {}

    public static final int BAR_W = 4, BAR_HIT = 8;

    private final List<Child> children = new ArrayList<>();
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final Anim scroll = new Anim(0, 180, Ease.OUT_CUBIC);
    private int contentHeight;
    private int padding = 0;
    private boolean drawBackground;
    private boolean edgeFades = true;
    private boolean draggingBar;
    private boolean barHover;
    private double dragStartY, dragStartScroll;
    private int scrollStep = 24;

    public SlateScrollPanel(final int x, final int y, final int width, final int height) {
        super(x, y, width, height, Component.empty());
    }

    // ------------------------------------------------------------------ building

    /** Adds a widget at content-relative (relX, relY). Its current x/y are overwritten. */
    public <T extends AbstractWidget> T add(final T widget, final int relX, final int relY) {
        children.add(new Child(widget, relX, relY));
        widgets.add(widget);
        contentHeight = Math.max(contentHeight, relY + widget.getHeight());
        widget.setX(getX() + relX);
        widget.setY(getY() + relY - Math.round(scroll.get()));
        return widget;
    }

    /** Adds a widget keeping its current ABSOLUTE position (relative to the panel origin). */
    public <T extends AbstractWidget> T add(final T widget) {
        return add(widget, widget.getX() - getX(), widget.getY() - getY());
    }

    /** Adds a widget whose current x/y are already content-relative (rows placed by a {@code Flow} at 0,0). */
    public <T extends AbstractWidget> T addRelative(final T widget) {
        return add(widget, widget.getX(), widget.getY());
    }

    public void clear() {
        children.clear();
        widgets.clear();
        contentHeight = 0;
        scroll.snap(0);
        setFocused(null);
    }

    public SlateScrollPanel padding(final int p) { this.padding = p; return this; }

    public SlateScrollPanel background(final boolean draw) { this.drawBackground = draw; return this; }

    /** Soft shadows at the top/bottom edge while there is more content that way (dark skin). */
    public SlateScrollPanel edgeFades(final boolean on) { this.edgeFades = on; return this; }

    public SlateScrollPanel scrollStep(final int px) { this.scrollStep = px; return this; }

    /** Force the content height (e.g. when the last child is not the lowest point). */
    public void setContentHeight(final int h) { this.contentHeight = h; }

    public int contentHeight() { return contentHeight; }

    /** Width children should use to leave room for the scrollbar. */
    public int innerWidth() { return getWidth() - BAR_HIT; }

    public int maxScroll() { return Math.max(0, contentHeight + padding - getHeight()); }

    public double scrollAmount() { return scroll.get(); }

    public void scrollTo(final double y) { scroll.set((float) Math.max(0, Math.min(maxScroll(), y))); }

    public void scrollBy(final double dy) { scrollTo(scroll.target() + dy); }

    public void snapScroll(final double y) { scroll.snap((float) Math.max(0, Math.min(maxScroll(), y))); }

    /** Scrolls so that the child is fully visible. */
    public void ensureVisible(final AbstractWidget w) {
        for (final Child c : children) {
            if (c.widget != w) continue;
            final double s = scroll.target();
            if (c.relY < s + 4) scrollTo(c.relY - 4);
            else if (c.relY + w.getHeight() > s + getHeight() - 4) scrollTo(c.relY + w.getHeight() - getHeight() + 4);
        }
    }

    private void layout() {
        final int s = Math.round(scroll.get());
        for (final Child c : children) {
            c.widget.setX(getX() + c.relX);
            c.widget.setY(getY() + c.relY - s);
        }
    }

    private boolean visibleChild(final AbstractWidget w) {
        return w.visible && w.getY() + w.getHeight() > getY() && w.getY() < getY() + getHeight();
    }

    // ------------------------------------------------------------------ container plumbing

    @Override
    public List<? extends GuiEventListener> children() { return widgets; }

    private boolean inside(final double mx, final double my) {
        return mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
    }

    private boolean scrollbarVisible() { return maxScroll() > 0; }

    private int barX() { return getX() + getWidth() - BAR_W; }

    private int barHeight() {
        final int h = getHeight();
        return Math.max(12, Math.min(h, (int) ((long) h * h / Math.max(1, contentHeight + padding))));
    }

    private int barY() {
        final int h = getHeight(), bh = barHeight();
        return getY() + (int) ((h - bh) * (scroll.get() / Math.max(1, maxScroll())));
    }

    private boolean overBar(final double mx, final double my) {
        return scrollbarVisible() && mx >= getX() + getWidth() - BAR_HIT && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.visible || !this.active) return false;
        layout();
        if (button == 0 && overBar(mouseX, mouseY)) {
            final int by = barY(), bh = barHeight();
            if (mouseY < by || mouseY >= by + bh) {
                // Track click: centre the thumb on the mouse, then drag from there.
                final double f = (mouseY - getY() - bh / 2.0) / Math.max(1, getHeight() - bh);
                snapScroll(f * maxScroll());
            }
            draggingBar = true;
            dragStartY = mouseY;
            dragStartScroll = scroll.target();
            return true;
        }
        if (!inside(mouseX, mouseY)) return false;
        // Only children that are actually visible in the clip can be hit.
        for (final AbstractWidget w : widgets) {
            if (!visibleChild(w)) continue;
            if (w.mouseClicked(mouseX, mouseY, button)) {
                setFocused(w);
                if (button == 0) setDragging(true);
                return true;
            }
        }
        setFocused(null);
        return false;
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        draggingBar = false;
        layout();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY) {
        if (draggingBar) {
            final double ratio = (double) maxScroll() / Math.max(1, getHeight() - barHeight());
            snapScroll(dragStartScroll + (mouseY - dragStartY) * ratio);
            return true;
        }
        layout();
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (!this.visible || !inside(mouseX, mouseY)) return false;
        layout();
        for (final AbstractWidget w : widgets) {
            if (visibleChild(w) && w.isMouseOver(mouseX, mouseY) && w.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        }
        if (!scrollbarVisible()) return false;
        // Chain to the parent when this panel is already at the end the wheel points to.
        if (scrollY > 0 && scroll.target() <= 0) return false;
        if (scrollY < 0 && scroll.target() >= maxScroll()) return false;
        scrollBy(-scrollY * scrollStep);
        return true;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (!scrollbarVisible()) return false;
        switch (keyCode) {
            case 266 -> scrollBy(-(getHeight() - scrollStep));   // page up
            case 267 -> scrollBy(getHeight() - scrollStep);      // page down
            case 268 -> { if (getFocused() == null) scrollTo(0); else return false; }           // home
            case 269 -> { if (getFocused() == null) scrollTo(maxScroll()); else return false; } // end
            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean isMouseOver(final double mouseX, final double mouseY) {
        return this.active && this.visible && inside(mouseX, mouseY);
    }

    @Override
    public void setFocused(final GuiEventListener listener) {
        super.setFocused(listener);
        if (listener instanceof AbstractWidget w) ensureVisible(w);
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        layout();
        final boolean inWorld = Minecraft.getInstance().level != null;
        if (drawBackground) {
            if (t.isVanilla()) SlateDraw.vanillaListBackground(g, getX(), getY(), getWidth(), getHeight(), inWorld);
            else SlateDraw.panel(g, getX(), getY(), getWidth(), getHeight(), p.bg2(), p.border());
        }
        final boolean over = inside(mouseX, mouseY) && g.containsPointInScissor(mouseX, mouseY);
        barHover = over && overBar(mouseX, mouseY);
        final int mx = over && !barHover ? mouseX : -9999, my = over && !barHover ? mouseY : -9999;
        SlateDraw.scissor(g, getX(), getY(), getWidth(), getHeight());
        for (final AbstractWidget w : widgets) {
            if (!visibleChild(w)) continue;
            w.render(g, mx, my, partialTick);
        }
        SlateDraw.unscissor(g);
        if (edgeFades && !t.isVanilla() && scrollbarVisible()) {
            final int fadeH = 10;
            final int s = Math.round(scroll.get());
            final int shade = Colors.withAlpha(p.bg(), 0x90);
            if (s > 0) SlateDraw.vgradient(g, getX(), getY(), getWidth() - (scrollbarVisible() ? BAR_HIT : 0), fadeH, shade, 0);
            if (s < maxScroll()) SlateDraw.vgradient(g, getX(), getY() + getHeight() - fadeH, getWidth() - (scrollbarVisible() ? BAR_HIT : 0), fadeH, 0, shade);
        }
        if (scrollbarVisible()) drawScrollbar(g, p);
    }

    private void drawScrollbar(final GuiGraphics g, final Palette p) {
        final int x = barX(), y = getY(), h = getHeight();
        final int barH = barHeight(), barY = barY();
        if (Theme.current().isVanilla()) {
            g.fill(x, y, x + BAR_W, y + h, 0xFF000000);
            g.fill(x, barY, x + BAR_W, barY + barH, draggingBar || barHover ? 0xFFA0A0A0 : 0xFF808080);
            g.fill(x, barY, x + BAR_W - 1, barY + barH - 1, draggingBar || barHover ? 0xFFE0E0E0 : 0xFFC0C0C0);
        } else {
            SlateDraw.pixelRound(g, x, y, BAR_W, h, Colors.withAlpha(p.surface(), 0x80), 1);
            final int thumb = draggingBar ? p.accent() : barHover ? p.textMuted() : p.borderStrong();
            SlateDraw.pixelRound(g, x, barY, BAR_W, barH, thumb, 1);
        }
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {}
}
