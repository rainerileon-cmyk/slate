package dev.fallingcloud.slate.core.layout.editor;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.ElementType;
import dev.fallingcloud.slate.core.layout.ScreenLayout;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * One editable thing on the screen: either a vanilla widget (identified by its {@code WidgetKey}) or a
 * custom element of the layout (identified by its element id). Rebuilt on every re-apply; the stable
 * identity for selection is {@link #id}.
 */
final class EditorItem {

    /** {@code w:<widget key>} or {@code e:<element id>}. */
    final String id;
    @Nullable final String key;
    @Nullable final ScreenLayout.Element element;
    @Nullable final ElementType type;
    /** The live widget; null for a custom element that is hidden or whose type is unknown. */
    @Nullable final AbstractWidget widget;
    /** Current screen rectangle (the widget's, or the element's computed placement when it has no widget). */
    Rect rect;

    private EditorItem(final String id, @Nullable final String key, @Nullable final ScreenLayout.Element element,
                       @Nullable final ElementType type, @Nullable final AbstractWidget widget, final Rect rect) {
        this.id = id; this.key = key; this.element = element; this.type = type; this.widget = widget; this.rect = rect;
    }

    static EditorItem vanilla(final String key, final AbstractWidget w) {
        return new EditorItem("w:" + key, key, null, null, w, Rect.of(w.getX(), w.getY(), w.getWidth(), w.getHeight()));
    }

    static EditorItem custom(final ScreenLayout.Element e, @Nullable final ElementType type, @Nullable final AbstractWidget w, final Rect rect) {
        return new EditorItem("e:" + e.id, null, e, type, w, rect);
    }

    boolean isCustom() { return element != null; }

    boolean visible() { return element != null ? element.visible : widget != null && widget.visible; }

    boolean locked() { return element != null && element.locked; }

    /** Can be clicked / dragged on the canvas (visible and actually rendered). */
    boolean hittable() { return visible() && widget != null; }

    Component name() {
        if (element != null) return Component.literal(element.id);
        return Component.literal(widget == null ? String.valueOf(key) : widgetName(widget));
    }

    Component typeName() {
        if (element != null) return type != null ? type.label() : Component.literal(element.type);
        return Component.literal(widget == null ? "?" : widget.getClass().getSimpleName());
    }

    Icon icon() {
        if (element != null) return type != null ? type.icon() : Icon.QUESTION;
        if (widget instanceof EditBox) return Icon.TEXT;
        if (widget instanceof Checkbox) return Icon.CHECK;
        if (widget instanceof Button) return Icon.TOGGLE;
        return Icon.PANEL;
    }

    static String widgetName(final AbstractWidget w) {
        final Component m = w.getMessage();
        final String s = m == null ? "" : m.getString().trim();
        return s.isEmpty() ? w.getClass().getSimpleName() : s;
    }

    @Override
    public String toString() { return id; }
}
