package dev.fallingcloud.slate.core.layout.editor;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The thin top bar: icon buttons in groups (add / history / view / screen) on the left, save and exit
 * on the right, and the screen name (with an unsaved marker) in between. Buttons that do not fit the
 * window width collapse into a "more" menu, so it works at 427 px just as well as at 1920.
 */
final class EditorToolbar {

    private static final int SIZE = 20, GAP = 2, SEP = 5;

    private final EditorOverlay ed;
    private final List<SlateIconButton> left = new ArrayList<>();
    private final List<Runnable> leftActions = new ArrayList<>();
    private final List<Icon> leftIcons = new ArrayList<>();
    private final List<Integer> separatorsAfter = List.of(0, 2, 4, 7);
    private final List<SlateIconButton> right = new ArrayList<>();
    private final List<AbstractWidget> placed = new ArrayList<>();
    private final List<Integer> separatorX = new ArrayList<>();
    private final SlateIconButton undoB, redoB, gridB, snapB, layersB, propsB, previewB, saveB, moreB;
    private int width;
    private int visibleLeft;
    private int labelX, labelW;
    @Nullable private AbstractWidget pressed;

    EditorToolbar(final EditorOverlay ed) {
        this.ed = ed;
        add(Icon.PLUS, "toolbar.add", () -> ed.openAddPalette(4, EditorStyle.TOOLBAR_H));
        undoB = add(Icon.UNDO, "toolbar.undo", ed::undo);
        redoB = add(Icon.REDO, "toolbar.redo", ed::redo);
        gridB = add(Icon.GRID, "toolbar.grid", ed::toggleGrid);
        snapB = add(Icon.SNAP, "toolbar.snap", ed::toggleSnap);
        layersB = add(Icon.LAYERS, "toolbar.layers", ed::toggleLayers);
        propsB = add(Icon.SLIDERS, "toolbar.properties", ed::toggleProps);
        previewB = add(Icon.EYE, "toolbar.preview", ed::togglePreview);
        add(Icon.IMAGE, "toolbar.background", ed::showBackground);
        add(Icon.REFRESH, "toolbar.reset", ed::resetScreen);
        add(Icon.EXPORT, "toolbar.export", ed::exportLayout);
        add(Icon.IMPORT, "toolbar.import", ed::importLayout);
        add(Icon.MONITOR, "toolbar.new_screen", ed::newCustomScreen);
        saveB = new SlateIconButton(0, 0, SIZE, Icon.SAVE, EditorText.t("toolbar.save"), ed::save);
        right.add(saveB);
        right.add(new SlateIconButton(0, 0, SIZE, Icon.EXIT, EditorText.t("toolbar.exit"), () -> ed.requestExit(null)));
        moreB = new SlateIconButton(0, 0, SIZE, Icon.DOTS, EditorText.t("toolbar.more"), this::openMore);
    }

    private SlateIconButton add(final Icon icon, final String key, final Runnable action) {
        final SlateIconButton b = new SlateIconButton(0, 0, SIZE, icon, EditorText.t(key), action);
        left.add(b);
        leftActions.add(action);
        leftIcons.add(icon);
        return b;
    }

    private int leftWidth(final int n) {
        int w = n * (SIZE + GAP);
        for (final int s : separatorsAfter) if (s < n - 1) w += SEP;
        return w;
    }

    void relayout(final int width) {
        this.width = width;
        final int y = (EditorStyle.TOOLBAR_H - SIZE) / 2;
        placed.clear();
        separatorX.clear();
        final int rightW = right.size() * (SIZE + GAP);
        int rx = width - 4;
        for (int i = right.size() - 1; i >= 0; i--) {
            rx -= SIZE;
            right.get(i).setX(rx);
            right.get(i).setY(y);
            rx -= GAP;
        }
        int avail = width - 8 - rightW - 64;
        visibleLeft = left.size();
        if (leftWidth(visibleLeft) > avail) {
            avail -= SIZE + GAP;
            while (visibleLeft > 1 && leftWidth(visibleLeft) > avail) visibleLeft--;
        }
        int x = 4;
        for (int i = 0; i < visibleLeft; i++) {
            final SlateIconButton b = left.get(i);
            b.setX(x);
            b.setY(y);
            placed.add(b);
            x += SIZE + GAP;
            if (separatorsAfter.contains(i) && i < visibleLeft - 1) { separatorX.add(x + 1); x += SEP; }
        }
        if (visibleLeft < left.size()) {
            moreB.setX(x);
            moreB.setY(y);
            placed.add(moreB);
            x += SIZE + GAP;
        }
        labelX = x + 4;
        labelW = width - rightW - 8 - labelX;
        placed.addAll(right);
    }

    private void openMore() {
        final List<MenuPopup.Item> items = new ArrayList<>();
        for (int i = visibleLeft; i < left.size(); i++) {
            items.add(MenuPopup.Item.of(left.get(i).getMessage(), leftIcons.get(i), leftActions.get(i)));
        }
        Popups.open(new MenuPopup(moreB.getX(), EditorStyle.TOOLBAR_H, items, 140));
    }

    boolean contains(final double mx, final double my) {
        return my >= 0 && my < EditorStyle.TOOLBAR_H && mx >= 0 && mx < width;
    }

    void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final EditorSession s = ed.session();
        undoB.enabled(s.canUndo());
        redoB.enabled(s.canRedo());
        gridB.toggled(ed.canvas().grid);
        snapB.toggled(ed.canvas().snap);
        layersB.toggled(ed.showLayers());
        propsB.toggled(ed.showProps());
        previewB.toggled(false);
        saveB.enabled(true);

        EditorStyle.panel(g, 0, 0, width, EditorStyle.TOOLBAR_H);
        for (final int sx : separatorX) SlateDraw.vline(g, sx, 5, EditorStyle.TOOLBAR_H - 10, Colors.withAlpha(EditorStyle.panelBorder(), 0xC0));
        for (final AbstractWidget w : placed) w.render(g, mouseX, mouseY, partialTick);

        if (labelW > 30) {
            final String id = s.layoutId;
            final String name = id.startsWith("custom:") ? id.substring(7) : ScreenIds.display(id);
            final Component label = Component.literal(name + (s.dirty() ? " •" : ""));
            final int nw = Math.min(SlateDraw.width(label), labelW);
            g.drawString(SlateDraw.font(), SlateDraw.truncate(label, labelW), labelX, (EditorStyle.TOOLBAR_H - 9) / 2 + 1, EditorStyle.text(), EditorStyle.vanilla());
            final int rest = labelW - nw - 6;
            if (rest > 40) g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(id), rest), labelX + nw + 6, (EditorStyle.TOOLBAR_H - 9) / 2 + 1, EditorStyle.dim(), false);
        }
    }

    boolean mouseClicked(final double mx, final double my, final int button) {
        pressed = null;
        for (final AbstractWidget w : placed) {
            if (w.mouseClicked(mx, my, button)) { pressed = w; return true; }
        }
        return false;
    }

    void mouseReleased(final double mx, final double my, final int button) {
        if (pressed != null) pressed.mouseReleased(mx, my, button);
        pressed = null;
    }
}
