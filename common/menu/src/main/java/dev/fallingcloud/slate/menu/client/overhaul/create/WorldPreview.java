package dev.fallingcloud.slate.menu.client.overhaul.create;

import dev.fallingcloud.slate.core.stage.node.TerrainNode;
import dev.fallingcloud.slate.menu.SlateMenu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;

/**
 * Works out what the world about to be created looks like around its origin, without creating it: the height of
 * the ground and the biome of every few blocks, asked of the very generator the world will be made with, for the
 * very seed it will get. The answers become the columns of a {@link TerrainNode}: the preview on the world creation
 * screen.
 *
 * <p>There are thousands of columns. Vanilla's own generator is asked what it asks itself before it builds a
 * chunk, how high the land lies before caves and crags are cut into it, which is cheap enough to ask for every
 * column. Any other generator is asked for the finished height, which can take a millisecond or two: that is done
 * for every second column only, and the ones between are laid between their neighbours. Workers do the asking off
 * the render thread, from the middle outwards: the land shows at once and grows to its rim. A change of
 * seed or world type starts over. While no screen is showing the preview the workers rest, and go on where they
 * were when one shows it again: coming back from another screen (the game rules, the data packs) finds the land
 * as it was left.</p>
 */
final class WorldPreview {

    enum State { IDLE, WORKING, DONE, FAILED }

    /** Cells of the model, blocks a cell, and blocks of height a cell (the land is drawn twice as steep as it is). */
    static final int WIDTH = 192, DEPTH = 96, STEP = 4, RISE = LandSampler.RISE;

