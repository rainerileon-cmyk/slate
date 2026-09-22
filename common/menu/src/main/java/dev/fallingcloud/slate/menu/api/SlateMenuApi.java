package dev.fallingcloud.slate.menu.api;

import org.jetbrains.annotations.Nullable;

/**
 * The tiny SPI surface other modules use to plug into Menu without Menu depending on them. Set the
 * providers from your {@code initClient()}; Menu reads them lazily every time it needs them.
 */
public final class SlateMenuApi {

    @Nullable private static volatile ServerPresenceProvider presence;
    @Nullable private static volatile ScreenshotShareProvider share;

    public static void setPresenceProvider(@Nullable final ServerPresenceProvider provider) { presence = provider; }

    @Nullable public static ServerPresenceProvider presenceProvider() { return presence; }

    public static void setScreenshotShareProvider(@Nullable final ScreenshotShareProvider provider) { share = provider; }

    @Nullable public static ScreenshotShareProvider screenshotShareProvider() { return share; }

    /** Friends on a server address, 0 when no provider is installed or it throws. */
    public static int friendsOn(final String address) {
        final ServerPresenceProvider p = presence;
        if (p == null || address == null) return 0;
        try { return Math.max(0, p.friendsOn(address)); } catch (final Exception e) { return 0; }
    }

    private SlateMenuApi() {}
}
