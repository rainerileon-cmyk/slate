package dev.fallingcloud.slate.core.screen;

import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * The cross-screen fade: when a screen replaces another in the menus, the new one starts under a dark
 * veil that clears in ~140 ms. Skipped in-world for overlays that must not flash (chat) and when the
 * theme disables transitions.
 */
public final class Transitions {

    private static final int DURATION = 140;
    private static long startMs;
    @Nullable private static Screen forScreen;

    public static void onScreenChanged(@Nullable final Screen previous, @Nullable final Screen next) {
        forScreen = null;
        if (next == null || previous == null || !Theme.current().transitions()) return;
        if (next instanceof ChatScreen || previous instanceof ChatScreen) return;
        if (Minecraft.getInstance().level != null && !ScreenIds.isSlate(next)) return;
        startMs = Clock.nowMs();
        forScreen = next;
    }

    /** Draw the veil (called after the screen rendered). */
    public static void render(final GuiGraphics g, final Screen screen, final int w, final int h) {
        if (forScreen != screen) return;
        final float t = (Clock.nowMs() - startMs) / (float) Theme.current().ms(DURATION);
        if (t >= 1 || Theme.current().motion() <= 0) { forScreen = null; return; }
        final float a = (1 - t) * (1 - t) * 0.55f;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(0, 0, w, h, Colors.withAlpha(Theme.current().isVanilla() ? 0x000000 : 0x0E0E0D, Math.round(a * 255)));
        g.pose().popPose();
    }

    private Transitions() {}
}
