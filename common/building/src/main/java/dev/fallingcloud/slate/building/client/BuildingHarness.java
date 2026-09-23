package dev.fallingcloud.slate.building.client;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.registry.BuildingBlocks;
import dev.fallingcloud.slate.building.registry.BuildingItems;
import dev.fallingcloud.slate.building.registry.BuildingRegistry;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.jetbrains.annotations.Nullable;

/**
 * Dev-only scenario runner for unattended in-game checks. Inactive unless the JVM has
 * {@code -Dslate.building.harness=<a,b,...>} (Gradle: {@code -PbuildingHarness=a,b}; add {@code -PautoSkin=VANILLA}
 * to start in the vanilla skin). It then waits for the title screen, creates a fresh creative FLAT world
 * ("building-harness-NNNNN": peaceful, cheats on, no daylight/weather cycle, no mob spawning), waits until the player
 * is in and the chunks are rendered, runs each named scenario in order, logs {@code [BuildingHarness] done} and quits
 * the game.
 *
 * <p>Owners register scenarios from their client init:
 * <pre>{@code
 * BuildingHarness.register("wheel", s -> s
 *     .command("item replace entity @s weapon.mainhand with minecraft:oak_planks 64")
 *     .wait(20)
 *     .run(() -> WheelOverlay.debugOpen())
 *     .wait(30)
 *     .screenshot("wheel-open"));   // -> <gameDir>/screenshots/building-wheel-open.png
 * }</pre>
 * Steps run on the render thread, one frame apart at least. The frame clock is {@code Minecraft.runTick} HEAD, where
 * the main render target still holds the complete previous frame (world, HUD, screens, toasts), so a screenshot
 * shows exactly what was on screen. {@link Script#command} runs on the integrated server as the player (permission
 * 4, output suppressed, failures logged) and the next step waits until the server has executed it. A failing step is
 * logged and ends only its scenario. Built in: {@code smoke}.
 */
public final class BuildingHarness {

    public static final String PROPERTY = "slate.building.harness";

    private static final Map<String, Consumer<Script>> SCENARIOS = new LinkedHashMap<>();
    private static final int MENU_SETTLE_FRAMES = 10;
    private static final int WORLD_SETTLE_FRAMES = 100;
    private static final int WORLD_MAX_WAIT_FRAMES = 600;

    private enum Phase { OFF, MENU, LOADING, RUNNING, DONE }

    private static Phase phase = Phase.OFF;
    private static int frames;
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static @Nullable Deque<Step> steps;
    private static @Nullable String running;
    private static @Nullable Step current;

    /** Adds (or replaces) a scenario. Safe to call whether or not the harness is active. */
    public static synchronized void register(final String name, final Consumer<Script> builder) {
        SCENARIOS.put(name, builder);
    }

    /** Whether this run is a harness run. */
    public static boolean active() {
        return phase != Phase.OFF;
    }

    /** Reads the system property; called once from {@link BuildingClient#init()}. */
    static void init() {
        register("smoke", BuildingHarness::smoke);
        final String names = System.getProperty(PROPERTY);
        if (names == null || names.isBlank()) return;
        for (final String n : names.split(",")) if (!n.isBlank()) QUEUE.add(n.trim());
        phase = Phase.MENU;
        SlateBuilding.LOGGER.info("[BuildingHarness] scenarios: {}", QUEUE);
    }

    /** Frame clock: {@code Minecraft.runTick} HEAD (mixin). */
    public static void onFrame() {
        if (phase == Phase.OFF || phase == Phase.DONE) return;
        try {
            tick(Minecraft.getInstance());
        } catch (final RuntimeException e) {
            SlateBuilding.LOGGER.error("[BuildingHarness] harness failure", e);
            finish();
        }
    }

    private static void tick(final Minecraft mc) {
        switch (phase) {
            case MENU -> {
                if (mc.getOverlay() != null || mc.screen == null || mc.level != null) { frames = 0; return; }
                if (++frames < MENU_SETTLE_FRAMES) return;
                applySkin(System.getProperty("slate.autoSkin"));
                // Unfocused dev windows would otherwise open the pause menu over every screenshot.
                mc.options.pauseOnLostFocus = false;
                createWorld(mc);
                phase = Phase.LOADING;
                frames = 0;
            }
            case LOADING -> {
                if (mc.level == null || mc.player == null || mc.screen != null || mc.getOverlay() != null) { frames = 0; return; }
                frames++;
                final boolean rendered = mc.levelRenderer.hasRenderedAllSections();
                if (frames < WORLD_SETTLE_FRAMES || (!rendered && frames < WORLD_MAX_WAIT_FRAMES)) return;
                SlateBuilding.LOGGER.info("[BuildingHarness] world ready after {} frames (all sections rendered: {})", frames, rendered);
                phase = Phase.RUNNING;
                frames = 0;
            }
            case RUNNING -> runSteps(mc);
            default -> {}
        }
    }

