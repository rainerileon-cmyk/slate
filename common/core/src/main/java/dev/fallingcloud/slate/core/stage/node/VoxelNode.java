package dev.fallingcloud.slate.core.stage.node;

import dev.fallingcloud.slate.core.stage.StageLevel;
import java.util.Random;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Generated voxel content, meshed like a chunk: a small {@link #planet planet} (a noisy sphere of grass, dirt, stone,
 * sand and water pools, snow caps and a few trees) and a {@link #heightfield heightfield} (rolling terrain with a
 * pond, flowers and trees). Deterministic per seed, so two calls with the same seed look the same and different seeds
 * look "slightly different" the way the Play ring wants.
 */
public class VoxelNode extends MeshedBlocksNode {

    private VoxelNode(final StageLevel level) {
        super(level);
    }

    /** A planet of radius {@code radius} blocks (4..24 is sensible); origin at the planet's centre. */
    public static VoxelNode planet(final StageLevel level, final int radius, final long seed) {
        final VoxelNode n = new VoxelNode(level);
        final int r = Mth.clamp(radius, 3, 24);
        final int d = r * 2 + 1;
        final Noise noise = new Noise(seed);
        final Random rnd = new Random(seed * 31L + 7L);
        final float freq = 1.6f / r;
        for (int x = 0; x < d; x++) {
            for (int y = 0; y < d; y++) {
                for (int z = 0; z < d; z++) {
                    final float fx = x - r, fy = y - r, fz = z - r;
                    final float dist = (float) Math.sqrt(fx * fx + fy * fy + fz * fz) / r;
                    final float bump = noise.fbm(fx * freq, fy * freq, fz * freq) * 0.12f;
                    final float shell = dist + bump;
                    if (shell > 1f) continue;
                    final float lat = fy / r;
                    final BlockState state;
                    if (shell > 0.9f) {
                        final float m = noise.fbm(fx * freq * 2.3f + 11f, fy * freq * 2.3f, fz * freq * 2.3f + 5f);
                        if (Math.abs(lat) > 0.74f) state = Blocks.SNOW_BLOCK.defaultBlockState();
                        else if (m < -0.22f) state = Blocks.WATER.defaultBlockState();
                        else if (m < -0.12f) state = Blocks.SAND.defaultBlockState();
                        else state = Blocks.GRASS_BLOCK.defaultBlockState();
                    } else if (shell > 0.74f) {
                        state = Blocks.DIRT.defaultBlockState();
                    } else {
                        state = Blocks.STONE.defaultBlockState();
                    }
                    n.set(x, y, z, state, null);
                }
            }
        }
        // Water needs a floor: turn water that sits over air into sand.
        for (int x = 0; x < d; x++) for (int y = 0; y < d; y++) for (int z = 0; z < d; z++) {
            if (n.get(x, y, z).getBlock() == Blocks.WATER && (y == 0 || n.get(x, y - 1, z).isAir())) n.set(x, y, z, Blocks.SAND.defaultBlockState(), null);
        }
        // A few trees on the top cap.
        int trees = 0;
        for (int attempt = 0; attempt < 60 && trees < Math.max(1, r / 3); attempt++) {
            final int x = r + rnd.nextInt(Math.max(1, r)) - r / 2, z = r + rnd.nextInt(Math.max(1, r)) - r / 2;
            for (int y = d - 1; y > r; y--) {
                if (n.get(x, y, z).getBlock() == Blocks.GRASS_BLOCK && n.get(x, y + 1, z).isAir()) {
                    n.tree(x, y + 1, z, rnd, d);
                    trees++;
                    break;
                }
            }
        }
        n.finishFill(new Vec3i(d, d + 5, d));
        n.meshOffset(-r, -r, -r);       // the node origin is the planet's centre
        n.bounds(-r - 0.5f, -r - 0.5f, -r - 0.5f, r + 0.5f, r + 0.5f, r + 0.5f);
        n.named("planet");
        return n;
    }

