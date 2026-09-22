package dev.fallingcloud.slate.chat.neoforge;

import dev.fallingcloud.slate.chat.SlateChat;
import dev.fallingcloud.slate.core.module.Modules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** NeoForge entry point of Slate Chat. */
@Mod(value = SlateChat.MOD_ID)
public final class SlateChatNeoForge {

    public SlateChatNeoForge(final IEventBus modBus, final ModContainer container) {
        Modules.register(SlateChat.MODULE);
    }
}
