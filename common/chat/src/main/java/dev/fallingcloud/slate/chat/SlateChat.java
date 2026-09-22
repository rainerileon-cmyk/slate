package dev.fallingcloud.slate.chat;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.module.SlateModule;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Slate Chat module. Registered with Core by the loader entry points on both sides. */
public final class SlateChat implements SlateModule {

    public static final String MOD_ID = "slate_chat";
    public static final Logger LOGGER = LoggerFactory.getLogger("Slate Chat");
    public static final SlateChat MODULE = new SlateChat();

    private SlateChat() {}

    public static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override public String id() { return MOD_ID; }

    @Override public Component displayName() { return Component.translatable("slate_chat.name"); }

    @Override public Icon icon() { return Icon.CHAT; }

    @Override
    public void init() {
        Slate.init();
        LOGGER.info("[Slate Chat] init");
    }

    @Override
    public void initClient() {
        LOGGER.info("[Slate Chat] client init");
    }
}
