package dev.fallingcloud.slate.building.chisel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.fallingcloud.slate.building.SlateBuilding;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * The providers of chisel groups (design §9), each read straight from the data it lives in (never from a mod id,
 * since Chisel Modern and Chisel Reborn share the id {@code chisel}). Every provider yields its own groups, which
 * become their own wheel pages; the rules in {@link ChiselRules} run afterwards on all of them alike. Formats and
 * pitfalls: {@code docs/building-maps/chisel-compat.md}.
 *
 * <p>Order (= page order): the overrides file → Rechiseled → Chipped → Chisel Modern → Chisel Reborn → the stonecutter
 * graph → vanilla block families.
 */
final class ChiselSources {

    static final String OVERRIDES = "overrides";
    static final String RECHISELED = "rechiseled";
    static final String CHIPPED = "chipped";
    static final String CHISEL = "chisel";
    static final String STONECUTTER = "stonecutter";
    static final String FAMILIES = "families";

    private static final String KEY = "slate_building.chisel.source.";

    /** One group as a provider sees it, before the rules. {@code key} empty = {@code fallback} is literal text. */
    record Raw(String source, String key, String fallback, List<Block> members) {}

    private ChiselSources() {}

    // ------------------------------------------------------------------ overrides file

    static List<Raw> overrides(final ChiselOverrides file) {
        final List<ChiselOverrides.GroupSpec> specs = new ArrayList<>();
        if (file.includeDefaults) specs.addAll(ChiselOverrides.shippedDefaults());
        specs.addAll(file.groups);
        final List<Raw> out = new ArrayList<>();
        for (final ChiselOverrides.GroupSpec spec : specs) {
            if (spec == null || spec.members == null) continue;
            final List<Block> members = new ArrayList<>();
            for (final String id : spec.members) {
                final Block b = block(id);
                if (b != null) members.add(b);
            }
            final boolean named = spec.name != null && !spec.name.isBlank();
            final boolean keyed = spec.key != null && !spec.key.isBlank();
            if (keyed) out.add(new Raw(OVERRIDES, spec.key.trim(), named ? spec.name.trim() : "Custom", members));
            else if (named) out.add(new Raw(OVERRIDES, "", spec.name.trim(), members));
            else out.add(new Raw(OVERRIDES, KEY + OVERRIDES, "Custom", members));
        }
        return out;
    }

    // ------------------------------------------------------------------ Rechiseled

    /**
     * {@code data/<ns>/chiseling_recipes/<path>.json}. Files at the same path concatenate across packs; one with
     * {@code "overwrite": true} drops what came before. Entries: a bare id, or an object with {@code block}
     * (current grammar) or {@code item} (older jars) plus optional {@code connecting_block} / {@code connecting_item}.
     * The connecting variant (connected textures) is its own block worth the same, so it joins the group right after
     * its base; stairs/slab siblings are shapes, which the variant wheel handles. Unknown ids are skipped.
     */
    static List<Raw> rechiseled(final ResourceManager resources) {
        final Map<ResourceLocation, List<Resource>> stacks =
            resources.listResourceStacks("chiseling_recipes", id -> id.getPath().endsWith(".json"));
        if (stacks.isEmpty()) return List.of();
        final List<Raw> out = new ArrayList<>();
        for (final Map.Entry<ResourceLocation, List<Resource>> e : new TreeMap<>(stacks).entrySet()) {
            final List<Block> members = new ArrayList<>();
            for (final Resource res : e.getValue()) {
                try (Reader reader = res.openAsReader()) {
                    final JsonElement root = JsonParser.parseReader(reader);
                    if (!root.isJsonObject()) continue;
                    final JsonObject obj = root.getAsJsonObject();
                    if (obj.has("overwrite") && obj.get("overwrite").isJsonPrimitive() && obj.get("overwrite").getAsBoolean()) members.clear();
                    final JsonElement entries = obj.get("entries");
                    if (entries == null || !entries.isJsonArray()) continue;
                    for (final JsonElement entry : (JsonArray) entries) readRechiseledEntry(entry, members);
                } catch (final Exception ex) {
                    SlateBuilding.LOGGER.warn("[Slate Building] skipping unreadable Rechiseled group {} in {}: {}", e.getKey(), res.sourcePackId(), ex.toString());
                }
            }
            out.add(new Raw(RECHISELED, KEY + RECHISELED, "Rechiseled", members));
        }
        return out;
    }

