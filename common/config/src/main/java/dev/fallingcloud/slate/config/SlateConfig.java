package dev.fallingcloud.slate.config;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.module.SlateModule;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Slate Config module. Registered with Core by the loader entry points on both sides. */
public final class SlateConfig implements SlateModule {

    public static final String MOD_ID = "slate_config";
    public static final Logger LOGGER = LoggerFactory.getLogger("Slate Config");
    public static final SlateConfig MODULE = new SlateConfig();

    private SlateConfig() {}

    public static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override public String id() { return MOD_ID; }

    @Override public Component displayName() { return Component.translatable("slate_config.name"); }

    @Override public Icon icon() { return Icon.SLIDERS; }

    @Override
    public void init() {
        Slate.init();
        LOGGER.info("[Slate Config] init");
    }

    @Override
    public void initClient() {
        LOGGER.info("[Slate Config] client init");
    }
}
