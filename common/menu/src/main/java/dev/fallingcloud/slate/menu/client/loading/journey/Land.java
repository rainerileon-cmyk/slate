package dev.fallingcloud.slate.menu.client.loading.journey;

import dev.fallingcloud.slate.core.stage.node.TerrainNode;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.overhaul.create.LandSampler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.jetbrains.annotations.Nullable;

/**
 * The land around a place of a world, as the cells of a model: what the loading screen of the Overhaul layout shows
 * of the world that is being opened. A cell stands for {@link #STEP} blocks each way; the cells lie in tiles of
 * {@link #TILE} by {@link #TILE}, which is what the model rises by.
 *
 * <p>The cells come from two places. What the player saw of the world when leaving it ({@link LandSnapshot}) is laid
 * in first: the land as it is, with what was built on it. What that does not cover is asked of the world's
 * generator ({@link #ask}), by workers off the render thread, from the middle outwards: the land as it was made.
 * The generator is asked what it asks itself before it builds a chunk, which lies a few blocks off what it builds
 * in the end; so where both are known the two are compared first, and what the generator says is raised or lowered
 * by what they differ: the land that was seen and the land around it meet without a step.</p>
 */
final class Land {

    static final int WIDTH = 160, DEPTH = 128, STEP = 4, TILE = 8;
    static final int TILES_X = WIDTH / TILE, TILES_Z = DEPTH / TILE;
    /**
     * Cells the land is let into the ground it stands on. A model on a table shows a deep cut all round; this land
     * comes up out of a plain, and what shows of its cut while it rises is as high as its hills, not as its bedrock.
     */
    static final int SUNK = 11;
    /** Cells below the surface from which the cut through natural ground shows stone. */
    static final int NATURAL = 3;
    /** The same for what was built: it is of one material all the way down. */
    static final int BUILT = 9999;

    /**
     * What stands on one cell. Textures are names of block textures ({@code grass_block_top}), or whole sprite ids
     * where a colon says so ({@code create:block/andesite_casing}).
     *
     * @param ground cells of ground, 1 at least
     * @param strata how many cells under the surface wear {@code under}; below them lies stone, then the deep
     * @param water  cells of water standing on the ground, 0 for dry land
     */
    record Cell(int ground, String top, int topTint, String under, int underTint, int strata, int water, int waterTint, boolean frozen,
                @Nullable String trunk, @Nullable String canopy, int canopyTint) {

        static Cell of(final TerrainNode.Column c) {
            final int ground = sunk(c.ground()), level = sunk(c.ground() + Math.max(0, c.water()));
            return new Cell(ground, c.top(), c.topTint(), c.under(), 0xFFFFFFFF, NATURAL, c.water() > 0 ? Math.max(1, level - ground) : 0, c.waterTint(),
                c.frozen(), c.trunk(), c.canopy(), c.canopyTint());
        }

        boolean tree() { return trunk != null && canopy != null && water <= 0; }

        /** The same cell, its ground so many cells higher (lower when negative), never under the sea it stands by. */
        Cell raised(final int by) {
            return new Cell(Math.max(1, ground + by), top, topTint, under, underTint, strata, water, waterTint, frozen, trunk, canopy, canopyTint);
        }

        /** The same cell with a tree standing on it. */
        Cell withTree(final String bark, final String leaves, final int leafTint) {
            return new Cell(ground, top, topTint, under, underTint, strata, water, waterTint, frozen, bark, leaves, leafTint);
        }

        /** How high the cell stands, water counted. */
        int level() { return ground + water; }
    }

    /** A height in cells of a model on a table, as a height of this land. */
    static int sunk(final int cells) {
        return Math.max(1, cells - SUNK);
    }

