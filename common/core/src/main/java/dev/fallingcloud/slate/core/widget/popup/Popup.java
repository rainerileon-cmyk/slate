package dev.fallingcloud.slate.core.widget.popup;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Something drawn above the whole screen that takes input first: dropdown lists, context menus, modals,
 * colour pickers. Managed by {@link Popups}. Coordinates are absolute GUI coordinates.
 */
public interface Popup {

    void render(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    /** Return true to consume. Clicks outside a non-modal popup close it by default (see {@link #isModal}). */
    boolean mouseClicked(double mouseX, double mouseY, int button);

    default boolean mouseReleased(final double mouseX, final double mouseY, final int button) { return false; }

    default boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY) { return false; }

    default boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) { return false; }

    default boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) { return false; }

    default boolean charTyped(final char c, final int modifiers) { return false; }

    /** Whether (mx,my) is inside the popup's box. Clicks outside close non-modal popups. */
    boolean contains(double mouseX, double mouseY);

    /** Modal popups dim the screen and swallow every event. */
    default boolean isModal() { return false; }

    /** Called when the popup is closed for any reason. */
    default void onClose() {}
}
