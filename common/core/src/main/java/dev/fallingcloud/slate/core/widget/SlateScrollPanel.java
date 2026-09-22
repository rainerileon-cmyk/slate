package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.List;
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
 * scrolling. The scrollbar is drawn on the right when the content overflows. Mouse wheel scrolls; the
 * scroll amount eases (no jump). Keyboard focus and Tab order are inherited from AbstractContainerWidget.
 */
public class SlateScrollPanel extends AbstractContainerWidget {

    private record Child(AbstractWidget widget, int relX, int relY) {}

    private final List<Child> children = new ArrayList<>();
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final Anim scroll = new Anim(0, 180, Ease.OUT_CUBIC);
    private int contentHeight;
    private int padding = 0;
    private boolean drawBackground;
    private boolean draggingBar;
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
        contentHeight = Math.max(contentHeight, relY + widget.getHeight() + padding);
        return widget;
    }

    /** Adds a widget keeping its current position relative to the panel origin. */
    public <T extends AbstractWidget> T add(final T widget) {
        return add(widget, widget.getX() - getX(), widget.getY() - getY());
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

    public SlateScrollPanel scrollStep(final int px) { this.scrollStep = px; return this; }

    /** Force the content height (e.g. when the last child is not the lowest point). */
    public void setContentHeight(final int h) { this.contentHeight = h; }

    public int contentHeight() { return contentHeight; }

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
            if (c.relY < s) scrollTo(c.relY - 4);
            else if (c.relY + w.getHeight() > s + getHeight()) scrollTo(c.relY + w.getHeight() - getHeight() + 4);
        }
    }

    private void layout() {
        final int s = Math.round(scroll.get());
        for (final Child c : children) {
            c.widget.setX(getX() + c.relX);
            c.widget.setY(getY() + c.relY - s);
        }
    }

    // ------------------------------------------------------------------ container plumbing

    @Override
    public List<? extends GuiEventListener> children() { return widgets; }

    private boolean inside(final double mx, final double my) {
        return mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
    }

    private boolean scrollbarVisible() { return maxScroll() > 0; }

    private int barX() { return getX() + getWidth() - 4; }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.visible || !this.active) return false;
        layout();
        if (button == 0 && scrollbarVisible() && mouseX >= barX() - 2 && mouseX < getX() + getWidth() && inside(mouseX, mouseY)) {
            draggingBar = true;
            dragStartY = mouseY;
            dragStartScroll = scroll.target();
            return true;
        }
        if (!inside(mouseX, mouseY)) return false;
        // Only children that are actually visible in the clip can be hit.
        for (final AbstractWidget w : widgets) {
            if (!w.visible) continue;
            if (w.getY() + w.getHeight() < getY() || w.getY() > getY() + getHeight()) continue;
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
            final int trackH = getHeight();
            final double ratio = (double) contentHeight / Math.max(1, trackH);
            scroll.snap((float) Math.max(0, Math.min(maxScroll(), dragStartScroll + (mouseY - dragStartY) * ratio)));
            return true;
        }
        layout();
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (!inside(mouseX, mouseY)) return false;
        layout();
        for (final AbstractWidget w : widgets) {
            if (w.visible && w.isMouseOver(mouseX, mouseY) && w.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        }
        if (!scrollbarVisible()) return false;
        scrollBy(-scrollY * scrollStep);
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
        if (drawBackground) {
            if (t.isVanilla()) SlateDraw.vanillaListBackground(g, getX(), getY(), getWidth(), getHeight(), net.minecraft.client.Minecraft.getInstance().level != null);
            else SlateDraw.panel(g, getX(), getY(), getWidth(), getHeight(), p.bg2(), p.border());
        }
        final int clipW = scrollbarVisible() ? getWidth() - 6 : getWidth();
        SlateDraw.scissor(g, getX(), getY(), getWidth(), getHeight());
        final int mx = inside(mouseX, mouseY) ? mouseX : -9999, my = inside(mouseX, mouseY) ? mouseY : -9999;
        for (final AbstractWidget w : widgets) {
            if (!w.visible) continue;
            if (w.getY() + w.getHeight() < getY() || w.getY() > getY() + getHeight()) continue;
            w.render(g, mx, my, partialTick);
        }
        SlateDraw.unscissor(g);
        if (scrollbarVisible()) drawScrollbar(g, p, clipW);
    }

    private void drawScrollbar(final GuiGraphics g, final Palette p, final int clipW) {
        final int x = barX(), y = getY(), h = getHeight();
        final int barH = Math.max(12, (int) ((long) h * h / Math.max(1, contentHeight + padding)));
        final int barY = y + (int) ((h - barH) * (scroll.get() / Math.max(1, maxScroll())));
        if (Theme.current().isVanilla()) {
            g.fill(x, y, x + 4, y + h, 0xFF000000);
            g.fill(x, barY, x + 4, barY + barH, 0xFF808080);
            g.fill(x, barY, x + 3, barY + barH - 1, 0xFFC0C0C0);
        } else {
            SlateDraw.pixelRound(g, x, y, 4, h, Colors.withAlpha(p.surface(), 0x80), 1);
            SlateDraw.pixelRound(g, x, barY, 4, barH, draggingBar ? p.accent() : p.borderStrong(), 1);
        }
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {}
}
