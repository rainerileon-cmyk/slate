package dev.fallingcloud.slate.multiplayer;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.module.SlateModule;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Slate Multiplayer module. Registered with Core by the loader entry points on both sides. */
public final class SlateMultiplayer implements SlateModule {

    public static final String MOD_ID = "slate_multiplayer";
    public static final Logger LOGGER = LoggerFactory.getLogger("Slate Multiplayer");
    public static final SlateMultiplayer MODULE = new SlateMultiplayer();

    private SlateMultiplayer() {}

    public static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override public String id() { return MOD_ID; }

    @Override public Component displayName() { return Component.translatable("slate_multiplayer.name"); }

    @Override public Icon icon() { return Icon.FRIENDS; }

    @Override
    public void init() {
        Slate.init();
        LOGGER.info("[Slate Multiplayer] init");
    }

    @Override
    public void initClient() {
        LOGGER.info("[Slate Multiplayer] client init");
    }
}
