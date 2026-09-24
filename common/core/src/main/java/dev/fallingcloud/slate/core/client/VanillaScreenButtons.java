package dev.fallingcloud.slate.core.client;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.LayoutApplier;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import java.util.Set;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * What vanilla's own title and pause screens get whenever they are the ones on screen (the vanilla LAYOUT, or a
 * single screen switched back to vanilla, or Slate Menu not installed): a Slate button, so the hub stays one click
 * away. Slate's rebuilt screens (the custom layout) carry no such button; their Options lead to Slate's settings.
 * <ul>
 *   <li>Title screen: a 20 px icon button right of vanilla's accessibility button.</li>
 *   <li>Pause menu: vanilla's "Give Feedback" (or the snapshot "Send Feedback" / "Report Bugs" pair) is hidden and
 *       the Slate button takes its row; when another mod already replaced that row, the button goes under the grid.</li>
 * </ul>
 * Runs on every screen init (so also after a resize, when vanilla rebuilds its widgets from scratch).
 */
public final class VanillaScreenButtons {

    private static final Set<String> FEEDBACK_KEYS = Set.of("menu.feedback", "menu.sendFeedback", "menu.reportBugs");

    static void init() {
        dev.fallingcloud.slate.core.event.SlateEvents.SCREEN_INIT_POST.register(VanillaScreenButtons::onScreenInit);
    }

    private static void onScreenInit(final Screen screen) {
        if (!(screen instanceof LayoutApplier.ScreenAccess access)) return;
        if (screen.getClass() == TitleScreen.class) {
            final int y = screen.height / 4 + 48 + 72;
            final SlateIconButton hub = new SlateIconButton(screen.width / 2 + 128, y, 20, Icon.SLATE, Component.translatable("slate.hub.title"),
                () -> CoreActions.openScreen("slate:hub"));
            hub.variant(SlateButton.Variant.SECONDARY);
            access.slate$add(hub);
        } else if (screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
            replaceFeedback(screen, access);
        }
    }

    /** Hides the feedback / bug-report buttons and puts the Slate button where they were (or under everything else). */
    private static void replaceFeedback(final Screen screen, final LayoutApplier.ScreenAccess access) {
        int minX = Integer.MAX_VALUE, maxRight = Integer.MIN_VALUE, rowY = Integer.MIN_VALUE;
        int gridBottom = Integer.MIN_VALUE, gridLeft = Integer.MAX_VALUE, gridRight = Integer.MIN_VALUE;
        for (final GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget w)) continue;
            gridBottom = Math.max(gridBottom, w.getY() + w.getHeight());
            gridLeft = Math.min(gridLeft, w.getX());
            gridRight = Math.max(gridRight, w.getX() + w.getWidth());
            if (!isFeedback(w)) continue;
            w.visible = false;
            minX = Math.min(minX, w.getX());
            maxRight = Math.max(maxRight, w.getX() + w.getWidth());
            rowY = Math.max(rowY, w.getY());
        }
        final int x, y, w;
        if (rowY != Integer.MIN_VALUE) {
            x = minX;
            y = rowY;
            w = Math.max(98, maxRight - minX);
        } else if (gridBottom != Integer.MIN_VALUE) {
            // Another mod already took the feedback row: one more row under the grid.
            w = Math.max(98, gridRight - gridLeft);
            x = gridLeft;
            y = Math.min(gridBottom + 4, screen.height - 24);
        } else {
            return;
        }
        access.slate$add(new SlateButton(x, y, w, Component.translatable("slate.hub.title"), () -> CoreActions.openScreen("slate:hub")).icon(Icon.SLATE));
    }

    private static boolean isFeedback(final AbstractWidget w) {
        final Component msg = w.getMessage();
        return msg != null && msg.getContents() instanceof TranslatableContents tc && FEEDBACK_KEYS.contains(tc.getKey());
    }

    private VanillaScreenButtons() {}
}
