package dev.fallingcloud.slate.core.layout.editor;

import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The layout editor overlay for one screen. THIS IS THE SKELETON: it draws an "editing" banner and
 * consumes nothing. The full editor (selection, drag/resize handles, snapping, alignment guides,
 * multi-select, undo/redo, layers, properties panel, add palette, save/reset/import/export, custom
 * screens) is implemented by the editor task on top of this class's public surface:
 * {@link #screen()}, {@link #onInit()}, {@link #close()}, {@link #render}, and the input methods,
 * which Core's hooks call before the screen sees the event.
 */
public class EditorOverlay {

    private final Screen screen;

    public EditorOverlay(final Screen screen) {
        this.screen = screen;
    }

    public Screen screen() { return screen; }

    /** The screen re-inited (resize or rebuild): refresh widget references. */
    public void onInit() {}

    /** Editing ended (save prompt, cleanup). */
    public void close() {}

    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final int w = screen.width;
        g.pose().pushPose();
        g.pose().translate(0, 0, 600);
        SlateDraw.rect(g, 0, 0, w, 14, 0xC0202020);
        SlateDraw.textCentered(g, Component.literal("Slate layout editor  -  F7 to exit"), w / 2, 3, Theme.current().accent());
        g.pose().popPose();
    }

    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) { return false; }

    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) { return false; }

    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY) { return false; }

    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) { return false; }

    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) { return false; }

    public boolean charTyped(final char c, final int modifiers) { return false; }
}
