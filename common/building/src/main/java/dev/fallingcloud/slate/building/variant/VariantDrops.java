package dev.fallingcloud.slate.building.variant;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Drops of NATIVE variants (design §1): whatever the block's loot table produced that is a variant item of the same
 * material (oak_stairs x1, oak_slab x2 from a double slab, the re-pointed fence item of a DiagonalFences twin)
 * becomes that many of the material's own item. Harvest rules stay vanilla's: a stone stair mined by hand yields no
 * loot to rewrite. Anything else in the loot (a mod's bonus drop) is left alone. Slate Building's own shapes compute
 * their drops themselves.
 *
 * <p>{@link #dropsOwnWorth} is the loot guard for changing a block in place (hammer reshape), the same rule the chisel
 * applies (design §9).
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

    /**
     * Whether {@code state} at {@code pos} drops exactly what it is worth in its own material with NO tool: only items of
     * {@code material} (the block itself or a variant of it) totalling {@code units}. Reshaping a block in place turns
     * it into something that drops the material, so anything else would skip its loot table: stone (drops
     * cobblestone), glass and ice (nothing), ores, grass, melons, glowstone and bookshelves would all come back as
     * themselves, a free silk touch. Oak planks, stone bricks, a native oak stair (its drop is unified to planks) or a
     * native double slab (2) pass. Slate Building's own shapes always pass (their drops come from the stored material),
     * so callers skip them.
     */
    public static boolean dropsOwnWorth(final ServerLevel level, final BlockPos pos, final BlockState state, final @Nullable BlockEntity be,
                                        final Block material, final int units) {
        final List<ItemStack> drops;
        try {
            drops = Block.getDrops(state, level, pos, be);
        } catch (final RuntimeException e) {
            return false;
        }
        final Item materialItem = material.asItem();
        final VariantRegistry registry = VariantRegistry.get();
        int total = 0;
        for (final ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            if (!drop.is(materialItem)) {
                final Variant v = registry.identify(drop).orElse(null);
                if (v == null || v.material() != material) return false;
            }
            total += drop.getCount();
        }
        return total == units;
    }
}
