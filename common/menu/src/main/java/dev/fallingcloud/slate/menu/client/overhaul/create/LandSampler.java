package dev.fallingcloud.slate.menu.client.overhaul.create;

import dev.fallingcloud.slate.core.stage.node.TerrainNode;
import java.util.Locale;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;

/**
 * Asks one generator what the land is like at a place, for one seed: how high the ground stands, what it wears, what
 * grows on it, what water lies on it. The answer is a column of a model of the land ({@link TerrainNode.Column}): the
 * world creation screen shows the world about to be made with it, the loading screen the world being opened. Used by
 * several workers at once: it keeps nothing between questions.
 */
public final class LandSampler {

    /** Blocks of height a cell of the model stands for (the land is drawn twice as steep as it is). */
    public static final int RISE = 2;
    /** How far under the sea the model's table lies, in blocks. */
    public static final int DEEP = 39;

    private static final int WHITE = 0xFFFFFFFF;
    private static final String[] BADLANDS = {"terracotta", "orange_terracotta", "yellow_terracotta", "terracotta", "red_terracotta", "white_terracotta", "orange_terracotta"};

    /** The density of the land above which the generator takes a place for solid when it looks for the surface. */
    private static final double SOLID = 0.390625;

    private final ChunkGenerator generator;
    private final RandomState random;
    private final LevelHeightAccessor heights;
    private final int sea, table;
    private final long seed;
    /** Vanilla's generator: the land's density before caves and crags, and the span it is defined over. */
    @Nullable private final DensityFunction density;
    private final int bottom, top, cell;

    /** For a world that is not there yet: the generator it would be made with, asked as the world would ask it. */
    public static LandSampler of(final WorldCreationContext context, final ChunkGenerator generator, final long seed) {
        final var noises = context.worldgenLoadContext().lookupOrThrow(Registries.NOISE);
        final RandomState random = generator instanceof NoiseBasedChunkGenerator noise
            ? RandomState.create(noise.generatorSettings().value(), noises, seed)
            : RandomState.create(NoiseGeneratorSettings.dummy(), noises, seed);
        final var stem = context.selectedDimensions().get(LevelStem.OVERWORLD);
        final int minY = stem.map(s -> s.type().value().minY()).orElse(-64);
        final int height = stem.map(s -> s.type().value().height()).orElse(384);
        return new LandSampler(generator, random, LevelHeightAccessor.create(minY, height), seed);
    }

    /** For a world that is running: its own generator, and what that generator works with. */
    public static LandSampler of(final ChunkGenerator generator, final RandomState random, final LevelHeightAccessor heights, final long seed) {
        return new LandSampler(generator, random, LevelHeightAccessor.create(heights.getMinBuildHeight(), heights.getHeight()), seed);
    }

    private LandSampler(final ChunkGenerator generator, final RandomState random, final LevelHeightAccessor heights, final long seed) {
        this.generator = generator;
        this.seed = seed;
        this.random = random;
        this.heights = heights;
        final int minY = heights.getMinBuildHeight(), height = heights.getHeight();
        this.sea = generator.getSeaLevel();
        this.table = Math.max(minY, sea - DEEP);
        if (generator instanceof NoiseBasedChunkGenerator noise) {
            final var shape = noise.generatorSettings().value().noiseSettings();
            this.density = random.router().initialDensityWithoutJaggedness();
            this.cell = Math.max(1, shape.getCellHeight());
            this.bottom = shape.minY();
            this.top = shape.minY() + shape.height();
        } else {
            this.density = null;
            this.cell = 1;
            this.bottom = minY;
            this.top = minY + height;
        }
    }

    /** Whether {@link #floor} is cheap enough to ask for every column. */
    public boolean quick() { return density != null; }

    private double densityAt(final int x, final int y, final int z) {
        return density.compute(new DensityFunction.SinglePointContext(x, y, z));
    }

    /** The level of the sea, in blocks. */
    public int sea() { return sea; }

    /** How many cells of the model stand for a height in blocks. */
    public int cells(final int y) {
        return Math.max(1, Math.round((y - table) / (float) RISE));
    }

