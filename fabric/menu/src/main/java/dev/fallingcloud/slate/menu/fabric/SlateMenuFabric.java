package dev.fallingcloud.slate.menu.fabric;

import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.core.module.Modules;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entry point of Slate Menu (client-only module). */
public final class SlateMenuFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Modules.register(SlateMenu.MODULE);
    }
}
