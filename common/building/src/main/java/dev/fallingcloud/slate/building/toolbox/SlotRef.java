package dev.fallingcloud.slate.building.toolbox;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * A live reference to one slot of some container (a toolbox pouch backed by the toolbox stack, a linked chest, the
 * player inventory), so the ops economy can take from and refund to it uniformly.
 *
 * <p>Owner: E (toolbox). Skeleton: the simple container-backed form; E may add implementations that write back to
 * an item's {@code CONTAINER} component (keep {@link #get()} / {@link #set} semantics).
 */
public record SlotRef(Container container, int slot) {

    public ItemStack get() {
        return container.getItem(slot);
    }

    public void set(final ItemStack stack) {
        container.setItem(slot, stack);
        container.setChanged();
    }
}
