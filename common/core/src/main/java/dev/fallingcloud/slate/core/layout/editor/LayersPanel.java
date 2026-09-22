package dev.fallingcloud.slate.core.layout.editor;

import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.theme.Colors;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The left-hand layer list: custom elements top-most first (drag a row to change z-order), then the
 * screen's own widgets. Each row has a visibility eye and, for custom elements, a lock; clicking the
 * name selects (Shift adds).
 */
final class LayersPanel {

    private static final int ROW = 16, TITLE_H = 18;

    private record Row(@Nullable EditorItem item, @Nullable Component header) {}

    private final EditorOverlay ed;
    private final List<Row> rows = new ArrayList<>();
    private Rect bounds = Rect.of(0, 0, 10, 10);
    private int scroll;
    private int pressRow = -1;
    private double pressY;
    private boolean dragging;
    private int dropSlot = -1;

    LayersPanel(final EditorOverlay ed) {
        this.ed = ed;
    }

    void setBounds(final Rect r) { bounds = r; clampScroll(); }

    Rect bounds() { return bounds; }

    boolean contains(final double mx, final double my) { return bounds.contains(mx, my); }

    void refresh() {
        rows.clear();
        final List<EditorItem> items = ed.canvas().items;
        final List<EditorItem> custom = new ArrayList<>(), vanilla = new ArrayList<>();
        for (final EditorItem it : items) (it.isCustom() ? custom : vanilla).add(it);
        rows.add(new Row(null, EditorText.t("layers.elements")));
        for (int i = custom.size() - 1; i >= 0; i--) rows.add(new Row(custom.get(i), null));
        rows.add(new Row(null, EditorText.t("layers.widgets")));
        for (int i = vanilla.size() - 1; i >= 0; i--) rows.add(new Row(vanilla.get(i), null));
        clampScroll();
    }

    private int customCount() {
        int n = 0;
        for (final Row r : rows) if (r.item != null && r.item.isCustom()) n++;
        return n;
    }

    private int listTop() { return bounds.y() + TITLE_H; }

    private int listHeight() { return bounds.h() - TITLE_H - 2; }

    private int maxScroll() { return Math.max(0, rows.size() * ROW - listHeight()); }

    private void clampScroll() { scroll = Math.max(0, Math.min(scroll, maxScroll())); }

    private int rowAt(final double my) {
        if (my < listTop() || my >= listTop() + listHeight()) return -1;
        final int i = (int) ((my - listTop() + scroll) / ROW);
        return i >= 0 && i < rows.size() ? i : -1;
    }

    // ------------------------------------------------------------------ input

    boolean mouseClicked(final double mx, final double my, final int button) {
        pressRow = -1;
        dragging = false;
        final int i = rowAt(my);
        if (i < 0) return true;
        final Row r = rows.get(i);
        if (r.item == null) return true;
        final EditorItem it = r.item;
        final EditorCanvas c = ed.canvas();
        final int x0 = bounds.x() + 4;
        if (button == 0 && mx >= x0 && mx < x0 + 12) { c.setVisible(it, !it.visible()); return true; }
        if (button == 0 && it.isCustom() && mx >= x0 + 12 && mx < x0 + 24) { c.setLocked(it, !it.locked()); return true; }
        if (button == 0) {
            if (EditorKeys.shift()) c.toggleSelect(it.id);
            else c.select(it.id, false);
            if (it.isCustom()) { pressRow = i; pressY = my; }
        }
        return true;
    }

    void mouseDragged(final double mx, final double my) {
        if (pressRow < 0) return;
        if (!dragging && Math.abs(my - pressY) > 3) dragging = true;
        if (!dragging) return;
        final int n = customCount();
        final double pos = (my - listTop() + scroll) / ROW - 1;             // relative to the first custom row
        dropSlot = (int) Math.max(0, Math.min(n, Math.round(pos)));
    }

