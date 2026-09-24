package dev.fallingcloud.slate.building.client.menu;

import dev.fallingcloud.slate.building.client.BuildingClient;
import dev.fallingcloud.slate.building.client.input.BuildKeys;
import dev.fallingcloud.slate.building.client.input.KeyClaims;
import dev.fallingcloud.slate.core.event.SlateEvents;
import net.minecraft.client.Minecraft;

/**
 * Opens the build menu on its key (R by default) in the contexts of design §4: {@code menuKeyContext = ALWAYS}, or
 * (SMART) while holding a block / variant / the toolbox / a building tool or while a mode is active. The same
 * condition decides whether the key is claimed exclusively ({@code KeyClaims}), so outside those contexts R keeps
 * doing what the other mods on it do (Iris shader reload, spell wheels).
 */
public final class MenuKeys {

    private static boolean initialised;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        SlateEvents.CLIENT_TICK_END.register(MenuKeys::tick);
    }

    private static void tick() {
        final Minecraft mc = Minecraft.getInstance();
        boolean pressed = false;
        while (BuildKeys.BUILD_MENU.consumeClick()) pressed = true;
        if (!pressed || mc.screen != null || mc.player == null || mc.getOverlay() != null) return;
        if (KeyClaims.menuWanted()) BuildingClient.openBuildMenu();
    }

    private MenuKeys() {}
}
