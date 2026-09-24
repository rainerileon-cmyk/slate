package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;

/**
 * Staggered entrance for a page's widgets, like {@link SlateScreen#entrance} but also reaching option rows
 * and widgets nested in containers (toolbars, cards in panels). Delays scale with {@code Theme.motion()}.
 */
public final class Entrance {

    /** @return the next stagger index */
    public static int play(final GuiEventListener w, final int index) {
        final int delay = Math.min(SlateScreen.STAGGER_MAX_MS, index * SlateScreen.STAGGER_MS);
        if (w instanceof OptionRow r) { r.playEntrance(delay); return index + 1; }
        if (w instanceof SlateWidget sw) { sw.playEntrance(delay); return index + 1; }
        if (w instanceof SlateCard c) { c.playEntrance(delay); return index + 1; }
        int i = index;
        if (w instanceof ContainerEventHandler c) for (final GuiEventListener child : c.children()) i = play(child, i);
        return i;
    }

    private Entrance() {}
}
