package dev.fallingcloud.slate.config.fabric;

import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.core.module.Modules;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entry point of Slate Config (client-only module). */
public final class SlateConfigFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Modules.register(SlateConfig.MODULE);
    }
}
