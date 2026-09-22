package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A virtualised list of items drawn by a {@link RowRenderer}: only visible rows render, any item count
 * is fine. Selection (click / arrows), activation (double click / Enter), right click, hover highlight,
 * smooth scrolling (wheel, thumb drag, track click, Page Up/Down, Home/End) and a scrollbar. Rows are
 * not widgets; put buttons in rows by hit-testing inside {@link RowRenderer#click} if needed, or use
 * {@link SlateScrollPanel} with cards instead.
 */
public class SlateList<T> extends SlateWidget {

    /** Draws one row into (x,y,w,h). */
    @FunctionalInterface
    public interface RowRenderer<T> {
        void render(GuiGraphics g, T item, int index, int x, int y, int w, int h, boolean hovered, boolean selected, int mouseX, int mouseY);

        /** Optional: handle a click inside the row (mouseX/Y absolute). Return true to consume. */
        default boolean click(final T item, final int index, final int x, final int y, final int w, final int h, final double mouseX, final double mouseY, final int button) {
            return false;
        }
    }

    public static final int BAR_W = 4, BAR_HIT = 8;

    private final List<T> items = new ArrayList<>();
    private int rowHeight;
    private int gap = 2;
    private final RowRenderer<T> renderer;
    private final Anim scroll = new Anim(0, 180, Ease.OUT_CUBIC);
    private int selected = -1;
    @Nullable private Consumer<T> onSelect, onActivate, onRightClick;
    private long lastClickMs;
    private int lastClickIndex = -1;
    private boolean draggingBar;
    private boolean barHover;
    private double dragStartY, dragStartScroll;
    @Nullable private Component emptyText;
    private boolean rowBackgrounds = true;

    public SlateList(final int x, final int y, final int width, final int height, final int rowHeight, final RowRenderer<T> renderer) {
        super(x, y, width, height, Component.empty());
        this.rowHeight = rowHeight;
        this.renderer = renderer;
    }

    // ------------------------------------------------------------------ config

    public SlateList<T> items(final List<T> newItems) {
        items.clear();
        items.addAll(newItems);
        if (selected >= items.size()) selected = -1;
        scroll.snap((float) Math.min(scroll.target(), maxScroll()));
        return this;
    }

    public List<T> items() { return items; }

    public SlateList<T> gap(final int px) { this.gap = px; return this; }

    public SlateList<T> rowHeight(final int px) { this.rowHeight = px; return this; }

    public int rowHeight() { return rowHeight; }

    public SlateList<T> onSelect(final Consumer<T> c) { this.onSelect = c; return this; }

    public SlateList<T> onActivate(final Consumer<T> c) { this.onActivate = c; return this; }

    public SlateList<T> onRightClick(final Consumer<T> c) { this.onRightClick = c; return this; }

    public SlateList<T> emptyText(final Component text) { this.emptyText = text; return this; }

    /** Disable the hover/selection row fills (rows draw their own cards). */
    public SlateList<T> plainRows() { this.rowBackgrounds = false; return this; }

    @Nullable public T selectedItem() { return selected >= 0 && selected < items.size() ? items.get(selected) : null; }

    public int selectedIndex() { return selected; }

    public void select(final int index) {
        selected = index < 0 || index >= items.size() ? -1 : index;
        if (selected >= 0) {
            ensureVisible(selected);
            if (onSelect != null) onSelect.accept(items.get(selected));
        }
    }

    public void select(final T item) { select(items.indexOf(item)); }

    /** Clears the selection without firing callbacks. */
    public void clearSelection() { selected = -1; }

    private int stride() { return rowHeight + gap; }

    private int contentHeight() { return items.isEmpty() ? 0 : items.size() * stride() - gap; }

    public int maxScroll() { return Math.max(0, contentHeight() - getHeight()); }

    public double scrollAmount() { return scroll.get(); }

    private boolean scrollbarVisible() { return maxScroll() > 0; }

    /** Width of the row area (the scrollbar takes the rest). */
    public int rowsWidth() { return scrollbarVisible() ? getWidth() - BAR_HIT : getWidth(); }

    public void scrollTo(final double y) { scroll.set((float) Math.max(0, Math.min(maxScroll(), y))); }

    public void snapScroll(final double y) { scroll.snap((float) Math.max(0, Math.min(maxScroll(), y))); }

    public void ensureVisible(final int index) {
        final int top = index * stride();
        final double s = scroll.target();
        if (top < s) scrollTo(top);
        else if (top + rowHeight > s + getHeight()) scrollTo(top + rowHeight - getHeight());
    }

    private int indexAt(final double mouseY) {
        if (!contains(getX() + 1, mouseY)) return -1;
        final int rel = (int) (mouseY - getY() + scroll.get());
        final int i = rel / stride();
        if (i < 0 || i >= items.size()) return -1;
        return rel - i * stride() < rowHeight ? i : -1;
    }

    private int rowY(final int i) { return getY() + i * stride() - Math.round(scroll.get()); }

    private int barHeight() {
        final int h = getHeight();
        return Math.max(12, Math.min(h, (int) ((long) h * h / Math.max(1, contentHeight()))));
    }

    private int barY() {
        return getY() + (int) ((getHeight() - barHeight()) * (scroll.get() / Math.max(1, maxScroll())));
    }

    private boolean overBar(final double mx, final double my) {
        return scrollbarVisible() && mx >= getX() + getWidth() - BAR_HIT && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.active || !this.visible || !contains(mouseX, mouseY)) return false;
        if (button == 0 && overBar(mouseX, mouseY)) {
            final int by = barY(), bh = barHeight();
            if (mouseY < by || mouseY >= by + bh) {
                final double f = (mouseY - getY() - bh / 2.0) / Math.max(1, getHeight() - bh);
                snapScroll(f * maxScroll());
            }
            draggingBar = true;
            dragStartY = mouseY;
            dragStartScroll = scroll.target();
            return true;
        }
        final int i = indexAt(mouseY);
        if (i < 0) return true;
        if (renderer.click(items.get(i), i, getX(), rowY(i), rowsWidth(), rowHeight, mouseX, mouseY, button)) return true;
        if (button == 1) {
            if (selected != i) {
                selected = i;
                if (onSelect != null) onSelect.accept(items.get(i));
            }
            if (onRightClick != null) onRightClick.accept(items.get(i));
            return true;
        }
        if (button != 0) return true;
        final long now = Clock.nowMs();
        final boolean dbl = i == lastClickIndex && now - lastClickMs < 350;
        lastClickMs = now;
        lastClickIndex = i;
        if (selected != i) {
            selected = i;
            SlateSounds.tick();
            if (onSelect != null) onSelect.accept(items.get(i));
        }
        if (dbl && onActivate != null) { onActivate.accept(items.get(i)); lastClickIndex = -1; }
        return true;
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        draggingBar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY) {
        if (draggingBar) {
            final double ratio = (double) maxScroll() / Math.max(1, getHeight() - barHeight());
            snapScroll(dragStartScroll + (mouseY - dragStartY) * ratio);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (!contains(mouseX, mouseY) || !scrollbarVisible()) return false;
        if (scrollY > 0 && scroll.target() <= 0) return false;
        if (scrollY < 0 && scroll.target() >= maxScroll()) return false;
        scrollTo(scroll.target() - scrollY * stride());
        return true;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible || items.isEmpty()) return false;
        final int page = Math.max(1, getHeight() / stride());
        switch (keyCode) {
            case 264 -> { select(Math.min(items.size() - 1, selected + 1)); return true; }     // down
            case 265 -> { select(Math.max(0, selected - 1)); return true; }                    // up
            case 266 -> { select(Math.max(0, (selected < 0 ? 0 : selected) - page)); return true; }             // page up
            case 267 -> { select(Math.min(items.size() - 1, (selected < 0 ? 0 : selected) + page)); return true; } // page down
            case 268 -> { select(0); return true; }                                             // home
            case 269 -> { select(items.size() - 1); return true; }                              // end
            case 257, 335 -> { if (selected >= 0 && onActivate != null) { onActivate.accept(items.get(selected)); return true; } }
            default -> {}
        }
        return false;
    }

    @Override
    public void playDownSound(final net.minecraft.client.sounds.SoundManager handler) {}

    // ------------------------------------------------------------------ render

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, mouseX, mouseY);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, mouseX, mouseY);
    }

    private void draw(final GuiGraphics g, final int mouseX, final int mouseY) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final int x = getX(), y = getY(), w = rowsWidth(), h = getHeight();
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        if (items.isEmpty()) {
            if (emptyText != null) SlateDraw.textCentered(g, emptyText, x + getWidth() / 2, y + h / 2 - 4, Colors.scaleAlpha(p.textDim(), a), t.isVanilla());
            return;
        }
        final boolean over = this.isHovered() && g.containsPointInScissor(mouseX, mouseY);
        barHover = over && overBar(mouseX, mouseY);
        final int s = Math.round(scroll.get());
        final int hoverIdx = over && !barHover ? indexAt(mouseY) : -1;
        SlateDraw.scissor(g, x, y, getWidth(), h);
        final int first = Math.max(0, s / stride());
        final int last = Math.min(items.size() - 1, (s + h) / stride() + 1);
        for (int i = first; i <= last; i++) {
            final int ry = y + i * stride() - s;
            final boolean hov = i == hoverIdx, sel = i == selected;
            if (rowBackgrounds) {
                if (t.isVanilla()) {
                    if (hov || sel) g.fill(x, ry, x + w, ry + rowHeight, Colors.scaleAlpha(sel ? 0x40000000 : 0x20FFFFFF, a));
                    if (sel) SlateDraw.outline(g, x, ry, w, rowHeight, Colors.scaleAlpha(isFocused() ? 0xFFFFFFFF : 0xFF808080, a), 0);
                } else {
                    final int fill = sel ? p.surfaceActive() : hov ? p.surfaceHover() : 0;
                    if (fill != 0) SlateDraw.pixelRound(g, x, ry, w, rowHeight, Colors.scaleAlpha(fill, a), t.radius());
                    if (sel) {
                        SlateDraw.rect(g, x, ry + 2, 2, rowHeight - 4, Colors.scaleAlpha(p.accent(), a));
                        if (isFocused()) SlateDraw.outline(g, x, ry, w, rowHeight, Colors.scaleAlpha(Colors.withAlpha(p.accent(), 0x80), a), t.radius());
                    }
                }
            }
            renderer.render(g, items.get(i), i, x, ry, w, rowHeight, hov, sel, mouseX, mouseY);
        }
        SlateDraw.unscissor(g);
        if (scrollbarVisible()) {
            final int bx = x + getWidth() - BAR_W;
            final int barH = barHeight(), barY = barY();
            if (t.isVanilla()) {
                g.fill(bx, y, bx + BAR_W, y + h, 0xFF000000);
                g.fill(bx, barY, bx + BAR_W, barY + barH, draggingBar || barHover ? 0xFFA0A0A0 : 0xFF808080);
                g.fill(bx, barY, bx + BAR_W - 1, barY + barH - 1, draggingBar || barHover ? 0xFFE0E0E0 : 0xFFC0C0C0);
            } else {
                SlateDraw.pixelRound(g, bx, y, BAR_W, h, Colors.withAlpha(p.surface(), 0x80), 1);
                SlateDraw.pixelRound(g, bx, barY, BAR_W, barH, draggingBar ? p.accent() : barHover ? p.textMuted() : p.borderStrong(), 1);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {
        if (selected >= 0 && selected < items.size()) {
            out.add(NarratedElementType.POSITION, Component.translatable("narrator.position.list", selected + 1, items.size()));
        }
    }
}
