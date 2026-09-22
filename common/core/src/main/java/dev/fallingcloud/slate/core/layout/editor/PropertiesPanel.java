package dev.fallingcloud.slate.core.layout.editor;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.layout.ElementType;
import dev.fallingcloud.slate.core.layout.LayoutBackground;
import dev.fallingcloud.slate.core.layout.LayoutStore;
import dev.fallingcloud.slate.core.layout.ScreenLayout;
import dev.fallingcloud.slate.core.layout.action.ActionType;
import dev.fallingcloud.slate.core.layout.action.ActionType.Arg;
import dev.fallingcloud.slate.core.layout.action.Actions;
import dev.fallingcloud.slate.core.layout.ui.Anchor;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateSeparator;
import dev.fallingcloud.slate.core.widget.SlateToggle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The right-hand panel. Shows, depending on the selection: a custom element's identity, anchor,
 * geometry, flags, type properties and actions; a vanilla widget's geometry, visibility and reset; the
 * alignment tools for a multi-selection; or, with nothing selected, the screen itself (background,
 * stats, reset). Every edit applies live. The panel is only rebuilt when the selection or the layout
 * structure changes, so typing in a field never loses focus; geometry fields refresh in place.
 */
final class PropertiesPanel {

    private final EditorOverlay ed;
    private final SlateScrollPanel panel = new SlateScrollPanel(0, 0, 10, 10);
    private Rect bounds = Rect.of(0, 0, 10, 10);
    private boolean refreshing;

    @Nullable private EditorTextField fx, fy, fw, fh;
    @Nullable private SlateDropdown<String> anchorDd;
    @Nullable private ScreenLayout.Element shownElement;
    @Nullable private String shownKey;
    private int shownCount = -1;
    private Icon titleIcon = Icon.PANEL;
    private boolean titleIconShown;

    PropertiesPanel(final EditorOverlay ed) {
        this.ed = ed;
        panel.padding(4).scrollStep(30);
    }

    // ------------------------------------------------------------------ geometry & plumbing

    void setBounds(final Rect r) {
        final boolean changed = r.w() != bounds.w() || r.h() != bounds.h();
        bounds = r;
        panel.setX(r.x());
        panel.setY(r.y() + 2);
        panel.setWidth(r.w());
        panel.setHeight(r.h() - 2);
        if (changed) rebuild();
    }

    Rect bounds() { return bounds; }

    boolean contains(final double mx, final double my) { return bounds.contains(mx, my); }

    boolean hasFocus() { return panel.getFocused() != null; }

    boolean textInputFocused() {
        final GuiEventListener f = panel.getFocused();
        if (f instanceof EditBox eb) return eb.isFocused();
        if (f instanceof MultiLineField m) return m.isFocused();
        if (f instanceof ColorRow c) return c.textFocused();
        return false;
    }

    /** A one-line field has focus (Enter commits it); multi-line boxes need Enter for new lines. */
    boolean singleLineFocused() {
        final GuiEventListener f = panel.getFocused();
        return (f instanceof EditBox eb && eb.isFocused()) || (f instanceof ColorRow c && c.textFocused());
    }

    void clearFocus() { panel.setFocused(null); }

