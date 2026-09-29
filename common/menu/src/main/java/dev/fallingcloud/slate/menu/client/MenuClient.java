package dev.fallingcloud.slate.menu.client;

import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.Placeholders;
import dev.fallingcloud.slate.core.module.Modules;
import dev.fallingcloud.slate.core.module.SlateModule;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.Layout;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.menu.MenuConfig;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.disconnect.SlateDisconnectedScreen;
import dev.fallingcloud.slate.menu.client.options.SlateOptionsScreen;
import dev.fallingcloud.slate.menu.client.pause.SlatePauseScreen;
import dev.fallingcloud.slate.menu.client.screenshots.ScreenshotViewer;
import dev.fallingcloud.slate.menu.client.screenshots.Screenshots;
import dev.fallingcloud.slate.menu.client.screenshots.SlateScreenshotsScreen;
import dev.fallingcloud.slate.menu.client.servers.SlateServersScreen;
import dev.fallingcloud.slate.menu.client.title.SlateTitleScreen;
import dev.fallingcloud.slate.menu.client.worlds.SlateWorldsScreen;
import dev.fallingcloud.slate.menu.mixin.DisconnectedScreenAccessor;
import dev.fallingcloud.slate.menu.mixin.JoinMultiplayerScreenAccessor;
import dev.fallingcloud.slate.menu.mixin.OptionsScreenAccessor;
import dev.fallingcloud.slate.menu.mixin.SelectWorldScreenAccessor;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

/**
 * Client bootstrap of the Menu module: screen swaps (each gated by its menu.json flag so any screen can
 * fall back to vanilla), screen ids and factories for the dev mode, layout elements, placeholders,
 * hub entries and the last-played recorder.
 */
public final class MenuClient {

    private static boolean initialised;
    private static long screenshotCountMs;
    private static int screenshotCount = -1;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;

        ScreenIds.register(SlateTitleScreen.class, "slate_menu:title", "Slate title screen");
        ScreenIds.register(SlateWorldsScreen.class, "slate_menu:worlds", "Slate worlds");
        ScreenIds.register(SlateServersScreen.class, "slate_menu:servers", "Slate servers");
        ScreenIds.register(SlatePauseScreen.class, "slate_menu:pause", "Slate pause menu");
        ScreenIds.register(SlateOptionsScreen.class, "slate_menu:options", "Slate options");
        ScreenIds.register(SlateScreenshotsScreen.class, "slate_menu:screenshots", "Screenshots");
        ScreenIds.register(ScreenshotViewer.class, "slate_menu:screenshot_viewer", "Screenshot viewer");
        ScreenIds.register(SlateDisconnectedScreen.class, "slate_menu:disconnected", "Slate disconnected");

        // Menu slots (Core defines them): this module provides the Custom layout of every vanilla menu it rebuilds. Which
        // layout a menu shows is resolved by Core from the global layout and the per-menu overrides in core.json; a
        // factory returns null to leave vanilla's screen alone (the demo title screen, a pause screen without its menu).
        MenuSlots.provide(CoreSlots.TITLE, Layout.CUSTOM, s -> Minecraft.getInstance().isDemo() ? null : new SlateTitleScreen());
        MenuSlots.provide(CoreSlots.WORLDS, Layout.CUSTOM, s -> new SlateWorldsScreen(((SelectWorldScreenAccessor) s).slate$lastScreen()));
        MenuSlots.provide(CoreSlots.SERVERS, Layout.CUSTOM, s -> new SlateServersScreen(((JoinMultiplayerScreenAccessor) s).slate$lastScreen()));
        MenuSlots.provide(CoreSlots.PAUSE, Layout.CUSTOM, s -> ((PauseScreen) s).showsPauseMenu() ? new SlatePauseScreen() : null);
        // Slate Config's hub outranks this options screen when that module is installed (it provides at a higher priority).
        MenuSlots.provide(CoreSlots.OPTIONS, Layout.CUSTOM, -10, s -> new SlateOptionsScreen(((OptionsScreenAccessor) s).slate$lastScreen()));
        MenuSlots.provide(CoreSlots.DISCONNECTED, Layout.CUSTOM, s -> {
            final DisconnectedScreenAccessor acc = (DisconnectedScreenAccessor) s;
            return new SlateDisconnectedScreen(acc.slate$parent(), s.getTitle(), acc.slate$details());
        });
        MenuSlots.provide(CoreSlots.SCREENSHOTS, Layout.CUSTOM, SlateScreenshotsScreen::new);
        refreshLoadingSupport();
        migrateScreenFlags();

        CoreActions.SCREEN_FACTORIES.put("slate_menu:title", p -> new SlateTitleScreen());
        CoreActions.SCREEN_FACTORIES.put("slate_menu:worlds", SlateWorldsScreen::new);
        CoreActions.SCREEN_FACTORIES.put("slate_menu:servers", SlateServersScreen::new);
        CoreActions.SCREEN_FACTORIES.put("slate_menu:options", SlateOptionsScreen::new);
        CoreActions.SCREEN_FACTORIES.put("slate_menu:screenshots", SlateScreenshotsScreen::new);

