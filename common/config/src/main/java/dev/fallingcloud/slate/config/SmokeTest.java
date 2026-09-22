package dev.fallingcloud.slate.config;

import dev.fallingcloud.slate.config.doc.Documents;
import dev.fallingcloud.slate.config.doc.FileDocument;
import dev.fallingcloud.slate.config.editor.FileEditorScreen;
import dev.fallingcloud.slate.config.hub.ConfigHubScreen;
import dev.fallingcloud.slate.config.preset.Presets;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.screen.SidebarPage;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Self-driving verification, enabled by the {@code SLATE_CONFIG_SMOKE} environment variable in a dev
 * run: opens the hub, walks every page in both skins, exercises search, a favourite, a preset and the
 * file editor, saves a screenshot of each step under {@code screenshots/}, then stops the client.
 * Never active in a normal launch.
 */
final class SmokeTest {

    private static int ticks, step, pageIdx, pass;
    private static ConfigHubScreen hub;
    private static List<String> pageIds = List.of();
    private static boolean done;

    static void install() {
        SlateConfig.LOGGER.warn("[Slate Config] SMOKE TEST enabled: driving the settings screens, then exiting");
        SlateEvents.CLIENT_TICK_END.register(SmokeTest::tick);
    }

    private static void shot(final String name) {
        final Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "slate-config-" + name + ".png", mc.getMainRenderTarget(), c -> SlateConfig.LOGGER.info("[smoke] {}", c.getString()));
    }

    private static void tick() {
        if (done) return;
        final Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null) return;
        ticks++;
        if (ticks < 60 || ticks % 25 != 0) return;
        try {
            advance(mc);
        } catch (final Exception e) {
            SlateConfig.LOGGER.error("[smoke] step {} failed", step, e);
            done = true;
            mc.stop();
        }
    }

    private static void advance(final Minecraft mc) {
        final String skin = pass == 0 ? "dark" : "vanilla";
        switch (step) {
            case 0 -> {
                Slate.configFile().update(c -> c.skin = pass == 0 ? "DARK" : "VANILLA");
                Theme.reload();
                if (pass == 0 && !ConfigSettings.isFavourite("optionsTxt:renderDistance")) ConfigSettings.toggleFavourite("optionsTxt:renderDistance");
                hub = new ConfigHubScreen(mc.screen, "video");
                mc.setScreen(hub);
                pageIds = hub.pages().stream().map(SidebarPage::id).toList();
                SlateConfig.LOGGER.info("[smoke] {} pass: pages {}", skin, pageIds);
                pageIdx = 0;
                step = 1;
            }
            case 1 -> {
                if (mc.screen != hub) { mc.setScreen(hub); return; }
                shot(skin + "-" + pageIds.get(pageIdx).replace(':', '_'));
                pageIdx++;
                if (pageIdx < pageIds.size()) hub.showPage(pageIds.get(pageIdx));
                else step = 2;
            }
            case 2 -> {
                hub.showPage("video");
                hub.debugSearch("render");
                step = 3;
            }
            case 3 -> {
                shot(skin + "-search");
                Popups.closeAll();
                hub.debugSearch("");
                step = 4;
            }
            case 4 -> {
                final FileDocument doc = ConfigPlatform.get().nativeConfig("neoforge-client.toml")
                    .orElseGet(() -> Documents.json(JsonConfig.dir().resolve("core.json"), "slate"));
                SlateConfig.LOGGER.info("[smoke] editor: {} ({} sections)", doc.path(), doc.sections().size());
                mc.setScreen(new FileEditorScreen(hub, doc));
                step = 5;
            }
            case 5 -> {
                shot(skin + "-editor");
                step = 6;
            }
            case 6 -> {
                if (pass == 0) {
                    final int before = mc.options.renderDistance().get();
                    final Presets.ApplyResult r = Presets.apply(Presets.builtins().get(0));
                    SlateConfig.LOGGER.info("[smoke] preset '{}': applied {} skipped {}; renderDistance {} -> {}",
                        Presets.builtins().get(0).name, r.applied(), r.skipped(), before, mc.options.renderDistance().get());
                    pass = 1;
                    step = 0;
                    mc.setScreen(new TitleScreen());
                } else {
                    Slate.configFile().update(c -> c.skin = "DARK");
                    Theme.reload();
                    done = true;
                    SlateConfig.LOGGER.warn("[smoke] DONE - stopping client");
                    mc.stop();
                }
            }
            default -> {}
        }
    }

    private SmokeTest() {}
}
