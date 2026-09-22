package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.List;

/** Convenience: open a right-click menu at the mouse. */
public final class SlateContextMenu {

    public static MenuPopup open(final double mouseX, final double mouseY, final List<MenuPopup.Item> items) {
        final MenuPopup p = new MenuPopup((int) mouseX, (int) mouseY, items);
        Popups.open(p);
        return p;
    }

    private SlateContextMenu() {}
}
