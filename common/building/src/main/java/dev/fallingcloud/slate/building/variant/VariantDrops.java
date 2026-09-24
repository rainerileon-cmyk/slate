package dev.fallingcloud.slate.building.variant;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Drops of NATIVE variants (design §1): whatever the block's loot table produced that is a variant item of the same
 * material (oak_stairs x1, oak_slab x2 from a double slab, the re-pointed fence item of a DiagonalFences twin)
 * becomes that many of the material's own item. Harvest rules stay vanilla's: a stone stair mined by hand yields no
 * loot to rewrite. Anything else in the loot (a mod's bonus drop) is left alone. Slate Building's own shapes compute
 * their drops themselves.
 */
public final class VariantDrops {

    private VariantDrops() {}

    /** The rewritten drops of {@code state}, or null to keep {@code drops} as they are. */
    public static @Nullable List<ItemStack> rewriteNative(final BlockState state, final @Nullable List<ItemStack> drops) {
        if (drops == null || drops.isEmpty() || state.getBlock() instanceof ShapeBlock) return null;
        final VariantRegistry registry = VariantRegistry.get();
        final Variant variant = registry.nativeVariant(state.getBlock());
        if (variant == null) return null;
        final Item materialItem = variant.material().asItem();
        if (materialItem == Items.AIR) return null;
        List<ItemStack> out = null;
        for (int i = 0; i < drops.size(); i++) {
            final ItemStack drop = drops.get(i);
            final Optional<Variant> v = registry.identify(drop);
            if (v.isEmpty() || v.get().isFull() || v.get().material() != variant.material()) continue;
            if (out == null) out = new ArrayList<>(drops);
            out.set(i, new ItemStack(materialItem, drop.getCount()));
        }
        return out;
    }
}
