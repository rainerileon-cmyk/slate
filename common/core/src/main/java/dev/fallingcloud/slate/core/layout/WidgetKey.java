package dev.fallingcloud.slate.core.layout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * A stable identity for a vanilla widget across launches: its message's translation key (or literal
 * text) plus an occurrence index for duplicates, e.g. {@code menu.singleplayer#0}. Class name is
 * appended for widgets with empty messages ({@code #EditBox#1}) so text fields keep an identity too.
 */
public final class WidgetKey {

    public static String of(final AbstractWidget widget, final List<? extends GuiEventListener> siblings) {
        final String base = base(widget);
        int index = 0;
        for (final GuiEventListener s : siblings) {
            if (s == widget) break;
            if (s instanceof AbstractWidget w && base(w).equals(base)) index++;
        }
        return base + "#" + index;
    }

    /** Keys for every widget in a list, in order. */
    public static Map<AbstractWidget, String> all(final List<? extends GuiEventListener> widgets) {
        final Map<String, Integer> counts = new HashMap<>();
        final Map<AbstractWidget, String> out = new HashMap<>();
        for (final GuiEventListener l : widgets) {
            if (!(l instanceof AbstractWidget w)) continue;
            final String base = base(w);
            final int i = counts.merge(base, 1, Integer::sum) - 1;
            out.put(w, base + "#" + i);
        }
        return out;
    }

    private static String base(final AbstractWidget w) {
        final Component msg = w.getMessage();
        String text = "";
        if (msg != null) {
            if (msg.getContents() instanceof TranslatableContents tc) text = tc.getKey();
            else text = msg.getString();
        }
        text = text.replace('#', '_').trim();
        if (text.isEmpty()) return "#" + w.getClass().getSimpleName();
        if (text.length() > 48) text = text.substring(0, 48);
        return text;
    }

    private WidgetKey() {}
}
