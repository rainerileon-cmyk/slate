package dev.fallingcloud.slate.core.layout.editor;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.client.CustomScreen;
import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ElementType;
import dev.fallingcloud.slate.core.layout.ElementTypes;
import dev.fallingcloud.slate.core.layout.LayoutStore;
import dev.fallingcloud.slate.core.layout.ScreenLayout;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The layout editor overlay for one screen: a toolbar, an optional layers panel, an optional properties
 * panel and the canvas in between. Core routes input here BEFORE the screen sees it and draws the
 * overlay after the screen rendered; while editing, the screen is drawn with the mouse parked
 * off-screen (see {@code ScreenLayoutMixin}) so nothing underneath reacts.
 *
 * <p>Keyboard: Esc (deselect / exit), Delete, arrows (nudge 1 px, Shift 8), Tab (next element),
 * Ctrl+Z/Y (undo/redo), Ctrl+S (save), Ctrl+C/V (copy/paste), Ctrl+A (all), Ctrl+D (duplicate),
 * G (grid), P (preview), L (layers). Mouse: click / Shift-click select, drag moves, handles resize,
 * drag on empty space rubber-bands, right click for a context menu, Alt while dragging disables
 * snapping, Shift while dragging constrains to one axis.</p>
 */
public class EditorOverlay implements EditorCanvas.Listener {

    private final Screen screen;
    private final EditorSession session;
    private final EditorCanvas canvas;
    private final EditorToolbar toolbar;
    private final PropertiesPanel props;
    private final LayersPanel layers;
    private boolean showProps = true;
    private boolean showLayers;
    private boolean preview;
    private boolean exiting;
    private boolean broken;
    @Nullable private Object pressTarget;
    private int lastW = -1, lastH = -1;
    private long lastErrorMs;
    private int errorCount;