    private static void runSteps(final Minecraft mc) {
        // Several instant steps may run in one frame; a waiting step ends the frame.
        for (int guard = 0; guard < 64; guard++) {
            if (current == null) {
                if (steps == null || steps.isEmpty()) {
                    if (running != null) SlateBuilding.LOGGER.info("[BuildingHarness] scenario '{}' finished", running);
                    running = null;
                    if (!nextScenario()) { finish(); return; }
                }
                current = steps.poll();
                if (current == null) continue;
            }
            final boolean done;
            try {
                done = current.run(mc);
            } catch (final RuntimeException e) {
                SlateBuilding.LOGGER.error("[BuildingHarness] scenario '{}' failed at {}", running, current, e);
                current = null;
                steps = null;
                continue;
            }
            if (!done) return;
            current = null;
        }
    }

    private static boolean nextScenario() {
        while (!QUEUE.isEmpty()) {
            final String name = QUEUE.poll();
            final Consumer<Script> builder;
            synchronized (BuildingHarness.class) { builder = SCENARIOS.get(name); }
            if (builder == null) {
                SlateBuilding.LOGGER.error("[BuildingHarness] unknown scenario '{}' (known: {})", name, SCENARIOS.keySet());
                continue;
            }
            final Script script = new Script();
            try {
                builder.accept(script);
            } catch (final RuntimeException e) {
                SlateBuilding.LOGGER.error("[BuildingHarness] scenario '{}' could not be built", name, e);
                continue;
            }
            running = name;
            steps = new ArrayDeque<>(script.steps);
            SlateBuilding.LOGGER.info("[BuildingHarness] scenario '{}' ({} steps)", name, script.steps.size());
            return true;
        }
        return false;
    }

