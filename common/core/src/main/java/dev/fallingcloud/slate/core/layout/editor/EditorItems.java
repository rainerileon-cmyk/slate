package dev.fallingcloud.slate.core.layout.editor;

import dev.fallingcloud.slate.core.layout.ElementType;
import dev.fallingcloud.slate.core.layout.ElementTypes;
import dev.fallingcloud.slate.core.layout.LayoutApplier;
import dev.fallingcloud.slate.core.layout.ScreenLayout;
import dev.fallingcloud.slate.core.layout.WidgetKey;
import dev.fallingcloud.slate.core.layout.ui.Anchor;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the {@link EditorItem} list for a screen: every vanilla {@code AbstractWidget} in
 * {@code screen.children()} that the layout did not add, then every custom element of the layout bound
 * to the widget {@code LayoutApplier} created for it. The applier adds widgets in element order and
 * places each at the position computed from its placement, so binding walks both lists in step and
 * matches on position; an element whose type returned null (or that is hidden) simply has no widget.
 */
final class EditorItems {

    static List<EditorItem> bind(final Screen screen, final ScreenLayout layout) {
        final List<EditorItem> out = new ArrayList<>();
        final List<AbstractWidget> added = LayoutApplier.addedTo(screen);
        final Set<AbstractWidget> addedSet = Collections.newSetFromMap(new IdentityHashMap<>());
        addedSet.addAll(added);

        final Map<AbstractWidget, String> keys = WidgetKey.all(screen.children());
        for (final GuiEventListener l : screen.children()) {
            if (!(l instanceof AbstractWidget w) || addedSet.contains(w)) continue;
            final String key = keys.get(w);
            if (key == null) continue;
            out.add(EditorItem.vanilla(key, w));
        }

        final List<ScreenLayout.Element> els = layout.elements;
        int j = 0;
        for (int i = 0; i < els.size(); i++) {
            final ScreenLayout.Element e = els.get(i);
            final ElementType type = ElementTypes.get(e.type).orElse(null);
            final Rect expected = expectedRect(e, type, screen.width, screen.height);
            AbstractWidget w = null;
            if (e.visible && type != null && j < added.size()) {
                final AbstractWidget cand = added.get(j);
                if (at(cand, expected)) {
                    w = cand;
                    j++;
                } else if (!fitsLater(cand, els, i + 1, screen)) {
                    w = cand;                       // the type moved its widget; best effort
                    j++;
                }
            }
            final Rect rect = w != null ? Rect.of(w.getX(), w.getY(), w.getWidth(), w.getHeight()) : expected;
            out.add(EditorItem.custom(e, type, w, rect));
        }
        return out;
    }

    private static boolean at(final AbstractWidget w, final Rect r) {
        return w.getX() == r.x() && w.getY() == r.y();
    }

    /** Whether the candidate widget belongs to a later element (so the current one produced none). */
    private static boolean fitsLater(final AbstractWidget cand, final List<ScreenLayout.Element> els, final int from, final Screen screen) {
        for (int k = from; k < els.size(); k++) {
            final ScreenLayout.Element e2 = els.get(k);
            if (!e2.visible) continue;
            final ElementType t2 = ElementTypes.get(e2.type).orElse(null);
            if (t2 == null) continue;
            if (at(cand, expectedRect(e2, t2, screen.width, screen.height))) return true;
        }
        return false;
    }

    /** Where {@code LayoutApplier} puts an element, from its placement and the type's default size. */
    static Rect expectedRect(final ScreenLayout.Element e, @Nullable final ElementType type, final int sw, final int sh) {
        final int[] def = type == null ? new int[] { 100, 20 } : type.defaultSize();
        final ScreenLayout.Placement p = e.place;
        final int w = p.w > 0 ? p.w : def[0], h = p.h > 0 ? p.h : def[1];
        final Anchor a = Anchor.parse(p.anchor, Anchor.TOP_LEFT);
        return Rect.of(a.x(sw, p.x, w), a.y(sh, p.y, h), w, h);
    }

    private EditorItems() {}
}
