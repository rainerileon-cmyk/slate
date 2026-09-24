package dev.fallingcloud.slate.building.client;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.input.BuildKeys;
import dev.fallingcloud.slate.building.client.input.KeyClaims;
import dev.fallingcloud.slate.building.client.mode.ClientModeState;
import dev.fallingcloud.slate.building.client.place.AccuratePlacement;
import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.module.SlateModule;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Client entry of Slate Building ({@code SlateBuilding.initClient()}): keys and input first, then each area's client
 * init in a fixed order, then the dev harness. On Fabric this runs from the MAIN entrypoint, before Core's client
 * entry has initialised Slate's theme and installed key mappings (keys are queued, so registering is safe; do not
 * use {@code Theme}/screens here, only register listeners).
 */
public final class BuildingClient {

    /** {@code CoreActions.SCREEN_FACTORIES} id of the build menu (registered by the UI owner, C). */
    public static final String BUILD_MENU_SCREEN = "slate_building:build_menu";
    /** Screen id of a standalone building settings screen, used when Slate Config is absent (optional, C). */
    public static final String SETTINGS_SCREEN = "slate_building:settings";

    private static boolean initialised;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        BuildKeys.init();
        AccuratePlacement.init();
        KeyClaims.init();
        ServerSettingsClient.init();
        ClientModeState.init();
        dev.fallingcloud.slate.building.variant.client.VariantClient.init();
        dev.fallingcloud.slate.building.client.render.BuildingRender.init();
        dev.fallingcloud.slate.building.client.ui.BuildingUi.init();
        dev.fallingcloud.slate.building.client.mode.ModeClient.init();
        dev.fallingcloud.slate.building.toolbox.client.ToolboxClient.init();
        dev.fallingcloud.slate.building.chisel.client.ChiselClient.init();
        BuildingHarness.init();
    }

    /** Entries on the Slate hub. */
    public static List<SlateModule.HubEntry> hubEntries() {
        return List.of(
            new SlateModule.HubEntry(Component.translatable("slate_building.hub.build_menu"), BuildingIcons.WHEEL, BuildingClient::openBuildMenu),
            new SlateModule.HubEntry(Component.translatable("slate_building.hub.settings"), dev.fallingcloud.slate.core.gfx.Icon.SETTINGS,
                () -> openSettings(Minecraft.getInstance().screen)));
    }

    /** Opens the build menu (C registers it under {@link #BUILD_MENU_SCREEN}). */
    public static void openBuildMenu() {
        CoreActions.openScreen(BUILD_MENU_SCREEN);
    }

    /**
     * Opens the Building settings: the Slate Config hub on the "Building" tab of its Gameplay category
     * ({@code gameplay/building}) when Slate Config is installed, else the standalone {@link #SETTINGS_SCREEN}.
     */
    public static void openSettings(final @Nullable Screen parent) {
        if (SlatePlatform.get().isModLoaded("slate_config")) {
            ConfigBridge.openBuildingTab(parent);
            return;
        }
        if (CoreActions.SCREEN_FACTORIES.containsKey(SETTINGS_SCREEN)) {
            CoreActions.openScreen(SETTINGS_SCREEN);
            return;
        }
        SlateBuilding.LOGGER.info("[Slate Building] no settings screen available (Slate Config is not installed)");
    }

    /**
     * The Building settings as a screen, for loader "Config" buttons: the Slate Config hub on {@code gameplay/building},
     * else the standalone {@link #SETTINGS_SCREEN}, else null (no settings UI installed).
     */
    public static @Nullable Screen settingsScreen(final @Nullable Screen parent) {
        return settingsScreen(parent, null);
    }

    /**
     * {@link #settingsScreen(Screen)} opened on one section ({@code BuildingSettings} group id, e.g. {@code server_ops});
     * the standalone screen ignores the section.
     */
    public static @Nullable Screen settingsScreen(final @Nullable Screen parent, final @Nullable String section) {
        if (SlatePlatform.get().isModLoaded("slate_config")) return ConfigBridge.hub(parent, section);
        final java.util.function.Function<Screen, Screen> factory = CoreActions.SCREEN_FACTORIES.get(SETTINGS_SCREEN);
        return factory != null ? factory.apply(parent) : null;
    }

    /** Only loaded after the {@code slate_config} presence check (soft dependency). */
    private static final class ConfigBridge {
        /** Slate Config page path of the Building tab: category {@code gameplay}, tab {@code building}. */
        private static final String TAB_PATH = dev.fallingcloud.slate.config.api.SettingsTabs.GAMEPLAY + "/"
            + dev.fallingcloud.slate.building.client.settings.BuildingSettingsTab.TAB_ID;

        static void openBuildingTab(final @Nullable Screen parent) {
            dev.fallingcloud.slate.config.SlateConfigApi.openHub(parent, TAB_PATH);
        }

        static Screen hub(final @Nullable Screen parent, final @Nullable String section) {
            return dev.fallingcloud.slate.config.SlateConfigApi.hub(parent, section == null ? TAB_PATH : TAB_PATH + "/" + section);
        }
    }

    private BuildingClient() {}
}