    private static void createWorld(final Minecraft mc) {
        final GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        final LevelSettings settings = new LevelSettings("building-harness", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        final WorldOptions options = new WorldOptions(20260924L, false, false);
        final String folder = "building-harness-" + System.currentTimeMillis() % 100000;
        SlateBuilding.LOGGER.info("[BuildingHarness] creating flat world {}", folder);
        mc.createWorldOpenFlows().createFreshLevel(folder, settings, options,
            registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
            new TitleScreen());
    }

    private static void applySkin(final @Nullable String skin) {
        if (skin == null || skin.isBlank()) return;
        Slate.config().skin = skin.trim().toUpperCase(java.util.Locale.ROOT);
        Theme.reload();
    }

    private static void finish() {
        phase = Phase.DONE;
        SlateBuilding.LOGGER.info("[BuildingHarness] done");
        final Minecraft mc = Minecraft.getInstance();
        // Give the asynchronous PNG writes a moment before the game shuts down.
        final Thread quit = new Thread(() -> {
            try { Thread.sleep(2000); } catch (final InterruptedException ignored) { Thread.currentThread().interrupt(); }
            mc.execute(mc::stop);
        }, "slate-building-harness-quit");
        quit.setDaemon(true);
        quit.start();
    }

    /** The built-in scenario: a few vanilla blocks and our items, a registry report, one HUD screenshot. */
    private static void smoke(final Script s) {
        s.command("time set noon")
            .command("weather clear")
            .command("tp @s 0 -60 0 0 20")
            .command("fill -3 -60 4 3 -58 4 minecraft:stone_bricks")
            .command("fill -3 -60 5 3 -60 7 minecraft:oak_planks")
            .command("setblock -2 -59 5 minecraft:oak_stairs")
            .command("setblock 2 -59 5 minecraft:glass")
            .command("setblock 0 -59 5 slate_building:stairs")
            .command("item replace entity @s hotbar.0 with minecraft:oak_planks 64")
            .command("item replace entity @s hotbar.1 with slate_building:toolbox")
            .command("item replace entity @s hotbar.2 with slate_building:iron_hammer")
            .command("item replace entity @s hotbar.3 with slate_building:vertical_slab")
            .run(() -> {
                SlateBuilding.LOGGER.info("[BuildingHarness] registry: {} entries, {} shape blocks, {} items, all bound: {}",
                    BuildingRegistry.entries().size(), BuildingBlocks.all().size(), BuildingItems.all().size(),
                    BuildingItems.all().stream().allMatch(r -> r.isBound()) && BuildingBlocks.all().stream().allMatch(r -> r.isBound()));
                final Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.getInventory().selected = 0;
            })
            .wait(40)
            .screenshot("smoke");
    }

    /** A step: returns true when finished (the next step may run), false to be called again next frame. */
    @FunctionalInterface
    private interface Step {
        boolean run(Minecraft mc);
    }

    /** The steps of one scenario, built fluently by the scenario's builder. */
    public static final class Script {

        private final List<Step> steps = new ArrayList<>();

        /** Waits {@code frames} rendered frames. */
        public Script wait(final int frames) {
            final int[] left = {Math.max(0, frames)};
            steps.add(new Step() {
                @Override public boolean run(final Minecraft mc) { return left[0]-- <= 0; }
                @Override public String toString() { return "wait(" + frames + ")"; }
            });
            return this;
        }

        /** Waits until {@code condition} holds, at most {@code maxFrames} (then logs a warning and continues). */
        public Script waitUntil(final BooleanSupplier condition, final int maxFrames) {
            final int[] left = {Math.max(1, maxFrames)};
            steps.add(new Step() {
                @Override public boolean run(final Minecraft mc) {
                    if (condition.getAsBoolean()) return true;
                    if (--left[0] > 0) return false;
                    SlateBuilding.LOGGER.warn("[BuildingHarness] waitUntil timed out after {} frames", maxFrames);
                    return true;
                }
                @Override public String toString() { return "waitUntil(" + maxFrames + ")"; }
            });
            return this;
        }

        /** Runs {@code action} on the render thread. */
        public Script run(final Runnable action) {
            steps.add(new Step() {
                @Override public boolean run(final Minecraft mc) { action.run(); return true; }
                @Override public String toString() { return "run"; }
            });
            return this;
        }

        /** Runs a command (without the slash) on the integrated server as the player, then waits for it to finish. */
        public Script command(final String command) {
            final String cmd = command.startsWith("/") ? command.substring(1) : command;
            steps.add(new Step() {
                private @Nullable CompletableFuture<Void> pending;

                @Override public boolean run(final Minecraft mc) {
                    if (pending == null) {
                        final IntegratedServer server = mc.getSingleplayerServer();
                        if (server == null || mc.player == null) throw new IllegalStateException("no integrated server / player");
                        final UUID id = mc.player.getUUID();
                        pending = server.submit(() -> {
                            final ServerPlayer player = server.getPlayerList().getPlayer(id);
                            if (player == null) { SlateBuilding.LOGGER.warn("[BuildingHarness] no server player for /{}", cmd); return; }
                            final CommandSourceStack source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput()
                                .withCallback((success, result) -> {
                                    if (!success) SlateBuilding.LOGGER.warn("[BuildingHarness] command failed: /{}", cmd);
                                });
                            server.getCommands().performPrefixedCommand(source, cmd);
                        });
                        return false;
                    }
                    return pending.isDone();
                }

                @Override public String toString() { return "command(/" + cmd + ")"; }
            });
            return this;
        }

        /** Saves the current frame to {@code <gameDir>/screenshots/building-<name>.png}. */
        public Script screenshot(final String name) {
            final String file = "building-" + name.replaceAll("[^A-Za-z0-9_.-]", "_") + ".png";
            steps.add(new Step() {
                @Override public boolean run(final Minecraft mc) {
                    Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(),
                        msg -> SlateBuilding.LOGGER.info("[BuildingHarness] screenshot {}: {}", file, msg.getString()));
                    return true;
                }
                @Override public String toString() { return "screenshot(" + file + ")"; }
            });
            return this;
        }

        /** Switches the Slate skin in memory ({@code DARK} / {@code VANILLA}), for both-skin screenshots. */
        public Script skin(final String skin) {
            return run(() -> applySkin(skin));
        }

        /** Writes {@code message} to the log (marks points of interest in long scenarios). */
        public Script log(final String message) {
            return run(() -> SlateBuilding.LOGGER.info("[BuildingHarness] {}", message));
        }
    }

    private BuildingHarness() {}
}
