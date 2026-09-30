package dev.fallingcloud.slate.earlywindow;

import dev.fallingcloud.slate.earlywindow.scene.LoadingScene;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

/**
 * What FML knows about the loading, as the Overhaul scene asks for it. FML has progress meters, one inside the other:
 * the last one is the task at hand, and a meter that is gone from the list is a task that is done.
 *
 * <p>How far <em>all</em> of the loading is, FML does not know. It is put together from two halves: before the game's
 * own loading starts (NeoForge's "Minecraft Progress" meter) only time can be counted, against how long that half took
 * the last time; after that the meter says it. How long each half took is kept in
 * {@code config/slate/earlywindow-times.properties}, so the second launch of a pack shows it right.</p>
 */
final class FmlFeed implements LoadingScene.Feed {

    private static final String GAME = "Minecraft Progress";
    private static final String TIMES = "earlywindow-times.properties";
    /** A meter that is the only one and this long is shown a tenth at a time, so the belt has something to carry. */
    private static final int LONG = 20, PARTS = 10;

    private final long start = System.nanoTime();
    /** How long the two halves took the last time, milliseconds. */
    private long expectedEarly = 9_000, expectedGame = 21_000;
    private long gameStart = -1, gameEnd = -1;
    private boolean written;

    private List<ProgressMeter> before = List.of();
    private long done;
    private int part = -1;
    private ProgressMeter parted;

    // One frame's answers.
    private float overall, taskProgress = -1f;
    private String task = "Loading", count, detail;
    private long used, max;

    FmlFeed() {
        try {
            final Path file = FMLPaths.CONFIGDIR.get().resolve("slate").resolve(TIMES);
            if (Files.isRegularFile(file)) {
                final Properties p = new Properties();
                try (var in = Files.newBufferedReader(file)) {
                    p.load(in);
                }
                final long early = Long.parseLong(p.getProperty("early", "0").trim()), game = Long.parseLong(p.getProperty("game", "0").trim());
                if (early >= 500 && early <= 600_000 && game >= 500 && game <= 1_800_000) {
                    expectedEarly = early;
                    expectedGame = game;
                }
            }
        } catch (final Exception e) {
            // The first launch, or a file somebody wrote into: the guesses stay.
        }
    }

    /** Reads what FML has now; the scene's questions of this frame are answered from it. */
    void frame() {
        final List<ProgressMeter> meters = new ArrayList<>(StartupNotificationManager.getCurrentProgress());
        // Every meter that is gone is a task that is done.
        for (final ProgressMeter m : before) {
            boolean still = false;
            for (final ProgressMeter n : meters) if (n == m) still = true;
            if (!still) done++;
        }
        before = meters;

        final ProgressMeter current = meters.isEmpty() ? null : meters.get(meters.size() - 1);
        ProgressMeter game = null;
        for (final ProgressMeter m : meters) if (GAME.equals(m.name())) game = m;

        final String log = latestLog();
        if (current == null) {
            task = log != null ? log : "Loading";
            detail = null;
            count = null;
            taskProgress = -1f;
        } else {
            task = label(current);
            detail = log != null && !log.equals(task) ? log : null;
            final int steps = current.steps();
            if (steps <= 0) {
                count = null;
                taskProgress = -1f;
            } else if (meters.size() == 1 && steps >= LONG) {
                final float at = clamp(current.progress()) * PARTS;
                final int now = Math.min(PARTS - 1, (int) at);
                if (parted == current && now > part) done += now - part;
                parted = current;
                part = now;
                taskProgress = Math.min(1f, at - now);
                count = Math.round(clamp(current.progress()) * 100f) + "%";
            } else {
                taskProgress = clamp(current.progress());
                count = current.current() + " / " + steps;
            }
        }

        final long now = System.nanoTime();
        if (game != null && gameStart < 0) gameStart = now;
        final float share = Math.max(0.1f, Math.min(0.7f, expectedEarly / (float) (expectedEarly + expectedGame)));
        float all;
        if (gameStart < 0) {
            final float t = (now - start) / 1e6f / expectedEarly;
            // Up to where the last launch was at this time; past that, ever slower towards the end of this half.
            all = share * (t < 0.8f ? t : 0.8f + 0.18f * (1f - (float) Math.exp(-(t - 0.8f) * 2f)));
        } else {
            final float p = game != null ? clamp(game.progress()) : 1f;
            all = share + (1f - share) * p;
            if (p >= 0.999f && gameEnd < 0) gameEnd = now;
        }
        overall = Math.max(overall, Math.min(1f, all));

        final Runtime rt = Runtime.getRuntime();
        max = rt.maxMemory();
        used = rt.totalMemory() - rt.freeMemory();
    }

    /** Keeps how long the halves took, for the next launch. Once, when the loading is over. */
    void finished() {
        if (written || gameStart < 0) return;
        written = true;
        final long end = gameEnd > 0 ? gameEnd : System.nanoTime();
        final long early = (gameStart - start) / 1_000_000L, game = (end - gameStart) / 1_000_000L;
        if (early < 500 || game < 500) return;
        try {
            final Path dir = FMLPaths.CONFIGDIR.get().resolve("slate");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(TIMES), "# How long the last launch took, in milliseconds: before the game's own loading, and the game's own loading.\n"
                + "# Written by Slate's start-up window so that its next launch knows how far it is.\n"
                + "early=" + early + "\ngame=" + game + "\n");
        } catch (final Exception e) {
            SlateLook.LOGGER.debug("[Slate] could not keep the launch times: {}", e.toString());
        }
    }

    @Override public float overall() { return overall; }

    @Override public String task() { return task; }

    @Override public float taskProgress() { return taskProgress; }

    @Override public String taskCount() { return count; }

    @Override public long tasksDone() { return done; }

    @Override public String detail() { return detail; }

    @Override public long memoryUsed() { return used; }

    @Override public long memoryMax() { return max; }

    private static float clamp(final float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static String label(final ProgressMeter m) {
        final String text = m.label() != null ? m.label().getText() : null;
        return text != null && !text.isBlank() ? text : m.name();
    }

    /** The newest line of FML's start-up log, or null. */
    private static String latestLog() {
        try {
            return StartupNotificationManager.getMessages().stream()
                .min(Comparator.comparingInt(StartupNotificationManager.AgeMessage::age))
                .map(a -> a.message().getText())
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
        } catch (final RuntimeException e) {
            return null;
        }
    }
}
