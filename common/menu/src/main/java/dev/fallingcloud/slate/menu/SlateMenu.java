package dev.fallingcloud.slate.menu;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.action.ActionType;
import dev.fallingcloud.slate.core.module.SlateModule;
import dev.fallingcloud.slate.menu.client.MenuActions;
import dev.fallingcloud.slate.menu.client.MenuClient;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Slate Menu module (client only). Registered with Core by the loader entry points; everything
 * client-side hangs off {@link MenuClient}, which {@link #initClient()} boots.
 */
public final class SlateMenu implements SlateModule {

    public static final String MOD_ID = "slate_menu";
    public static final Logger LOGGER = LoggerFactory.getLogger("Slate Menu");
    public static final SlateMenu MODULE = new SlateMenu();

    private static JsonConfig<MenuConfig> config;

    private SlateMenu() {}

    public static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** The live config wrapper ({@code configFile().update(c -> ...)} edits and saves atomically). */
    public static synchronized JsonConfig<MenuConfig> configFile() {
        if (config == null) config = JsonConfig.of("menu", MenuConfig.class, MenuConfig::new);
        return config;
    }

    public static MenuConfig config() {
        return configFile().get();
    }

    @Override public String id() { return MOD_ID; }

    @Override public Component displayName() { return Component.translatable("slate_menu.name"); }

    @Override public Icon icon() { return Icon.HOME; }

    @Override
    public void init() {
        Slate.init();
        configFile();
        LOGGER.info("[Slate Menu] init");
    }

    @Override
    public void initClient() {
        MenuClient.init();
        LOGGER.info("[Slate Menu] client init");
    }

    @Override
    public List<HubEntry> hubEntries() {
        return MenuClient.hubEntries();
    }

    @Override
    public List<ActionType> actions() {
        return MenuActions.all();
    }
}