    void mouseReleased(final double mx, final double my) {
        if (dragging && pressRow >= 0 && pressRow < rows.size() && dropSlot >= 0) {
            final EditorItem it = rows.get(pressRow).item;
            if (it != null && it.isCustom()) {
                final int n = customCount();
                final int display = pressRow - 1;                                 // index among custom rows
                final int slot = display < dropSlot ? dropSlot - 1 : dropSlot;
                ed.canvas().reorderElement(it, (n - 1) - slot);
            }
        }
        pressRow = -1;
        dragging = false;
        dropSlot = -1;
    }

    boolean mouseScrolled(final double mx, final double my, final double sx, final double sy) {
        if (maxScroll() == 0) return false;
        scroll = (int) Math.max(0, Math.min(maxScroll(), scroll - sy * ROW));
        return true;
    }

    // ------------------------------------------------------------------ render

    void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final int x = bounds.x(), y = bounds.y(), w = bounds.w(), h = bounds.h();
        EditorStyle.panel(g, x, y, w, h);
        g.drawString(SlateDraw.font(), Fonts.heading(EditorText.t("layers.title")), x + 6, y + 5, EditorStyle.text(), EditorStyle.vanilla());
        final int top = listTop(), lh = listHeight();
        final int hoverRow = contains(mouseX, mouseY) ? rowAt(mouseY) : -1;
        final List<String> selection = ed.canvas().selection;
        SlateDraw.scissor(g, x + 1, top, w - 2, lh);
        int ry = top - scroll;
        for (int i = 0; i < rows.size(); i++, ry += ROW) {
            if (ry + ROW < top || ry > top + lh) continue;
            final Row r = rows.get(i);
            if (r.item == null) {
                g.drawString(SlateDraw.font(), r.header, x + 6, ry + 4, EditorStyle.dim(), false);
                SlateDraw.hline(g, x + 4, ry + ROW - 2, w - 8, Colors.withAlpha(EditorStyle.panelBorder(), 0x80));
                continue;
            }
            final EditorItem it = r.item;
            final boolean sel = selection.contains(it.id), hov = i == hoverRow;
            if (sel) { SlateDraw.rect(g, x + 2, ry, w - 4, ROW, EditorStyle.rowActive()); SlateDraw.rect(g, x + 2, ry + 2, 2, ROW - 4, EditorStyle.accent()); }
            else if (hov) SlateDraw.rect(g, x + 2, ry, w - 4, ROW, EditorStyle.rowHover());
            final int x0 = x + 4;
            final boolean vis = it.visible();
            Icons.draw(g, vis ? Icon.EYE : Icon.EYE_OFF, x0 + 1, ry + 3, 10, vis ? EditorStyle.muted() : EditorStyle.dim());
            int tx = x0 + 14;
            if (it.isCustom()) {
                Icons.draw(g, it.locked() ? Icon.LOCK : Icon.UNLOCK, tx, ry + 3, 10, it.locked() ? EditorStyle.accent() : Colors.withAlpha(EditorStyle.dim(), 0x80));
                tx += 13;
            }
            Icons.draw(g, it.icon(), tx, ry + 3, 10, vis ? EditorStyle.muted() : EditorStyle.dim());
            tx += 13;
            final int fg = !vis ? EditorStyle.dim() : sel ? EditorStyle.text() : EditorStyle.muted();
            g.drawString(SlateDraw.font(), SlateDraw.truncate(it.name(), x + w - 8 - tx), tx, ry + 4, fg, false);
        }
        if (dragging && dropSlot >= 0) {
            final int ly = top - scroll + (1 + dropSlot) * ROW;
            SlateDraw.rect(g, x + 4, ly - 1, w - 8, 2, EditorStyle.accent());
        }
        SlateDraw.unscissor(g);
        final int max = maxScroll();
        if (max > 0) {
            final int barH = Math.max(10, lh * lh / (rows.size() * ROW));
            final int by = top + (lh - barH) * scroll / max;
            SlateDraw.rect(g, x + w - 4, by, 2, barH, Colors.withAlpha(EditorStyle.dim(), 0xA0));
        }
    }
}
