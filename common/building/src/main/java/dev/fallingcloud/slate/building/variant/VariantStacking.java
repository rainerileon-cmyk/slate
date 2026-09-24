package dev.fallingcloud.slate.building.variant;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Shape items stack ACROSS the shapes of one material (design §1: every shape item is one unit of its material). A
 * picked-up stair joins the planks, slabs or stairs already carried, converted 1:1, so mining a misplaced shape hands
 * the unit straight back to the stack it is built from. Only shapes convert: the full block never turns into a shape
 * (it is what recipes ask for), it just stacks with its own kind.
 *
 * <p>Order: an exact stack with room always wins (vanilla), then the first same-material stack with room in vanilla's
 * order (selected slot, off hand, the inventory), and an empty slot keeps the item as it is. Used by the pickup path
 * ({@code Inventory.addResource} through {@code InventoryMixin}) and by the economy's refunds and drops.
 */
public final class VariantStacking {

    private VariantStacking() {}

    /** Whether {@code incoming} (a shape item) may join {@code dest} (another shape of the same material) by conversion. */
    public static boolean joins(final ItemStack dest, final ItemStack incoming) {
        if (dest.isEmpty() || incoming.isEmpty() || !dest.isStackable() || ItemStack.isSameItemSameComponents(dest, incoming)) return false;
        final VariantRegistry registry = VariantRegistry.get();
        if (!registry.unifies()) return false;
        final Variant in = registry.identify(incoming).orElse(null);
        if (in == null || in.isFull()) return false;
        final Variant d = registry.identify(dest).orElse(null);
        return d != null && d.material() == in.material();
    }

    /** The slot of {@code inv} that {@code stack} may join by conversion and that has room, in vanilla's order; -1 when none. */
    public static int joinSlot(final Inventory inv, final ItemStack stack) {
        if (stack.isEmpty()) return -1;
        final VariantRegistry registry = VariantRegistry.get();
        if (!registry.unifies()) return -1;
        final Variant in = registry.identify(stack).orElse(null);
        if (in == null || in.isFull()) return -1;
        if (fits(inv, inv.selected, in)) return inv.selected;
        if (fits(inv, Inventory.SLOT_OFFHAND, in)) return Inventory.SLOT_OFFHAND;
        for (int i = 0; i < inv.items.size(); i++) if (fits(inv, i, in)) return i;
        return -1;
    }

    private static boolean fits(final Inventory inv, final int slot, final Variant in) {
        final ItemStack dest = inv.getItem(slot);
        if (dest.isEmpty() || !dest.isStackable() || dest.getCount() >= inv.getMaxStackSize(dest)) return false;
        final Variant d = VariantRegistry.get().identify(dest).orElse(null);
        return d != null && d.material() == in.material();
    }
}
