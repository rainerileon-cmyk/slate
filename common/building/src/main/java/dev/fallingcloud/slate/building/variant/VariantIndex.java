package dev.fallingcloud.slate.building.variant;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.config.ServerVariants;
import dev.fallingcloud.slate.building.mixin.variant.StairBlockAccessor;
import dev.fallingcloud.slate.building.registry.BuildingTags;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * One immutable snapshot of what {@link VariantRegistry} knows, built from the registries, the block tags and the
 * server's variant rules. Deterministic: both sides iterate the same registries in id order with the same tags and
 * rules, so they reach the same answer without syncing anything.
 *
 * <p>Discovery of native variants (design §2), strongest claim first:
 * <ol>
 *   <li>server overrides ({@code variantOverrides});</li>
 *   <li>vanilla {@code BlockFamilies}: base block → STAIRS / SLAB / WALL / FENCE / FENCE_GATE;</li>
 *   <li>every {@link StairBlock} → its base state's block;</li>
 *   <li>slabs, walls, fences and gates by name: the sibling stairs ({@code oak_slab} → {@code oak_stairs} → its
 *       base), else a full block named {@code X}, {@code Xs}, {@code X_planks}, {@code X_block}, {@code X_bricks},
 *       {@code X_tiles} in the same namespace, then {@code minecraft};</li>
 *   <li>blocks whose item is a native variant's item (DiagonalFences/Walls/Windows' twins re-point the vanilla item
 *       to their own block) identify as that variant.</li>
 * </ol>
 * When several natives realise one (material, shape), the one handed out is chosen by: override/family, then same
 * namespace as the material, then {@code minecraft}, then registry order.
 */
final class VariantIndex {

    private static final int CLAIM_OVERRIDE = 0;
    private static final int CLAIM_FAMILY = 1;
    private static final int CLAIM_STAIRS = 2;
    private static final int CLAIM_SIBLING = 3;
    private static final int CLAIM_NAME = 4;

    private static final String[] NAME_SUFFIXES = {"", "s", "_planks", "_block", "_bricks", "_tiles"};

    final int generation;
    final int fingerprint;
    final boolean unify;
    final boolean customShapes;
    final boolean deleteNatives;

    private final Set<Block> materials;
    private final Map<Block, Variant> natives;
    private final Map<Item, Variant> nativeItems;
    private final Map<Block, Map<Shape, Block>> realisations;
    private final Set<Item> nativeItemSet;
    private final Map<Block, List<Shape>> shapeCache = new ConcurrentHashMap<>();

    private VariantIndex(final int generation, final int fingerprint, final ServerVariants rules, final Set<Block> materials,
                         final Map<Block, Variant> natives, final Map<Item, Variant> nativeItems, final Map<Block, Map<Shape, Block>> realisations) {
        this.generation = generation;
        this.fingerprint = fingerprint;
        this.unify = rules.unify;
        this.customShapes = rules.customShapes;
        this.deleteNatives = rules.deleteNativeVariants;
        this.materials = materials;
        this.natives = natives;
        this.nativeItems = nativeItems;
        this.realisations = realisations;
        final Set<Item> items = Collections.newSetFromMap(new IdentityHashMap<>());
        items.addAll(nativeItems.keySet());
        this.nativeItemSet = Collections.unmodifiableSet(items);
    }

    boolean isMaterial(final Block block) {
        return materials.contains(block);
    }

    @Nullable Variant nativeVariant(final Block block) {
        return natives.get(block);
    }

    @Nullable Variant nativeItemVariant(final Item item) {
        return nativeItems.get(item);
    }

    @Nullable Block realisation(final Block material, final Shape shape) {
        final Map<Shape, Block> byShape = realisations.get(material);
        return byShape == null ? null : byShape.get(shape);
    }

    boolean hasRealisations(final Block material) {
        return realisations.containsKey(material);
    }

    Set<Item> nativeItems() {
        return nativeItemSet;
    }

    List<Shape> shapes(final Block material, final java.util.function.Predicate<Shape> available) {
        return shapeCache.computeIfAbsent(material, m -> {
            final List<Shape> out = new ArrayList<>(Shape.values().length);
            for (final Shape s : Shape.values()) if (available.test(s)) out.add(s);
            return List.copyOf(out);
        });
    }

