package dev.fallingcloud.slate.core.client;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.LayoutApplier;
import dev.fallingcloud.slate.core.module.Features;
import dev.fallingcloud.slate.core.module.KnownModules;
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jetbrains.annotations.Nullable;

/**
 * What vanilla's own title and pause screens get whenever they are the ones on screen (the vanilla LAYOUT of that
 * menu, globally or per menu, or Slate UI not installed): a Slate button, so the hub stays one click away, plus one
 * vanilla-looking button per installed module that adds a menu of its own (Friends, Screenshots, Profile), placed the
 * way well-behaved mods place theirs (rule R8). A module that is missing adds nothing.
 * <ul>
 *   <li>Title screen: 20 px icon buttons right of vanilla's accessibility button, then left of the language button
 *       once the right side is full. The row is taken from the accessibility button itself, so it stays right when a
 *       loader moves the rows (NeoForge's Mods button).</li>
 *   <li>Pause menu: vanilla's "Give Feedback" (or the snapshot "Send Feedback" / "Report Bugs" pair) is hidden and the
 *       buttons take its row, half width, two per row; further rows go under the grid. When another mod already took
 *       that row, every row goes under the grid.</li>
 * </ul>
 * Slate's own rebuilt screens carry none of these; their Options lead to Slate's settings. Runs on every screen init
 * (so also after a resize, when vanilla rebuilds its widgets from scratch).
 */
public final class VanillaScreenButtons {

    private static final Set<String> FEEDBACK_KEYS = Set.of("menu.feedback", "menu.sendFeedback", "menu.reportBugs");
    private static final Set<String> ACCESSIBILITY_KEYS = Set.of("options.accessibility", "options.accessibility.title");
    private static final Set<String> LANGUAGE_KEYS = Set.of("options.language");
    private static final int ICON = 20, GAP = 4, ROW = 24;

    /** One button: its glyph, label (the icon button's tooltip) and what it opens. */
    private record Extra(Icon icon, Component label, Runnable action) {}

    static void init() {
        dev.fallingcloud.slate.core.event.SlateEvents.SCREEN_INIT_POST.register(VanillaScreenButtons::onScreenInit);
    }

    private static void onScreenInit(final Screen screen) {
        if (!(screen instanceof LayoutApplier.ScreenAccess access)) return;
        if (screen.getClass() == TitleScreen.class) title(screen, access);
        else if (screen instanceof PauseScreen pause && pause.showsPauseMenu()) pause(screen, access);
    }

    /** The Slate hub first, then the menus of the installed modules, in the catalogue's order. */
    private static List<Extra> extras(final Screen screen) {
        final List<Extra> out = new ArrayList<>();
        out.add(new Extra(Icon.SLATE, Component.translatable("slate.hub.title"), () -> CoreActions.openScreen("slate:hub")));
        if (Features.present(KnownModules.UI)) out.add(new Extra(Icon.CAMERA, Component.translatable("slate.slot.screenshots"), () -> MenuSlots.open(CoreSlots.SCREENSHOTS, screen)));
        if (Features.present(KnownModules.MULTIPLAYER)) out.add(new Extra(Icon.FRIENDS, Component.translatable("slate.slot.friends"), () -> MenuSlots.open(CoreSlots.FRIENDS, screen)));
        if (Features.present(KnownModules.PROFILE)) out.add(new Extra(Icon.USER, Component.translatable("slate.slot.profile"), () -> MenuSlots.open(CoreSlots.PROFILE, screen)));
        return out;
    }

    // ------------------------------------------------------------------ title

    private static void title(final Screen screen, final LayoutApplier.ScreenAccess access) {
        final AbstractWidget accessibility = find(screen, ACCESSIBILITY_KEYS);
        final AbstractWidget language = find(screen, LANGUAGE_KEYS);
        // Vanilla's row: language | Options | Quit | accessibility; the formula is the fallback for a title screen
        // another mod rebuilt without those buttons.
        final int y = accessibility != null ? accessibility.getY() : language != null ? language.getY() : screen.height / 4 + 48 + 72 + 12;
        int right = accessibility != null ? accessibility.getX() + accessibility.getWidth() + GAP : screen.width / 2 + 104 + ICON + GAP;
        int left = language != null ? language.getX() - GAP : screen.width / 2 - 124 - GAP;
        for (final Extra e : extras(screen)) {
            final int x;
            if (right + ICON <= screen.width - GAP) { x = right; right += ICON + GAP; }
            else if (left - ICON >= GAP) { left -= ICON; x = left; left -= GAP; }
            else break;                                          // no room left on the row: the rest is not added
            final SlateIconButton b = new SlateIconButton(x, y, ICON, e.icon(), e.label(), e.action());
            b.variant(SlateButton.Variant.SECONDARY);
            access.slate$add(b);
        }
    }

    // ------------------------------------------------------------------ pause

    /** Hides the feedback / bug-report buttons and lays the extras out on their row, then under the grid. */
    private static void pause(final Screen screen, final LayoutApplier.ScreenAccess access) {
        int minX = Integer.MAX_VALUE, maxRight = Integer.MIN_VALUE, rowY = Integer.MIN_VALUE;
        int gridBottom = Integer.MIN_VALUE, gridLeft = Integer.MAX_VALUE, gridRight = Integer.MIN_VALUE;
        for (final GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget w)) continue;
            gridBottom = Math.max(gridBottom, w.getY() + w.getHeight());
            gridLeft = Math.min(gridLeft, w.getX());
            gridRight = Math.max(gridRight, w.getX() + w.getWidth());
            if (!is(w, FEEDBACK_KEYS)) continue;
            w.visible = false;
            minX = Math.min(minX, w.getX());
            maxRight = Math.max(maxRight, w.getX() + w.getWidth());
            rowY = Math.max(rowY, w.getY());
        }
        if (gridBottom == Integer.MIN_VALUE) return;
        final boolean haveRow = rowY != Integer.MIN_VALUE;
        final int x0 = haveRow ? minX : gridLeft;
        final int w = Math.max(98, (haveRow ? maxRight - minX : gridRight - gridLeft));
        final int half = (w - GAP) / 2;
        final List<Extra> extras = extras(screen);
        int i = 0, y = haveRow ? rowY : gridBottom + GAP;
        while (i < extras.size()) {
            if (y + 20 > screen.height - GAP) break;             // no room: the rest is not added
            final boolean pair = i + 1 < extras.size();
            for (int k = 0; k < (pair ? 2 : 1); k++, i++) {
                final Extra e = extras.get(i);
                final int bw = pair ? half : w;
                access.slate$add(new SlateButton(x0 + k * (half + GAP), y, bw, e.label(), e.action()).icon(e.icon()));
            }
            // The feedback row sits inside the grid; further rows go under it.
            y = Math.max(y + ROW, gridBottom + GAP);
        }
    }

    // ------------------------------------------------------------------ helpers

    @Nullable
    private static AbstractWidget find(final Screen screen, final Set<String> keys) {
        for (final GuiEventListener child : screen.children()) if (child instanceof AbstractWidget w && is(w, keys)) return w;
        return null;
    }

    private static boolean is(final AbstractWidget w, final Set<String> keys) {
        final Component msg = w.getMessage();
        return msg != null && msg.getContents() instanceof TranslatableContents tc && keys.contains(tc.getKey());
    }

    private VanillaScreenButtons() {}
}
