package dev.fallingcloud.slate.config.neoforge;

import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.core.module.Modules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** NeoForge entry point of Slate Config. */
@Mod(value = SlateConfig.MOD_ID, dist = Dist.CLIENT)
public final class SlateConfigNeoForge {

    public SlateConfigNeoForge(final IEventBus modBus, final ModContainer container) {
        Modules.register(SlateConfig.MODULE);
    }
}
