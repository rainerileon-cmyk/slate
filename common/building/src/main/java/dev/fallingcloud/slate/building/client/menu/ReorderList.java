package dev.fallingcloud.slate.building.client.menu;

import dev.fallingcloud.slate.core.widget.SlateList;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A {@link SlateList} whose rows can be reordered: drag a row (it follows the mouse, rows make room as it passes),
 * or Alt+Up / Alt+Down on the selected row. {@code onMove(from, to)} applies the move to the backing data; the
 * list's items are updated by the caller.
 */
class ReorderList<T> extends SlateList<T> {

    static final int GAP = 2;

    private final BiConsumer<Integer, Integer> onMove;
    private int dragFrom = -1;
    private boolean moved;

    ReorderList(final int x, final int y, final int w, final int h, final int rowHeight, final RowRenderer<T> renderer,
                final BiConsumer<Integer, Integer> onMove) {
        super(x, y, w, h, rowHeight, renderer);
        this.onMove = onMove;
        gap(GAP);
    }

    private int indexAtY(final double mouseY) {
        final int stride = rowHeight() + GAP;
        final int i = (int) Math.floor((mouseY - getY() + scrollAmount()) / stride);
        return Math.max(0, Math.min(items().size() - 1, i));
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        final boolean r = super.mouseClicked(mouseX, mouseY, button);
        dragFrom = r && button == 0 && contains(mouseX, mouseY) && mouseX < getX() + rowsWidth() ? selectedIndex() : -1;
        moved = false;
        return r;
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        if (button != 0 || dragFrom < 0 || items().size() < 2) return false;
        final int to = indexAtY(mouseY);
        if (to != dragFrom) {
            onMove.accept(dragFrom, to);
            dragFrom = to;
            moved = true;
            select(to);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        dragFrom = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Whether the last press turned into a drag that moved something. */
    boolean dragged() { return moved; }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (Screen.hasAltDown() && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            final int from = selectedIndex();
            final int to = from + (keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1);
            if (from < 0 || to < 0 || to >= items().size()) return true;
            onMove.accept(from, to);
            select(to);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Nullable T selected() {
        return selectedItem();
    }
}
