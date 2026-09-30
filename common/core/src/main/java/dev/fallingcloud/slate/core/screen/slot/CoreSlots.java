package dev.fallingcloud.slate.core.screen.slot;

import dev.fallingcloud.slate.core.client.SlateHubScreen;
import dev.fallingcloud.slate.core.client.setup.SlateSetupScreen;
import net.minecraft.network.chat.Component;

/**
 * Core's catalogue of menu slots: every menu the suite touches, defined here so the settings tables can list them
 * (with what is missing) whether or not the module that implements them is installed. Modules only
 * {@link MenuSlots#provide} their layouts. Registered when {@link MenuSlots} loads, so it precedes every provider.
 */
public final class CoreSlots {

    private static final String SCREENS = "net.minecraft.client.gui.screens.";

    public static final String TITLE = "minecraft:title";
    public static final String WORLDS = "minecraft:select_world";
    public static final String SERVERS = "minecraft:multiplayer";
    public static final String CREATE_WORLD = "minecraft:create_world";
    public static final String OPTIONS = "minecraft:options";
    public static final String PAUSE = "minecraft:pause";
    public static final String DISCONNECTED = "minecraft:disconnected";
    /** The start-up window (NeoForge's early display; Fabric's loading overlay). */
    public static final String LOADING = "slate:loading";
    /** The in-world loading screens (loading a world, joining, terrain, saving, progress), drawn over vanilla's. */
    public static final String LEVEL_LOADING = "minecraft:level_loading";
    public static final String SCREENSHOTS = "slate_menu:screenshots";
    public static final String FRIENDS = "slate_multiplayer:hub";
    public static final String PROFILE = "slate_profile:profile";
    public static final String SETUP = "slate:setup";
    public static final String HUB = "slate:hub";

    static void register() {
        MenuSlots.register(MenuSlot.vanilla(TITLE, name("title"), "slate_menu", SCREENS + "TitleScreen"));
        MenuSlots.register(MenuSlot.vanilla(WORLDS, name("select_world"), "slate_menu", SCREENS + "worldselection.SelectWorldScreen"));
        MenuSlots.register(MenuSlot.vanilla(SERVERS, name("multiplayer"), "slate_menu", SCREENS + "multiplayer.JoinMultiplayerScreen"));
        MenuSlots.register(MenuSlot.vanilla(CREATE_WORLD, name("create_world"), "slate_menu", SCREENS + "worldselection.CreateWorldScreen"));
        MenuSlots.register(MenuSlot.vanilla(OPTIONS, name("options"), "slate_config", SCREENS + "options.OptionsScreen"));
        MenuSlots.register(MenuSlot.vanilla(PAUSE, name("pause"), "slate_menu", SCREENS + "PauseScreen"));
        MenuSlots.register(MenuSlot.vanilla(DISCONNECTED, name("disconnected"), "slate_menu", SCREENS + "DisconnectedScreen"));
        MenuSlots.register(MenuSlot.vanilla(LOADING, name("loading"), "slate_menu"));
        MenuSlots.register(MenuSlot.vanilla(LEVEL_LOADING, name("level_loading"), "slate_menu"));
        MenuSlots.register(MenuSlot.custom(SCREENSHOTS, name("screenshots"), "slate_menu"));
        MenuSlots.register(MenuSlot.custom(FRIENDS, name("friends"), "slate_multiplayer"));
        MenuSlots.register(MenuSlot.custom(PROFILE, name("profile"), "slate_profile"));
        MenuSlots.register(MenuSlot.custom(SETUP, name("setup"), "slate"));
        MenuSlots.register(MenuSlot.custom(HUB, name("hub"), "slate"));
        // Core's own menus: the chooser and the hub have one layout each.
        MenuSlots.provide(SETUP, Layout.CUSTOM, SlateSetupScreen::new);
        MenuSlots.provide(HUB, Layout.CUSTOM, SlateHubScreen::new);
    }

    private static Component name(final String key) {
        return Component.translatable("slate.slot." + key);
    }

    private CoreSlots() {}
}
