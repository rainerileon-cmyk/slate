package dev.fallingcloud.slate.chat.fabric;

import dev.fallingcloud.slate.chat.SlateChat;
import dev.fallingcloud.slate.core.module.Modules;
import net.fabricmc.api.ModInitializer;

/** Fabric entry point (both sides) of Slate Chat. */
public final class SlateChatFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Modules.register(SlateChat.MODULE);
    }
}
