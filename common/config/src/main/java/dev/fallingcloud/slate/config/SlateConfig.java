package dev.fallingcloud.slate.config;

import dev.fallingcloud.slate.config.curated.CuratedPages;
import dev.fallingcloud.slate.config.editor.FileEditorScreen;
import dev.fallingcloud.slate.config.hub.ConfigHubScreen;
import dev.fallingcloud.slate.config.resolver.Resolvers;
import dev.fallingcloud.slate.config.sodium.SodiumBridge;
import dev.fallingcloud.slate.config.ui.ApplyQueue;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.action.ActionType;
import dev.fallingcloud.slate.core.module.SlateModule;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.screen.ScreenSwaps;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Slate Config module. Registered with Core by the loader entry points on both sides. */
public final class SlateConfig implements SlateModule {

    public static final String MOD_ID = "slate_config";
    public static final Logger LOGGER = LoggerFactory.getLogger("Slate Config");
    public static final SlateConfig MODULE = new SlateConfig();

    private static final List<String> PAGE_IDS = List.of("video", "audio", "controls", "chat", "interface", "multiplayer", "accessibility",
        "language", "packs", "mods", "favourites", "presets");

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
        ConfigSettings.file();
        Resolvers.registerAll();
        CuratedPages.bootstrap();
        ScreenIds.register(ConfigHubScreen.class, "slate_config:hub", "Slate settings");
        ScreenIds.register(FileEditorScreen.class, "slate_config:editor", "Config file editor");
        CoreActions.SCREEN_FACTORIES.put("slate_config:hub", p -> new ConfigHubScreen(p, null));
        SlateEvents.CLIENT_TICK_END.register(ApplyQueue::tick);
        installSwaps();
        LOGGER.info("[Slate Config] client init ({} curated page(s))", CuratedPages.load().size());
    }

    /** Vanilla option sub-screens open the matching hub page; Sodium's screen too. Switched off by config.json. */
    private static void installSwaps() {
        if (!ConfigSettings.get().swapVanillaScreens) return;
        swap(VideoSettingsScreen.class, "video");
        swap(SoundOptionsScreen.class, "audio");
        swap(ControlsScreen.class, "controls");
        swap(KeyBindsScreen.class, "controls");
        swap(ChatOptionsScreen.class, "chat");
        swap(LanguageSelectScreen.class, "language");
        swap(AccessibilityOptionsScreen.class, "accessibility");
        swap(OnlineOptionsScreen.class, "multiplayer");
        if (SlatePlatform.get().isModLoaded("sodium")) SodiumBridge.installScreenSwap((parent, page) -> new ConfigHubScreen(parent, page));
    }

    private static void swap(final Class<? extends Screen> cls, final String page) {
        // setScreen has not switched yet when the swap runs, so the current screen is the sub-screen's parent.
        ScreenSwaps.register(cls, original -> new ConfigHubScreen(Minecraft.getInstance().screen, page));
    }

    @Override
    public List<HubEntry> hubEntries() {
        return List.of(new HubEntry(Component.translatable("slate_config.hub.open"), Icon.SLIDERS, () -> SlateConfigApi.openHub(Minecraft.getInstance().screen, null)));
    }

    @Override
    public List<ActionType> actions() {
        return List.of(new ActionType("slate_config:open", Component.translatable("slate_config.action.open"),
            List.of(ActionType.Arg.choice("page", Component.translatable("slate_config.action.arg.page"), "video", PAGE_IDS)),
            a -> SlateConfigApi.openHub(Minecraft.getInstance().screen, a.getOrDefault("page", "video"))));
    }
}