    public EditorOverlay(final Screen screen) {
        this.screen = screen;
        final EditorSession resumed = EditorSession.takeResumable(screen);
        this.session = resumed != null ? resumed : new EditorSession(screen);
        this.canvas = new EditorCanvas(screen, session, this, this);
        this.toolbar = new EditorToolbar(this);
        this.props = new PropertiesPanel(this);
        this.layers = new LayersPanel(this);
        try {
            canvas.grid = Slate.config().devGrid;
            canvas.snap = true;
            showLayers = screen.width >= 560;
            if (resumed != null) {
                canvas.grid = resumed.grid;
                canvas.snap = resumed.snap;
                showProps = resumed.showProps;
                showLayers = resumed.showLayers;
                canvas.selection.addAll(resumed.selection);
            }
            canvas.rebind();
            relayout();
            props.rebuild();
            layers.refresh();
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] editor could not start on {}", session.layoutId, e);
        }
    }

    public Screen screen() { return screen; }

    EditorSession session() { return session; }

    EditorCanvas canvas() { return canvas; }

    boolean showLayers() { return showLayers; }

    boolean showProps() { return showProps; }

    /** While editing the screen underneath must not hover/animate; preview mode lets it. */
    public boolean hidesScreenMouse() { return !preview; }

    // ------------------------------------------------------------------ lifecycle

    /** The screen re-inited (resize or rebuild): widgets are new instances, bind again by key/id. */
    public void onInit() {
        try {
            relayout();
            canvas.rebind();
            props.rebuild();
            layers.refresh();
        } catch (final Exception e) {
            fail(e);
        }
    }

    /** Editing ended through {@code LayoutEditor.stop()} (F7, hub toggle, or the screen closed). */
    public void close() {
        if (exiting) return;
        try {
            session.selection = new ArrayList<>(canvas.selection);
            session.showProps = showProps;
            session.showLayers = showLayers;
            session.grid = canvas.grid;
            session.snap = canvas.snap;
            if (!session.dirty()) return;
            final Minecraft mc = Minecraft.getInstance();
            if (mc.screen != screen) {
                // The screen went away under us: the edits stay in memory (and applied); say so.
                SlateToasts.show(EditorText.t("toast.unsaved_kept"), Component.literal(session.layoutId), Icon.WARNING);
                return;
            }
            // F7 with unsaved changes: ask; Cancel (or Esc) parks the session so the next F7 resumes it.
            Popups.closeAll();
            EditorSession.setResumable(session);
            askSaveDiscard(
                () -> { session.save(); EditorSession.clearResumable(session); toastSaved(); },
                () -> { session.discard(); EditorSession.clearResumable(session); },
                () -> LayoutEditor.start(screen));
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] editor close failed", e);
        }
    }

    private void relayout() {
        lastW = screen.width;
        lastH = screen.height;
        toolbar.relayout(screen.width);
        final int top = EditorStyle.TOOLBAR_H;
        final int h = screen.height - top;
        layers.setBounds(Rect.of(0, top, Math.min(EditorStyle.LAYERS_W, screen.width / 3), h));
        final int pw = Math.min(EditorStyle.PROPS_W, screen.width / 2);
        props.setBounds(Rect.of(screen.width - pw, top, pw, h));
    }

    private void fail(final Exception e) {
        errorCount++;
        final long now = Clock.nowMs();
        if (now - lastErrorMs > 2000) {
            lastErrorMs = now;
            Slate.LOGGER.error("[Slate] layout editor error ({} so far)", errorCount, e);
        }
        if (errorCount >= 8 && !broken) {
            broken = true;
            SlateToasts.show(EditorText.t("toast.editor_failed"), Component.literal(e.toString()), Icon.ERROR);
            exiting = true;
            LayoutEditor.stop();
        }
    }

    // ------------------------------------------------------------------ canvas listener

    @Override
    public void selectionChanged() {
        props.rebuild();
        layers.refresh();
    }

    @Override
    public void itemsChanged(final boolean structural) {
        if (structural) props.rebuild(); else props.refreshGeometry();
        layers.refresh();
    }

    /** Re-apply after a geometry/property edit: same elements, fresh widgets. */
    void applyLive() {
        session.reapply();
        canvas.rebind();
        itemsChanged(false);
    }

    /** Re-apply after elements were added/removed/reordered/replaced. */
    void applyStructural() {
        session.reapply();
        canvas.rebind();
        itemsChanged(true);
    }

    // ------------------------------------------------------------------ rendering

    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        if (broken) return;
        try {
            if (screen.width != lastW || screen.height != lastH) onInit();
            g.pose().pushPose();
            g.pose().translate(0, 0, EditorStyle.Z);
            if (preview) {
                final Component hint = EditorText.t("preview.hint");
                final int w = SlateDraw.width(hint) + 10;
                SlateDraw.pixelRound(g, screen.width - w - 4, screen.height - 16, w, 12, Colors.withAlpha(EditorStyle.panelFill(), 0x90), EditorStyle.radius() > 0 ? 2 : 0);
                g.drawString(SlateDraw.font(), hint, screen.width - w + 1, screen.height - 14, Colors.withAlpha(EditorStyle.muted(), 0xC0), false);
                g.pose().popPose();
                return;
            }
            canvas.render(g, mouseX, mouseY, chromeContains(mouseX, mouseY));
            if (showLayers) layers.render(g, mouseX, mouseY, partialTick);
            if (showProps) props.render(g, mouseX, mouseY, partialTick);
            toolbar.render(g, mouseX, mouseY, partialTick);
            g.pose().popPose();
        } catch (final Exception e) {
            fail(e);
        }
    }

    private boolean chromeContains(final double mx, final double my) {
        return toolbar.contains(mx, my) || (showLayers && layers.contains(mx, my)) || (showProps && props.contains(mx, my));
    }

    // ------------------------------------------------------------------ mouse

    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (broken) return false;
        try {
            if (preview) { preview = false; return true; }
            pressTarget = null;
            if (toolbar.contains(mouseX, mouseY)) {
                props.clearFocus();
                toolbar.mouseClicked(mouseX, mouseY, button);
                pressTarget = toolbar;
                return true;
            }
            if (showProps && props.contains(mouseX, mouseY)) {
                props.mouseClicked(mouseX, mouseY, button);
                pressTarget = props;
                return true;
            }
            if (showLayers && layers.contains(mouseX, mouseY)) {
                props.clearFocus();
                layers.mouseClicked(mouseX, mouseY, button);
                pressTarget = layers;
                return true;
            }
            props.clearFocus();
            canvas.mouseClicked(mouseX, mouseY, button);
            pressTarget = canvas;
        } catch (final Exception e) {
            fail(e);
        }
        return true;
    }

    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        if (broken) return false;
        try {
            final Object t = pressTarget;
            pressTarget = null;
            if (t == toolbar) toolbar.mouseReleased(mouseX, mouseY, button);
            else if (t == props) props.mouseReleased(mouseX, mouseY, button);
            else if (t == layers) layers.mouseReleased(mouseX, mouseY);
            else if (t == canvas) canvas.mouseReleased(mouseX, mouseY);
        } catch (final Exception e) {
            fail(e);
        }
        return true;
    }

    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY) {
        if (broken) return false;
        try {
            if (pressTarget == props) props.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            else if (pressTarget == layers) layers.mouseDragged(mouseX, mouseY);
            else if (pressTarget == canvas) canvas.mouseDragged(mouseX, mouseY);
        } catch (final Exception e) {
            fail(e);
        }
        return true;
    }

    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (broken || preview) return !broken;
        try {
            if (showProps && props.contains(mouseX, mouseY)) props.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            else if (showLayers && layers.contains(mouseX, mouseY)) layers.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        } catch (final Exception e) {
            fail(e);
        }
        return true;
    }

    // ------------------------------------------------------------------ keyboard

    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (broken) return false;
        try {
            if (preview) { preview = false; return true; }
            if (keyCode >= EditorKeys.F1 && keyCode <= EditorKeys.F12) return false;     // screenshots, fullscreen, debug
            if (showProps && props.textInputFocused()) {
                if (keyCode == EditorKeys.ESCAPE || (EditorKeys.isEnter(keyCode) && props.singleLineFocused())) { props.clearFocus(); return true; }
                props.keyPressed(keyCode, scanCode, modifiers);
                return true;
            }
            if (showProps && props.hasFocus() && props.keyPressed(keyCode, scanCode, modifiers)) return true;
            final boolean ctrl = EditorKeys.ctrl(), shift = EditorKeys.shift();
            switch (keyCode) {
                case EditorKeys.ESCAPE -> { if (!canvas.selection.isEmpty()) canvas.clearSelection(); else requestExit(null); }
                case EditorKeys.DELETE, EditorKeys.BACKSPACE -> canvas.deleteSelected();
                case EditorKeys.LEFT -> canvas.nudge(shift ? -8 : -1, 0);
                case EditorKeys.RIGHT -> canvas.nudge(shift ? 8 : 1, 0);
                case EditorKeys.UP -> canvas.nudge(0, shift ? -8 : -1);
                case EditorKeys.DOWN -> canvas.nudge(0, shift ? 8 : 1);
                case EditorKeys.TAB -> canvas.selectNext(shift ? -1 : 1);
                case EditorKeys.Z -> { if (ctrl) { if (shift) redo(); else undo(); } }
                case EditorKeys.Y -> { if (ctrl) redo(); }
                case EditorKeys.S -> { if (ctrl) save(); }
                case EditorKeys.C -> { if (ctrl) canvas.copy(); }
                case EditorKeys.V -> { if (ctrl) canvas.paste(); }
                case EditorKeys.A -> { if (ctrl) canvas.selectAll(); }
                case EditorKeys.D -> { if (ctrl) canvas.duplicate(); }
                case EditorKeys.G -> { if (!ctrl) toggleGrid(); }
                case EditorKeys.P -> { if (!ctrl) togglePreview(); }
                case EditorKeys.L -> { if (!ctrl) toggleLayers(); }
                default -> {}
            }
        } catch (final Exception e) {
            fail(e);
        }
        return true;
    }

    public boolean charTyped(final char c, final int modifiers) {
        if (broken) return false;
        try {
            if (preview) { preview = false; return true; }
            if (showProps && props.textInputFocused()) props.charTyped(c, modifiers);
        } catch (final Exception e) {
            fail(e);
        }
        return true;
    }

    // ------------------------------------------------------------------ commands (toolbar, keys, menus)

    void undo() {
        if (session.undo()) { session.reapply(); canvas.rebind(); itemsChanged(true); }
    }

    void redo() {
        if (session.redo()) { session.reapply(); canvas.rebind(); itemsChanged(true); }
    }

    void save() {
        session.save();
        toastSaved();
    }

    private void toastSaved() {
        SlateToasts.show(EditorText.t("toast.saved"), Component.literal(LayoutStore.fileFor(session.layoutId).getFileName().toString()), Icon.SAVE);
    }

    void toggleGrid() {
        canvas.grid = !canvas.grid;
        final boolean g = canvas.grid;
        try { Slate.configFile().update(c -> c.devGrid = g); } catch (final Exception ignored) {}
    }

    void toggleSnap() { canvas.snap = !canvas.snap; }

    void toggleLayers() { showLayers = !showLayers; }

    void toggleProps() { showProps = !showProps; if (showProps) props.rebuild(); }

    void togglePreview() {
        preview = !preview;
        if (preview) Popups.closeAll();
    }

    void showProperties() {
        showProps = true;
        props.rebuild();
    }

    void showBackground() {
        canvas.clearSelection();
        showProps = true;
        props.rebuild();
    }

    /** The "Add" palette: every registered element type; picking one adds it centred and selects it. */
    void openAddPalette(final int x, final int y) {
        final List<MenuPopup.Item> items = new ArrayList<>();
        for (final ElementType type : ElementTypes.all()) {
            items.add(MenuPopup.Item.of(type.label(), type.icon(), () -> {
                canvas.addElement(type);
                showProperties();
            }));
        }
        if (items.isEmpty()) items.add(MenuPopup.Item.disabled(EditorText.t("palette.empty"), null));
        Popups.open(new MenuPopup(x, y, items, 130));
    }

    void resetScreen() {
        SlateModal.confirmDanger(EditorText.t("reset.title"), EditorText.t("reset.body"), EditorText.t("reset.ok"), () -> {
            session.replaceWith(new ScreenLayout());
            canvas.selection.clear();
            applyStructural();
        });
    }

    void exportLayout() {
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(EditorSession.json(session.layout()));
            SlateToasts.show(EditorText.t("toast.exported"), Component.literal(session.layoutId), Icon.EXPORT);
        } catch (final Exception e) {
            SlateToasts.show(EditorText.t("toast.export_failed"), Component.literal(e.toString()), Icon.ERROR);
        }
    }

    void importLayout() {
        ScreenLayout parsed = null;
        try {
            final String json = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (json != null && !json.isBlank()) parsed = JsonConfig.GSON.fromJson(json, ScreenLayout.class);
        } catch (final Exception ignored) {}
        if (parsed == null) {
            SlateToasts.show(EditorText.t("toast.import_invalid"), null, Icon.WARNING);
            return;
        }
        final ScreenLayout layout = parsed;
        SlateModal.confirm(EditorText.t("import.title"), EditorText.t("import.body"), EditorText.t("import.ok"), () -> {
            session.replaceWith(layout);
            canvas.selection.clear();
            applyStructural();
            SlateToasts.show(EditorText.t("toast.imported"), Component.literal(session.layoutId), Icon.IMPORT);
        });
    }

    /** Prompt for an id, then open (creating if new) the custom screen and edit it. */
    void newCustomScreen() {
        final List<String> existing = EditorForms.customScreens();
        final Component body = existing.isEmpty() ? EditorText.t("new_screen.body") : EditorText.t("new_screen.body_existing", String.join(", ", existing));
        SlateModal.prompt(EditorText.t("new_screen.title"), body, "", raw -> {
            final String id = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9_-]{1,32}")) {
                SlateToasts.show(EditorText.t("toast.bad_id"), EditorText.t("toast.bad_id_body"), Icon.WARNING);
                return;
            }
            requestExit(() -> {
                final Minecraft mc = Minecraft.getInstance();
                mc.setScreen(new CustomScreen(id, screen));
                if (mc.screen instanceof CustomScreen) LayoutEditor.start(mc.screen);
            });
        });
    }

    /** Leave the editor; asks Save / Discard / Cancel when there are unsaved changes. */
    void requestExit(@Nullable final Runnable then) {
        final Runnable finish = () -> {
            exiting = true;
            LayoutEditor.stop();
            if (then != null) then.run();
        };
        if (!session.dirty()) { finish.run(); return; }
        askSaveDiscard(() -> { session.save(); toastSaved(); finish.run(); }, () -> { session.discard(); finish.run(); }, null);
    }

    private static void askSaveDiscard(final Runnable save, final Runnable discard, @Nullable final Runnable cancel) {
        new SlateModal(EditorText.t("exit.title"), EditorText.t("exit.body"), Icon.SAVE)
            .button(EditorText.t("exit.cancel"), SlateButton.Variant.SECONDARY, cancel)
            .button(EditorText.t("exit.discard"), SlateButton.Variant.DANGER, discard)
            .button(EditorText.t("exit.save"), SlateButton.Variant.PRIMARY, save)
            .show();
    }
}