    int materialCount() {
        return materials.size();
    }

    int nativeCount() {
        return natives.size();
    }

    // ------------------------------------------------------------------------------------------------ build

    private record Claim(Block material, Shape shape, int strength) {}

    private record Rules(Set<Block> deny, List<TagKey<Block>> denyTags, Set<Block> allow, List<TagKey<Block>> allowTags, Set<Block> ignored) {}

    static VariantIndex build(final ServerVariants rules, final int generation, final int fingerprint) {
        final long start = System.nanoTime();
        final Rules r = parseRules(rules);

        final Set<Block> materials = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final Block block : BuiltInRegistries.BLOCK) if (eligible(block, r)) materials.add(block);

        final Map<Block, Claim> claims = new IdentityHashMap<>();
        if (rules.unify) {
            claimOverrides(rules, claims);
            claimFamilies(claims);
            claimStairs(claims);
            claimByName(claims);
        }

        // Resolve claims into identified natives (registry order = deterministic ties).
        final Map<Block, Variant> natives = new IdentityHashMap<>();
        final Map<Block, Map<Shape, List<Block>>> candidates = new IdentityHashMap<>();
        final Map<Block, Integer> strength = new IdentityHashMap<>();
        for (final Block block : BuiltInRegistries.BLOCK) {
            final Claim claim = claims.get(block);
            if (claim == null || r.ignored.contains(block) || block instanceof ShapeBlock || block instanceof EntityBlock) continue;
            final boolean validMaterial = claim.strength == CLAIM_OVERRIDE
                ? claim.material.asItem() != Items.AIR
                : materials.contains(claim.material);
            if (!validMaterial || claim.material == block) continue;
            natives.put(block, new Variant(claim.material, claim.shape));
            strength.put(block, claim.strength);
            candidates.computeIfAbsent(claim.material, m -> new EnumMap<>(Shape.class))
                .computeIfAbsent(claim.shape, s -> new ArrayList<>()).add(block);
        }

        // Pick the native that realises each (material, shape).
        final Map<Block, Map<Shape, Block>> realisations = new IdentityHashMap<>();
        candidates.forEach((material, byShape) -> {
            final Map<Shape, Block> chosen = new EnumMap<>(Shape.class);
            byShape.forEach((shape, list) -> {
                Block best = null;
                for (final Block b : list) {
                    if (b.asItem() == Items.AIR) continue;
                    if (best == null || better(b, best, material, strength)) best = b;
                }
                if (best != null) chosen.put(shape, best);
            });
            if (!chosen.isEmpty()) realisations.put(material, Collections.unmodifiableMap(chosen));
        });

        // Items of identified natives, then re-pointed twins (rule 5).
        final Map<Item, Variant> nativeItems = new IdentityHashMap<>();
        natives.forEach((block, v) -> {
            final Item item = block.asItem();
            if (item != Items.AIR && !(item instanceof dev.fallingcloud.slate.building.item.ShapeBlockItem)) nativeItems.putIfAbsent(item, v);
        });
        for (final Item item : BuiltInRegistries.ITEM) {
            if (item instanceof BlockItem bi && !nativeItems.containsKey(item)) {
                final Variant v = natives.get(bi.getBlock());
                if (v != null) nativeItems.put(item, v);
            }
        }
        int twins = 0;
        if (rules.unify) {
            for (final Block block : BuiltInRegistries.BLOCK) {
                if (natives.containsKey(block) || materials.contains(block) || block instanceof ShapeBlock || r.ignored.contains(block)) continue;
                final Item item = block.asItem();
                if (item == Items.AIR) continue;
                final Variant v = nativeItems.get(item);
                if (v != null) {
                    natives.put(block, v);
                    twins++;
                }
            }
        }

