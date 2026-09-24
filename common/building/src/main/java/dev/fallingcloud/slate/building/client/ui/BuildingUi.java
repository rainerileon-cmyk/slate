package dev.fallingcloud.slate.building.client.ui;

import dev.fallingcloud.slate.building.client.BuildingClient;
import dev.fallingcloud.slate.building.client.hud.ModeHud;
import dev.fallingcloud.slate.building.client.menu.BuildMenuScreen;
import dev.fallingcloud.slate.building.client.menu.MenuKeys;
import dev.fallingcloud.slate.building.client.menu.WheelEditorScreen;
import dev.fallingcloud.slate.building.client.settings.BuildingSettingsScreen;
import dev.fallingcloud.slate.building.client.wheel.PickBlockSwap;
import dev.fallingcloud.slate.building.client.wheel.WheelConfig;
import dev.fallingcloud.slate.building.client.wheel.WheelOverlay;
import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.screen.ScreenIds;

/**
 * Client init of the UI (design §5, §10): the Alt wheel overlay and its input handler, pick-block swap, the mode
 * HUD, the build-menu key and screen ({@link BuildingClient#BUILD_MENU_SCREEN}), the wheel editor, the standalone
 * settings screen ({@link BuildingClient#SETTINGS_SCREEN}), the Slate Config "Building" tab when Slate Config is
 * installed, and the dev-harness scenarios. Called once from {@code BuildingClient.init()}; on Fabric that runs
 * before Core's client init, so this only registers listeners and factories.
 */
public final class BuildingUi {

    /** Screen id of the wheel editor. */
    public static final String WHEEL_EDITOR_SCREEN = "slate_building:wheel_editor";

    private static boolean initialised;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        WheelConfig.init();
        dev.fallingcloud.slate.building.client.settings.BuildingSettings.init();
        WheelOverlay.init();
        PickBlockSwap.init();
        ModeHud.init();
        MenuKeys.init();
        CoreActions.SCREEN_FACTORIES.put(BuildingClient.BUILD_MENU_SCREEN, BuildMenuScreen::new);
        CoreActions.SCREEN_FACTORIES.put(BuildingClient.SETTINGS_SCREEN, BuildingSettingsScreen::new);
        CoreActions.SCREEN_FACTORIES.put(WHEEL_EDITOR_SCREEN, WheelEditorScreen::new);
        ScreenIds.register(BuildMenuScreen.class, BuildingClient.BUILD_MENU_SCREEN, "Build menu");
        ScreenIds.register(BuildingSettingsScreen.class, BuildingClient.SETTINGS_SCREEN, "Building settings");
        ScreenIds.register(WheelEditorScreen.class, WHEEL_EDITOR_SCREEN, "Wheel editor");
        if (SlatePlatform.get().isModLoaded("slate_config")) ConfigSide.init();
        UiHarness.init();
    }

    /** Only loaded after the {@code slate_config} presence check (soft dependency). */
    private static final class ConfigSide {
        static void init() {
            dev.fallingcloud.slate.building.client.settings.BuildingSettingsTab.register();
        }
    }

    private BuildingUi() {}
}
