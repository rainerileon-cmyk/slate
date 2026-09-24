package dev.fallingcloud.slate.building.client.model;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Item model resolution for the shape items (design §6 "Items"): the loader glue wraps each shape item's baked model
 * with an {@code ItemOverrides} whose {@code resolve} lands here. A stack with a material gets a cached
 * {@link ShapeItemModel} of that material; a stack without one gets the same shape cut from the "no material"
 * placeholder cube, so even an unset item shows what shape it is.
 */
public final class ShapeItemModels {

    /** Keyed by the base model too: a resource reload bakes new base models, so nothing built from the old atlas is reused. */
    private record Key(BakedModel base, Block shape, @Nullable Block material) {}

    private static final Map<Key, BakedModel> CACHE = new ConcurrentHashMap<>();

    /**
     * The model to draw {@code stack} with.
     *
     * @param base the shape item's own baked model (its JSON: display transforms + the placeholder cube)
     */
    public static BakedModel resolve(final BakedModel base, final ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof ShapeBlock)) return base;
        final Key key = new Key(base, item.getBlock(), ShapeModels.materialOf(stack));
        final BakedModel hit = CACHE.get(key);
        if (hit != null) return hit;
        final BakedModel built = build(base, key);
        CACHE.put(key, built);
        return built;
    }

    private static BakedModel build(final BakedModel base, final Key key) {
        final BlockState shape = ShapeModels.displayState(key.shape());
        try {
            if (key.material() == null) return new ShapeItemModel(shape, ShapeModels.UNSET, null, base, base);
            final BlockState material = key.material().defaultBlockState();
            return new ShapeItemModel(shape, material, material, base, ShapeModels.modelOf(material));
        } catch (final RuntimeException e) {
            // A broken material model must not take the inventory screen down: show the placeholder instead.
            SlateBuilding.LOGGER.warn("[Slate Building] could not build the item model of {} in {}", key.shape(), key.material(), e);
            return base;
        }
    }

    /** Drops every cached item model (resource reload, geometry changes). */
    public static void clear() {
        CACHE.clear();
        ShapeModels.clear();
    }

    private ShapeItemModels() {}
}