    /** Water as the evening light leaves it readable: the biome's own colour, a third of the way to white. */
    private static int lively(final int rgb) {
        final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return 0xFF000000 | (r + (255 - r) / 3) << 16 | (g + (255 - g) / 3) << 8 | (b + (255 - b) / 3);
    }

    /** A number between 0 and 1 that belongs to a place, the same every time. */
    private float chance(final int x, final int z, final int salt) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xBF58476D1CE4E5B9L + z * 0x94D049BB133111EBL + salt * 0x2545F4914F6CDD1DL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 40) / (float) (1 << 24);
    }

    /** How high the ground stands at a place, water not counted. */
    public int floor(final int x, final int z) {
        if (density == null) return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random);
        // The land gets thinner the higher one goes: the surface is where it crosses the mark, found by halving.
        // Started well over the floor of the world, where the generator thins the land out on purpose.
        int solid = Math.min(top - cell, bottom + 5 * cell), air = top;
        double below = 1.0, above = -1.0;
        if (densityAt(x, solid, z) <= SOLID) {
            // No solid ground down there (islands in the sky, a void): looked for from the top, step by step.
            for (int y = top - cell; y >= bottom; y -= cell) if (densityAt(x, y, z) > SOLID) return y;
            return bottom;
        }
        while (air - solid > cell) {
            final int mid = solid + Math.max(cell, ((air - solid) / 2) / cell * cell);
            if (mid >= air) break;
            final double d = densityAt(x, mid, z);
            if (d > SOLID) { solid = mid; below = d; } else { air = mid; above = d; }
        }
        if (below > 0.999) below = densityAt(x, solid, z);
        if (above < -0.999) above = densityAt(x, air, z);
        final double span = below - above;
        final double part = span <= 1e-9 ? 0.5 : Mth.clamp((below - SOLID) / span, 0.0, 1.0);
        return solid + (int) Math.round(part * (air - solid));
    }

    /** What stands at a place whose ground is {@code floor} high: what it wears, what grows on it, what water lies on it. */
    public TerrainNode.Column column(final int x, final int z, final int floor) {
        final boolean wet = floor < sea;
        final Holder<Biome> holder = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(Math.max(floor, sea)),
            QuartPos.fromBlock(z), random.sampler());
        final Biome biome = holder.value();
        final String name = holder.unwrapKey().map(k -> k.location().getPath()).orElse("").toLowerCase(Locale.ROOT);
        final float temperature = biome.getBaseTemperature();
        final boolean cold = temperature < 0.15f;
        final int grass = biome.getGrassColor(x, z) | 0xFF000000;
        final int foliage = biome.getFoliageColor() | 0xFF000000;
        final int ground = cells(floor);

        if (wet) {
            final int water = Math.max(1, cells(sea) - ground);
            final boolean warm = name.contains("warm") || holder.is(BiomeTags.IS_RIVER) || holder.is(BiomeTags.IS_BEACH);
            final String bed = name.contains("swamp") ? "mud" : warm ? "sand" : sea - floor > 14 ? "gravel" : sea - floor > 5 ? "clay" : "sand";
            final boolean frozen = cold && (name.contains("frozen") || name.contains("ice") || sea - floor < 4);
            return new TerrainNode.Column(ground, bed, WHITE, bed, water, lively(biome.getWaterColor()), frozen, null, null, WHITE);
        }

        // What the ground wears.
        String top = "grass_block_top", under = "dirt";
        int tint = grass;
        final int above = floor - sea;
        if (holder.is(BiomeTags.IS_NETHER)) { top = "netherrack"; under = "netherrack"; tint = WHITE; }
        else if (holder.is(BiomeTags.IS_END)) { top = "end_stone"; under = "end_stone"; tint = WHITE; }
        else if (holder.is(BiomeTags.IS_BADLANDS)) {
            final boolean plateau = above > 12;
            top = plateau ? BADLANDS[Math.floorMod(floor / 3, BADLANDS.length)] : "red_sand";
            under = BADLANDS[Math.floorMod(floor / 3 + 1, BADLANDS.length)];
            tint = WHITE;
        } else if (name.contains("desert")) { top = "sand"; under = "sandstone"; tint = WHITE; }
        else if (name.contains("mushroom")) { top = "mycelium_top"; tint = WHITE; }
        else if (name.contains("stony") || name.contains("stone_shore")) { top = above > 90 && chance(x, z, 5) < 0.3f ? "calcite" : "stone"; under = "stone"; tint = WHITE; }
        else if (name.contains("gravel")) { top = "gravel"; under = "stone"; tint = WHITE; }
        else if (name.contains("frozen_peaks") || name.contains("ice_spikes")) { top = chance(x, z, 6) < 0.35f ? "packed_ice" : "snow"; under = "packed_ice"; tint = WHITE; }
        else if (name.contains("jagged") || name.contains("snowy_slopes") || name.contains("grove")) { top = "snow"; under = above > 110 ? "stone" : "dirt"; tint = WHITE; }
        else if (holder.is(BiomeTags.IS_BEACH) || (above <= 1 && !name.contains("swamp") && !name.contains("mangrove"))) {
            top = cold ? "snow" : "sand"; under = "sand"; tint = WHITE;
        } else if (cold) { top = "snow"; tint = WHITE; }
        else if (name.contains("mangrove")) { top = "mud"; under = "mud"; tint = WHITE; }
        else if (name.contains("old_growth") && name.contains("taiga")) { top = chance(x, z, 7) < 0.55f ? "podzol_top" : "grass_block_top"; tint = top.startsWith("podzol") ? WHITE : grass; }
        else if (above > 130) { top = above > 170 ? "snow" : "stone"; under = "stone"; tint = WHITE; }
        else if (holder.is(BiomeTags.IS_MOUNTAIN) && above > 85 && chance(x, z, 8) < 0.5f) { top = "stone"; under = "stone"; tint = WHITE; }

        // What grows on it.
        String canopy = null;
        int canopyTint = foliage;
        float trees = 0f;
        final boolean soil = top.equals("grass_block_top") || top.equals("podzol_top") || top.equals("snow") || top.equals("mud");
        String trunk = "oak_log";
        if (soil && above < 140) {
            if (name.contains("cherry")) { canopy = "cherry_leaves"; trunk = "cherry_log"; canopyTint = WHITE; trees = 0.22f; }
            else if (name.contains("mangrove")) { canopy = "mangrove_leaves"; trunk = "mangrove_log"; trees = 0.4f; }
            else if (name.contains("dark_forest")) { canopy = "dark_oak_leaves"; trunk = "dark_oak_log"; trees = 0.7f; }
            else if (name.contains("birch")) { canopy = "birch_leaves"; trunk = "birch_log"; canopyTint = 0xFF80A755; trees = 0.36f; }
            else if (holder.is(BiomeTags.IS_JUNGLE)) { canopy = "jungle_leaves"; trunk = "jungle_log"; trees = name.contains("sparse") || name.contains("bamboo") ? 0.2f : 0.6f; }
            else if (holder.is(BiomeTags.IS_TAIGA) || name.contains("grove")) { canopy = "spruce_leaves"; trunk = "spruce_log"; canopyTint = 0xFF619961; trees = name.contains("old_growth") ? 0.45f : 0.32f; }
            else if (holder.is(BiomeTags.IS_SAVANNA)) { canopy = "acacia_leaves"; trunk = "acacia_log"; trees = 0.05f; }
            else if (name.contains("swamp")) { canopy = "oak_leaves"; trees = 0.12f; }
            else if (name.contains("flower_forest")) { canopy = "oak_leaves"; trees = 0.26f; }
            else if (holder.is(BiomeTags.IS_FOREST) || name.contains("forest")) { canopy = "oak_leaves"; trees = 0.38f; }
            else if (name.contains("windswept")) { canopy = "spruce_leaves"; trunk = "spruce_log"; canopyTint = 0xFF619961; trees = 0.04f; }
            else if (top.equals("grass_block_top")) { canopy = "oak_leaves"; trees = 0.008f; }
        }
        if (canopy != null && chance(x, z, 1) >= trees) canopy = null;
        return new TerrainNode.Column(ground, top, tint, under, 0, WHITE, false, canopy == null ? null : trunk, canopy, canopyTint);
    }
}