    private static final AtomicInteger THREADS = new AtomicInteger();
    private static final int WORKERS = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() - 2));
    private static final ExecutorService POOL = Executors.newFixedThreadPool(WORKERS, r -> {
        final Thread t = new Thread(r, "slate-world-preview-" + THREADS.incrementAndGet());
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** Heights are asked for at every {@code GRAIN}th cell; the cells between are laid between them. */
    private static final int GRAIN = 2;
    private static final int BLOCKS_X = WIDTH / GRAIN, BLOCKS_Z = DEPTH / GRAIN;
    /** The blocks of cells in the order they are worked out: nearest the middle first. */
    private static final int[] ORDER = order();
    private static final int UNKNOWN = Integer.MIN_VALUE;

    private final TerrainNode.Column[] columns = new TerrainNode.Column[WIDTH * DEPTH];
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicInteger done = new AtomicInteger();
    private final AtomicInteger running = new AtomicInteger();
    private volatile State state = State.IDLE;
    @Nullable private volatile TerrainNode sink;
    @Nullable private volatile Run current;
    @Nullable private Object key;
    private long seed;

    /** One working out: the generator asked, the heights it gave so far, how far the workers are. */
    private static final class Run {
        final int generation;
        final LandSampler sampler;
        final AtomicInteger next = new AtomicInteger();
        final java.util.concurrent.atomic.AtomicIntegerArray heights = new java.util.concurrent.atomic.AtomicIntegerArray((BLOCKS_X + 1) * (BLOCKS_Z + 1));

        Run(final int generation, final LandSampler sampler) {
            this.generation = generation;
            this.sampler = sampler;
            for (int i = 0; i < heights.length(); i++) heights.set(i, UNKNOWN);
        }

        /** The height of the ground at a corner of the blocks, asked for once. */
        int height(final int gx, final int gz) {
            final int i = gz * (BLOCKS_X + 1) + gx;
            int h = heights.get(i);
            if (h == UNKNOWN) {
                h = sampler.floor((gx * GRAIN - WIDTH / 2) * STEP, (gz * GRAIN - DEPTH / 2) * STEP);
                heights.set(i, h);
            }
            return h;
        }
    }

    private static int[] order() {
        final List<Integer> blocks = new ArrayList<>(BLOCKS_X * BLOCKS_Z);
        for (int i = 0; i < BLOCKS_X * BLOCKS_Z; i++) blocks.add(i);
        blocks.sort(Comparator.comparingDouble(i -> {
            final double x = i % BLOCKS_X - BLOCKS_X / 2.0 + 0.5, z = i / BLOCKS_X - BLOCKS_Z / 2.0 + 0.5;
            return x * x + z * z * 1.6;
        }));
        final int[] out = new int[blocks.size()];
        for (int i = 0; i < out.length; i++) out[i] = blocks.get(i);
        return out;
    }

    State state() { return state; }

    float progress() { return done.get() / (float) (WIDTH * DEPTH); }

    long seed() { return seed; }

    /** Blocks the model spans, east to west and north to south. */
    static int blocksWide() { return WIDTH * STEP; }

    static int blocksDeep() { return DEPTH * STEP; }

    /** The model the columns go to. It gets those worked out so far at once, and the rest as they come. */
    void show(@Nullable final TerrainNode node) {
        sink = node;
        if (node == null) return;
        synchronized (columns) {
            for (int i = 0; i < columns.length; i++) if (columns[i] != null) node.put(i % WIDTH, i / WIDTH, columns[i]);
        }
        // The workers rested while nobody was looking: they go on where they were.
        final Run run = current;
        if (run != null && state == State.WORKING) start(run);
    }

    private void start(final Run run) {
        // As many as are missing: some may still be on their way out.
        for (int w = running.get(); w < WORKERS; w++) {
            running.incrementAndGet();
            POOL.execute(() -> {
                try {
                    work(run);
                } finally {
                    running.decrementAndGet();
                }
            });
        }
    }

    /**
     * Brings the preview up to the world {@code context} would create. Nothing happens when that is the world already
     * shown (or being worked out).
     */
    void update(final WorldCreationContext context) {
        final ChunkGenerator generator;
        final long newSeed = context.options().seed();
        try {
            generator = context.selectedDimensions().overworld();
        } catch (final Exception e) {
            fail(e);
            return;
        }
        final boolean debug = context.selectedDimensions().isDebug();
        final Object newKey = List.of(System.identityHashCode(generator), newSeed, debug);
        if (newKey.equals(key)) return;
        key = newKey;
        seed = newSeed;
        final int run = generation.incrementAndGet();
        synchronized (columns) {
            java.util.Arrays.fill(columns, null);
        }
        done.set(0);
        final TerrainNode node = sink;
        if (node != null) node.clear();
        if (debug) {
            // The debug world is a grid of every block state: there is no land to show.
            state = State.FAILED;
            return;
        }
        state = State.WORKING;
        final LandSampler sampler;
        try {
            sampler = LandSampler.of(context, generator, newSeed);
        } catch (final Exception e) {
            fail(e);
            return;
        }
        final Run started = new Run(run, sampler);
        current = started;
        start(started);
    }

    private void fail(final Exception e) {
        SlateMenu.LOGGER.warn("[Slate Menu] no preview for this world type: {}", e.toString());
        generation.incrementAndGet();
        state = State.FAILED;
    }

    /** Stops whatever is being worked out. The columns there are stay. */
    void cancel() {
        if (state == State.WORKING) {
            generation.incrementAndGet();
            key = null;
            state = State.IDLE;
        }
    }

    private void work(final Run run) {
        try {
            while (generation.get() == run.generation) {
                // Nobody is looking: rest. Whoever shows the preview again sets the workers going.
                if (sink == null) return;
                final int n = run.next.getAndIncrement();
                if (n >= ORDER.length) return;
                final int block = ORDER[n];
                final int bx = block % BLOCKS_X, bz = block / BLOCKS_X;
                final boolean quick = run.sampler.quick();
                final int h00 = quick ? 0 : run.height(bx, bz), h10 = quick ? 0 : run.height(bx + 1, bz);
                final int h01 = quick ? 0 : run.height(bx, bz + 1), h11 = quick ? 0 : run.height(bx + 1, bz + 1);
                for (int dz = 0; dz < GRAIN; dz++) {
                    for (int dx = 0; dx < GRAIN; dx++) {
                        final int cx = bx * GRAIN + dx, cz = bz * GRAIN + dz;
                        final int wx = (cx - WIDTH / 2) * STEP + STEP / 2, wz = (cz - DEPTH / 2) * STEP + STEP / 2;
                        final float u = (dx + 0.5f) / GRAIN, v = (dz + 0.5f) / GRAIN;
                        final float floor = quick ? run.sampler.floor(wx, wz)
                            : (h00 * (1f - u) + h10 * u) * (1f - v) + (h01 * (1f - u) + h11 * u) * v;
                        final TerrainNode.Column column = run.sampler.column(wx, wz, Math.round(floor));
                        synchronized (columns) {
                            if (generation.get() != run.generation) return;
                            columns[cz * WIDTH + cx] = column;
                        }
                        final TerrainNode node = sink;
                        if (node != null) node.put(cx, cz, column);
                    }
                }
                if (done.addAndGet(GRAIN * GRAIN) >= WIDTH * DEPTH) state = State.DONE;
            }
        } catch (final Throwable t) {
            // A generator of some mod that cannot be asked outside a world: the screen says so, and goes on.
            if (generation.compareAndSet(run.generation, run.generation + 1)) {
                SlateMenu.LOGGER.warn("[Slate Menu] the world preview stopped: {}", t.toString());
                state = State.FAILED;
            }
        }
    }
}
