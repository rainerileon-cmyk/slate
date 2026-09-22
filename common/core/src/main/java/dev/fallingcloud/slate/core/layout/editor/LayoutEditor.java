package dev.fallingcloud.slate.core.layout.editor;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Entry point of the dev-mode layout editor. The overlay implementation ({@code EditorOverlay}) is
 * attached to the current screen when editing starts; Core's input and render hooks route to it
 * first. This class only owns the on/off state so the rest of Core can stay decoupled from the editor.
 */
public final class LayoutEditor {

    @Nullable private static EditorOverlay overlay;

    public static boolean isEditing() { return overlay != null; }

    /** Editing is allowed on any non-container screen when dev mode is on. */
    public static boolean canEdit(@Nullable final Screen screen) {
        return screen != null && Slate.config().devMode && !ScreenIds.isContainer(screen);
    }

    public static void toggle() {
        final Screen s = Minecraft.getInstance().screen;
        if (overlay != null) stop();
        else if (canEdit(s)) start(s);
    }

    public static void start(final Screen screen) {
        if (!canEdit(screen)) return;
        overlay = new EditorOverlay(screen);
        Slate.LOGGER.info("[Slate] editing layout of {}", ScreenIds.of(screen));
    }

    public static void stop() {
        if (overlay != null) overlay.close();
        overlay = null;
    }

    /** Screen hooks: the overlay survives resizes of the same screen and dies on screen change. */
    public static void onScreenChanged(@Nullable final Screen screen) {
        if (overlay != null && overlay.screen() != screen) stop();
    }

    public static void onScreenInit(final Screen screen) {
        if (overlay != null && overlay.screen() == screen) overlay.onInit();
    }

    @Nullable public static EditorOverlay overlay() { return overlay; }

    public static void render(final GuiGraphics g, final Screen screen, final int mouseX, final int mouseY, final float partialTick) {
        if (overlay != null && overlay.screen() == screen) overlay.render(g, mouseX, mouseY, partialTick);
    }

    private LayoutEditor() {}
}
