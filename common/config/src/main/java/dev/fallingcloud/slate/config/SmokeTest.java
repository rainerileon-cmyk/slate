package dev.fallingcloud.slate.config;

import dev.fallingcloud.slate.config.doc.Documents;
import dev.fallingcloud.slate.config.doc.FileDocument;
import dev.fallingcloud.slate.config.editor.FileEditorScreen;
import dev.fallingcloud.slate.config.hub.ConfigHubScreen;
import dev.fallingcloud.slate.config.preset.Presets;
import dev.fallingcloud.slate.config.ui.TabHost;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.screen.SidebarPage;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Self-driving verification for dev runs, enabled by the {@code SLATE_CONFIG_SMOKE} environment variable or
 * {@code -Pslate.autoScreens=slate_config:smoke} (with a large {@code autoFrames} so Core's harness waits):
 * opens the hub, walks every sidebar page and every top tab of it in both skins, a secondary tab, search,
 * the file editor and a preset, then creates a world and captures the in-world Gameplay category (difficulty
 * and its lock), saving a screenshot of each step under {@code screenshots/slate-config-*.png}, and stops the
 * client. Never active in a normal launch.
 */
final class SmokeTest {

    private static int ticks, step, pageIdx, tabIdx, pass, worldTicks;
    private static ConfigHubScreen hub;
    private static List<String> pageIds = List.of();
    private static boolean done;
    /** A frame captured two ticks after a tab switch, to see the transition mid-flight. */
    private static String animShot;
    private static int animWait;

    static void install() {
        SlateConfig.LOGGER.warn("[Slate Config] SMOKE TEST enabled: driving the settings screens, then exiting");
        CoreActions.SCREEN_FACTORIES.putIfAbsent("slate_config:smoke", p -> new ConfigHubScreen(p, null));
        // A contributed tab (what Slate Building does), to see the SettingsTabs path in a category and on a page.
        final java.util.function.Supplier<List<dev.fallingcloud.slate.config.ui.Section>> sample = () -> List.of(
            dev.fallingcloud.slate.config.ui.Section.of("first", net.minecraft.network.chat.Component.literal("First"),
                dev.fallingcloud.slate.config.resolver.VanillaOptions.all("fov", "bobView")),
            dev.fallingcloud.slate.config.ui.Section.of("second", net.minecraft.network.chat.Component.literal("Second"),
                dev.fallingcloud.slate.config.resolver.VanillaOptions.all("autoJump")));
        dev.fallingcloud.slate.config.api.SettingsTabs.register(dev.fallingcloud.slate.config.api.SettingsTabs.GAMEPLAY,
            new dev.fallingcloud.slate.config.api.SettingsTabs.Tab("sample", net.minecraft.network.chat.Component.literal("Sample module"),
                dev.fallingcloud.slate.core.gfx.Icon.BLOCK, sample, 100));
        SlateEvents.CLIENT_TICK_END.register(SmokeTest::tick);
    }

    private static void shot(final String name) {
        final Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "slate-config-" + name + ".png", mc.getMainRenderTarget(), c -> SlateConfig.LOGGER.info("[smoke] {}", c.getString()));
    }

    private static void tick() {
        if (done) return;
        final Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        if (animShot != null && ++animWait >= 2) { shot(animShot); animShot = null; }
        if (step < 20 && mc.screen == null) return;
        ticks++;
        if (ticks < 60 || ticks % 20 != 0) return;
        try {
            advance(mc);
        } catch (final Exception e) {
            SlateConfig.LOGGER.error("[smoke] step {} failed", step, e);
            done = true;
            mc.stop();
        }
    }

    private static void skin(final boolean vanilla) {
        Slate.configFile().update(c -> c.skin = vanilla ? "VANILLA" : "DARK");
        Theme.reload();
    }

    private static void advance(final Minecraft mc) {
        final String skin = pass == 0 ? "dark" : "vanilla";
        switch (step) {
            case 0 -> {
                skin(pass == 1);
                if (pass == 0 && !ConfigSettings.isFavourite("optionsTxt:renderDistance")) ConfigSettings.toggleFavourite("optionsTxt:renderDistance");
                hub = new ConfigHubScreen(mc.screen, "video");
                mc.setScreen(hub);
                pageIds = hub.pages().stream().map(SidebarPage::id).toList();
                SlateConfig.LOGGER.info("[smoke] {} pass: pages {}", skin, pageIds);
                pageIdx = 0;
                tabIdx = 0;
                step = 1;
            }
            case 1 -> {
                if (mc.screen != hub) { mc.setScreen(hub); return; }
                final String page = pageIds.get(pageIdx).replace(':', '_');
                shot(skin + "-" + String.format("%02d", pageIdx) + "-" + page + "-" + tabIdx);
                final int n = hub.currentPage() instanceof TabHost th ? th.tabCount() : 1;
                if (tabIdx + 1 < n) {
                    tabIdx++;
                    ((TabHost) hub.currentPage()).cycleTab(1, false);
                    return;
                }
                if (n > 1) ((TabHost) hub.currentPage()).cycleTab(1, false);     // wraps back to the tab it opened on
                tabIdx = 0;
                pageIdx++;
                if (pageIdx < pageIds.size()) hub.showPage(pageIds.get(pageIdx));
                else step = 2;
            }
            case 2 -> {
                hub.open("multiplayer/chat");
                if (hub.leaf() instanceof TabHost th) th.cycleTab(1, false);          // a secondary tab of a category tab
                step = 3;
            }
            case 3 -> {
                shot(skin + "-secondary-tab");
                hub.open("video");
                hub.debugSearch("chat");
                step = 4;
            }
            case 4 -> {
                shot(skin + "-search");
                Popups.closeAll();
                hub.debugSearch("");
                hub.open("interface");
                if (hub.currentPage() instanceof TabHost th) th.cycleTab(1, false);
                animShot = skin + "-transition";
                animWait = 0;
                step = 5;
            }
            case 5 -> {
                final FileDocument doc = ConfigPlatform.get().nativeConfig("neoforge-client.toml")
                    .orElseGet(() -> Documents.json(JsonConfig.dir().resolve("core.json"), "slate"));
                SlateConfig.LOGGER.info("[smoke] editor: {} ({} sections)", doc.path(), doc.sections().size());
                mc.setScreen(new FileEditorScreen(hub, doc));
                step = 6;
            }
            case 6 -> {
                shot(skin + "-editor");
                step = 7;
            }
            case 7 -> {
                if (pass == 0) {
                    final int before = mc.options.renderDistance().get();
                    final Presets.ApplyResult r = Presets.apply(Presets.builtins().get(0));
                    SlateConfig.LOGGER.info("[smoke] preset '{}': applied {} skipped {}; renderDistance {} -> {}",
                        Presets.builtins().get(0).name, r.applied(), r.skipped(), before, mc.options.renderDistance().get());
                    pass = 1;
                    step = 0;
                    mc.setScreen(new TitleScreen());
                } else {
                    skin(false);
                    step = 20;
                    worldTicks = 0;
                    final LevelSettings settings = new LevelSettings("slate-config-smoke", GameType.CREATIVE, false, Difficulty.NORMAL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel("slate-config-smoke-" + System.currentTimeMillis() % 100000, settings,
                        new WorldOptions(20260924L, false, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
                }
            }
            case 20 -> {
                // In the world once the level exists and no loading screen remains; let chunks settle.
                if (mc.level == null || mc.player == null || mc.screen != null) { worldTicks = 0; return; }
                if (++worldTicks < 4) return;
                hub = new ConfigHubScreen(null, "gameplay");
                mc.setScreen(hub);
                step = 21;
            }
            case 21 -> {
                shot("world-dark-gameplay");
                skin(true);
                hub = new ConfigHubScreen(null, "gameplay");
                mc.setScreen(hub);
                step = 22;
            }
            case 22 -> {
                shot("world-vanilla-gameplay");
                skin(false);
                done = true;
                SlateConfig.LOGGER.warn("[smoke] DONE - stopping client");
                mc.stop();
            }
            default -> {}
        }
    }

    private SmokeTest() {}
}
