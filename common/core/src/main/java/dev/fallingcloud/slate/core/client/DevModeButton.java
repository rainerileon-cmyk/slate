package dev.fallingcloud.slate.core.client;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.LayoutApplier;
import dev.fallingcloud.slate.core.layout.editor.LayoutEditor;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The pencil button dev mode promises: with {@code devMode} on, every editable screen (any non-container screen)
 * gets a small ghost button in its bottom-right corner that opens the layout editor on it, the same as the F7 key.
 * The corner is the one place no Slate or vanilla screen puts anything but footer text. Added on every screen init,
 * so it survives resizes; not added while the editor is already open.
 */
public final class DevModeButton {

    private static final int SIZE = 20, MARGIN = 4;

    static void init() {
        SlateEvents.SCREEN_INIT_POST.register(DevModeButton::onScreenInit);
    }

    private static void onScreenInit(final Screen screen) {
        if (!Slate.config().devMode || LayoutEditor.isEditing() || !LayoutEditor.canEdit(screen)) return;
        if (!(screen instanceof LayoutApplier.ScreenAccess access)) return;
        final SlateIconButton b = new SlateIconButton(screen.width - SIZE - MARGIN, screen.height - SIZE - MARGIN, SIZE, Icon.EDIT,
            Component.translatable("slate.dev.edit_screen"), () -> LayoutEditor.start(screen));
        b.variant(SlateButton.Variant.GHOST);
        access.slate$add(b);
    }

    private DevModeButton() {}
}