        final VariantIndex index = new VariantIndex(generation, fingerprint, rules, materials, natives, nativeItems, realisations);
        final String summary = materials.size() + " materials, " + natives.size() + " native variants (" + twins + " re-pointed twins, "
            + nativeItems.size() + " native items)" + (rules.unify ? "" : " [unify off]");
        final long ms = (System.nanoTime() - start) / 1_000_000;
        // Rebuilds happen on every tag reload on both sides; only a changed result is worth an info line.
        if (summary.equals(lastSummary)) SlateBuilding.LOGGER.debug("[Slate Building] variants rebuilt: {} in {} ms", summary, ms);
        else SlateBuilding.LOGGER.info("[Slate Building] variants: {} in {} ms", summary, ms);
        lastSummary = summary;
        return index;
    }

    private static volatile @Nullable String lastSummary;

    /** Whether native {@code a} should realise its variant rather than {@code b}. */
    private static boolean better(final Block a, final Block b, final Block material, final Map<Block, Integer> strength) {
        final int sa = Math.min(strength.getOrDefault(a, CLAIM_NAME), CLAIM_STAIRS);
        final int sb = Math.min(strength.getOrDefault(b, CLAIM_NAME), CLAIM_STAIRS);
        final int fa = sa <= CLAIM_FAMILY ? 0 : 1;
        final int fb = sb <= CLAIM_FAMILY ? 0 : 1;
        if (fa != fb) return fa < fb;
        final String ns = BuiltInRegistries.BLOCK.getKey(material).getNamespace();
        final ResourceLocation ka = BuiltInRegistries.BLOCK.getKey(a);
        final ResourceLocation kb = BuiltInRegistries.BLOCK.getKey(b);
        final boolean na = ka.getNamespace().equals(ns), nb = kb.getNamespace().equals(ns);
        if (na != nb) return na;
        final boolean ma = ka.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE), mb = kb.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE);
        if (ma != mb) return ma;
        return BuiltInRegistries.BLOCK.getId(a) < BuiltInRegistries.BLOCK.getId(b);
    }

    private static void claim(final Map<Block, Claim> claims, final Block block, final Block material, final Shape shape, final int strength) {
        if (block == Blocks.AIR || material == Blocks.AIR || block == material) return;
        final Claim existing = claims.get(block);
        if (existing == null || strength < existing.strength) claims.put(block, new Claim(material, shape, strength));
    }

    private static void claimOverrides(final ServerVariants rules, final Map<Block, Claim> claims) {
        if (rules.variantOverrides == null) return;
        rules.variantOverrides.forEach((key, value) -> {
            final Block block = block(key);
            if (block == null || value == null) {
                SlateBuilding.LOGGER.warn("[Slate Building] variantOverrides: unknown block '{}'", key);
                return;
            }
            final int hash = value.indexOf('#');
            final Block material = block(hash < 0 ? value : value.substring(0, hash));
            Shape shape = hash < 0 ? null : Shape.byId(value.substring(hash + 1));
            if (shape == null) shape = shapeOfClass(block);
            if (material == null || shape == null || shape == Shape.FULL) {
                SlateBuilding.LOGGER.warn("[Slate Building] variantOverrides: '{}' -> '{}' is not '<material id>#<shape id>'", key, value);
                return;
            }
            claim(claims, block, material, shape, CLAIM_OVERRIDE);
        });
    }

    private static void claimFamilies(final Map<Block, Claim> claims) {
        BlockFamilies.getAllFamilies().forEach(family -> {
            final Block base = family.getBaseBlock();
            family.getVariants().forEach((variant, block) -> {
                final Shape shape = familyShape(variant);
                if (shape != null) claim(claims, block, base, shape, CLAIM_FAMILY);
            });
        });
    }

    private static @Nullable Shape familyShape(final BlockFamily.Variant variant) {
        return switch (variant) {
            case STAIRS -> Shape.STAIRS;
            case SLAB -> Shape.SLAB;
            case WALL -> Shape.WALL;
            case FENCE, CUSTOM_FENCE -> Shape.FENCE;
            case FENCE_GATE, CUSTOM_FENCE_GATE -> Shape.FENCE_GATE;
            default -> null;
        };
    }

    private static void claimStairs(final Map<Block, Claim> claims) {
        for (final Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof StairBlock) || block instanceof ShapeBlock) continue;
            final BlockState base = ((StairBlockAccessor) block).slateBuilding$baseState();
            if (base != null && !base.isAir()) {
                claim(claims, block, base.getBlock(), Shape.STAIRS, CLAIM_STAIRS);
                continue;
            }
            // Create 6's copper stairs (CreateCopperStairBlock / CreateWeatheringCopperStairBlock) pass AIR as their base
            // state: fall back to the name, like slabs do ("copper_shingle_stairs" -> "copper_shingles").
            final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getPath().endsWith("_stairs")) continue;
            final Block material = nameCandidate(id.getNamespace(), id.getPath().substring(0, id.getPath().length() - "_stairs".length()));
            if (material != null) claim(claims, block, material, Shape.STAIRS, CLAIM_NAME);
        }
    }

    private static void claimByName(final Map<Block, Claim> claims) {
        for (final Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof ShapeBlock) continue;
            final Shape shape = shapeOfClass(block);
            if (shape == null || shape == Shape.STAIRS) continue;
            final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            final String prefix = stripSuffix(id.getPath(), shape);
            if (prefix == null || prefix.isEmpty()) continue;
            // The sibling stairs know their material exactly.
            final Block stairs = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), prefix + "_stairs")).orElse(null);
            if (stairs != null) {
                final Claim sibling = claims.get(stairs);
                if (sibling != null && sibling.shape == Shape.STAIRS) {
                    claim(claims, block, sibling.material, shape, CLAIM_SIBLING);
                    continue;
                }
            }
            final Block material = nameCandidate(id.getNamespace(), prefix);
            if (material != null) claim(claims, block, material, shape, CLAIM_NAME);
        }
    }

    private static @Nullable Block nameCandidate(final String namespace, final String prefix) {
        for (final String ns : namespace.equals(ResourceLocation.DEFAULT_NAMESPACE) ? new String[] {namespace} : new String[] {namespace, ResourceLocation.DEFAULT_NAMESPACE}) {
            for (final String suffix : NAME_SUFFIXES) {
                final ResourceLocation candidate = ResourceLocation.tryBuild(ns, prefix + suffix);
                if (candidate == null) continue;
                final Block b = BuiltInRegistries.BLOCK.getOptional(candidate).orElse(null);
                if (b != null && b != Blocks.AIR && shapeOfClass(b) == null && !(b instanceof ShapeBlock)) return b;
            }
        }
        return null;
    }

    private static @Nullable String stripSuffix(final String path, final Shape shape) {
        final String suffix = switch (shape) {
            case SLAB -> "_slab";
            case WALL -> "_wall";
            case FENCE -> "_fence";
            case FENCE_GATE -> "_fence_gate";
            default -> null;
        };
        if (suffix == null || !path.endsWith(suffix)) return null;
        return path.substring(0, path.length() - suffix.length());
    }

    /** The native shape a block's class implies (gates before fences: both are "fence" by name). */
    static @Nullable Shape shapeOfClass(final Block block) {
        if (block instanceof StairBlock) return Shape.STAIRS;
        if (block instanceof SlabBlock) return Shape.SLAB;
        if (block instanceof WallBlock) return Shape.WALL;
        if (block instanceof FenceGateBlock) return Shape.FENCE_GATE;
        if (block instanceof FenceBlock) return Shape.FENCE;
        return null;
    }

    // ------------------------------------------------------------------------------------------------ materials

    /**
     * Material rule (design §2): full-cube collision and outline, rendered as a model, has its own block item, no
     * block entity, not one of our shapes, not denied (tag {@code #slate_building:not_material} or the server
     * denylist); OR forced by {@code #slate_building:material} / the allowlist (still needs an item).
     */
    private static boolean eligible(final Block block, final Rules r) {
        if (block instanceof ShapeBlock || block == Blocks.AIR) return false;
        if (r.deny.contains(block)) return false;
        final BlockState state = block.defaultBlockState();
        for (final TagKey<Block> tag : r.denyTags) if (state.is(tag)) return false;
        if (r.allow.contains(block) || state.is(BuildingTags.MATERIAL) || anyTag(state, r.allowTags)) return block.asItem() != Items.AIR;
        if (state.is(BuildingTags.NOT_MATERIAL) || block instanceof EntityBlock) return false;
        if (state.getRenderShape() != RenderShape.MODEL) return false;
        if (!(block.asItem() instanceof BlockItem item) || item.getBlock() != block) return false;
        if (shapeOfClass(block) != null) return false;
        try {
            return Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
                && Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        } catch (final RuntimeException e) {
            return false;   // shapes that need a real level
        }
    }

    private static boolean anyTag(final BlockState state, final List<TagKey<Block>> tags) {
        for (final TagKey<Block> tag : tags) if (state.is(tag)) return true;
        return false;
    }

    private static Rules parseRules(final ServerVariants rules) {
        final Set<Block> deny = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Block> allow = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Block> ignored = Collections.newSetFromMap(new IdentityHashMap<>());
        final List<TagKey<Block>> denyTags = new ArrayList<>();
        final List<TagKey<Block>> allowTags = new ArrayList<>();
        parseList(rules.materialDenylist, deny, denyTags, "materialDenylist");
        parseList(rules.materialAllowlist, allow, allowTags, "materialAllowlist");
        parseList(rules.ignoredVariants, ignored, null, "ignoredVariants");
        return new Rules(deny, denyTags, allow, allowTags, ignored);
    }

    private static void parseList(final @Nullable List<String> ids, final Set<Block> out, final @Nullable List<TagKey<Block>> tags, final String field) {
        if (ids == null) return;
        for (final String raw : ids) {
            if (raw == null || raw.isBlank()) continue;
            final String id = raw.trim().toLowerCase(Locale.ROOT);
            if (id.startsWith("#")) {
                final ResourceLocation tag = ResourceLocation.tryParse(id.substring(1));
                if (tag != null && tags != null) tags.add(TagKey.create(Registries.BLOCK, tag));
                else SlateBuilding.LOGGER.warn("[Slate Building] {}: tags are not supported here ('{}')", field, raw);
                continue;
            }
            final Block block = block(id);
            if (block != null) out.add(block);
            else SlateBuilding.LOGGER.warn("[Slate Building] {}: unknown block '{}'", field, raw);
        }
    }

    private static @Nullable Block block(final String id) {
        final ResourceLocation rl = ResourceLocation.tryParse(id.trim().toLowerCase(Locale.ROOT));
        if (rl == null) return null;
        final Block b = BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
        return b == Blocks.AIR ? null : b;
    }

    /** The last {@link #fingerprint} computed, keyed by the identity of the four rule collections it hashed. */
    private record CollectionsHash(Object deny, Object allow, Object overrides, Object ignored, int hash) {
        boolean covers(final ServerVariants rules) {
            return deny == rules.materialDenylist && allow == rules.materialAllowlist
                && overrides == rules.variantOverrides && ignored == rules.ignoredVariants;
        }
    }

    private static volatile @Nullable CollectionsHash lastCollections;

    /**
     * Hash of the rule COLLECTIONS (deny/allow lists, overrides, ignored variants). Content-based, so a re-synced but
     * identical config reuses the index; memoised by identity, because these collections are only ever replaced
     * wholesale (a file load, a server sync), never edited in place, so an unchanged object has an unchanged hash. The
     * three booleans are NOT in here: the settings screen flips them in place, so {@link #matches} compares them on
     * every call.
     */
    static int fingerprint(final ServerVariants rules) {
        CollectionsHash h = lastCollections;
        if (h == null || !h.covers(rules)) {
            lastCollections = h = new CollectionsHash(rules.materialDenylist, rules.materialAllowlist, rules.variantOverrides,
                rules.ignoredVariants, java.util.Objects.hash(rules.materialDenylist, rules.materialAllowlist, rules.variantOverrides, rules.ignoredVariants));
        }
        return h.hash();
    }

    /** Whether this snapshot is still the answer for {@code rules} at registry/tag {@code generation}. */
    boolean matches(final ServerVariants rules, final int generation) {
        return this.generation == generation && unify == rules.unify && customShapes == rules.customShapes
            && deleteNatives == rules.deleteNativeVariants && fingerprint == fingerprint(rules);
    }

    /** Debug helper: every identified native, for logs. */
    Map<Block, Variant> nativesView() {
        return Collections.unmodifiableMap(new HashMap<>(natives));
    }
}
