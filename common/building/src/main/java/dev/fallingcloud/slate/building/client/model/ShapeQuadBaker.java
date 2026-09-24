package dev.fallingcloud.slate.building.client.model;

import dev.fallingcloud.slate.building.variant.RotatedBox;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the quads of a shape block made of a material (design §6): the material's own full-block model is cut to
 * the shape's render boxes, face by face. Used by the loader block models (chunk meshing, both loaders), the item
 * models and the ghost renderer, so a stone stair looks the same in the world, in the hand and as a preview.
 *
 * <p>For every visible face region of the shape ({@link ShapeFaces}) the material's quads pointing the same way are
 * cropped to the region and moved onto its plane ({@link QuadCropper}). Tint index and shade are kept, so grass
 * stays biome-tinted and faces keep vanilla's directional shading. Output buckets follow the vanilla contract:
 * <ul>
 *   <li>{@code side != null}: regions on the block boundary facing {@code side}, made from the material's quads
 *       CULLED on {@code side}; the renderer drops them when the neighbour hides that face.</li>
 *   <li>{@code side == null}: everything that must never be culled: internal regions (a slab's top at half height,
 *       a stair's riser) and any of the material's own unculled quads.</li>
 * </ul>
 *
 * <p>Caches: face regions per shape state (unbounded, a few hundred states), and cropped quads in a bounded LRU keyed
 * by shape state, material key, render layer, bucket and the CONTENT of the source quads the bucket is cut from
 * (vertex data, sprite, tint, direction, shade). Keying by content rather than by list identity keeps the hit rate
 * for every kind of material model: a weighted model (stone's mirrored variants) hands out different quads per
 * variant, so each variant gets its own entry; a multipart model builds a fresh list of the same quads on every call
 * and a connected-texture style model may build fresh but identical quads, and both still hit. Only the source lists
 * a bucket actually uses are requested from the model (a side bucket: that side; the unculled bucket: the unculled
 * quads plus the directions that have internal faces). The LRU is split into shards with their own locks, so chunk
 * builder threads rarely wait on each other. Both caches are cleared on resource reload ({@link #clearCaches()},
 * called by the loader glue). Thread-safe: chunk meshing calls this from worker threads.
 */
public final class ShapeQuadBaker {

    /** The material's quads for one side (or the unculled ones for {@code null}), as its model returns them. */
    @FunctionalInterface
    public interface QuadSource {
        List<BakedQuad> quads(@Nullable Direction side);
    }

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int MAX_CACHED = 8192;
    private static final int SHARDS = 16;
    private static final int PER_SHARD = MAX_CACHED / SHARDS;
    private static final Object NO_LAYER = new Object();
    private static final List<BakedQuad> NONE = List.of();

    private static final Map<BlockState, ShapeFaces> FACES = new ConcurrentHashMap<>();
    @SuppressWarnings("unchecked")
    private static final Map<Key, List<BakedQuad>>[] QUADS = new Map[SHARDS];
    private static final AtomicLong HITS = new AtomicLong();
    private static final AtomicLong MISSES = new AtomicLong();

    static {
        for (int i = 0; i < SHARDS; i++) {
            QUADS[i] = new LinkedHashMap<>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<Key, List<BakedQuad>> eldest) {
                    return size() > PER_SHARD;
                }
            };
        }
    }

    /**
     * The quads of {@code shape} made of the source model, for one bucket.
     *
     * @param shape       a shape block state (any other state renders as a full cube)
     * @param side        the bucket: a culled side, or null for the never-culled quads
     * @param materialKey what the source is (the material state, or a sentinel for the "no material" look); part of the cache key
     * @param layer       the render layer the source was asked for (NeoForge render type, Fabric blend mode), or null
     * @param source      the material model's quads
     */
    public static List<BakedQuad> quads(final BlockState shape, final @Nullable Direction side, final Object materialKey,
                                        final @Nullable Object layer, final QuadSource source) {
        return quads(shape, side, materialKey, layer, source, 0);
    }

    /**
     * As {@link #quads(BlockState, Direction, Object, Object, QuadSource)}, plus the diagonal arms of {@code diagonals}
     * (the block entity's mask, {@code DiagonalShapes}): they are cut from the material like any box, turned into place,
     * and always land in the never-culled bucket.
     */
    public static List<BakedQuad> quads(final BlockState shape, final @Nullable Direction side, final Object materialKey,
                                        final @Nullable Object layer, final QuadSource source, final int diagonals) {
        final ShapeFaces faces = faces(shape);
        final List<RotatedBox> rotated = side == null && diagonals != 0 && shape.getBlock() instanceof ShapeBlock sb
            ? sb.renderRotatedBoxes(shape, diagonals) : List.of();
        final List<?>[] lists = new List<?>[7];
        if (side != null) {
            if (!faces.hasBoundary(side)) return NONE;   // only internal faces (or none) point this way
            lists[side.ordinal()] = source.quads(side);
        } else {
            // Internal faces are cut from the material's quads of their own direction; everything else only needs
            // the unculled quads. Directions without an internal face are never asked for (all six for turned arms).
            for (final Direction d : DIRECTIONS) if (faces.hasInternal(d) || !rotated.isEmpty()) lists[d.ordinal()] = source.quads(d);
            lists[6] = source.quads(null);
        }
        final int bucket = (side == null ? 6 : side.ordinal()) | (rotated.isEmpty() ? 0 : diagonals << 4);
        final Key key = new Key(shape, materialKey, bucket, layer == null ? NO_LAYER : layer, lists);
        final Map<Key, List<BakedQuad>> shard = QUADS[Math.floorMod(key.hash ^ (key.hash >>> 16), SHARDS)];
        synchronized (shard) {
            final List<BakedQuad> hit = shard.get(key);
            if (hit != null) {
                HITS.incrementAndGet();
                return hit;
            }
        }
        MISSES.incrementAndGet();
        final List<BakedQuad> built = build(faces, side, lists, rotated);
        synchronized (shard) {
            shard.put(key, built);
        }
        return built;
    }

    /** The shape's visible face regions (render boxes minus self-covered parts), cached per state. */
    static ShapeFaces faces(final BlockState shape) {
        return FACES.computeIfAbsent(shape, s -> ShapeFaces.of(ShapeGeometry.boxes(s)));
    }

    /** Whether the shape fully covers the block face on {@code side} (it then hides its neighbour like a full block). */
    public static boolean coversFace(final BlockState shape, final Direction side) {
        return faces(shape).fullFace(side);
    }

    /** Drops every cached region and quad. Call on resource reload. */
    public static void clearCaches() {
        FACES.clear();
        ROTATED_FACES.clear();
        ShapeGeometry.clear();
        for (final Map<Key, List<BakedQuad>> shard : QUADS) {
            synchronized (shard) {
                shard.clear();
            }
        }
    }

    /** Cache lookups since start-up: {hits, misses} (dev harness and diagnostics). */
    public static long[] cacheStats() {
        return new long[] {HITS.get(), MISSES.get()};
    }

    @SuppressWarnings("unchecked")
    private static List<BakedQuad> build(final ShapeFaces faces, final @Nullable Direction side, final List<?>[] lists, final List<RotatedBox> rotated) {
        final List<BakedQuad> out = new ArrayList<>();
        if (side != null) {
            // Boundary faces on this side, from the material's quads culled on the same side.
            for (final ShapeFaces.Region r : faces.facing(side)) {
                if (!r.boundary()) continue;
                crop((List<BakedQuad>) lists[side.ordinal()], r, side, out);
            }
        } else {
            final List<BakedQuad> unculled = (List<BakedQuad>) lists[6];
            for (final Direction d : DIRECTIONS) {
                for (final ShapeFaces.Region r : faces.facing(d)) {
                    // Internal faces reuse the material's quads of the same direction; they are never culled.
                    if (!r.boundary()) crop((List<BakedQuad>) lists[d.ordinal()], r, d, out);
                    // The material's own unculled quads stay unculled wherever they end up.
                    crop(unculled, r, d, out);
                }
            }
            for (final RotatedBox rb : rotated) bakeRotated(rb, lists, out);
        }
        return out.isEmpty() ? NONE : Collections.unmodifiableList(out);
    }

    // ---- turned boxes (diagonal arms)

    private static final Map<AABB, ShapeFaces> ROTATED_FACES = new ConcurrentHashMap<>();
    private static final float SQRT2 = (float) Math.sqrt(2);

    /**
     * A turned box: every face of the box (boundary faces included, it never touches a neighbour) is cut from the
     * material's quads of that direction and from its unculled quads, then the quads are stretched by √2 along Z about
     * the block centre and turned about the block's vertical axis ({@link RotatedBox}).
     */
    @SuppressWarnings("unchecked")
    private static void bakeRotated(final RotatedBox rb, final List<?>[] lists, final List<BakedQuad> out) {
        final ShapeFaces f = ROTATED_FACES.computeIfAbsent(rb.box(), b -> ShapeFaces.of(List.of(b)));
        final List<BakedQuad> arm = new ArrayList<>();
        for (final Direction d : DIRECTIONS) {
            for (final ShapeFaces.Region r : f.facing(d)) {
                crop((List<BakedQuad>) lists[d.ordinal()], r, d, arm);
                crop((List<BakedQuad>) lists[6], r, d, arm);
            }
        }
        final double rad = Math.toRadians(rb.yaw());
        final float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        for (final BakedQuad q : arm) out.add(turn(q, cos, sin));
    }

    private static BakedQuad turn(final BakedQuad q, final float cos, final float sin) {
        final int[] src = q.getVertices();
        if (src.length < 16 || src.length % 4 != 0) return q;
        final int stride = src.length / 4;
        final int[] v = src.clone();
        final float[][] p = new float[4][3];
        float nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < 4; i++) {
            final int b = i * stride;
            final float x = Float.intBitsToFloat(v[b]) - 0.5f, y = Float.intBitsToFloat(v[b + 1]);
            final float z = (Float.intBitsToFloat(v[b + 2]) - 0.5f) * SQRT2;
            final float rx = x * cos - z * sin, rz = x * sin + z * cos;
            p[i][0] = rx + 0.5f;
            p[i][1] = y;
            p[i][2] = rz + 0.5f;
            v[b] = Float.floatToRawIntBits(p[i][0]);
            v[b + 2] = Float.floatToRawIntBits(p[i][2]);
            if (stride >= 8) {
                final int packed = v[b + 7];
                final float px = (byte) (packed & 0xFF) / 127f, py = (byte) (packed >> 8 & 0xFF) / 127f, pz = (byte) (packed >> 16 & 0xFF) / 127f;
                nx = px * cos - pz * sin;
                ny = py;
                nz = px * sin + pz * cos;
                v[b + 7] = ((int) (nx * 127) & 0xFF) | ((int) (ny * 127) & 0xFF) << 8 | ((int) (nz * 127) & 0xFF) << 16;
            }
        }
        if (Math.abs(nx) + Math.abs(ny) + Math.abs(nz) < 0.01f) {
            // No packed normal in the source: take the face's own.
            final float ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
            final float bx = p[3][0] - p[0][0], by = p[3][1] - p[0][1], bz = p[3][2] - p[0][2];
            nx = ay * bz - az * by;
            ny = az * bx - ax * bz;
            nz = ax * by - ay * bx;
        }
        return new BakedQuad(v, q.getTintIndex(), Direction.getNearest(nx, ny, nz), q.getSprite(), q.isShade());
    }

    private static void crop(final @Nullable List<BakedQuad> source, final ShapeFaces.Region region, final Direction dir,
                             final List<BakedQuad> out) {
        if (source == null) return;
        for (final BakedQuad q : source) {
            if (q.getDirection() != dir) continue;
            final BakedQuad cropped = QuadCropper.crop(q, region);
            if (cropped != null) out.add(cropped);
        }
    }

    /**
     * Cache key. The source quads are compared by content: the same quad object (a multipart model's fresh list of
     * shared quads) matches at once, and a different object with the same vertex data, sprite, tint, direction and
     * shade (a model that builds its quads per call) matches too. The source lists are copied into arrays, so a
     * model's throwaway lists are not kept alive by the cache.
     */
    private static final class Key {
        private final BlockState shape;
        private final Object material;
        private final int bucket;
        private final Object layer;
        private final BakedQuad[][] quads;
        private final int hash;

        Key(final BlockState shape, final Object material, final int bucket, final Object layer, final List<?>[] lists) {
            this.shape = shape;
            this.material = material;
            this.bucket = bucket;
            this.layer = layer;
            this.quads = new BakedQuad[lists.length][];
            int h = Objects.hash(shape, material, bucket, layer);
            for (int i = 0; i < lists.length; i++) {
                final List<?> list = lists[i];
                if (list == null) {
                    h = 31 * h;
                    continue;
                }
                final BakedQuad[] arr = new BakedQuad[list.size()];
                int lh = 1;
                for (int j = 0; j < arr.length; j++) {
                    arr[j] = (BakedQuad) list.get(j);
                    lh = 31 * lh + contentHash(arr[j]);
                }
                this.quads[i] = arr;
                h = 31 * h + lh;
            }
            this.hash = h;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k) || k.hash != hash || k.bucket != bucket || k.shape != shape || k.layer != layer
                || !k.material.equals(material)) return false;
            for (int i = 0; i < quads.length; i++) {
                final BakedQuad[] a = quads[i], b = k.quads[i];
                if (a == b) continue;
                if (a == null || b == null || a.length != b.length) return false;
                for (int j = 0; j < a.length; j++) if (!sameContent(a[j], b[j])) return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static int contentHash(final BakedQuad q) {
        int h = Arrays.hashCode(q.getVertices());
        h = 31 * h + q.getTintIndex();
        h = 31 * h + q.getDirection().ordinal();
        h = 31 * h + (q.isShade() ? 1 : 0);
        return 31 * h + System.identityHashCode(q.getSprite());
    }

    private static boolean sameContent(final BakedQuad a, final BakedQuad b) {
        if (a == b) return true;
        return a.getDirection() == b.getDirection() && a.getTintIndex() == b.getTintIndex() && a.isShade() == b.isShade()
            && a.getSprite() == b.getSprite() && Arrays.equals(a.getVertices(), b.getVertices());
    }

    /** Whether {@code state} is one of our shape blocks. */
    public static boolean isShape(final @Nullable BlockState state) {
        return state != null && state.getBlock() instanceof ShapeBlock;
    }

    private ShapeQuadBaker() {}
}