    private static final AtomicInteger THREADS = new AtomicInteger();
    private static final int WORKERS = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 3));
    private static final ExecutorService POOL = Executors.newFixedThreadPool(WORKERS, r -> {
        final Thread t = new Thread(r, "slate-land-" + THREADS.incrementAndGet());
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** Heights are asked for at every {@code GRAIN}th cell where asking is dear; the cells between are laid between them. */
    private static final int GRAIN = 2;
    private static final int BLOCKS_X = WIDTH / GRAIN, BLOCKS_Z = DEPTH / GRAIN;
    private static final int[] ORDER = order();
    private static final int UNKNOWN = Integer.MIN_VALUE;

    private final AtomicReferenceArray<Cell> cells = new AtomicReferenceArray<>(WIDTH * DEPTH);
    /** Per tile: how many of its cells have been looked at (they may turn out to hold nothing). */
    private final AtomicIntegerArray looked = new AtomicIntegerArray(TILES_X * TILES_Z);
    private final AtomicInteger filled = new AtomicInteger();
    private volatile boolean closed, asking, dropped;
    /** Cells the generator's ground lies under what was seen (over it when negative); worked out once, before the asking. */
    private int lift;
    private boolean compared;
    /** The block the middle of the land lies on. */
    final int centerX, centerZ;
    /** The world's time of day when the land was last seen, 0..23999. */
    volatile int dayTime;
    /** Cells of height the sea stands at. */
    volatile int sea = sunk(Math.round(LandSampler.DEEP / (float) LandSampler.RISE));

    Land(final int centerX, final int centerZ) {
        // On the grid of the cells, so the same place always falls into the same cell.
        this.centerX = Math.floorDiv(centerX, STEP) * STEP;
        this.centerZ = Math.floorDiv(centerZ, STEP) * STEP;
    }

    private static int[] order() {
        final List<Integer> blocks = new ArrayList<>(BLOCKS_X * BLOCKS_Z);
        for (int i = 0; i < BLOCKS_X * BLOCKS_Z; i++) blocks.add(i);
        blocks.sort(Comparator.comparingDouble(i -> {
            final double x = i % BLOCKS_X - BLOCKS_X / 2.0 + 0.5, z = i / BLOCKS_X - BLOCKS_Z / 2.0 + 0.5;
            return x * x + z * z;
        }));
        final int[] out = new int[blocks.size()];
        for (int i = 0; i < out.length; i++) out[i] = blocks.get(i);
        return out;
    }

    /** The block a cell's middle lies on. */
    int blockX(final int x) { return centerX + (x - WIDTH / 2) * STEP + STEP / 2; }

    int blockZ(final int z) { return centerZ + (z - DEPTH / 2) * STEP + STEP / 2; }

    @Nullable
    Cell at(final int x, final int z) {
        return x < 0 || z < 0 || x >= WIDTH || z >= DEPTH ? null : cells.get(z * WIDTH + x);
    }

    /** Lays a cell that has none yet; what is there stays (what was seen goes before what is worked out). Any thread. */
    void lay(final int x, final int z, @Nullable final Cell cell) {
        if (x < 0 || z < 0 || x >= WIDTH || z >= DEPTH) return;
        final int i = z * WIDTH + x;
        if (cell != null && cells.compareAndSet(i, null, cell)) filled.incrementAndGet();
    }

    /** Says a cell has been looked at, whatever was found. Any thread. */
    private void seen(final int x, final int z) {
        looked.incrementAndGet(z / TILE * TILES_X + x / TILE);
    }

    /** Whether a tile is as it will stay: every cell looked at, or nobody left to look. */
    boolean ready(final int tx, final int tz) {
        return closed || looked.get(tz * TILES_X + tx) >= TILE * TILE;
    }

    /** Whether any of the land is there. */
    boolean any() { return filled.get() > 0; }

    /** Nobody will lay cells any more: the tiles are taken as they are. */
    void close() { closed = true; }

    boolean closed() { return closed; }

    /** The land is not shown any more: the workers stop. */
    void drop() { dropped = true; }

    /**
     * Has the workers ask a generator for every cell that has none. Once: a land is asked of one generator.
     */
    void ask(final LandSampler sampler) {
        if (asking || closed) return;
        asking = true;
        sea = sunk(sampler.cells(sampler.sea()));
        final AtomicInteger next = new AtomicInteger();
        final AtomicInteger running = new AtomicInteger(WORKERS);
        final AtomicIntegerArray heights = new AtomicIntegerArray((BLOCKS_X + 1) * (BLOCKS_Z + 1));
        for (int i = 0; i < heights.length(); i++) heights.set(i, UNKNOWN);
        for (int w = 0; w < WORKERS; w++) {
            POOL.execute(() -> {
                try {
                    work(sampler, next, heights);
                } catch (final Throwable t) {
                    // A generator of some mod that cannot be asked from outside: what is there is shown.
                    SlateMenu.LOGGER.warn("[Slate Menu] the land of the world could not be worked out: {}", t.toString());
                    closed = true;
                } finally {
                    if (running.decrementAndGet() == 0) closed = true;
                }
            });
        }
    }

    private int height(final LandSampler sampler, final AtomicIntegerArray heights, final int gx, final int gz) {
        final int i = gz * (BLOCKS_X + 1) + gx;
        int h = heights.get(i);
        if (h == UNKNOWN) {
            h = sampler.floor(centerX + (gx * GRAIN - WIDTH / 2) * STEP, centerZ + (gz * GRAIN - DEPTH / 2) * STEP);
            heights.set(i, h);
        }
        return h;
    }

    /**
     * How far the generator's ground lies from the ground that was seen, in cells: the middle one of what a spread of
     * places say. Only dry, natural ground is asked about: a house is not where the generator thinks the hill ends.
     */
    private int compare(final LandSampler sampler) {
        final List<Integer> off = new ArrayList<>();
        for (int z = 3; z < DEPTH && off.size() < 120; z += 7) {
            for (int x = 3; x < WIDTH && off.size() < 120; x += 7) {
                final Cell seen = cells.get(z * WIDTH + x);
                if (seen == null || seen.water() > 0 || seen.strata() != NATURAL) continue;
                final int wx = blockX(x), wz = blockZ(z);
                final Cell said = Cell.of(sampler.column(wx, wz, sampler.floor(wx, wz)));
                if (said.water() > 0) continue;
                off.add(seen.ground() - said.ground());
            }
        }
        if (off.size() < 8) return 0;
        off.sort(Comparator.naturalOrder());
        return Math.max(-8, Math.min(8, off.get(off.size() / 2)));
    }

    private void work(final LandSampler sampler, final AtomicInteger next, final AtomicIntegerArray heights) {
        final boolean quick = sampler.quick();
        final int raise;
        synchronized (this) {
            if (!compared) {
                lift = filled.get() > 0 ? compare(sampler) : 0;
                compared = true;
            }
            raise = lift;
        }
        while (!dropped) {
            final int n = next.getAndIncrement();
            if (n >= ORDER.length) return;
            final int block = ORDER[n];
            final int bx = block % BLOCKS_X, bz = block / BLOCKS_X;
            boolean missing = false;
            for (int dz = 0; dz < GRAIN && !missing; dz++) for (int dx = 0; dx < GRAIN; dx++) {
                if (cells.get((bz * GRAIN + dz) * WIDTH + bx * GRAIN + dx) == null) { missing = true; break; }
            }
            if (missing) {
                final int h00 = quick ? 0 : height(sampler, heights, bx, bz), h10 = quick ? 0 : height(sampler, heights, bx + 1, bz);
                final int h01 = quick ? 0 : height(sampler, heights, bx, bz + 1), h11 = quick ? 0 : height(sampler, heights, bx + 1, bz + 1);
                for (int dz = 0; dz < GRAIN; dz++) {
                    for (int dx = 0; dx < GRAIN; dx++) {
                        final int cx = bx * GRAIN + dx, cz = bz * GRAIN + dz;
                        if (cells.get(cz * WIDTH + cx) != null) continue;
                        final int wx = blockX(cx), wz = blockZ(cz);
                        final float u = (dx + 0.5f) / GRAIN, v = (dz + 0.5f) / GRAIN;
                        final float floor = quick ? sampler.floor(wx, wz) : (h00 * (1f - u) + h10 * u) * (1f - v) + (h01 * (1f - u) + h11 * u) * v;
                        final Cell cell = Cell.of(sampler.column(wx, wz, Math.round(floor)));
                        // Dry land moves with what was seen; water keeps its level, and so does the ground under it.
                        lay(cx, cz, raise == 0 || cell.water() > 0 ? cell : cell.raised(raise));
                    }
                }
            }
            // Counted whether the cells were worked out now or lay there already.
            for (int dz = 0; dz < GRAIN; dz++) for (int dx = 0; dx < GRAIN; dx++) seen(bx * GRAIN + dx, bz * GRAIN + dz);
        }
    }
}
