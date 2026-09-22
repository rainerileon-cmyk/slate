package dev.fallingcloud.slate.core.layout;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.action.ActionType;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A kind of custom element the editor can add to a screen (button, label, image, ..., plus module
 * types like {@code slate_menu:world_card}). Creates the widget for a saved {@link ScreenLayout.Element}
 * and describes its editable properties so the editor can build a form.
 */
public interface ElementType {

    String id();

    Component label();

    Icon icon();

    /** Default size when placed. */
    default int[] defaultSize() { return new int[] { 100, 20 }; }

    /** Property schema (rendered by the editor). Reuses the action Arg record for the form kinds. */
    default List<ActionType.Arg> props() { return List.of(); }

    /** Whether this element runs actions on click (buttons yes, labels no). */
    default boolean clickable() { return true; }

    /**
     * Build the widget for the element at the given absolute rect. {@code runActions} executes the element's
     * saved actions (already bound). Return null to skip the element.
     */
    AbstractWidget create(Screen screen, ScreenLayout.Element element, int x, int y, int w, int h, Runnable runActions);
}
