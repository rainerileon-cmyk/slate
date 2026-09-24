package dev.fallingcloud.slate.building.client.model;

import dev.fallingcloud.slate.building.variant.ShapeBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
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
 * <p>Caches: face regions per shape state (unbounded, a few hundred states), cropped quads in a bounded LRU keyed by
 * shape state, material key, render layer, bucket and the IDENTITY of the source quad lists (a weighted model such
 * as stone's random rotations hands out one list per variant, so each variant gets its own entry without knowing
 * the seed). Both are cleared on resource reload ({@link #clearCaches()}, called by the loader glue). Thread-safe:
 * chunk meshing calls this from worker threads.
 */
public final class ShapeQuadBaker {

    /** The material's quads for one side (or the unculled ones for {@code null}), as its model returns them. */
    @FunctionalInterface
    public interface QuadSource {
        List<BakedQuad> quads(@Nullable Direction side);
    }

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int MAX_CACHED = 8192;
    private static final Object NO_LAYER = new Object();

    private static final Map<BlockState, ShapeFaces> FACES = new ConcurrentHashMap<>();
    private static final Map<Key, List<BakedQuad>> QUADS = new LinkedHashMap<>(1024, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<Key, List<BakedQuad>> eldest) {
            return size() > MAX_CACHED;
        }
    };

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
        final ShapeFaces faces = faces(shape);
        final List<?>[] lists = new List<?>[7];
        if (side != null) {
            if (faces.facing(side).isEmpty()) return List.of();
            lists[side.ordinal()] = source.quads(side);
        } else {
            for (final Direction d : DIRECTIONS) lists[d.ordinal()] = source.quads(d);
            lists[6] = source.quads(null);
        }
        final Key key = new Key(shape, materialKey, side == null ? 6 : side.ordinal(), layer == null ? NO_LAYER : layer, lists);
        synchronized (QUADS) {
            final List<BakedQuad> hit = QUADS.get(key);
            if (hit != null) return hit;
        }
        final List<BakedQuad> built = build(faces, side, lists);
        synchronized (QUADS) {
            QUADS.put(key, built);
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

    /** Drops every cached region and quad. Call on resource reload and when render boxes change (dev stand-ins). */
    public static void clearCaches() {
        FACES.clear();
        synchronized (QUADS) {
            QUADS.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<BakedQuad> build(final ShapeFaces faces, final @Nullable Direction side, final List<?>[] lists) {
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
        }
        return out.isEmpty() ? List.of() : Collections.unmodifiableList(out);
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
     * Cache key. The source lists are compared by identity: models hand out the same list objects for the same
     * variant, and a model that builds fresh lists every call simply never hits (the LRU bounds the cost).
     */
    private static final class Key {
        private final BlockState shape;
        private final Object material;
        private final int bucket;
        private final Object layer;
        private final Object[] lists;
        private final int hash;

        Key(final BlockState shape, final Object material, final int bucket, final Object layer, final Object[] lists) {
            this.shape = shape;
            this.material = material;
            this.bucket = bucket;
            this.layer = layer;
            this.lists = lists;
            int h = Objects.hash(shape, material, bucket, layer);
            for (final Object l : lists) h = 31 * h + System.identityHashCode(l);
            this.hash = h;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k) || k.hash != hash || k.bucket != bucket || k.shape != shape || k.layer != layer
                || !k.material.equals(material)) return false;
            for (int i = 0; i < lists.length; i++) if (k.lists[i] != lists[i]) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    /** Whether {@code state} is one of our shape blocks. */
    public static boolean isShape(final @Nullable BlockState state) {
        return state != null && state.getBlock() instanceof ShapeBlock;
    }

    private ShapeQuadBaker() {}
}
