package dev.fallingcloud.slate.core.client;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.layout.editor.EditorOverlay;
import dev.fallingcloud.slate.core.layout.editor.LayoutEditor;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Input that Slate takes BEFORE the current screen sees it: popups, the layout editor, toast clicks and
 * the editor toggle key. Called from the MouseHandler/KeyboardHandler mixins (the top of the input
 * path, so it works for screens that override mouseClicked/keyPressed without calling super).
 * Every method returns true when the event was consumed and vanilla must not dispatch it.
 */
public final class ScreenInput {

    private static int heldButton = -1;
    private static double lastX, lastY;

    private static double guiX() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
    }

    private static double guiY() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
    }

    public static boolean mousePressed(final int button) {
        final Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return false;
        final double mx = guiX(), my = guiY();
        lastX = mx; lastY = my;
        if (Popups.mouseClicked(mx, my, button)) { heldButton = button; return true; }
        if (SlateToasts.mouseClicked(mx, my, screen.width)) return true;
        final EditorOverlay ed = LayoutEditor.overlay();
        if (ed != null && ed.screen() == screen && ed.mouseClicked(mx, my, button)) { heldButton = button; return true; }
        return false;
    }

    public static boolean mouseReleased(final int button) {
        final Screen screen = Minecraft.getInstance().screen;
        final double mx = guiX(), my = guiY();
        final boolean wasHeld = heldButton == button;
        heldButton = -1;
        if (screen == null) return false;
        boolean consumed = Popups.mouseReleased(mx, my, button);
        final EditorOverlay ed = LayoutEditor.overlay();
        if (ed != null && ed.screen() == screen && ed.mouseReleased(mx, my, button)) consumed = true;
        return consumed || wasHeld;
    }

    /** Called on every mouse move; dispatches drags for presses Slate consumed. */
    public static void mouseMoved() {
        final Screen screen = Minecraft.getInstance().screen;
        if (screen == null || heldButton < 0) return;
        final double mx = guiX(), my = guiY();
        final double dx = mx - lastX, dy = my - lastY;
        lastX = mx; lastY = my;
        if (Popups.mouseDragged(mx, my, heldButton, dx, dy)) return;
        final EditorOverlay ed = LayoutEditor.overlay();
        if (ed != null && ed.screen() == screen) ed.mouseDragged(mx, my, heldButton, dx, dy);
    }

    public static boolean mouseScrolled(final double scrollX, final double scrollY) {
        final Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return false;
        final double mx = guiX(), my = guiY();
        if (Popups.mouseScrolled(mx, my, scrollX, scrollY)) return true;
        final EditorOverlay ed = LayoutEditor.overlay();
        return ed != null && ed.screen() == screen && ed.mouseScrolled(mx, my, scrollX, scrollY);
    }

    public static boolean keyPressed(final int key, final int scancode, final int modifiers) {
        final Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return false;
        if (SlateClient.EDITOR_KEY.matches(key, scancode) && Slate.config().devMode
            && !(screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox eb && eb.isFocused())) {
            LayoutEditor.toggle();
            return true;
        }
        if (Popups.keyPressed(key, scancode, modifiers)) return true;
        final EditorOverlay ed = LayoutEditor.overlay();
        return ed != null && ed.screen() == screen && ed.keyPressed(key, scancode, modifiers);
    }

    public static boolean charTyped(final char c, final int modifiers) {
        final Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return false;
        if (Popups.charTyped(c, modifiers)) return true;
        final EditorOverlay ed = LayoutEditor.overlay();
        return ed != null && ed.screen() == screen && ed.charTyped(c, modifiers);
    }

    private ScreenInput() {}
}
