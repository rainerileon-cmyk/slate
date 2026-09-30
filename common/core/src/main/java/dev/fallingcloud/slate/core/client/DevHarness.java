package dev.fallingcloud.slate.core.client;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.mixin.MouseHandlerAccessor;
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
 *       {@code none} closes the screen; {@code reload} reloads the resources and is taken from the loading overlay
 *       that shows meanwhile). An entry may carry {@code @<ms>} (capture that long after the screen
 *       opened: a frame of an intro) and {@code #<x>x<y>} (put the pointer there, as fractions of the screen: a hover
 *       state), as in {@code minecraft:title@1500} or {@code minecraft:title@4000#0.35x0.6}. The same id may be
 *       listed several times; the file name carries the whole entry.</li>
 *   <li>{@code slate.autoWorld} — {@code true}: afterwards create a fresh creative world ("slate-harness",
 *       fixed seed) and capture {@code slate.autoWorldScreens} (default {@code hud,minecraft:pause,minecraft:chat,slate:hub};
 *       {@code hud} = no screen; {@code front} and {@code behind} = no screen, the player seen from outside) with a few
 *       seeded chat lines. {@code click#<x>x<y>} presses the left button there on the screen that is open and
 *       captures what that leads to; {@code command:<text>@<ms>} sends a command as the player (the wait is needed:
 *       it is what ends the text).</li>
 *   <li>{@code slate.autoWorldId} — the folder of the world of {@code slate.autoWorld}: opened when it is there, made
 *       under that name when it is not (without it every run makes a world of its own).</li>
 *   <li>{@code slate.autoLoadingShots} — milliseconds between pictures of what is drawn while no screen of the queue
 *       shows: the loading screens on the way into the world and out of it ({@code loading-NNN-...png}).</li>
 *   <li>{@code slate.autoSkin} — {@code DARK} or {@code VANILLA}, applied in memory (not saved).</li>
 *   <li>{@code slate.autoLayout} — {@code VANILLA}, {@code CUSTOM} or {@code OVERHAUL}, applied in memory (not saved).</li>
 *   <li>{@code slate.autoMotion} — the animation speed (0 = everything snaps to its end state), in memory.</li>
 *   <li>{@code slate.autoIntro} — {@code true}: intros that play once per session play on every open.</li>
 *   <li>{@code slate.autoDir} — output base; PNGs land in {@code <autoDir>/screenshots/<id>-<skin>.png}.</li>
 *   <li>{@code slate.autoFrames} — frames to wait before each capture (default 45).</li>
 *   <li>{@code slate.autoQuit} — {@code true} stops the game after the last capture.</li>
 * </ul>
 */
public final class DevHarness {

    private enum Phase { MENU, WORLD_LOADING, WORLD, DONE }

    /** One entry of a queue: the screen, how long after opening it is captured, where the pointer sits. */
    private record Shot(String raw, String id, long waitMs, double mouseX, double mouseY) {

        static Shot parse(final String entry) {
            String id = entry.trim();
            double mx = -1, my = -1;
            long wait = 0;
            final int hash = id.indexOf('#');
            if (hash >= 0) {
                final String[] xy = id.substring(hash + 1).split("x");
                id = id.substring(0, hash);
                try {
                    if (xy.length == 2) { mx = Double.parseDouble(xy[0]); my = Double.parseDouble(xy[1]); }
                } catch (final NumberFormatException ignored) {}
            }
            final int at = id.lastIndexOf('@');
            if (at > 0) {
                try { wait = Long.parseLong(id.substring(at + 1)); } catch (final NumberFormatException ignored) {}
                id = id.substring(0, at);
            }
            return new Shot(entry.trim(), id, wait, mx, my);
        }

        boolean hasMouse() { return mouseX >= 0 && mouseY >= 0; }
    }

    private static List<Shot> queue;
    private static List<Shot> worldQueue;
    private static int frames;
    private static int waitFrames = 45;
    private static long openedMs;
    private static boolean started;
    private static boolean overridesApplied;
    private static Phase phase = Phase.MENU;
    private static File dir;
    private static boolean wantWorld;

    /** {@code slate.autoIntro}: screens play their once-per-session intro every time they open, so its frames can be captured. */
    public static boolean replayIntros() { return Boolean.getBoolean("slate.autoIntro"); }

    /**
     * {@code slate.sampleData}: screens that show what the player has (friends, groups, looks) show a made-up set
     * instead, so they can be looked at and photographed without a hub or an account. Never set in production.
     */
    public static boolean sampleData() { return Boolean.getBoolean("slate.sampleData"); }

    /**
     * The layout an unattended run asks for ({@code slate.autoLayout}), or null. The run sets it when its first screen
     * is drawn; what is drawn before that (the first loading overlay) asks here.
     */
    public static @org.jetbrains.annotations.Nullable String autoLayout() {
        final String layout = System.getProperty("slate.autoLayout");
        return layout == null || layout.isBlank() ? null : layout.trim();
    }

    /** The style an unattended run asks for ({@code slate.autoSkin}: {@code VANILLA} or {@code DARK}), or null. */
    public static @org.jetbrains.annotations.Nullable String autoSkin() {
        final String skin = System.getProperty("slate.autoSkin");
        return skin == null || skin.isBlank() ? null : skin.trim();
    }

    /** True while an unattended run is on: screens may skip what only a person would wait for. */
    public static boolean active() { return queue != null && phase != Phase.DONE; }

    static void init() {
        final String screens = System.getProperty("slate.autoScreens");
        wantWorld = Boolean.getBoolean("slate.autoWorld");
        if ((screens == null || screens.isBlank()) && !wantWorld) return;
        queue = new ArrayList<>();
        if (screens != null && !screens.isBlank()) for (final String s : screens.split(",")) queue.add(Shot.parse(s));
        worldQueue = new ArrayList<>();
        for (final String s : System.getProperty("slate.autoWorldScreens", "hud,minecraft:pause,minecraft:chat,slate:hub").split(",")) worldQueue.add(Shot.parse(s));
        waitFrames = Integer.getInteger("slate.autoFrames", 45);
        dir = new File(System.getProperty("slate.autoDir", SlatePlatform.get().gameDir().resolve("slate-shots").toString()));
        // Skin and layout are applied on the first frame, after every module ran its config migrations, so a
        // migration save cannot persist the harness override into the run directory's config.
        // Capture after whatever is drawn last: the screen when one is open, with what Slate lays over it (popups,
        // toasts, tooltips: screenFrame is called after them), else the HUD. The HUD event fires BEFORE a screen
        // renders, so it must not clock screen frames.
        SlateEvents.HUD_RENDER.register((g, pt) -> { if (Minecraft.getInstance().screen == null) onFrame(); });
        Slate.LOGGER.info("[Slate] dev harness: {} menu screen(s){} -> {}", queue.size(), wantWorld ? " + world" : "", dir);
    }

    /** A screen has drawn a frame, and so has everything Slate draws over a screen. Nothing happens outside a run. */
    public static void screenFrame() {
        if (queue != null) onFrame();
    }

    private static final String RELOAD = "reload";
    /** The shot of a loading overlay is taken; what follows waits for the overlay to go. */
    private static boolean afterOverlay;

    private static void onFrame() {
        final Minecraft mc = Minecraft.getInstance();
        if (!overridesApplied) {
            overridesApplied = true;
            final String skin = System.getProperty("slate.autoSkin");
            final String layout = System.getProperty("slate.autoLayout");
            final String motion = System.getProperty("slate.autoMotion");
            if (skin != null) Slate.config().skin = skin;
            if (layout != null) Slate.config().setLayout(dev.fallingcloud.slate.core.screen.slot.Layout.parse(layout, Slate.config().layout()));
            if (motion != null) {
                try { Slate.config().motion = Double.parseDouble(motion); } catch (final NumberFormatException ignored) {}
            }
            if (skin != null || layout != null || motion != null) { Theme.reload(); dev.fallingcloud.slate.core.screen.Reskin.invalidate(); }
        }
        switch (phase) {
            case MENU -> {
                if (mc.getOverlay() != null || mc.screen == null) return;
                if (afterOverlay) {
                    // The shot of the overlay is taken, the overlay is gone: on with what is left.
                    afterOverlay = false;
                    frames = 0;
                    if (queue.isEmpty()) advanceFromMenu();
                    else open(queue.get(0));
                    return;
                }
                if (!started) {
                    started = true;
                    frames = 0;
                    if (queue.isEmpty()) { advanceFromMenu(); return; }
                    open(queue.get(0));
                    return;
                }
                if (RELOAD.equals(queue.get(0).id())) return;   // taken from the overlay (overlayFrame)
                if (!ready(queue.get(0))) return;
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
                if (!ready(worldQueue.get(0))) return;
                frames = 0;
                capture(worldQueue.remove(0));
                if (worldQueue.isEmpty()) finish();
                else openWorldScreen(worldQueue.get(0));
            }
            case DONE -> {}
        }
    }

    /**
     * A loading overlay has drawn a frame. Screens are not drawn under an overlay, so the shot {@code reload} is
     * counted and taken from here.
     */
    public static void overlayFrame() {
        if (queue == null || phase != Phase.MENU || !started || afterOverlay || queue.isEmpty() || !RELOAD.equals(queue.get(0).id())) return;
        if (!ready(queue.get(0))) return;
        frames = 0;
        capture(queue.remove(0));
        afterOverlay = true;
    }

    /** Counts this frame, keeps the pointer where the shot wants it, and says whether the shot may be taken. */
    private static boolean ready(final Shot shot) {
        if (shot.hasMouse()) {
            final Minecraft mc = Minecraft.getInstance();
            final MouseHandlerAccessor mouse = (MouseHandlerAccessor) mc.mouseHandler;
            mouse.slate$setXpos(shot.mouseX() * mc.getWindow().getScreenWidth());
            mouse.slate$setYpos(shot.mouseY() * mc.getWindow().getScreenHeight());
        }
        return ++frames >= waitFrames && System.currentTimeMillis() - openedMs >= shot.waitMs();
    }

    private static void advanceFromMenu() {
        if (!wantWorld) { finish(); return; }
        phase = Phase.WORLD_LOADING;
        frames = 0;
        final Minecraft mc = Minecraft.getInstance();
        try {
            final LevelSettings settings = new LevelSettings("slate-harness", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            final WorldOptions options = new WorldOptions(20260923L, false, false);
            final String id = System.getProperty("slate.autoWorldId");
            if (id != null && !id.isBlank() && mc.getLevelSource().levelExists(id)) {
                mc.createWorldOpenFlows().openWorld(id, DevHarness::finish);
                return;
            }
            mc.createWorldOpenFlows().createFreshLevel(id != null && !id.isBlank() ? id : "slate-harness-" + System.currentTimeMillis() % 100000, settings, options,
                WorldPresets::createNormalWorldDimensions, new TitleScreen());
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

    private static void openWorldScreen(final Shot shot) {
        final Minecraft mc = Minecraft.getInstance();
        openedMs = System.currentTimeMillis();
        try {
            switch (shot.id()) {
                case "hud", "none" -> mc.setScreen(null);
                // The world with the player in it, seen from in front or from behind: what the player looks like.
                case "front" -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); mc.setScreen(null); }
                case "behind" -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); mc.setScreen(null); }
                case "minecraft:pause" -> mc.setScreen(new PauseScreen(true));
                case "minecraft:chat" -> mc.setScreen(new ChatScreen(""));
                case "click" -> {
                    if (mc.screen != null && shot.hasMouse()) {
                        final double x = shot.mouseX() * mc.getWindow().getGuiScaledWidth(), y = shot.mouseY() * mc.getWindow().getGuiScaledHeight();
                        mc.screen.mouseClicked(x, y, 0);
                        if (mc.screen != null) mc.screen.mouseReleased(x, y, 0);
                    }
                }
                default -> {
                    if (shot.id().startsWith("command:") && mc.player != null) mc.player.connection.sendCommand(shot.id().substring(8));
                    else CoreActions.openScreen(shot.id());
                }
            }
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] dev harness could not open {}", shot.id(), e);
        }
    }

    private static void capture(final Shot shot) {
        final Minecraft mc = Minecraft.getInstance();
        final String file = shot.raw().replaceAll("[^A-Za-z0-9_.-]", "_") + "-" + Theme.current().skin().name().toLowerCase() + ".png";
        // What was drawn in batches and not ended yet (the words of a tooltip) is in the frame only after this.
        mc.renderBuffers().bufferSource().endBatch();
        Screenshot.grab(dir, file, mc.getMainRenderTarget(), c -> Slate.LOGGER.info("[Slate] dev harness: {}", c.getString()));
    }

    private static long shotAt;
    private static int shots;

    /**
     * A loading screen has drawn a frame: with {@code slate.autoLoadingShots} a picture of it is taken every so often.
     * {@code what} goes into the file's name. Called by whoever draws loading screens; nothing happens outside a run.
     */
    public static void loadingFrame(final net.minecraft.client.gui.GuiGraphics g, final String what) {
        if (queue == null || dir == null) return;
        final long every = Long.getLong("slate.autoLoadingShots", 0L);
        final long now = System.currentTimeMillis();
        if (every <= 0 || now - shotAt < every || shots >= 120) return;
        shotAt = now;
        g.flush();
        final Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(dir, "loading-%03d-%s-%s.png".formatted(shots++, what.replaceAll("[^A-Za-z0-9_.-]", "_"), Theme.current().skin().name().toLowerCase()),
            mc.getMainRenderTarget(), c -> {});
    }

    private static void finish() {
        phase = Phase.DONE;
        if (Boolean.getBoolean("slate.autoQuit")) {
            final Minecraft mc = Minecraft.getInstance();
            // Give the async PNG write a moment before tearing the game down.
            new Thread(() -> { try { Thread.sleep(2000); } catch (InterruptedException ignored) {} mc.execute(mc::stop); }, "slate-harness-quit").start();
        }
    }

    private static void open(final Shot shot) {
        final Minecraft mc = Minecraft.getInstance();
        openedMs = System.currentTimeMillis();
        try {
            if (RELOAD.equals(shot.id())) mc.reloadResourcePacks();
            else if ("none".equals(shot.id())) mc.setScreen(null);
            else if ("minecraft:title".equals(shot.id())) mc.setScreen(new TitleScreen());
            else CoreActions.openScreen(shot.id());
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] dev harness could not open {}", shot.id(), e);
        }
    }

    private DevHarness() {}
}
