package dev.fallingcloud.slate.building.client.hud;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.BuildingHarness;
import dev.fallingcloud.slate.building.client.input.BuildKeys;
import dev.fallingcloud.slate.building.client.wheel.WheelConfig;
import dev.fallingcloud.slate.building.client.wheel.WheelOverlay;
import dev.fallingcloud.slate.building.client.wheel.WheelTarget;
import dev.fallingcloud.slate.building.config.HudSettings;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A tip for new players: the first times they hold a block that can change shape in a world, a Slate toast names the
 * two keys that matter, as they are bound ("Hold Left Alt to change shape · R opens the build menu"). Once per play
 * session, at most {@link #MAX_SHOWN} times in all ({@code hud.onboardingShown} in {@code building.json}), never again
 * once the player opened the wheel themselves, and never with {@code hud.hints} off. The Builder's Toolbox needs no tip:
 * its screen explains itself.
 */
public final class OnboardingHints {

    /** How many sessions show the tip. */
    public static final int MAX_SHOWN = 2;
    /** Held this long (ticks) first: the player is looking at the block, not scrolling past it. */
    private static final int HOLD_TICKS = 30;
    private static final int LIFE_MS = 9000;

    private static int heldTicks;
    private static boolean shownThisSession;

    private OnboardingHints() {}

    public static void init() {
        SlateEvents.CLIENT_TICK_END.register(OnboardingHints::tick);
        SlateEvents.CLIENT_LEFT_SERVER.register(() -> heldTicks = 0);
    }

    private static void tick() {
        final HudSettings hud = WheelConfig.hud();
        if (!hud.hints || hud.onboardingShown >= MAX_SHOWN || shownThisSession || BuildingHarness.active()) return;
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (WheelOverlay.INSTANCE.isOpen()) {
            // Found the wheel on their own: the tip has nothing left to teach.
            hud.onboardingShown = MAX_SHOWN;
            SlateBuilding.configFile().save();
            return;
        }
        if (mc.screen != null || WheelTarget.heldTarget() == null) {
            heldTicks = 0;
            return;
        }
        if (++heldTicks < HOLD_TICKS) return;
        final Component body = message();
        if (body == null || !Theme.current().toasts()) return;   // nothing to say, or toasts are off: try another time
        show(body);
        shownThisSession = true;
        hud.onboardingShown++;
        SlateBuilding.configFile().save();
    }

    /** Shows the tip toast. */
    static void show(final Component body) {
        SlateToasts.show(Component.translatable("slate_building.hint.title"), body, Icon.SHAPE_STAIRS, null, LIFE_MS);
    }

    /** The tip with the keys as bound; the half about an unbound key (or a disabled wheel) is left out. Null if both are. */
    public static @Nullable Component message() {
        final boolean swap = WheelConfig.wheel().swapEnabled && !BuildKeys.SWAP.isUnbound();
        final boolean menu = !BuildKeys.BUILD_MENU.isUnbound();
        if (swap && menu) return Component.translatable("slate_building.hint.shape_and_menu", key(BuildKeys.SWAP), key(BuildKeys.BUILD_MENU));
        if (swap) return Component.translatable("slate_building.hint.shape", key(BuildKeys.SWAP));
        if (menu) return Component.translatable("slate_building.hint.menu", key(BuildKeys.BUILD_MENU));
        return null;
    }

    private static Component key(final KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().copy().withStyle(s -> s.withBold(true));
    }

    /** Dev harness: shows the tip now, whatever the counters say. */
    public static boolean debugShow() {
        final Component body = message();
        if (body == null) return false;
        show(body);
        return true;
    }
}
