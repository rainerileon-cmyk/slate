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
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.Layout;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
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

    /** Choices of the dev action: sidebar pages and the category tabs (any path {@link ConfigHubScreen#open} takes works). */
    private static final List<String> PAGE_IDS = List.of("video", "audio", "controls", "controls/keys", "gameplay", "gameplay/building",
        "multiplayer", "multiplayer/online", "multiplayer/chat", "multiplayer/skin", "customization", "customization/mods", "customization/packs",
        "customization/shaders", "interface", "language_accessibility", "language_accessibility/language",
        "language_accessibility/accessibility", "favourites", "presets");

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
        // The Custom layout of the options menu is this hub; it outranks Slate Menu's own options screen. setScreen has
        // not switched when the swap runs, so the current screen is the one that opened the options.
        MenuSlots.provide(CoreSlots.OPTIONS, Layout.CUSTOM, 10, s -> new ConfigHubScreen(Minecraft.getInstance().screen, null));
        // slate_config:hub/<path> opens a page directly (screenshot harness: -PautoScreens=slate_config:hub/video,...).
        for (final String path : PAGE_IDS) CoreActions.SCREEN_FACTORIES.put("slate_config:hub/" + path, p -> new ConfigHubScreen(p, path));
        SlateEvents.CLIENT_TICK_END.register(ApplyQueue::tick);
        installSwaps();
        LOGGER.info("[Slate Config] client init ({} curated page(s))", CuratedPages.load().size());
        if (System.getenv("SLATE_CONFIG_SMOKE") != null || String.valueOf(System.getProperty("slate.autoScreens")).contains("slate_config:smoke")) SmokeTest.install();
    }

    /**
     * Vanilla option sub-screens open the matching hub page and tab; Sodium's screen too. Switched off by config.json,
     * and only while the options menu itself shows in a Slate layout: with the {@code minecraft:options} slot on its
     * vanilla layout (globally, per menu, or because Slate UI is absent) every vanilla sub-screen stays as well.
     */
    private static void installSwaps() {
        if (!ConfigSettings.get().swapVanillaScreens) return;
        swap(VideoSettingsScreen.class, "video");
        swap(SoundOptionsScreen.class, "audio");
        swap(ControlsScreen.class, "controls");
        swap(KeyBindsScreen.class, "controls/keys");
        swap(ChatOptionsScreen.class, "multiplayer/chat");
        swap(LanguageSelectScreen.class, "language_accessibility/language");
        swap(AccessibilityOptionsScreen.class, "language_accessibility/accessibility");
        swap(OnlineOptionsScreen.class, "multiplayer/online");
        if (SlatePlatform.get().isModLoaded("sodium")) SodiumBridge.installScreenSwap((parent, page) -> redirecting() ? new ConfigHubScreen(parent, page) : null);
    }

    /** Whether vanilla's option sub-screens are redirected into the hub right now (the options slot is not vanilla). */
    public static boolean redirecting() {
        return MenuSlots.effective(CoreSlots.OPTIONS) != Layout.VANILLA;
    }

    private static void swap(final Class<? extends Screen> cls, final String page) {
        // setScreen has not switched yet when the swap runs, so the current screen is the sub-screen's parent.
        ScreenSwaps.register(cls, original -> redirecting() ? new ConfigHubScreen(Minecraft.getInstance().screen, page) : null);
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
