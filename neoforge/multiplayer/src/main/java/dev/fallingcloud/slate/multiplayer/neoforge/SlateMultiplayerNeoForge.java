package dev.fallingcloud.slate.multiplayer.neoforge;

import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import dev.fallingcloud.slate.core.module.Modules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** NeoForge entry point of Slate Multiplayer. */
@Mod(value = SlateMultiplayer.MOD_ID)
public final class SlateMultiplayerNeoForge {

    public SlateMultiplayerNeoForge(final IEventBus modBus, final ModContainer container) {
        Modules.register(SlateMultiplayer.MODULE);
    }
}
