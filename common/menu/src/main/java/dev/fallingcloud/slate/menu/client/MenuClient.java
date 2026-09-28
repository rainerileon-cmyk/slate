package dev.fallingcloud.slate.menu.client;

import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.Placeholders;
import dev.fallingcloud.slate.core.module.Modules;
import dev.fallingcloud.slate.core.module.SlateModule;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.screen.ScreenSwaps;
import dev.fallingcloud.slate.core.theme.Theme;
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
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
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

        // Every swap is gated twice: Core's layout switch (the setup screen's "custom layout"; off = every vanilla screen
        // stays), then the per-screen toggle in menu.json (an advanced override for one screen at a time).
        ScreenSwaps.register(TitleScreen.class, s -> custom() && cfg().titleScreen && !Minecraft.getInstance().isDemo() ? new SlateTitleScreen() : null);
        ScreenSwaps.register(SelectWorldScreen.class, s -> custom() && cfg().worldsScreen ? new SlateWorldsScreen(((SelectWorldScreenAccessor) s).slate$lastScreen()) : null);
        ScreenSwaps.register(JoinMultiplayerScreen.class, s -> custom() && cfg().serversScreen ? new SlateServersScreen(((JoinMultiplayerScreenAccessor) s).slate$lastScreen()) : null);
        ScreenSwaps.register(PauseScreen.class, s -> custom() && cfg().pauseScreen && ((PauseScreen) s).showsPauseMenu() ? new SlatePauseScreen() : null);
        ScreenSwaps.register(OptionsScreen.class, s -> custom() && cfg().optionsScreen ? optionsScreen(((OptionsScreenAccessor) s).slate$lastScreen()) : null);
        ScreenSwaps.register(DisconnectedScreen.class, s -> {
            if (!custom() || !cfg().disconnectedScreen) return null;
            final DisconnectedScreenAccessor acc = (DisconnectedScreenAccessor) s;
            return new SlateDisconnectedScreen(acc.slate$parent(), s.getTitle(), acc.slate$details());
        });

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

    /** Core's layout switch: with it off, no vanilla screen is replaced (Core adds its Slate button to vanilla's screens instead). */
    public static boolean custom() {
        return Theme.customLayout();
    }

    /**
     * What replaces vanilla's options screen: the Slate Config hub when that module is installed (looked up
     * through Core's screen factories, so Menu needs no dependency on it), else Menu's own options screen.
     */
    private static Screen optionsScreen(final Screen lastScreen) {
        final Function<Screen, Screen> hub = CoreActions.SCREEN_FACTORIES.get("slate_config:hub");
        if (hub != null) {
            final Screen s = hub.apply(lastScreen);
            if (s != null) return s;
        }
        return new SlateOptionsScreen(lastScreen);
    }

    /** The Multiplayer module's friends screen id, when that module registered one. */
    public static Optional<String> friendsScreenId() {
        if (!Modules.isLoaded("slate_multiplayer")) return Optional.empty();
        for (final String id : List.of("slate_multiplayer:friends", "slate_multiplayer:hub", "slate_multiplayer:social")) {
            if (CoreActions.SCREEN_FACTORIES.containsKey(id)) return Optional.of(id);
        }
        return Optional.empty();
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
