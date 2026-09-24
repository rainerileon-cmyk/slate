package dev.fallingcloud.slate.building.variant;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.ServerVariants;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps recipes that make NATIVE variants from being dupes (design §1). A variant breaks back into one material unit,
 * so a recipe may not make more variants than the material units it consumes: 3 planks → 6 slabs becomes 3 → 3,
 * 6 planks → 4 stairs becomes 6 → 6, a fence gate from 2 planks + 4 sticks makes 2. Runs on the recipe JSON before
 * parsing (every loader, every mod's recipe format that uses the usual keys), server side.
 *
 * <p>Units consumed = the ingredient slots that are the material (anything that identifies as a variant: the full
 * block or any shape of it); if no slot names an item directly (tag ingredients), every slot that is not a
 * rod/stick. Stonecutting always consumes 1. Recipes whose units cannot be told are left alone.
 * {@code deleteNativeVariants} removes the recipes (or just their native outputs, for multi-output recipes) instead.
 * Only recipes that MAKE natives go: recipes that consume them stay craftable, because
 * {@link VariantRegistry#stackFor} keeps handing out the native item (swap wheel, build menu, chisel, pick-block).
 */
public final class RecipeRebalancer {

    private static final int EXAMPLES = 6;

    private enum Slot { ROD, MATERIAL, UNKNOWN, OTHER }

    private RecipeRebalancer() {}

    /** The recipe map with native-variant recipes rebalanced or removed (the input map is not modified). */
    public static Map<ResourceLocation, JsonElement> process(final Map<ResourceLocation, JsonElement> recipes) {
        final ServerVariants rules = BuildingServerSettings.local().variants();
        if (rules == null || !rules.unify || (!rules.rebalanceRecipes && !rules.deleteNativeVariants)) return recipes;
        final VariantRegistry registry = VariantRegistry.get();
        final Set<Item> natives = registry.nativeVariantItems();
        if (natives.isEmpty()) return recipes;

        final Map<ResourceLocation, JsonElement> out = new LinkedHashMap<>(recipes.size());
        final List<String> examples = new ArrayList<>();
        int rebalanced = 0;
        int removed = 0;
        for (final Map.Entry<ResourceLocation, JsonElement> entry : recipes.entrySet()) {
            final JsonElement json = entry.getValue();
            if (!(json instanceof JsonObject recipe) || !producesNative(recipe, natives)) {
                out.put(entry.getKey(), json);
                continue;
            }
            try {
                if (rules.deleteNativeVariants) {
                    final JsonObject kept = withoutNativeResults(recipe, natives);
                    if (kept == null) {
                        removed++;
                        SlateBuilding.LOGGER.debug("[Slate Building] removed native-variant recipe {}", entry.getKey());
                    } else {
                        out.put(entry.getKey(), kept);
                    }
                    continue;
                }
                final int units = consumedUnits(recipe, registry);
                if (units <= 0) {
                    out.put(entry.getKey(), json);
                    continue;
                }
                final JsonObject copy = recipe.deepCopy();
                final int before = setNativeResultCounts(copy, natives, units);
                if (before >= 0 && before != units) {
                    rebalanced++;
                    if (examples.size() < EXAMPLES) examples.add(entry.getKey() + " " + before + "→" + units);
                    SlateBuilding.LOGGER.debug("[Slate Building] rebalanced {}: {} → {}", entry.getKey(), before, units);
                    out.put(entry.getKey(), copy);
                } else {
                    out.put(entry.getKey(), json);
                }
            } catch (final RuntimeException e) {
                SlateBuilding.LOGGER.warn("[Slate Building] could not rebalance recipe {}: {}", entry.getKey(), e.toString());
                out.put(entry.getKey(), json);
            }
        }
        if (rebalanced > 0 || removed > 0) {
            SlateBuilding.LOGGER.info("[Slate Building] recipes: rebalanced {} native-variant recipes to one unit per variant{}, removed {}",
                rebalanced, examples.isEmpty() ? "" : " (" + String.join(", ", examples) + (rebalanced > examples.size() ? ", ..." : "") + ")", removed);
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ results

    private static boolean producesNative(final JsonObject recipe, final Set<Item> natives) {
        if (recipe.has("result") && natives.contains(resultItem(recipe.get("result")))) return true;
        if (recipe.get("results") instanceof JsonArray results) {
            for (final JsonElement r : results) if (natives.contains(resultItem(r))) return true;
        }
        return false;
    }

    /** The item a result entry names: {@code {"id"|"item": ...}} or a bare id string. */
    private static @Nullable Item resultItem(final @Nullable JsonElement result) {
        String id = null;
        if (result instanceof JsonPrimitive p && p.isString()) id = p.getAsString();
        else if (result instanceof JsonObject o) {
            if (o.get("id") instanceof JsonPrimitive p && p.isString()) id = p.getAsString();
            else if (o.get("item") instanceof JsonPrimitive p && p.isString()) id = p.getAsString();
        }
        if (id == null) return null;
        final ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        final Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        return item == Items.AIR ? null : item;
    }

    /** Sets every native result's count to {@code units}; returns the first previous count (-1 when none). */
    private static int setNativeResultCounts(final JsonObject recipe, final Set<Item> natives, final int units) {
        int before = -1;
        final JsonElement result = recipe.get("result");
        if (result != null && natives.contains(resultItem(result))) {
            if (result instanceof JsonObject o) {
                before = count(o, 1);
                o.addProperty("count", units);
            } else {
                before = count(recipe, 1);   // legacy: "result": "id", "count": n
                recipe.addProperty("count", units);
            }
        }
        if (recipe.get("results") instanceof JsonArray results) {
            for (final JsonElement r : results) {
                if (r instanceof JsonObject o && natives.contains(resultItem(o))) {
                    if (before < 0) before = count(o, 1);
                    o.addProperty("count", units);
                }
            }
        }
        return before;
    }

    /** The recipe without its native outputs; null when nothing else is left (the recipe goes). */
    private static @Nullable JsonObject withoutNativeResults(final JsonObject recipe, final Set<Item> natives) {
        if (recipe.has("result")) return null;
        if (!(recipe.get("results") instanceof JsonArray results)) return null;
        final JsonArray kept = new JsonArray();
        for (final JsonElement r : results) if (!natives.contains(resultItem(r))) kept.add(r);
        if (kept.isEmpty()) return null;
        final JsonObject copy = recipe.deepCopy();
        copy.add("results", kept);
        return copy;
    }

    // ------------------------------------------------------------------------------------------------ ingredients

    /** Material units a recipe consumes (see the class comment); 0 when it cannot be told. */
    static int consumedUnits(final JsonObject recipe, final VariantRegistry registry) {
        final String type = recipe.get("type") instanceof JsonPrimitive p && p.isString() ? p.getAsString() : "";
        if (type.equals("minecraft:stonecutting")) return 1;
        final List<JsonElement> slots = new ArrayList<>();
        int unknown = 0;
        if (recipe.get("pattern") instanceof JsonArray pattern && recipe.get("key") instanceof JsonObject key) {
            for (final JsonElement row : pattern) {
                if (!(row instanceof JsonPrimitive p) || !p.isString()) continue;
                for (final char c : p.getAsString().toCharArray()) {
                    if (c == ' ') continue;
                    final JsonElement ingredient = key.get(String.valueOf(c));
                    if (ingredient != null) slots.add(ingredient);
                    else unknown++;
                }
            }
        } else if (recipe.get("ingredients") instanceof JsonArray ingredients) {
            for (final JsonElement e : ingredients) slots.add(e);
        } else if (recipe.has("ingredient")) {
            slots.add(recipe.get("ingredient"));
        } else {
            return 0;
        }
        int material = 0;
        for (final JsonElement slot : slots) {
            switch (classify(slot, registry)) {
                case MATERIAL -> material += slotCount(slot);
                case UNKNOWN -> unknown += slotCount(slot);
                default -> { }
            }
        }
        return material > 0 ? material : unknown;
    }

    private static Slot classify(final JsonElement ingredient, final VariantRegistry registry) {
        if (ingredient instanceof JsonArray alternatives) {
            boolean allRods = !alternatives.isEmpty();
            boolean anyUnknown = false;
            for (final JsonElement e : alternatives) {
                final Slot s = classify(e, registry);
                if (s == Slot.MATERIAL) return Slot.MATERIAL;
                if (s != Slot.ROD) allRods = false;
                if (s == Slot.UNKNOWN) anyUnknown = true;
            }
            return allRods ? Slot.ROD : anyUnknown ? Slot.UNKNOWN : Slot.OTHER;
        }
        if (!(ingredient instanceof JsonObject o)) return Slot.UNKNOWN;
        if (o.get("item") instanceof JsonPrimitive p && p.isString()) {
            final ResourceLocation id = ResourceLocation.tryParse(p.getAsString());
            if (id == null) return Slot.UNKNOWN;
            if (isRod(id)) return Slot.ROD;
            final Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null || item == Items.AIR) return Slot.UNKNOWN;
            return registry.identify(new ItemStack(item)).isPresent() ? Slot.MATERIAL : Slot.OTHER;
        }
        if (o.get("tag") instanceof JsonPrimitive p && p.isString()) {
            final ResourceLocation id = ResourceLocation.tryParse(p.getAsString());
            return id != null && isRod(id) ? Slot.ROD : Slot.UNKNOWN;
        }
        return Slot.UNKNOWN;   // custom ingredient types
    }

    private static boolean isRod(final ResourceLocation id) {
        final String path = id.getPath();
        return path.contains("stick") || path.contains("rod");
    }

    private static int slotCount(final JsonElement slot) {
        return slot instanceof JsonObject o ? Math.max(1, count(o, 1)) : 1;
    }

    private static int count(final JsonObject o, final int fallback) {
        return o.get("count") instanceof JsonPrimitive p && p.isNumber() ? p.getAsInt() : fallback;
    }
}
