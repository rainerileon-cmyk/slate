package dev.fallingcloud.slate.building.variant;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Drops of variants (design §1): every shape drops ITSELF, one shape item per unit. Natives keep their own loot tables
 * (oak_stairs x1, oak_slab x2 from a double slab, the re-pointed fence item of a DiagonalFences twin); Slate Building's
 * own shapes drop their item carrying the material ({@code ShapeBehaviour.drops}). Nothing is rewritten on the way out:
 * the unit finds its way back in the inventory instead, where a shape item joins any stack of the same material
 * ({@link VariantStacking}), so a mined stair lands in the planks it came from without a swap.
 *
 * <p>{@link #dropsOwnWorth} is the loot guard for changing a block in place (hammer reshape), the same rule the chisel
 * applies (design §9).
 */
public final class VariantDrops {

    private VariantDrops() {}

    /**
     * Whether {@code state} at {@code pos} drops exactly what it is worth in its own material with NO tool: only items of
     * {@code material} (the block itself or a variant of it) totalling {@code units}. Reshaping a block in place turns
     * it into a shape that drops its own worth, so anything else would skip its loot table: stone (drops
     * cobblestone), glass and ice (nothing), ores, grass, melons, glowstone and bookshelves would all come back as
     * themselves, a free silk touch. Oak planks, stone bricks, a native oak stair (drops one oak stair, a variant of
     * planks) or a native double slab (2 slabs) pass. Slate Building's own shapes always pass (their drops come from
     * the stored material), so callers skip them.
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
