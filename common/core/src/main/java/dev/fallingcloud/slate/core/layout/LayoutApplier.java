package dev.fallingcloud.slate.core.layout;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.layout.action.Actions;
import dev.fallingcloud.slate.core.layout.ui.Anchor;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

/**
 * Applies a {@link ScreenLayout} to a screen right after its widgets exist (SCREEN_INIT_POST): hides
 * and moves vanilla widgets by {@link WidgetKey}, then instantiates custom elements. Idempotent per
 * init: the widgets it added are remembered per screen instance and removed before re-applying.
 *
 * <p>Adding to a foreign screen goes through {@link ScreenAccess}, a small accessor mixin interface
 * that exposes the protected add/remove methods.</p>
 */
public final class LayoutApplier {

    private static final Map<Screen, List<AbstractWidget>> ADDED = new WeakHashMap<>();
    private static final Map<Screen, Map<AbstractWidget, int[]>> ORIGINAL = new WeakHashMap<>();
    private static boolean suspended;

    /** Editor preview can suspend applying (to show the pristine screen). */
    public static void setSuspended(final boolean s) { suspended = s; }

    /**
     * The layout id of a screen: {@link LayoutIdProvider#layoutId()} when the screen implements it
     * (custom screens are one class with many layouts), otherwise the class-based {@link ScreenIds#of}.
     */
    public static String layoutId(final Screen screen) {
        if (screen instanceof LayoutIdProvider p) {
            try {
                final String id = p.layoutId();
                if (id != null && !id.isBlank()) return id;
            } catch (final Exception ignored) {}
        }
        return ScreenIds.of(screen);
    }

    public static void apply(final Screen screen) {
        if (suspended || screen == null || ScreenIds.isContainer(screen)) return;
        final String id = layoutId(screen);
        final ScreenLayout layout = LayoutStore.get(id);
        remove(screen);
        if (layout.isEmpty()) return;
        try {
            applyVanilla(screen, layout);
            applyElements(screen, layout);
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] applying layout {} failed", id, e);
        }
    }

    /** Remove everything a previous apply added and restore moved widgets. */
    public static void remove(final Screen screen) {
        final List<AbstractWidget> added = ADDED.remove(screen);
        if (added != null && screen instanceof ScreenAccess acc) for (final AbstractWidget w : added) acc.slate$remove(w);
        final Map<AbstractWidget, int[]> orig = ORIGINAL.remove(screen);
        if (orig != null) orig.forEach((w, r) -> { w.setX(r[0]); w.setY(r[1]); w.setWidth(r[2]); w.setHeight(r[3]); w.visible = true; });
    }

    private static void applyVanilla(final Screen screen, final ScreenLayout layout) {
        if (layout.hidden.isEmpty() && layout.moved.isEmpty()) return;
        final Map<AbstractWidget, String> keys = WidgetKey.all(screen.children());
        final Map<AbstractWidget, int[]> orig = ORIGINAL.computeIfAbsent(screen, s -> new java.util.HashMap<>());
        keys.forEach((w, key) -> {
            final ScreenLayout.Placement p = layout.moved.get(key);
            final boolean hide = layout.hidden.contains(key);
            if (p == null && !hide) return;
            orig.putIfAbsent(w, new int[] { w.getX(), w.getY(), w.getWidth(), w.getHeight() });
            if (hide) { w.visible = false; return; }
            final int w2 = p.w > 0 ? p.w : w.getWidth(), h2 = p.h > 0 ? p.h : w.getHeight();
            final Anchor a = Anchor.parse(p.anchor, Anchor.TOP_LEFT);
            w.setX(a.x(screen.width, p.x, w2));
            w.setY(a.y(screen.height, p.y, h2));
            w.setWidth(w2);
            w.setHeight(h2);
        });
    }

    private static void applyElements(final Screen screen, final ScreenLayout layout) {
        if (layout.elements.isEmpty() || !(screen instanceof ScreenAccess acc)) return;
        final List<AbstractWidget> added = new ArrayList<>();
        for (final ScreenLayout.Element e : layout.elements) {
            if (!e.visible) continue;
            final ElementType type = ElementTypes.get(e.type).orElse(null);
            if (type == null) { Slate.LOGGER.warn("[Slate] unknown element type {} in layout for {}", e.type, layoutId(screen)); continue; }
            final int[] def = type.defaultSize();
            final int w = e.place.w > 0 ? e.place.w : def[0], h = e.place.h > 0 ? e.place.h : def[1];
            final Anchor a = Anchor.parse(e.place.anchor, Anchor.TOP_LEFT);
            final int x = a.x(screen.width, e.place.x, w), y = a.y(screen.height, e.place.y, h);
            final Runnable run = () -> { for (final ScreenLayout.Action act : e.actions) Actions.run(act.type, act.args); };
            final AbstractWidget widget = type.create(screen, e, x, y, w, h, run);
            if (widget == null) continue;
            acc.slate$add(widget);
            added.add(widget);
        }
        if (!added.isEmpty()) ADDED.put(screen, added);
    }

    /** Widgets added by layouts to this screen (the editor treats them as custom elements). */
    public static List<AbstractWidget> addedTo(final Screen screen) {
        final List<AbstractWidget> l = ADDED.get(screen);
        return l == null ? List.of() : l;
    }

    /** Implemented on Screen by {@code ScreenMixin}; exposes the protected widget list mutators. */
    public interface ScreenAccess {
        <T extends GuiEventListener & Renderable & NarratableEntry> T slate$add(T widget);

        void slate$remove(GuiEventListener widget);
    }

    private LayoutApplier() {}
}
