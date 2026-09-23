package dev.fallingcloud.slate.building.ops;

import dev.fallingcloud.slate.building.variant.Variant;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What an operation places, resolved from the held stack or the hotbar (mode parameter {@code palette}). Material
 * and shape aware: an entry that identifies as a variant is placed as that shape of that material and paid with
 * any stack of the same material.
 *
 * <p>Owner: D1 (ops server). Skeleton: the data shape plus {@link #single}; D1 adds the resolution from a player.
 */
public record Palette(List<WeightedEntry> entries) {

    /**
     * One palette entry.
     *
     * @param stack   a template stack (count ignored) the block comes from
     * @param variant what it identifies as, null for blocks outside the variant system
     * @param weight  relative weight (HOTBAR_RANDOM uses stack counts)
     */
    public record WeightedEntry(ItemStack stack, @Nullable Variant variant, int weight) {
        public WeightedEntry {
            stack = stack.copyWithCount(1);
        }
    }

    public static final Palette EMPTY = new Palette(List.of());

    public Palette {
        entries = List.copyOf(entries);
    }

    public static Palette single(final ItemStack stack, final @Nullable Variant variant) {
        return stack.isEmpty() ? EMPTY : new Palette(List.of(new WeightedEntry(stack, variant, 1)));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
