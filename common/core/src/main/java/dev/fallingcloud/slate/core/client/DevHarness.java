package dev.fallingcloud.slate.core.client;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.theme.Theme;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Unattended screenshot runs for development, driven by system properties (never set in production):
 * <ul>
 *   <li>{@code slate.autoScreens} — comma-separated screen ids to open in turn from the title screen
 *       (any id known to {@link CoreActions#openScreen}; {@code minecraft:title} keeps the title screen;
 *       {@code none} closes the screen).</li>
 *   <li>{@code slate.autoWorld} — {@code true}: afterwards create a fresh creative world ("slate-harness",
 *       fixed seed) and capture {@code slate.autoWorldScreens} (default {@code hud,minecraft:pause,minecraft:chat,slate:hub};
 *       {@code hud} = no screen) with a few seeded chat lines.</li>
 *   <li>{@code slate.autoSkin} — {@code DARK} or {@code VANILLA}, applied in memory (not saved).</li>
 *   <li>{@code slate.autoDir} — output base; PNGs land in {@code <autoDir>/screenshots/<id>-<skin>.png}.</li>
 *   <li>{@code slate.autoFrames} — frames to wait before each capture (default 45).</li>
 *   <li>{@code slate.autoQuit} — {@code true} stops the game after the last capture.</li>
 * </ul>
 */
public final class DevHarness {

    private enum Phase { MENU, WORLD_LOADING, WORLD, DONE }

    private static List<String> queue;
    private static List<String> worldQueue;
    private static int frames;
    private static int waitFrames = 45;
    private static boolean started;
    private static Phase phase = Phase.MENU;
    private static File dir;
    private static boolean wantWorld;

    static void init() {
        final String screens = System.getProperty("slate.autoScreens");
        wantWorld = Boolean.getBoolean("slate.autoWorld");
        if ((screens == null || screens.isBlank()) && !wantWorld) return;
        queue = new ArrayList<>();
        if (screens != null && !screens.isBlank()) queue.addAll(List.of(screens.split(",")));
        worldQueue = new ArrayList<>(List.of(System.getProperty("slate.autoWorldScreens", "hud,minecraft:pause,minecraft:chat,slate:hub").split(",")));
        waitFrames = Integer.getInteger("slate.autoFrames", 45);
        dir = new File(System.getProperty("slate.autoDir", SlatePlatform.get().gameDir().resolve("slate-shots").toString()));
        final String skin = System.getProperty("slate.autoSkin");
        if (skin != null) { Slate.config().skin = skin; Theme.reload(); }
        // Capture after whatever is drawn last: the screen when one is open (SCREEN_RENDER_POST runs after
        // it), else the HUD. The HUD event fires BEFORE a screen renders, so it must not clock screen frames.
        SlateEvents.SCREEN_RENDER_POST.register((screen, g, mx, my, pt) -> onFrame());
        SlateEvents.HUD_RENDER.register((g, pt) -> { if (Minecraft.getInstance().screen == null) onFrame(); });
        Slate.LOGGER.info("[Slate] dev harness: {} menu screen(s){} -> {}", queue.size(), wantWorld ? " + world" : "", dir);
    }

    private static void onFrame() {
        final Minecraft mc = Minecraft.getInstance();
        switch (phase) {
            case MENU -> {
                if (mc.getOverlay() != null || mc.screen == null) return;
                if (!started) {
                    started = true;
                    frames = 0;
                    if (queue.isEmpty()) { advanceFromMenu(); return; }
                    open(queue.get(0));
                    return;
                }
                if (++frames < waitFrames) return;
                frames = 0;
                if (!queue.isEmpty()) capture(queue.remove(0));
                if (queue.isEmpty()) advanceFromMenu();
                else open(queue.get(0));
            }
            case WORLD_LOADING -> {
                // In the world once the level exists, the player spawned and no loading screen remains.
                if (mc.level == null || mc.player == null || mc.screen != null || mc.getOverlay() != null) { frames = 0; return; }
                if (++frames < waitFrames * 2) return;     // let chunks and the HUD settle
                frames = 0;
                seedChat();
                phase = Phase.WORLD;
                openWorldScreen(worldQueue.get(0));
            }
            case WORLD -> {
                if (++frames < waitFrames) return;
                frames = 0;
                capture(worldQueue.remove(0));
                if (worldQueue.isEmpty()) finish();
                else openWorldScreen(worldQueue.get(0));
            }
            case DONE -> {}
        }
    }

    private static void advanceFromMenu() {
        if (!wantWorld) { finish(); return; }
        phase = Phase.WORLD_LOADING;
        frames = 0;
        final Minecraft mc = Minecraft.getInstance();
        try {
            final LevelSettings settings = new LevelSettings("slate-harness", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            final WorldOptions options = new WorldOptions(20260923L, false, false);
            mc.createWorldOpenFlows().createFreshLevel("slate-harness-" + System.currentTimeMillis() % 100000, settings, options, WorldPresets::createNormalWorldDimensions, new TitleScreen());
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] dev harness could not create a world", e);
            finish();
        }
    }

    private static void seedChat() {
        final Minecraft mc = Minecraft.getInstance();
        try {
            mc.gui.getChat().addMessage(Component.literal("<Steve> hey, anyone up for the nether trip tonight?"));
            mc.gui.getChat().addMessage(Component.literal("<Steve> I found a fortress at -240 80"));
            mc.gui.getChat().addMessage(Component.literal("<Alex> sure! bring blaze rods :)"));
            mc.gui.getChat().addMessage(Component.literal("<" + mc.getUser().getName() + "> on my way"));
            mc.gui.getChat().addMessage(Component.literal("Steve joined the game"));
        } catch (final Exception e) {
            Slate.LOGGER.warn("[Slate] dev harness could not seed chat: {}", e.toString());
        }
    }

    private static void openWorldScreen(final String id) {
        final Minecraft mc = Minecraft.getInstance();
        try {
            switch (id) {
                case "hud", "none" -> mc.setScreen(null);
                case "minecraft:pause" -> mc.setScreen(new PauseScreen(true));
                case "minecraft:chat" -> mc.setScreen(new ChatScreen(""));
                default -> CoreActions.openScreen(id);
            }
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] dev harness could not open {}", id, e);
        }
    }

    private static void capture(final String id) {
        final Minecraft mc = Minecraft.getInstance();
        final String file = id.replaceAll("[^A-Za-z0-9_.-]", "_") + "-" + Theme.current().skin().name().toLowerCase() + ".png";
        Screenshot.grab(dir, file, mc.getMainRenderTarget(), c -> Slate.LOGGER.info("[Slate] dev harness: {}", c.getString()));
    }

    private static void finish() {
        phase = Phase.DONE;
        if (Boolean.getBoolean("slate.autoQuit")) {
            final Minecraft mc = Minecraft.getInstance();
            // Give the async PNG write a moment before tearing the game down.
            new Thread(() -> { try { Thread.sleep(2000); } catch (InterruptedException ignored) {} mc.execute(mc::stop); }, "slate-harness-quit").start();
        }
    }

    private static void open(final String id) {
        final Minecraft mc = Minecraft.getInstance();
        try {
            if ("none".equals(id)) mc.setScreen(null);
            else if ("minecraft:title".equals(id)) mc.setScreen(new TitleScreen());
            else CoreActions.openScreen(id);
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] dev harness could not open {}", id, e);
        }
    }

    private DevHarness() {}
}