    /** Rolling terrain {@code width × depth} blocks with a pond; origin at the min corner (use {@link #centered()}). */
    public static VoxelNode heightfield(final StageLevel level, final int width, final int depth, final long seed) {
        final VoxelNode n = new VoxelNode(level);
        final int w = Mth.clamp(width, 4, 64), dp = Mth.clamp(depth, 4, 64);
        final Noise noise = new Noise(seed);
        final Random rnd = new Random(seed * 17L + 3L);
        final int sea = 3;
        int maxH = 0;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < dp; z++) {
                final float e = noise.fbm(x * 0.11f, 0f, z * 0.11f) * 3.5f + noise.fbm(x * 0.3f + 9f, 0f, z * 0.3f) * 1.2f;
                final int h = Mth.clamp(Math.round(4f + e), 1, 12);
                maxH = Math.max(maxH, h);
                for (int y = 0; y < h; y++) {
                    final Block b = y == h - 1 ? (h <= sea ? Blocks.SAND : Blocks.GRASS_BLOCK) : (y >= h - 3 ? Blocks.DIRT : Blocks.STONE);
                    n.set(x, y, z, b.defaultBlockState(), null);
                }
                for (int y = h; y < sea; y++) n.set(x, y, z, Blocks.WATER.defaultBlockState(), null);
                if (h > sea && h < 9) {
                    final float f = rnd.nextFloat();
                    if (f < 0.10f) n.set(x, h, z, Blocks.SHORT_GRASS.defaultBlockState(), null);
                    else if (f < 0.13f) n.set(x, h, z, Blocks.POPPY.defaultBlockState(), null);
                    else if (f < 0.16f) n.set(x, h, z, Blocks.DANDELION.defaultBlockState(), null);
                }
            }
        }
        int trees = 0;
        for (int attempt = 0; attempt < 40 && trees < Math.max(1, w * dp / 60); attempt++) {
            final int x = 2 + rnd.nextInt(Math.max(1, w - 4)), z = 2 + rnd.nextInt(Math.max(1, dp - 4));
            for (int y = 12; y > sea; y--) {
                if (n.get(x, y, z).getBlock() == Blocks.GRASS_BLOCK && n.get(x, y + 1, z).isAir()) {
                    n.tree(x, y + 1, z, rnd, 64);
                    trees++;
                    break;
                }
            }
        }
        n.finishFill(new Vec3i(w, maxH + 7, dp));
        n.named("terrain");
        return n;
    }

    private void tree(final int x, final int y, final int z, final Random rnd, final int limit) {
        final int h = 3 + rnd.nextInt(2);
        for (int i = 0; i < h; i++) set(x, y + i, z, Blocks.OAK_LOG.defaultBlockState(), null);
        final BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = h - 2; dy <= h; dy++) {
            final int lx = x + dx, ly = y + dy, lz = z + dz;
            if (lx < 0 || lz < 0 || lx >= limit || lz >= limit || ly < 0) continue;
            final int ring = Math.max(Math.abs(dx), Math.abs(dz));
            if (ring == 2 && (dy == h || rnd.nextInt(4) == 0)) continue;
            if (ring == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
            if (get(lx, ly, lz).isAir()) set(lx, ly, lz, leaves, null);
        }
        set(x, y + h, z, leaves, null);
        if (rnd.nextBoolean()) set(x, y + h + 1, z, leaves, null);
    }

    /** Small seeded value noise (two octaves), enough for planets and hills. */
    static final class Noise {
        private final long seed;

        Noise(final long seed) { this.seed = seed; }

        private float hash(final int x, final int y, final int z) {
            long h = seed ^ (x * 374761393L) ^ (y * 668265263L) ^ (z * 2147483647L);
            h = (h ^ (h >>> 13)) * 1274126177L;
            h ^= h >>> 16;
            return ((h & 0xFFFF) / 65535f) * 2f - 1f;
        }

        float value(final float x, final float y, final float z) {
            final int x0 = Mth.floor(x), y0 = Mth.floor(y), z0 = Mth.floor(z);
            final float tx = smooth(x - x0), ty = smooth(y - y0), tz = smooth(z - z0);
            final float c00 = Mth.lerp(tx, hash(x0, y0, z0), hash(x0 + 1, y0, z0));
            final float c10 = Mth.lerp(tx, hash(x0, y0 + 1, z0), hash(x0 + 1, y0 + 1, z0));
            final float c01 = Mth.lerp(tx, hash(x0, y0, z0 + 1), hash(x0 + 1, y0, z0 + 1));
            final float c11 = Mth.lerp(tx, hash(x0, y0 + 1, z0 + 1), hash(x0 + 1, y0 + 1, z0 + 1));
            return Mth.lerp(tz, Mth.lerp(ty, c00, c10), Mth.lerp(ty, c01, c11));
        }

        float fbm(final float x, final float y, final float z) {
            return value(x, y, z) * 0.65f + value(x * 2.1f + 3f, y * 2.1f, z * 2.1f - 2f) * 0.35f;
        }

        private static float smooth(final float t) { return t * t * (3f - 2f * t); }
    }
}
