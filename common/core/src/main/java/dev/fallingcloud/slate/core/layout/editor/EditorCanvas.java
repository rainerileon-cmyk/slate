package dev.fallingcloud.slate.core.layout.editor;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ElementType;
import dev.fallingcloud.slate.core.layout.ScreenLayout;
import dev.fallingcloud.slate.core.layout.ui.Anchor;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.widget.SlateContextMenu;
import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The editable surface: hit-testing, selection (click, Shift-click, rubber band), moving and resizing
 * with snapping and alignment guides, nudging, z-order, clipboard, and the overlay drawing for all of
 * it. Geometry edits mutate the working layout through {@link #commitRect} and then re-apply, so the
 * screen always shows what the layout says; during a drag the live widgets are moved directly so the
 * feedback is immediate and the applier runs once on release.
 */
final class EditorCanvas {

    interface Listener {
        void selectionChanged();

        /** The item list was rebuilt; {@code structural} when elements were added/removed/reordered/replaced. */
        void itemsChanged(boolean structural);
    }

    enum Align { LEFT, CENTER, RIGHT, TOP, MIDDLE, BOTTOM }

    private enum Mode { NONE, PRESS, MOVE, RESIZE, BAND }

    private static final int DRAG_THRESHOLD = 3;
    private static final int GUIDE_TOLERANCE = 4;

    private final Screen screen;
    private final EditorSession session;
    private final Listener listener;
    private final EditorOverlay overlay;

    List<EditorItem> items = new ArrayList<>();
    Map<String, EditorItem> byId = new HashMap<>();
    /** Selected item ids in selection order; the last one is the primary. */
    final List<String> selection = new ArrayList<>();
    @Nullable String hover;
    boolean grid = true;
    boolean snap = true;

    private Mode mode = Mode.NONE;
    private double pressX, pressY;
    private int handle = -1;
    private boolean pressShift;
    @Nullable private String pressedId;
    private final Map<String, Rect> startRects = new LinkedHashMap<>();
    @Nullable private Rect band;
    private final List<Integer> guidesV = new ArrayList<>();
    private final List<Integer> guidesH = new ArrayList<>();

    EditorCanvas(final Screen screen, final EditorSession session, final EditorOverlay overlay, final Listener listener) {
        this.screen = screen;
        this.session = session;
        this.overlay = overlay;
        this.listener = listener;
    }

    // ------------------------------------------------------------------ items & selection

    /** Rebuild the item list from the screen (after any apply or re-init); keeps the selection by id. */
    void rebind() {
        try {
            items = EditorItems.bind(screen, session.layout());
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] editor: binding items failed", e);
            items = new ArrayList<>();
        }
        byId = new HashMap<>();
        for (final EditorItem it : items) byId.put(it.id, it);
        selection.removeIf(id -> !byId.containsKey(id));
        if (hover != null && !byId.containsKey(hover)) hover = null;
    }

    @Nullable
    EditorItem primary() {
        return selection.isEmpty() ? null : byId.get(selection.get(selection.size() - 1));
    }

    /** The current item for a layout element (items are rebuilt on every apply; elements persist). */
    @Nullable
    EditorItem itemOf(final ScreenLayout.Element e) {
        for (final EditorItem it : items) if (it.element == e) return it;
        return null;
    }

    @Nullable
    EditorItem itemOfKey(final String key) {
        return byId.get("w:" + key);
    }

    List<EditorItem> selected() {
        final List<EditorItem> out = new ArrayList<>();
        for (final String id : selection) { final EditorItem it = byId.get(id); if (it != null) out.add(it); }
        return out;
    }

    private List<EditorItem> selectedMovable() {
        final List<EditorItem> out = new ArrayList<>();
        for (final EditorItem it : selected()) if (!it.locked()) out.add(it);
        return out;
    }

    void select(final String id, final boolean additive) {
        if (!byId.containsKey(id)) return;
        if (!additive) selection.clear();
        selection.remove(id);
        selection.add(id);
        listener.selectionChanged();
    }

    void toggleSelect(final String id) {
        if (selection.remove(id)) { listener.selectionChanged(); return; }
        select(id, true);
    }

    void clearSelection() {
        if (selection.isEmpty()) return;
        selection.clear();
        listener.selectionChanged();
    }

    void selectAll() {
        selection.clear();
        for (final EditorItem it : items) if (it.hittable()) selection.add(it.id);
        listener.selectionChanged();
    }

    /** Tab: cycle through the hittable items. */
    void selectNext(final int dir) {
        final List<EditorItem> hit = new ArrayList<>();
        for (final EditorItem it : items) if (it.hittable()) hit.add(it);
        if (hit.isEmpty()) return;
        final EditorItem cur = primary();
        int i = cur == null ? -1 : hit.indexOf(cur);
        i = ((i + dir) % hit.size() + hit.size()) % hit.size();
        select(hit.get(i).id, false);
    }

    @Nullable
    EditorItem hitTest(final double mx, final double my) {
        for (int i = items.size() - 1; i >= 0; i--) {
            final EditorItem it = items.get(i);
            if (it.hittable() && it.rect.contains(mx, my)) return it;
        }
        return null;
    }

    private int handleAt(final double mx, final double my) {
        if (selection.size() != 1) return -1;
        final EditorItem it = primary();
        if (it == null || !it.hittable() || it.locked()) return -1;
        final int[][] pts = EditorStyle.handlePoints(it.rect);
        for (int i = 0; i < pts.length; i++) {
            if (Math.abs(mx - pts[i][0]) <= EditorStyle.HANDLE_HIT && Math.abs(my - pts[i][1]) <= EditorStyle.HANDLE_HIT) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ mouse

    void mouseClicked(final double mx, final double my, final int button) {
        if (button == 1) { contextMenu(mx, my); return; }
        if (button != 0) return;
        pressX = mx; pressY = my;
        guidesV.clear(); guidesH.clear();
        startRects.clear();
        pressShift = EditorKeys.shift();
        final int h = handleAt(mx, my);
        if (h >= 0) {
            final EditorItem it = primary();
            if (it == null) return;
            mode = Mode.RESIZE;
            handle = h;
            pressedId = it.id;
            startRects.put(it.id, it.rect);
            session.pushUndo(null);
            return;
        }
        final EditorItem hit = hitTest(mx, my);
        if (hit == null) {
            if (!pressShift) clearSelection();
            mode = Mode.BAND;
            band = null;
            return;
        }
        if (pressShift) {
            if (selection.contains(hit.id)) { selection.remove(hit.id); listener.selectionChanged(); mode = Mode.NONE; return; }
            selection.add(hit.id);
        } else if (!selection.contains(hit.id)) {
            selection.clear();
            selection.add(hit.id);
        } else {
            selection.remove(hit.id);
            selection.add(hit.id);                                   // clicked one becomes primary
        }
        listener.selectionChanged();
        mode = Mode.PRESS;
        pressedId = hit.id;
        for (final EditorItem it : selectedMovable()) if (it.hittable()) startRects.put(it.id, it.rect);
    }

    void mouseDragged(final double mx, final double my) {
        switch (mode) {
            case PRESS -> {
                if (Math.abs(mx - pressX) < DRAG_THRESHOLD && Math.abs(my - pressY) < DRAG_THRESHOLD) return;
                if (startRects.isEmpty()) return;
                mode = Mode.MOVE;
                session.pushUndo(null);
                dragMove(mx, my);
            }
            case MOVE -> dragMove(mx, my);
            case RESIZE -> dragResize(mx, my);
            case BAND -> {
                final int x0 = (int) Math.min(pressX, mx), y0 = (int) Math.min(pressY, my);
                final int x1 = (int) Math.max(pressX, mx), y1 = (int) Math.max(pressY, my);
                band = Rect.of(x0, y0, x1 - x0, y1 - y0);
            }
            default -> {}
        }
    }

    void mouseReleased(final double mx, final double my) {
        switch (mode) {
            case MOVE, RESIZE -> {
                for (final Map.Entry<String, Rect> en : startRects.entrySet()) {
                    final EditorItem it = byId.get(en.getKey());
                    if (it != null) commitRect(it, it.rect, mode == Mode.RESIZE, en.getValue());
                }
                overlay.applyLive();
            }
            case BAND -> {
                if (band != null && (band.w() > 2 || band.h() > 2)) {
                    if (!pressShift) selection.clear();
                    for (final EditorItem it : items) {
                        if (!it.hittable() || !intersects(it.rect, band)) continue;
                        selection.remove(it.id);
                        selection.add(it.id);
                    }
                    listener.selectionChanged();
                }
                band = null;
            }
            default -> {}
        }
        mode = Mode.NONE;
        handle = -1;
        startRects.clear();
        guidesV.clear();
        guidesH.clear();
    }

    boolean dragging() { return mode == Mode.MOVE || mode == Mode.RESIZE; }

    private static boolean intersects(final Rect a, final Rect b) {
        return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
    }

    private void dragMove(final double mx, final double my) {
        double dx = mx - pressX, dy = my - pressY;
        if (EditorKeys.shift()) { if (Math.abs(dx) > Math.abs(dy)) dy = 0; else dx = 0; }
        int ndx = (int) Math.round(dx), ndy = (int) Math.round(dy);
        final Rect ref = pressedId != null && startRects.containsKey(pressedId) ? startRects.get(pressedId) : startRects.values().iterator().next();
        final int[] adj = snapAdjust(ref.move(ndx, ndy), true, true, true, true, startRects.keySet());
        ndx += adj[0];
        ndy += adj[1];
        for (final Map.Entry<String, Rect> en : startRects.entrySet()) {
            final EditorItem it = byId.get(en.getKey());
            if (it != null) applyLive(it, en.getValue().move(ndx, ndy));
        }
    }

    private void dragResize(final double mx, final double my) {
        final EditorItem it = primary();
        if (it == null || !startRects.containsKey(it.id)) return;
        final Rect s = startRects.get(it.id);
        final int dx = (int) Math.round(mx - pressX), dy = (int) Math.round(my - pressY);
        int x = s.x(), y = s.y(), r = s.right(), b = s.bottom();
        final boolean left = handle == 0 || handle == 6 || handle == 7, right = handle == 2 || handle == 3 || handle == 4;
        final boolean top = handle == 0 || handle == 1 || handle == 2, bottom = handle == 4 || handle == 5 || handle == 6;
        if (left) x += dx;
        if (right) r += dx;
        if (top) y += dy;
        if (bottom) b += dy;
        final Rect cand = Rect.of(Math.min(x, r), Math.min(y, b), Math.abs(r - x), Math.abs(b - y));
        final int[] adj = snapAdjust(cand, left, right, top, bottom, Set.of(it.id));
        if (left) x += adj[0]; else if (right) r += adj[0];
        if (top) y += adj[1]; else if (bottom) b += adj[1];
        if (r - x < 2) { if (left) x = r - 2; else r = x + 2; }
        if (b - y < 2) { if (top) y = b - 2; else b = y + 2; }
        applyLive(it, Rect.of(x, y, r - x, b - y));
    }

    /** Move/resize the live widget so the drag is visible before the applier runs. */
    private void applyLive(final EditorItem it, final Rect r) {
        it.rect = r;
        if (it.widget != null) {
            it.widget.setX(r.x());
            it.widget.setY(r.y());
            it.widget.setWidth(Math.max(1, r.w()));
            it.widget.setHeight(Math.max(1, r.h()));
        }
    }

    // ------------------------------------------------------------------ snapping & guides

    /**
     * How far to shift a rect so its edges/centre align with other elements or the screen (within a few
     * pixels), else onto the snap grid. Fills the guide lists for drawing. Alt disables snapping.
     */
    private int[] snapAdjust(final Rect r, final boolean left, final boolean right, final boolean top, final boolean bottom, final Set<String> exclude) {
        guidesV.clear();
        guidesH.clear();
        if (EditorKeys.alt()) return new int[] { 0, 0 };
        final int sw = screen.width, sh = screen.height;
        final List<Integer> vs = new ArrayList<>(List.of(0, sw / 2, sw));
        final List<Integer> hs = new ArrayList<>(List.of(0, sh / 2, sh));
        for (final EditorItem it : items) {
            if (exclude.contains(it.id) || !it.hittable()) continue;
            final Rect o = it.rect;
            vs.add(o.x()); vs.add(o.x() + o.w() / 2); vs.add(o.right());
            hs.add(o.y()); hs.add(o.y() + o.h() / 2); hs.add(o.bottom());
        }
        final boolean moveX = left && right, moveY = top && bottom;
        final int[] edgesX = moveX ? new int[] { r.x(), r.x() + r.w() / 2, r.right() } : left ? new int[] { r.x() } : right ? new int[] { r.right() } : new int[0];
        final int[] edgesY = moveY ? new int[] { r.y(), r.y() + r.h() / 2, r.bottom() } : top ? new int[] { r.y() } : bottom ? new int[] { r.bottom() } : new int[0];
        int adjX = best(vs, edgesX), adjY = best(hs, edgesY);
        final int gridStep = Math.max(1, Slate.config().devSnap);
        if (adjX == Integer.MIN_VALUE) adjX = snap && edgesX.length > 0 ? snapTo(edgesX[0], gridStep) - edgesX[0] : 0;
        if (adjY == Integer.MIN_VALUE) adjY = snap && edgesY.length > 0 ? snapTo(edgesY[0], gridStep) - edgesY[0] : 0;
        // Every edge that now sits on a line gets a guide (an element can be aligned on two at once).
        for (final int e : edgesX) if (vs.contains(e + adjX)) guidesV.add(e + adjX);
        for (final int e : edgesY) if (hs.contains(e + adjY)) guidesH.add(e + adjY);
        return new int[] { adjX, adjY };
    }

    private static int best(final List<Integer> lines, final int[] edges) {
        int best = Integer.MIN_VALUE;
        for (final int line : lines) {
            for (final int e : edges) {
                final int d = line - e;
                if (Math.abs(d) <= GUIDE_TOLERANCE && (best == Integer.MIN_VALUE || Math.abs(d) < Math.abs(best))) best = d;
            }
        }
        return best;
    }

    private static int snapTo(final int v, final int step) {
        return Math.round(v / (float) step) * step;
    }

    // ------------------------------------------------------------------ placements

    Anchor currentAnchor(final EditorItem it) {
        if (it.element != null) return Anchor.parse(it.element.place.anchor, Anchor.TOP_LEFT);
        final ScreenLayout.Placement p = it.key == null ? null : session.layout().moved.get(it.key);
        if (p != null) return Anchor.parse(p.anchor, Anchor.TOP_LEFT);
        return Anchor.nearest(it.rect.centerX(), it.rect.centerY(), screen.width, screen.height);
    }

    /** Whether the item's anchor was picked automatically (equals the nearest one for where it sits). */
    boolean anchorIsAuto(final EditorItem it, final Rect at) {
        if (it.element == null && (it.key == null || !session.layout().moved.containsKey(it.key))) return true;
        return currentAnchor(it) == Anchor.nearest(at.centerX(), at.centerY(), screen.width, screen.height);
    }

    /**
     * Write a screen rectangle into the layout as an anchored placement. Automatic anchors follow the
     * element (nearest corner/edge/centre); an anchor the user picked by hand sticks.
     */
    void commitRect(final EditorItem it, final Rect r, final boolean resized, final Rect before) {
        final int sw = screen.width, sh = screen.height;
        final Anchor a = anchorIsAuto(it, before) ? Anchor.nearest(r.centerX(), r.centerY(), sw, sh) : currentAnchor(it);
        writePlacement(it, r, a, resized);
    }

    private void writePlacement(final EditorItem it, final Rect r, final Anchor a, final boolean resized) {
        final ScreenLayout.Placement p;
        if (it.element != null) p = it.element.place;
        else if (it.key != null) p = session.layout().moved.computeIfAbsent(it.key, k -> new ScreenLayout.Placement());
        else return;
        p.anchor = a.name();
        p.x = a.offsetX(screen.width, r.x(), r.w());
        p.y = a.offsetY(screen.height, r.y(), r.h());
        if (resized || p.w > 0) p.w = r.w();
        if (resized || p.h > 0) p.h = r.h();
        it.rect = r;
    }

    /** Manual anchor choice; {@code null} = automatic (nearest). The element stays where it is. */
    void setAnchor(final EditorItem it, @Nullable final Anchor anchor) {
        session.pushUndo(null);
        final Anchor a = anchor != null ? anchor : Anchor.nearest(it.rect.centerX(), it.rect.centerY(), screen.width, screen.height);
        writePlacement(it, it.rect, a, false);
        overlay.applyLive();
    }

    /** Explicit geometry from the properties panel (absolute screen coordinates). */
    void setGeometry(final EditorItem it, final Rect r, final boolean resized, final String undoKey) {
        session.pushUndo(undoKey);
        commitRect(it, r, resized, it.rect);
        overlay.applyLive();
    }

    void nudge(final int dx, final int dy) {
        final List<EditorItem> sel = selectedMovable();
        if (sel.isEmpty()) return;
        session.pushUndo("nudge");
        for (final EditorItem it : sel) commitRect(it, it.rect.move(dx, dy), false, it.rect);
        overlay.applyLive();
    }

    void align(final Align a) {
        final List<EditorItem> sel = selectedMovable();
        if (sel.isEmpty()) return;
        Rect b;
        if (sel.size() >= 2) {
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
            for (final EditorItem it : sel) { x0 = Math.min(x0, it.rect.x()); y0 = Math.min(y0, it.rect.y()); x1 = Math.max(x1, it.rect.right()); y1 = Math.max(y1, it.rect.bottom()); }
            b = Rect.of(x0, y0, x1 - x0, y1 - y0);
        } else {
            b = Rect.of(0, 0, screen.width, screen.height);
        }
        session.pushUndo(null);
        for (final EditorItem it : sel) {
            final Rect r = it.rect;
            final Rect n = switch (a) {
                case LEFT -> r.withX(b.x());
                case CENTER -> r.withX(b.x() + (b.w() - r.w()) / 2);
                case RIGHT -> r.withX(b.right() - r.w());
                case TOP -> r.withY(b.y());
                case MIDDLE -> r.withY(b.y() + (b.h() - r.h()) / 2);
                case BOTTOM -> r.withY(b.bottom() - r.h());
            };
            commitRect(it, n, false, r);
        }
        overlay.applyLive();
    }

    // ------------------------------------------------------------------ structure

    void setVisible(final EditorItem it, final boolean visible) {
        session.pushUndo(null);
        if (it.element != null) it.element.visible = visible;
        else if (it.key != null) {
            if (visible) session.layout().hidden.remove(it.key);
            else if (!session.layout().hidden.contains(it.key)) session.layout().hidden.add(it.key);
        }
        overlay.applyLive();
    }

    void setLocked(final EditorItem it, final boolean locked) {
        if (it.element == null) return;
        session.pushUndo(null);
        it.element.locked = locked;
        overlay.applyLive();
    }

    /** Vanilla widget back to where the screen put it. */
    void resetVanilla(final EditorItem it) {
        if (it.key == null) return;
        session.pushUndo(null);
        session.layout().moved.remove(it.key);
        session.layout().hidden.remove(it.key);
        overlay.applyLive();
    }

    void deleteSelected() {
        final List<EditorItem> sel = selected();
        if (sel.isEmpty()) return;
        session.pushUndo(null);
        for (final EditorItem it : sel) {
            if (it.element != null) session.layout().elements.remove(it.element);
            else if (it.key != null && !session.layout().hidden.contains(it.key)) session.layout().hidden.add(it.key);
        }
        selection.clear();
        overlay.applyStructural();
        listener.selectionChanged();
    }

    void addElement(final ElementType type) {
        session.pushUndo(null);
        final ScreenLayout.Element e = session.newElement(type);
        session.layout().elements.add(e);
        selection.clear();
        selection.add("e:" + e.id);
        overlay.applyStructural();
        listener.selectionChanged();
    }

    boolean renameElement(final EditorItem it, final String newId) {
        if (it.element == null || newId.isBlank() || session.idInUse(newId, it.element)) return false;
        session.pushUndo("rename:" + System.identityHashCode(it.element));
        it.element.id = newId;
        final int i = selection.indexOf(it.id);
        if (i >= 0) selection.set(i, "e:" + newId);
        overlay.applyLive();
        return true;
    }

    void copy() {
        final List<ScreenLayout.Element> out = new ArrayList<>();
        for (final EditorItem it : orderedSelectedElements()) out.add(it.element.copy());
        if (out.isEmpty()) return;
        EditorSession.CLIPBOARD.clear();
        EditorSession.CLIPBOARD.addAll(out);
    }

    void paste() {
        if (EditorSession.CLIPBOARD.isEmpty()) return;
        pasteElements(EditorSession.CLIPBOARD);
    }

    void duplicate() {
        final List<ScreenLayout.Element> src = new ArrayList<>();
        for (final EditorItem it : orderedSelectedElements()) src.add(it.element);
        if (src.isEmpty()) return;
        pasteElements(src);
    }

    /** Custom elements of the selection in z-order (layout order). */
    private List<EditorItem> orderedSelectedElements() {
        final List<EditorItem> out = new ArrayList<>();
        for (final EditorItem it : items) if (it.element != null && selection.contains(it.id)) out.add(it);
        return out;
    }

    private void pasteElements(final List<ScreenLayout.Element> src) {
        session.pushUndo(null);
        selection.clear();
        for (final ScreenLayout.Element s : src) {
            final ScreenLayout.Element e = s.copy();
            e.id = session.newElementId(s.id);
            e.place.x += 8;
            e.place.y += 8;
            session.layout().elements.add(e);
            selection.add("e:" + e.id);
        }
        overlay.applyStructural();
        listener.selectionChanged();
    }

    /** Move a custom element to another index of the element list (z-order; 0 = bottom). */
    void reorderElement(final EditorItem it, final int newIndex) {
        if (it.element == null) return;
        final List<ScreenLayout.Element> els = session.layout().elements;
        final int cur = els.indexOf(it.element);
        if (cur < 0) return;
        final int target = Math.max(0, Math.min(els.size() - 1, newIndex));
        if (target == cur) return;
        session.pushUndo(null);
        els.remove(cur);
        els.add(target, it.element);
        overlay.applyStructural();
    }

    void zOrder(final EditorItem it, final int delta, final boolean extreme) {
        if (it.element == null) return;
        final List<ScreenLayout.Element> els = session.layout().elements;
        final int cur = els.indexOf(it.element);
        if (cur < 0) return;
        reorderElement(it, extreme ? (delta > 0 ? els.size() - 1 : 0) : cur + delta);
    }

    // ------------------------------------------------------------------ context menu

    private void contextMenu(final double mx, final double my) {
        final EditorItem hit = hitTest(mx, my);
        final List<MenuPopup.Item> menu = new ArrayList<>();
        if (hit != null) {
            if (!selection.contains(hit.id)) select(hit.id, false);
            final boolean custom = hit.element != null;
            menu.add(MenuPopup.Item.of(EditorText.t("menu.properties"), Icon.SLIDERS, overlay::showProperties));
            if (custom) menu.add(MenuPopup.Item.of(EditorText.t("menu.duplicate"), Icon.DUPLICATE, this::duplicate));
            menu.add(MenuPopup.Item.of(EditorText.t("menu.copy"), Icon.COPY, this::copy));
            menu.add(EditorSession.CLIPBOARD.isEmpty() ? MenuPopup.Item.disabled(EditorText.t("menu.paste"), Icon.IMPORT)
                : MenuPopup.Item.of(EditorText.t("menu.paste"), Icon.IMPORT, this::paste));
            menu.add(MenuPopup.Item.sep());
            if (custom) {
                menu.add(MenuPopup.Item.of(EditorText.t("menu.to_front"), Icon.ARROW_UP, () -> zOrder(hit, 1, true)));
                menu.add(MenuPopup.Item.of(EditorText.t("menu.forward"), Icon.CHEVRON_UP, () -> zOrder(hit, 1, false)));
                menu.add(MenuPopup.Item.of(EditorText.t("menu.backward"), Icon.CHEVRON_DOWN, () -> zOrder(hit, -1, false)));
                menu.add(MenuPopup.Item.of(EditorText.t("menu.to_back"), Icon.ARROW_DOWN, () -> zOrder(hit, -1, true)));
                menu.add(MenuPopup.Item.sep());
                menu.add(MenuPopup.Item.of(hit.locked() ? EditorText.t("menu.unlock") : EditorText.t("menu.lock"), hit.locked() ? Icon.UNLOCK : Icon.LOCK, () -> setLocked(hit, !hit.locked())));
            }
            menu.add(MenuPopup.Item.of(EditorText.t("menu.hide"), Icon.EYE_OFF, () -> setVisible(hit, false)));
            if (custom) menu.add(MenuPopup.Item.danger(EditorText.t("menu.delete"), Icon.TRASH, this::deleteSelected));
            else menu.add(MenuPopup.Item.of(EditorText.t("menu.reset_widget"), Icon.REFRESH, () -> resetVanilla(hit)));
        } else {
            menu.add(MenuPopup.Item.of(EditorText.t("menu.add"), Icon.PLUS, () -> overlay.openAddPalette((int) mx, (int) my)));
            menu.add(EditorSession.CLIPBOARD.isEmpty() ? MenuPopup.Item.disabled(EditorText.t("menu.paste"), Icon.IMPORT)
                : MenuPopup.Item.of(EditorText.t("menu.paste"), Icon.IMPORT, this::paste));
            menu.add(MenuPopup.Item.of(EditorText.t("menu.select_all"), Icon.GRID, this::selectAll));
            menu.add(MenuPopup.Item.sep());
            menu.add(MenuPopup.Item.of(EditorText.t("menu.background"), Icon.IMAGE, overlay::showBackground));
        }
        SlateContextMenu.open(mx, my, menu);
    }

    // ------------------------------------------------------------------ render

    void render(final GuiGraphics g, final int mouseX, final int mouseY, final boolean mouseOverChrome) {
        final int sw = screen.width, sh = screen.height;
        if (grid) EditorStyle.grid(g, sw, sh, Slate.config().devSnap);
        // Screens may move their own widgets (animations, layouts); follow them unless we are the mover.
        if (!dragging()) {
            for (final EditorItem it : items) {
                if (it.widget != null) it.rect = Rect.of(it.widget.getX(), it.widget.getY(), it.widget.getWidth(), it.widget.getHeight());
            }
        }

        if (mouseOverChrome || mode == Mode.MOVE || mode == Mode.RESIZE || mode == Mode.BAND) hover = null;
        else { final EditorItem h = hitTest(mouseX, mouseY); hover = h == null ? null : h.id; }

        final int phase = EditorStyle.dashPhase();
        final int accent = EditorStyle.accent();
        for (final String id : selection) {
            final EditorItem it = byId.get(id);
            if (it == null) continue;
            final Rect r = it.rect;
            if (!it.hittable()) {
                EditorStyle.dashedRect(g, r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2, EditorStyle.ghost(), 0);
                continue;
            }
            EditorStyle.dashedRect(g, r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2, accent, phase);
            if (it.locked()) Icons.draw(g, Icon.LOCK, r.right() - 9, r.y() + 1, 8, accent);
        }
        if (selection.size() == 1) {
            final EditorItem it = primary();
            if (it != null && it.hittable() && !it.locked()) EditorStyle.handles(g, it.rect);
        }
        if (hover != null && !selection.contains(hover)) {
            final EditorItem it = byId.get(hover);
            if (it != null) SlateDraw.outline(g, it.rect.x() - 1, it.rect.y() - 1, it.rect.w() + 2, it.rect.h() + 2, EditorStyle.hoverOutline(), 0);
        }
        if (hover != null) {
            final EditorItem it = byId.get(hover);
            if (it != null) {
                final Component label = Component.empty().append(it.name()).append("  ").append(Component.literal(it.rect.w() + "×" + it.rect.h()).withStyle(s -> s.withColor(EditorStyle.muted() & 0xFFFFFF)));
                final int ly = it.rect.y() - 14 < EditorStyle.TOOLBAR_H ? it.rect.bottom() + 2 : it.rect.y() - 14;
                EditorStyle.labelPill(g, label, Math.max(2, Math.min(it.rect.x(), sw - 60)), ly);
            }
        }
        final int guide = EditorStyle.guide();
        for (final int x : guidesV) SlateDraw.vline(g, x, 0, sh, guide);
        for (final int y : guidesH) SlateDraw.hline(g, 0, y, sw, guide);
        if ((mode == Mode.MOVE || mode == Mode.RESIZE) && pressedId != null) {
            final EditorItem it = byId.get(pressedId);
            if (it != null) {
                final Rect r = it.rect;
                final Component txt = Component.literal(r.x() + ", " + r.y() + "   " + r.w() + "×" + r.h());
                EditorStyle.labelPill(g, txt, Math.min(r.right() + 4, sw - 70), Math.min(r.bottom() + 2, sh - 14));
            }
        }
        if (mode == Mode.BAND && band != null) {
            g.fill(band.x(), band.y(), band.right(), band.bottom(), EditorStyle.bandFill());
            SlateDraw.outline(g, band.x(), band.y(), Math.max(1, band.w()), Math.max(1, band.h()), EditorStyle.hoverOutline(), 0);
        }
    }
}