    void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        EditorStyle.panel(g, bounds.x(), bounds.y(), bounds.w(), bounds.h());
        panel.render(g, mouseX, mouseY, partialTick);
        if (titleIconShown && panel.scrollAmount() < 4) Icons.draw(g, titleIcon, bounds.x() + 6, bounds.y() + 7 - (int) Math.round(panel.scrollAmount()), 12, EditorStyle.accent());
    }

    boolean mouseClicked(final double mx, final double my, final int button) {
        final boolean r = panel.mouseClicked(mx, my, button);
        if (!r) panel.setFocused(null);
        return r;
    }

    void mouseReleased(final double mx, final double my, final int button) { panel.mouseReleased(mx, my, button); }

    void mouseDragged(final double mx, final double my, final int button, final double dx, final double dy) { panel.mouseDragged(mx, my, button, dx, dy); }

    boolean mouseScrolled(final double mx, final double my, final double sx, final double sy) { return panel.mouseScrolled(mx, my, sx, sy); }

    boolean keyPressed(final int key, final int scan, final int mods) { return panel.keyPressed(key, scan, mods); }

    boolean charTyped(final char c, final int mods) { return panel.charTyped(c, mods); }

    // ------------------------------------------------------------------ building

    /** Vertical cursor + helpers for stacking rows inside the scroll panel. */
    private final class Rows {
        final int x = 6;
        final int w;
        int y = 6;

        Rows(final int w) { this.w = w; }

        <T extends AbstractWidget> T add(final T widget, final int height) {
            if (widget instanceof EditorTextField) panel.add(widget, x + EditorTextField.PAD_X, y + EditorTextField.PAD_Y);
            else panel.add(widget, x, y);
            y += height + 4;
            return widget;
        }

        void pair(final AbstractWidget a, final AbstractWidget b, final int height) {
            final int half = w / 2 - 2;
            placeAt(a, x, y, half);
            placeAt(b, x + half + 4, y, half);
            y += height + 4;
        }

        private void placeAt(final AbstractWidget wd, final int px, final int py, final int width) {
            if (wd instanceof EditorTextField) panel.add(wd, px + EditorTextField.PAD_X, py + EditorTextField.PAD_Y);
            else { wd.setWidth(width); panel.add(wd, px, py); }
        }

        void title(final Component text, @Nullable final Icon icon) {
            final int off = icon != null ? 16 : 0;
            titleIcon = icon != null ? icon : Icon.PANEL;
            titleIconShown = icon != null;
            panel.add(new SlateLabel(0, 0, w - off, text).style(SlateLabel.Style.TITLE), x + off, y);
            y += 14;
        }

        void caption(final Component text) {
            panel.add(new SlateLabel(0, 0, w, text).style(SlateLabel.Style.CAPTION), x, y);
            y += 11;
        }

        void caption2(final Component a, final Component b) {
            final int half = w / 2 - 2;
            panel.add(new SlateLabel(0, 0, half, a).style(SlateLabel.Style.CAPTION), x, y);
            panel.add(new SlateLabel(0, 0, half, b).style(SlateLabel.Style.CAPTION), x + half + 4, y);
            y += 11;
        }

        void separator(final Component text) {
            y += 2;
            panel.add(new SlateSeparator(0, 0, w, text), x, y);
            y += 14;
        }

        void gap(final int px) { y += px; }
    }

    void rebuild() {
        final double scroll = panel.scrollAmount();
        panel.clear();
        fx = fy = fw = fh = null;
        anchorDd = null;
        shownElement = null;
        shownKey = null;
        titleIconShown = false;
        final EditorCanvas c = ed.canvas();
        final Rows rows = new Rows(Math.max(40, bounds.w() - 18));
        shownCount = c.selection.size();
        try {
            if (c.selection.isEmpty()) buildScreen(rows);
            else if (c.selection.size() > 1) buildMulti(rows, c.selected());
            else {
                final EditorItem it = c.primary();
                if (it == null) buildScreen(rows);
                else if (it.element != null) buildElement(rows, it.element, it.type);
                else buildVanilla(rows, it);
            }
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] editor: properties panel failed", e);
            rows.caption(Component.literal(e.toString()));
        }
        panel.setContentHeight(rows.y + 6);
        panel.snapScroll(Math.min(scroll, panel.maxScroll()));
    }

    /** Geometry fields and anchor follow the selection without a rebuild; anything else rebuilds. */
    void refreshGeometry() {
        final EditorCanvas c = ed.canvas();
        final EditorItem it = c.selection.size() == 1 ? c.primary() : null;
        final boolean same = it != null && c.selection.size() == shownCount
            && (it.element != null ? it.element == shownElement : it.key != null && it.key.equals(shownKey));
        if (!same) { rebuild(); return; }
        refreshing = true;
        try {
            final Rect r = it.rect;
            setIfIdle(fx, r.x()); setIfIdle(fy, r.y()); setIfIdle(fw, r.w()); setIfIdle(fh, r.h());
            if (anchorDd != null) anchorDd.setValue(anchorName(it));
        } finally {
            refreshing = false;
        }
    }

    private static void setIfIdle(@Nullable final EditorTextField f, final int v) {
        if (f == null || f.isFocused()) return;
        final String s = Integer.toString(v);
        if (!s.equals(f.getValue())) f.setValue(s);
    }

    private String anchorName(final EditorItem it) {
        if (it.element != null) return it.element.place.anchor == null ? "AUTO" : it.element.place.anchor.toUpperCase(Locale.ROOT);
        final ScreenLayout.Placement p = it.key == null ? null : ed.session().layout().moved.get(it.key);
        return p == null || p.anchor == null ? "AUTO" : p.anchor.toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ custom element

    private void buildElement(final Rows rows, final ScreenLayout.Element e, @Nullable final ElementType type) {
        shownElement = e;
        final EditorCanvas c = ed.canvas();
        final Supplier<EditorItem> cur = () -> c.itemOf(e);
        rows.title(type != null ? type.label() : Component.literal(e.type), type != null ? type.icon() : Icon.QUESTION);
        rows.caption(EditorText.t("props.custom_element"));

        rows.caption(EditorText.t("props.id"));
        final EditorTextField idField = rows.add(new EditorTextField(0, 0, rows.w, EditorText.t("props.id")).text(e.id), EditorForms.FIELD_H);
        idField.onChange(v -> {
            if (refreshing) return;
            final String nv = v.trim();
            final boolean ok = nv.matches("[A-Za-z0-9_.-]+") && (nv.equals(e.id) || !ed.session().idInUse(nv, e));
            idField.setInvalid(!ok);
            final EditorItem it = cur.get();
            if (ok && it != null && !nv.equals(e.id)) c.renameElement(it, nv);
        });

        anchorRow(rows, cur);
        geometryRows(rows, cur);
        rows.pair(new SlateToggle(0, 0, 10, EditorText.t("props.visible"), e.visible, v -> { final EditorItem it = cur.get(); if (it != null && !refreshing) c.setVisible(it, v); }),
                  new SlateToggle(0, 0, 10, EditorText.t("props.locked"), e.locked, v -> { final EditorItem it = cur.get(); if (it != null && !refreshing) c.setLocked(it, v); }), 20);

        if (type != null && !type.props().isEmpty()) {
            rows.separator(EditorText.t("props.section_props"));
            for (final Arg arg : type.props()) {
                if (EditorForms.captioned(arg.kind())) rows.caption(arg.label());
                final String key = arg.key();
                rows.add(EditorForms.field(0, 0, rows.w, arg, e.props.getOrDefault(key, arg.defaultValue()), v -> {
                    if (refreshing) return;
                    ed.session().pushUndo("prop:" + e.id + ":" + key);
                    e.props.put(key, v);
                    ed.applyLive();
                }), EditorForms.height(arg.kind()));
            }
        }
        if (type == null || type.clickable()) buildActions(rows, e);

        rows.separator(EditorText.t("props.section_element"));
        rows.pair(new SlateButton(0, 0, 10, 18, EditorText.t("props.duplicate"), c::duplicate).icon(Icon.DUPLICATE).variant(SlateButton.Variant.GHOST),
                  new SlateButton(0, 0, 10, 18, EditorText.t("props.delete"), c::deleteSelected).icon(Icon.TRASH).variant(SlateButton.Variant.DANGER), 18);
    }

    private void buildActions(final Rows rows, final ScreenLayout.Element e) {
        rows.separator(EditorText.t("props.section_actions"));
        final List<ActionType> all = Actions.all();
        for (int i = 0; i < e.actions.size(); i++) {
            final int idx = i;
            final ScreenLayout.Action act = e.actions.get(i);
            final ActionType cur = Actions.get(act.type).orElse(null);
            final int y = rows.y;
            final int ddW = rows.w - 66;
            final SlateDropdown<ActionType> dd = new SlateDropdown<>(0, 0, ddW, all, cur, a -> a.label(), a -> {
                if (refreshing || a == null) return;
                ed.session().pushUndo(null);
                act.type = a.id();
                act.args = EditorSession.defaultArgs(a);
                ed.applyStructural();
            });
            panel.add(dd, rows.x, y);
            final SlateIconButton up = new SlateIconButton(0, 0, 20, Icon.ARROW_UP, EditorText.t("props.action_up"), () -> moveAction(e, idx, -1));
            up.enabled(idx > 0);
            panel.add(up, rows.x + ddW + 2, y);
            final SlateIconButton down = new SlateIconButton(0, 0, 20, Icon.ARROW_DOWN, EditorText.t("props.action_down"), () -> moveAction(e, idx, 1));
            down.enabled(idx < e.actions.size() - 1);
            panel.add(down, rows.x + ddW + 24, y);
            panel.add(new SlateIconButton(0, 0, 20, Icon.TRASH, EditorText.t("props.action_remove"), () -> {
                ed.session().pushUndo(null);
                if (idx < e.actions.size()) e.actions.remove(idx);
                ed.applyStructural();
            }), rows.x + ddW + 46, y);
            rows.y += 24;
            if (cur == null) {
                rows.caption(EditorText.t("props.unknown_action", act.type));
            } else {
                for (final Arg arg : cur.args()) {
                    if (EditorForms.captioned(arg.kind())) rows.caption(arg.label());
                    final String key = arg.key();
                    rows.add(EditorForms.field(0, 0, rows.w, arg, act.args.getOrDefault(key, arg.defaultValue()), v -> {
                        if (refreshing) return;
                        ed.session().pushUndo("act:" + e.id + ":" + idx + ":" + key);
                        act.args.put(key, v);
                        ed.applyLive();
                    }), EditorForms.height(arg.kind()));
                }
            }
            rows.gap(2);
        }
        rows.add(new SlateButton(0, 0, rows.w, 18, EditorText.t("props.add_action"), () -> {
            ed.session().pushUndo(null);
            final ActionType first = Actions.get("slate:open_screen").orElse(all.isEmpty() ? null : all.get(0));
            if (first == null) return;
            e.actions.add(new ScreenLayout.Action(first.id(), EditorSession.defaultArgs(first)));
            ed.applyStructural();
        }).icon(Icon.PLUS).variant(SlateButton.Variant.GHOST).leftAligned(), 18);
    }

    private void moveAction(final ScreenLayout.Element e, final int idx, final int delta) {
        final int to = idx + delta;
        if (idx < 0 || idx >= e.actions.size() || to < 0 || to >= e.actions.size()) return;
        ed.session().pushUndo(null);
        final ScreenLayout.Action a = e.actions.remove(idx);
        e.actions.add(to, a);
        ed.applyStructural();
    }

    // ------------------------------------------------------------------ vanilla widget

    private void buildVanilla(final Rows rows, final EditorItem item) {
        shownKey = item.key;
        final EditorCanvas c = ed.canvas();
        final String key = item.key == null ? "" : item.key;
        final Supplier<EditorItem> cur = () -> c.itemOfKey(key);
        rows.title(item.name(), item.icon());
        rows.caption(EditorText.t("props.vanilla_widget", item.typeName()));
        rows.caption(Component.literal(key));
        anchorRow(rows, cur);
        geometryRows(rows, cur);
        rows.add(new SlateToggle(0, 0, rows.w, EditorText.t("props.visible"), item.visible(), v -> { final EditorItem it = cur.get(); if (it != null && !refreshing) c.setVisible(it, v); }), 20);
        rows.gap(2);
        rows.add(new SlateButton(0, 0, rows.w, 18, EditorText.t("props.reset_widget"), () -> { final EditorItem it = cur.get(); if (it != null) c.resetVanilla(it); })
            .icon(Icon.REFRESH).variant(SlateButton.Variant.GHOST).leftAligned(), 18);
    }

    // ------------------------------------------------------------------ shared rows

    private void anchorRow(final Rows rows, final Supplier<EditorItem> cur) {
        rows.caption(EditorText.t("props.anchor"));
        final List<String> opts = new ArrayList<>();
        opts.add("AUTO");
        for (final Anchor a : Anchor.values()) opts.add(a.name());
        final EditorItem it0 = cur.get();
        final String curA = it0 == null ? "AUTO" : anchorName(it0);
        anchorDd = new SlateDropdown<>(0, 0, rows.w, opts, opts.contains(curA) ? curA : "AUTO",
            s -> "AUTO".equals(s) ? EditorText.t("props.anchor_auto") : Component.literal(pretty(s)),
            s -> {
                if (refreshing) return;
                final EditorItem it = cur.get();
                if (it != null) ed.canvas().setAnchor(it, "AUTO".equals(s) ? null : Anchor.parse(s, Anchor.TOP_LEFT));
            });
        rows.add(anchorDd, 20);
    }

    private void geometryRows(final Rows rows, final Supplier<EditorItem> cur) {
        final EditorItem it0 = cur.get();
        final Rect r = it0 == null ? Rect.of(0, 0, 0, 0) : it0.rect;
        final int half = rows.w / 2 - 2;
        rows.caption2(EditorText.t("props.x"), EditorText.t("props.y"));
        fx = new EditorTextField(0, 0, half, EditorText.t("props.x")).integer().text(Integer.toString(r.x()));
        fy = new EditorTextField(0, 0, half, EditorText.t("props.y")).integer().text(Integer.toString(r.y()));
        rows.pair(fx, fy, EditorForms.FIELD_H);
        rows.caption2(EditorText.t("props.w"), EditorText.t("props.h"));
        fw = new EditorTextField(0, 0, half, EditorText.t("props.w")).integer().text(Integer.toString(r.w()));
        fh = new EditorTextField(0, 0, half, EditorText.t("props.h")).integer().text(Integer.toString(r.h()));
        rows.pair(fw, fh, EditorForms.FIELD_H);
        fx.onChange(v -> geometry(cur, v, 0));
        fy.onChange(v -> geometry(cur, v, 1));
        fw.onChange(v -> geometry(cur, v, 2));
        fh.onChange(v -> geometry(cur, v, 3));
    }

    private void geometry(final Supplier<EditorItem> cur, final String v, final int which) {
        if (refreshing) return;
        final EditorItem it = cur.get();
        if (it == null || it.locked()) return;
        final int n;
        try { n = Integer.parseInt(v.trim()); } catch (final NumberFormatException e) { return; }
        final Rect r = it.rect;
        final Rect nr = switch (which) {
            case 0 -> r.withX(n);
            case 1 -> r.withY(n);
            case 2 -> r.withW(Math.max(1, n));
            default -> r.withH(Math.max(1, n));
        };
        if (nr.equals(r)) return;
        ed.canvas().setGeometry(it, nr, which >= 2, "geom:" + it.id + ":" + which);
    }

    private static String pretty(final String anchor) {
        final String s = anchor.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ multi-selection

    private void buildMulti(final Rows rows, final List<EditorItem> sel) {
        final EditorCanvas c = ed.canvas();
        rows.title(EditorText.t("props.n_selected", sel.size()), Icon.LAYERS);
        rows.caption(EditorText.t("props.align_hint"));
        final int y = rows.y;
        final EditorCanvas.Align[] aligns = EditorCanvas.Align.values();
        final Icon[] icons = { Icon.ALIGN_LEFT, Icon.ALIGN_CENTER, Icon.ALIGN_RIGHT, Icon.ALIGN_TOP, Icon.ALIGN_MIDDLE, Icon.ALIGN_BOTTOM };
        final int step = Math.max(20, rows.w / 6);
        for (int i = 0; i < aligns.length; i++) {
            final EditorCanvas.Align a = aligns[i];
            panel.add(new SlateIconButton(0, 0, 20, icons[i], EditorText.t("props.align." + a.name().toLowerCase(Locale.ROOT)), () -> c.align(a)), rows.x + i * step, y);
        }
        rows.y += 24;
        rows.separator(EditorText.t("props.section_element"));
        rows.pair(new SlateButton(0, 0, 10, 18, EditorText.t("props.hide"), () -> { for (final EditorItem it : c.selected()) c.setVisible(it, false); }).icon(Icon.EYE_OFF).variant(SlateButton.Variant.GHOST),
                  new SlateButton(0, 0, 10, 18, EditorText.t("props.delete"), c::deleteSelected).icon(Icon.TRASH).variant(SlateButton.Variant.DANGER), 18);
        rows.add(new SlateButton(0, 0, rows.w, 18, EditorText.t("props.duplicate"), c::duplicate).icon(Icon.DUPLICATE).variant(SlateButton.Variant.GHOST).leftAligned(), 18);
    }

    // ------------------------------------------------------------------ screen (nothing selected)

    private void buildScreen(final Rows rows) {
        final EditorSession s = ed.session();
        final ScreenLayout l = s.layout();
        final String id = s.layoutId;
        rows.title(Component.literal(id.startsWith("custom:") ? id.substring(7) : ScreenIds.display(id)), Icon.MONITOR);
        rows.caption(Component.literal(id));
        rows.caption(Component.literal(LayoutStore.fileFor(id).getFileName().toString()));

        rows.separator(EditorText.t("props.section_background"));
        rows.caption(EditorText.t("props.bg_kind"));
        final List<String> kinds = List.of("default", "color", "image", "panorama", "none");
        final String kind = l.background == null || l.background.kind == null ? "default" : l.background.kind.toLowerCase(Locale.ROOT);
        rows.add(new SlateDropdown<>(0, 0, rows.w, kinds, kinds.contains(kind) ? kind : "default", k -> EditorText.t("props.bg." + k), k -> {
            if (refreshing) return;
            s.pushUndo(null);
            if ("default".equals(k)) l.background = null;
            else {
                if (l.background == null) { l.background = new ScreenLayout.Background(); l.background.value = "color".equals(k) ? "#161615" : ""; }
                l.background.kind = k;
            }
            LayoutBackground.forgetImages();
            ed.applyStructural();
        }), 20);
        if (l.background != null) {
            final ScreenLayout.Background bg = l.background;
            switch (kind) {
                case "color" -> {
                    rows.caption(EditorText.t("props.bg_color"));
                    rows.add(new ColorRow(0, 0, rows.w, bg.value, v -> { if (refreshing) return; s.pushUndo("bg:color"); bg.value = v; }), 20);
                }
                case "image" -> {
                    rows.caption(EditorText.t("props.bg_image"));
                    rows.add(new EditorTextField(0, 0, rows.w, EditorText.t("props.bg_image")).text(bg.value)
                        .placeholder(Component.literal("config/slate/images/bg.png")).onChange(v -> {
                            if (refreshing) return;
                            s.pushUndo("bg:image");
                            bg.value = v.trim();
                            LayoutBackground.forgetImages();
                        }), 20);
                    rows.add(new SlateToggle(0, 0, rows.w, EditorText.t("props.bg_dim"), bg.dim, v -> { if (refreshing) return; s.pushUndo(null); bg.dim = v; }), 20);
                }
                case "panorama" -> rows.add(new SlateToggle(0, 0, rows.w, EditorText.t("props.bg_gradient"), bg.dim, v -> { if (refreshing) return; s.pushUndo(null); bg.dim = v; }), 20);
                default -> {}
            }
        }

        rows.separator(EditorText.t("props.section_layout"));
        rows.caption(EditorText.t("props.stats", l.elements.size(), l.moved.size(), l.hidden.size()));
        rows.add(new SlateButton(0, 0, rows.w, 18, EditorText.t("toolbar.add"), () -> ed.openAddPalette(bounds.x() + 6, bounds.y() + 40)).icon(Icon.PLUS).variant(SlateButton.Variant.GHOST).leftAligned(), 18);
        rows.add(new SlateButton(0, 0, rows.w, 18, EditorText.t("toolbar.reset"), ed::resetScreen).icon(Icon.REFRESH).variant(SlateButton.Variant.DANGER).leftAligned(), 18);
    }
}