    private static void readRechiseledEntry(final JsonElement entry, final List<Block> into) {
        if (entry.isJsonPrimitive()) {
            final Block b = block(entry.getAsString());
            if (b != null) into.add(b);
            return;
        }
        if (!entry.isJsonObject()) return;
        final JsonObject o = entry.getAsJsonObject();
        final Block base = firstBlock(o, "block", "item");
        final Block connecting = firstBlock(o, "connecting_block", "connecting_item");
        if (base != null) into.add(base);
        if (connecting != null) into.add(connecting);
    }

    private static @Nullable Block firstBlock(final JsonObject o, final String... keys) {
        for (final String k : keys) {
            final JsonElement v = o.get(k);
            if (v != null && v.isJsonPrimitive()) {
                final Block b = block(v.getAsString());
                if (b != null) return b;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ Chipped

    /**
     * Chipped groups are item tags {@code chipped:<base>}, listed by the {@code chipped:workbench} recipes. The recipe
     * record does not override {@code getIngredients()}, so its {@code ingredients()} accessor is read reflectively
     * (records keep their accessor names on both loaders); when that fails, every {@code chipped:*} item tag is used.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static List<Raw> chipped(final MinecraftServer server) {
        final List<Raw> out = new ArrayList<>();
        final RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.fromNamespaceAndPath("chipped", "workbench"));
        if (type != null) {
            final List<RecipeHolder<?>> recipes = new ArrayList<>((List) server.getRecipeManager().getAllRecipesFor((RecipeType) type));
            recipes.sort(Comparator.comparing(h -> h.id().toString()));
            for (final RecipeHolder<?> holder : recipes) {
                final Object recipe = holder.value();
                try {
                    final Method accessor = recipe.getClass().getMethod("ingredients");
                    final Object parts = accessor.invoke(recipe);
                    if (!(parts instanceof Iterable<?> iterable)) continue;
                    for (final Object part : iterable) out.add(new Raw(CHIPPED, KEY + CHIPPED, "Chipped", blocksOf(part)));
                } catch (final ReflectiveOperationException | RuntimeException e) {
                    SlateBuilding.LOGGER.debug("[Slate Building] Chipped recipe {} unreadable: {}", holder.id(), e.toString());
                }
            }
        }
        if (out.isEmpty()) out.addAll(itemTagGroups("chipped", "", CHIPPED, "Chipped"));
        return out;
    }

    // ------------------------------------------------------------------ Chisel (Modern / Reborn)

    /** Chisel Modern: item tags {@code chisel:carving/<name>} (KubeJS additions are invisible to data, like to us). */
    static List<Raw> chiselModern() {
        return itemTagGroups("chisel", "carving/", CHISEL, "Chisel");
    }

    /**
     * Chisel Reborn keeps its groups in code ({@code com.periut.chisel.block.ChiselGroupLookup}); ask it per item
     * reflectively (the parameter is vanilla {@code Item}, the name is the mod's own, so this works on both loaders).
     * If the lookup is missing, fall back to its id scheme: {@code chisel:<pattern>/<material>} + {@code minecraft:<material>}.
     */
    static List<Raw> chiselReborn() {
        final Class<?> lookup;
        try {
            lookup = Class.forName("com.periut.chisel.block.ChiselGroupLookup");
        } catch (final ClassNotFoundException | LinkageError e) {
            return List.of();
        }
        final List<Item> chiselItems = new ArrayList<>();
        for (final Item item : BuiltInRegistries.ITEM) {
            if ("chisel".equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) chiselItems.add(item);
        }
        chiselItems.sort(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
        final Map<Set<Block>, List<Block>> groups = new LinkedHashMap<>();
        try {
            final Method m = lookup.getMethod("getBlocksInGroup", Item.class);
            for (final Item item : chiselItems) {
                final Object result = m.invoke(null, item);
                if (!(result instanceof Iterable<?> list)) continue;
                final List<Block> members = new ArrayList<>();
                for (final Object o : list) {
                    if (o instanceof Item i && i instanceof BlockItem bi) members.add(bi.getBlock());
                    else if (o instanceof Block b) members.add(b);
                }
                if (members.size() >= 2) groups.putIfAbsent(ChiselRules.asSet(members), members);
            }
        } catch (final ReflectiveOperationException | RuntimeException e) {
            SlateBuilding.LOGGER.info("[Slate Building] Chisel Reborn lookup unavailable ({}), grouping its blocks by name", e.toString());
            groups.clear();
            final Map<String, List<Block>> byMaterial = new TreeMap<>();
            for (final Item item : chiselItems) {
                if (!(item instanceof BlockItem bi)) continue;
                final String path = BuiltInRegistries.ITEM.getKey(item).getPath();
                final int slash = path.lastIndexOf('/');
                if (slash < 0) continue;
                byMaterial.computeIfAbsent(path.substring(slash + 1), k -> {
                    final List<Block> l = new ArrayList<>();
                    final Block vanilla = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.withDefaultNamespace(k)).orElse(null);
                    if (vanilla != null) l.add(vanilla);
                    return l;
                }).add(bi.getBlock());
            }
            for (final List<Block> members : byMaterial.values()) groups.putIfAbsent(ChiselRules.asSet(members), members);
        }
        final List<Raw> out = new ArrayList<>();
        for (final List<Block> members : groups.values()) out.add(new Raw(CHISEL, KEY + CHISEL, "Chisel", members));
        return out;
    }

    /** Whether Chisel Modern's tags exist (then Chisel Reborn, which shares the mod id, is not installed). */
    static boolean hasChiselModernTags() {
        return BuiltInRegistries.ITEM.getTagNames().anyMatch(t -> "chisel".equals(t.location().getNamespace()) && t.location().getPath().startsWith("carving/"));
    }

    // ------------------------------------------------------------------ stonecutter graph

    /**
     * Undirected components of the one-for-one stonecutter recipes between full blocks. Tag ingredients are expanded
     * (Create's palettes cut from {@code create:stone_types/*}, which also lists stairs and walls: those fail
     * {@code allowed} and are dropped). Every vanilla edge is one-way; treating them as undirected is deliberate
     * (each is 1:1, so going back creates nothing), as Rechiseled does.
     */
    static List<Raw> stonecutter(final RecipeManager recipes, final HolderLookup.Provider registries, final Predicate<Block> allowed) {
        final Graph graph = new Graph();
        for (final RecipeHolder<StonecutterRecipe> holder : recipes.getAllRecipesFor(RecipeType.STONECUTTING)) {
            try {
                final StonecutterRecipe recipe = holder.value();
                final ItemStack result = recipe.getResultItem(registries);
                if (result.getCount() != 1) continue;
                final Block out = blockOf(result);
                if (out == null || !allowed.test(out)) continue;
                for (final Ingredient ingredient : recipe.getIngredients()) {
                    for (final ItemStack in : ingredient.getItems()) {
                        final Block b = blockOf(in);
                        if (b != null && b != out && allowed.test(b)) graph.edge(b, out);
                    }
                }
            } catch (final RuntimeException e) {
                SlateBuilding.LOGGER.debug("[Slate Building] stonecutter recipe {} unreadable: {}", holder.id(), e.toString());
            }
        }
        final List<Raw> out = new ArrayList<>();
        for (final List<Block> c : graph.components()) out.add(new Raw(STONECUTTER, KEY + STONECUTTER, "Stonecutter", c));
        return out;
    }

    // ------------------------------------------------------------------ block families

    private static final Set<BlockFamily.Variant> TEXTURE_VARIANTS = EnumSet.of(
        BlockFamily.Variant.CHISELED, BlockFamily.Variant.CRACKED, BlockFamily.Variant.CUT,
        BlockFamily.Variant.MOSAIC, BlockFamily.Variant.POLISHED);

    /**
     * Vanilla {@code BlockFamilies}: base → chiseled / cracked / cut / mosaic / polished, chained across families
     * (cobbled deepslate → polished → bricks → tiles). Adds the smelted and crafted links the stonecutter lacks
     * (cracked bricks, bamboo mosaic). The copper block → cut copper link is 1:4 and falls to the rules.
     */
    static List<Raw> families(final Predicate<Block> allowed) {
        final Graph graph = new Graph();
        BlockFamilies.getAllFamilies().forEach(family -> {
            final Block base = family.getBaseBlock();
            if (!allowed.test(base)) return;
            for (final Map.Entry<BlockFamily.Variant, Block> v : family.getVariants().entrySet()) {
                if (TEXTURE_VARIANTS.contains(v.getKey()) && allowed.test(v.getValue())) graph.edge(base, v.getValue());
            }
        });
        final List<Raw> out = new ArrayList<>();
        for (final List<Block> c : graph.components()) out.add(new Raw(FAMILIES, KEY + FAMILIES, "Block family", c));
        return out;
    }

    // ------------------------------------------------------------------ 1:N links

    /**
     * Every recipe link between two blocks that is not one-for-one: stonecutting with a result count ≠ 1, and
     * crafting where the result count differs from the number of slots holding that ingredient (4 copper blocks → 4
     * cut copper is fine, 2 slabs → 1 chiseled block or 1 block → 4 grates is not).
     */
    static ChiselRules.BadPairs badPairs(final RecipeManager recipes, final HolderLookup.Provider registries) {
        final ChiselRules.BadPairs bad = new ChiselRules.BadPairs();
        for (final RecipeHolder<StonecutterRecipe> holder : recipes.getAllRecipesFor(RecipeType.STONECUTTING)) {
            try {
                final ItemStack result = holder.value().getResultItem(registries);
                final Block out = blockOf(result);
                if (out == null || result.getCount() == 1) continue;
                for (final Ingredient ingredient : holder.value().getIngredients()) {
                    for (final ItemStack in : ingredient.getItems()) {
                        final Block b = blockOf(in);
                        if (b != null) bad.add(b, out);
                    }
                }
            } catch (final RuntimeException ignored) {
                // an unreadable recipe links nothing
            }
        }
        for (final RecipeHolder<CraftingRecipe> holder : recipes.getAllRecipesFor(RecipeType.CRAFTING)) {
            try {
                addCraftingPairs(holder.value(), registries, bad);
            } catch (final RuntimeException ignored) {
                // special / dynamic recipes (and broken modded ones) have no fixed ingredients
            }
        }
        return bad;
    }

    private static void addCraftingPairs(final Recipe<?> recipe, final HolderLookup.Provider registries, final ChiselRules.BadPairs bad) {
        if (recipe.isSpecial()) return;
        final ItemStack result = recipe.getResultItem(registries);
        final Block out = blockOf(result);
        if (out == null) return;
        final Map<Set<Item>, Integer> slots = new LinkedHashMap<>();
        for (final Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            final Set<Item> items = Collections.newSetFromMap(new IdentityHashMap<>());
            for (final ItemStack s : ingredient.getItems()) items.add(s.getItem());
            if (!items.isEmpty()) slots.merge(items, 1, Integer::sum);
        }
        for (final Map.Entry<Set<Item>, Integer> e : slots.entrySet()) {
            if (e.getValue() == result.getCount()) continue;
            for (final Item item : e.getKey()) {
                if (item instanceof BlockItem bi) bad.add(bi.getBlock(), out);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Groups from every item tag in {@code namespace} whose path starts with {@code prefix}, in tag-id order. */
    private static List<Raw> itemTagGroups(final String namespace, final String prefix, final String source, final String name) {
        final List<TagKey<Item>> tags = BuiltInRegistries.ITEM.getTagNames()
            .filter(t -> namespace.equals(t.location().getNamespace()) && t.location().getPath().startsWith(prefix))
            .sorted(Comparator.comparing(t -> t.location().toString()))
            .toList();
        final List<Raw> out = new ArrayList<>();
        for (final TagKey<Item> tag : tags) {
            final List<Block> members = new ArrayList<>();
            for (final Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                if (h.value() instanceof BlockItem bi) members.add(bi.getBlock());
            }
            out.add(new Raw(source, KEY + source, name, members));
        }
        return out;
    }

    /** The blocks behind one Chipped ingredient: an {@code Ingredient}, a holder set or an item tag key. */
    @SuppressWarnings("unchecked")
    private static List<Block> blocksOf(final Object part) {
        final List<Block> out = new ArrayList<>();
        if (part instanceof Ingredient ingredient) {
            for (final ItemStack s : ingredient.getItems()) {
                final Block b = blockOf(s);
                if (b != null) out.add(b);
            }
        } else if (part instanceof HolderSet<?> set) {
            for (final Holder<?> h : set) {
                if (h.value() instanceof BlockItem bi) out.add(bi.getBlock());
                else if (h.value() instanceof Block b) out.add(b);
            }
        } else if (part instanceof TagKey<?> tag && tag.registry().equals(Registries.ITEM)) {
            for (final Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty((TagKey<Item>) tag)) {
                if (h.value() instanceof BlockItem bi) out.add(bi.getBlock());
            }
        }
        return out;
    }

    private static @Nullable Block blockOf(final ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi ? bi.getBlock() : null;
    }

    /** A block by id; an item id resolves to its block (Rechiseled's older {@code item} entries). */
    static @Nullable Block block(final @Nullable String id) {
        if (id == null) return null;
        final ResourceLocation rl = ResourceLocation.tryParse(id.trim());
        if (rl == null) return null;
        final Block b = BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
        if (b != null) return b;
        return BuiltInRegistries.ITEM.getOptional(rl).orElse(null) instanceof BlockItem bi ? bi.getBlock() : null;
    }

    /**
     * A directed graph of one-for-one links, read out as undirected components. Members are ordered for the wheel:
     * the component's roots first (blocks nothing is made from, i.e. the raw material), then breadth-first along the
     * recipe direction, registry order breaking ties: stone, stone bricks, chiseled stone bricks.
     */
    static final class Graph {

        private final Map<Block, Set<Block>> out = new IdentityHashMap<>();
        private final Map<Block, Set<Block>> in = new IdentityHashMap<>();

        void edge(final Block from, final Block to) {
            if (from == to) return;
            out.computeIfAbsent(from, k -> identitySet()).add(to);
            in.computeIfAbsent(to, k -> identitySet()).add(from);
            out.computeIfAbsent(to, k -> identitySet());
            in.computeIfAbsent(from, k -> identitySet());
        }

        List<List<Block>> components() {
            final List<Block> nodes = new ArrayList<>(out.keySet());
            nodes.sort(Comparator.comparingInt(ChiselRules::order));
            final Set<Block> seen = identitySet();
            final List<List<Block>> result = new ArrayList<>();
            for (final Block start : nodes) {
                if (seen.contains(start)) continue;
                final List<Block> component = new ArrayList<>();
                final Deque<Block> queue = new ArrayDeque<>();
                queue.add(start);
                seen.add(start);
                while (!queue.isEmpty()) {
                    final Block b = queue.poll();
                    component.add(b);
                    for (final Block n : neighbours(b)) if (seen.add(n)) queue.add(n);
                }
                if (component.size() >= 2) result.add(ordered(component));
            }
            return result;
        }

        private List<Block> neighbours(final Block b) {
            final List<Block> n = new ArrayList<>(out.getOrDefault(b, Set.of()));
            n.addAll(in.getOrDefault(b, Set.of()));
            return n;
        }

        private List<Block> ordered(final List<Block> component) {
            final Comparator<Block> byId = Comparator.comparingInt(ChiselRules::order);
            final List<Block> pending = new ArrayList<>(component);
            pending.sort(byId);
            final List<Block> roots = new ArrayList<>();
            for (final Block b : pending) if (in.getOrDefault(b, Set.of()).isEmpty()) roots.add(b);
            final Set<Block> placed = identitySet();
            final List<Block> order = new ArrayList<>();
            final Deque<Block> queue = new ArrayDeque<>(roots.isEmpty() ? List.of(pending.get(0)) : roots);
            placed.addAll(queue);
            while (order.size() < component.size()) {
                if (queue.isEmpty()) {
                    // A cycle nothing outside feeds: continue from its lowest id.
                    for (final Block b : pending) if (placed.add(b)) { queue.add(b); break; }
                }
                final Block b = queue.poll();
                order.add(b);
                final List<Block> next = new ArrayList<>(out.getOrDefault(b, Set.of()));
                next.sort(byId);
                for (final Block n : next) if (placed.add(n)) queue.add(n);
            }
            return order;
        }

        private static Set<Block> identitySet() {
            return Collections.newSetFromMap(new IdentityHashMap<>());
        }
    }
}