        // Loading screens are drawn over by menu.mixin.*ScreenMixin (LoadingScreens), not swapped; previews for screenshot runs.
        if (System.getProperty("slate.autoScreens") != null) dev.fallingcloud.slate.menu.client.loading.LoadingPreviews.register();

        MenuElements.registerAll();

        Placeholders.register("last_world", () -> LastPlayed.quick().map(t -> t.name() == null || t.name().isBlank() ? t.id() : t.name()).orElse(""));
        Placeholders.register("last_server", () -> {
            final MenuConfig c = cfg();
            return c.lastServerName == null || c.lastServerName.isBlank() ? c.lastServerAddress : c.lastServerName;
        });
        Placeholders.register("last_played", () -> LastPlayed.quick().map(t -> Fmt.ago(t.at()).getString()).orElse(""));
        Placeholders.register("session_time", () -> Fmt.duration(LastPlayed.sessionMs()));
        Placeholders.register("screenshots", () -> Integer.toString(screenshotCount()));

        SlateEvents.CLIENT_JOINED_SERVER.register(LastPlayed::recordJoin);
        SlateEvents.CLIENT_LEFT_SERVER.register(LastPlayed::clearSession);
    }

    public static MenuConfig cfg() {
        return SlateMenu.config();
    }

    /**
     * The legacy layout switch: any Slate layout is on globally.
     * @deprecated menus resolve per slot now: {@code MenuSlots.effective(slot) != Layout.VANILLA}.
     */
    @Deprecated
    public static boolean custom() {
        return MenuSlots.globalLayout() != Layout.VANILLA;
    }

    /**
     * The loading screens and the start-up window are drawn over vanilla's, not swapped, so their slots carry a
     * support mark instead of a factory; {@code loadingScreens} in menu.json takes it away. Re-run after that toggle.
     */
    public static void refreshLoadingSupport() {
        final boolean on = cfg().loadingScreens;
        MenuSlots.support(CoreSlots.LEVEL_LOADING, Layout.CUSTOM, on);
        MenuSlots.support(CoreSlots.LOADING, Layout.CUSTOM, on);
    }

    /**
     * One-time move of the per-screen flags of older menu.json files ({@code titleScreen: false}, ...) into Core's
     * per-menu layout overrides ({@code screens.<slot>.layout = VANILLA}); the flags are not read any more.
     */
    private static void migrateScreenFlags() {
        final MenuConfig c = cfg();
        if (c.layoutFlagsMigrated) return;
        final java.util.Map<String, Boolean> flags = new java.util.LinkedHashMap<>();
        flags.put(CoreSlots.TITLE, c.titleScreen);
        flags.put(CoreSlots.WORLDS, c.worldsScreen);
        flags.put(CoreSlots.SERVERS, c.serversScreen);
        flags.put(CoreSlots.PAUSE, c.pauseScreen);
        flags.put(CoreSlots.OPTIONS, c.optionsScreen);
        flags.put(CoreSlots.DISCONNECTED, c.disconnectedScreen);
        final List<String> vanilla = flags.entrySet().stream().filter(e -> !e.getValue()).map(java.util.Map.Entry::getKey).toList();
        if (!vanilla.isEmpty()) {
            dev.fallingcloud.slate.core.Slate.configFile().update(core -> {
                for (final String slot : vanilla) core.overrideOrCreate(slot).layout = Layout.VANILLA.name();
            });
            MenuSlots.refresh();
            SlateMenu.LOGGER.info("[Slate Menu] moved {} per-screen flag(s) into Core's menu overrides", vanilla.size());
        }
        SlateMenu.configFile().update(m -> m.layoutFlagsMigrated = true);
    }

    /** Number of files in the screenshots folder, cached for a few seconds (placeholders refresh every frame). */
    private static int screenshotCount() {
        final long now = System.currentTimeMillis();
        if (screenshotCount >= 0 && now - screenshotCountMs < 5000) return screenshotCount;
        screenshotCountMs = now;
        int n = 0;
        final java.nio.file.Path dir = Screenshots.dir();
        if (Files.isDirectory(dir)) {
            try (Stream<java.nio.file.Path> s = Files.list(dir)) {
                n = (int) s.filter(Screenshots::isImage).count();
            } catch (final Exception ignored) {}
        }
        screenshotCount = n;
        return n;
    }

    public static List<SlateModule.HubEntry> hubEntries() {
        final Minecraft mc = Minecraft.getInstance();
        return List.of(
            new SlateModule.HubEntry(Component.translatable("slate_menu.screenshots.title"), Icon.CAMERA, () -> mc.setScreen(new SlateScreenshotsScreen(mc.screen))),
            new SlateModule.HubEntry(Component.translatable("menu.singleplayer"), Icon.SINGLEPLAYER, () -> mc.setScreen(new SelectWorldScreen(mc.screen))),
            new SlateModule.HubEntry(Component.translatable("menu.multiplayer"), Icon.MULTIPLAYER, () -> mc.setScreen(new JoinMultiplayerScreen(mc.screen))),
            new SlateModule.HubEntry(Component.translatable("menu.options"), Icon.SETTINGS, () -> mc.setScreen(new OptionsScreen(mc.screen, mc.options))));
    }

    private MenuClient() {}
}
