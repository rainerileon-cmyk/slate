package dev.fallingcloud.slate.multiplayer.fabric;

import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import dev.fallingcloud.slate.core.module.Modules;
import net.fabricmc.api.ModInitializer;

/** Fabric entry point (both sides) of Slate Multiplayer. */
public final class SlateMultiplayerFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Modules.register(SlateMultiplayer.MODULE);
    }
}
