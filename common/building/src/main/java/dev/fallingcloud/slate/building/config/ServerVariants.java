package dev.fallingcloud.slate.building.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code building-server.json → variants} (design §1, §2). Read by {@code VariantRegistry} on both sides (clients use
 * the copy the server synced), which rebuilds itself as soon as any of these change; recipe changes
 * ({@link #rebalanceRecipes}, {@link #deleteNativeVariants}) apply at the next datapack load ({@code /reload}).
 *
 * <p>Owner: A (variants).
 */
public final class ServerVariants {

    /**
     * Treat native variants (oak_stairs, a DiagonalFences twin, ...) and Slate Building's shapes of the same material
     * as one family: natives drop their material, swap into other shapes and count as the material in building
     * operations. Off: natives are ordinary blocks and only Slate Building's own shapes are variants.
     */
    public boolean unify = true;
    /**
     * Rewrite recipes that make native variants at better than one material unit per item (3 planks → 6 slabs) so
     * each variant costs exactly one unit; otherwise crafting and breaking them would duplicate materials.
     */
    public boolean rebalanceRecipes = true;
    /**
     * Hide native variant items (creative tabs, search, JEI) and remove the recipes that make them; the swap wheel
     * hands out Slate Building's shapes instead. Native blocks already in worlds keep working and still drop their
     * material.
     */
    public boolean deleteNativeVariants = false;
    /** Offer Slate Building's own shapes for materials that lack a native one (e.g. dirt stairs, glass steps). */
    public boolean customShapes = true;
    /** Blocks that are never materials: block ids ({@code "minecraft:tnt"}) or block tags ({@code "#c:ores"}). */
    public List<String> materialDenylist = new ArrayList<>();
    /**
     * Blocks that are always materials even if the automatic rules (full cube, own item, no block entity) reject
     * them: block ids or block tags ({@code "#mod:tag"}). They still need an item.
     */
    public List<String> materialAllowlist = new ArrayList<>();
    /**
     * Manual native-variant mapping, strongest rule of all: {@code "mod:weird_stairs": "mod:material#stairs"} (block
     * id → material block id, {@code #}, shape id). The shape may be left out for stairs/slabs/walls/fences/gates.
     */
    public Map<String, String> variantOverrides = new LinkedHashMap<>();
    /** Native blocks (ids) that must not be treated as variants even though the discovery rules match them. */
    public List<String> ignoredVariants = new ArrayList<>();
    /** Whether swapping a held stack's shape (swap wheel, build menu) needs a Hammer in the toolbox (creative never does). */
    public boolean swapNeedsTool = false;
}
