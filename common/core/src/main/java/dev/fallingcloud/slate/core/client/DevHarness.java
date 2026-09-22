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

/**
 * Unattended screenshot runs for development, driven by system properties (never set in production):
 * <ul>
 *   <li>{@code slate.autoScreens} — comma-separated screen ids to open in turn (any id known to
 *       {@link CoreActions#openScreen}; {@code minecraft:title} keeps the title screen; {@code none}
 *       closes the screen).</li>
 *   <li>{@code slate.autoSkin} — {@code DARK} or {@code VANILLA}, applied in memory (not saved).</li>
 *   <li>{@code slate.autoDir} — output base; PNGs land in {@code <autoDir>/screenshots/<id>.png}.</li>
 *   <li>{@code slate.autoFrames} — frames to wait before each capture (default 45).</li>
 *   <li>{@code slate.autoQuit} — {@code true} stops the game after the last capture.</li>
 * </ul>
 * Started once the title screen is up and no loading overlay remains.
 */
public final class DevHarness {

    private static List<String> queue;
    private static int frames;
    private static int waitFrames = 45;
    private static boolean started, done;
    private static File dir;

    static void init() {
        final String screens = System.getProperty("slate.autoScreens");
        if (screens == null || screens.isBlank()) return;
        queue = new ArrayList<>(List.of(screens.split(",")));
        waitFrames = Integer.getInteger("slate.autoFrames", 45);
        dir = new File(System.getProperty("slate.autoDir", SlatePlatform.get().gameDir().resolve("slate-shots").toString()));
        final String skin = System.getProperty("slate.autoSkin");
        if (skin != null) { Slate.config().skin = skin; Theme.reload(); }
        SlateEvents.SCREEN_RENDER_POST.register((screen, g, mx, my, pt) -> onFrame());
        Slate.LOGGER.info("[Slate] dev harness: {} screen(s) -> {}", queue.size(), dir);
    }

    private static void onFrame() {
        if (done) return;
        final Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null) return;
        if (!started) {
            started = true;
            frames = 0;
            open(queue.get(0));
            return;
        }
        if (++frames < waitFrames) return;
        frames = 0;
        final String id = queue.remove(0);
        final String file = id.replaceAll("[^A-Za-z0-9_.-]", "_") + "-" + Theme.current().skin().name().toLowerCase() + ".png";
        Screenshot.grab(dir, file, mc.getMainRenderTarget(), c -> Slate.LOGGER.info("[Slate] dev harness: {}", c.getString()));
        if (queue.isEmpty()) {
            done = true;
            if (Boolean.getBoolean("slate.autoQuit")) {
                // Give the async PNG write a moment before tearing the game down.
                new Thread(() -> { try { Thread.sleep(1500); } catch (InterruptedException ignored) {} mc.execute(mc::stop); }, "slate-harness-quit").start();
            }
            return;
        }
        open(queue.get(0));
    }

    private static void open(final String id) {
        final Minecraft mc = Minecraft.getInstance();
        try {
            if ("none".equals(id)) mc.setScreen(null);
            else if ("minecraft:title".equals(id)) mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
            else CoreActions.openScreen(id);
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] dev harness could not open {}", id, e);
        }
    }

    private DevHarness() {}
}
