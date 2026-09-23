package dev.fallingcloud.slate.building.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code building-server.json → variants} (design §1, §2).
 *
 * <p>Owner: A (variants). Skeleton declares the fields and defaults of design §10.
 */
public final class ServerVariants {

    /** Treat native variants (oak_stairs, ...) and our shapes of the same material as one family. */
    public boolean unify = true;
    /** Rewrite recipes that make native variants at better than 1:1 so each variant costs one material unit. */
    public boolean rebalanceRecipes = true;
    /** Hide and remove native variant blocks/items (world blocks and recipes that use them keep working). */
    public boolean deleteNativeVariants = false;
    /** Offer Slate Building's own shapes for materials that lack a native one. */
    public boolean customShapes = true;
    /** Block ids that are never materials. */
    public List<String> materialDenylist = new ArrayList<>();
    /** Block ids that are always materials (even if the automatic rules reject them). */
    public List<String> materialAllowlist = new ArrayList<>();
    /** Manual native-variant mapping: {@code "mod:weird_stairs": "mod:material#stairs"} (block id → material id + shape id). */
    public Map<String, String> variantOverrides = new LinkedHashMap<>();
    /** Native blocks that must not be treated as variants. */
    public List<String> ignoredVariants = new ArrayList<>();
    /** Whether swapping a held stack's shape needs a hammer in the toolbox. */
    public boolean swapNeedsTool = false;
}
