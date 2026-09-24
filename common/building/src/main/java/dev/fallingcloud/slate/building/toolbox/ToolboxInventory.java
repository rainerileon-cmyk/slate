package dev.fallingcloud.slate.building.toolbox;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

/**
 * A live {@link Container} over one toolbox stack's {@code DataComponents.CONTAINER}: the stack's component is the
 * only source of truth. Every read first checks whether the component instance changed since this view last read or
 * wrote it (another view - the open menu, the ops economy's pouch, tool wear, a click-to-insert - wrote in between) and
 * reloads; every write replaces the component. Several views of one toolbox may therefore be alive at once without
 * either keeping a private copy (which would duplicate items).
 *
 * <p>A reload merges slot by slot: a slot this view changed in place but has not written yet keeps its local value,
 * every other slot takes the new component's value (a slot both changed keeps the smaller stack, so a merge never makes
 * items), and the merge is written back. So an in-place pouch change by the
 * ops economy survives tool wear written by another view in between. Callers that mutate a returned stack in place
 * should still call {@link #setChanged()} (vanilla menus always do); views handed to the ops economy are also flushed at
 * the end of the server tick ({@link ToolboxAccess#pouch}).
 */
public final class ToolboxInventory implements Container {

    private static final Set<ToolboxInventory> PENDING_FLUSH = Collections.newSetFromMap(new IdentityHashMap<>());

    private final ItemStack toolbox;
    private final NonNullList<ItemStack> items = NonNullList.withSize(ToolboxContents.SIZE, ItemStack.EMPTY);
    private final @Nullable Runnable onWrite;
    private @Nullable ItemContainerContents synced;

    /**
     * @param toolbox the toolbox stack (the live one, in an inventory)
     * @param onWrite runs after every write, e.g. to let BetterInventory sync its toolbox slot; may be null
     */
    public ToolboxInventory(final ItemStack toolbox, final @Nullable Runnable onWrite) {
        this.toolbox = toolbox;
        this.onWrite = onWrite;
        pull();
    }

    /** The stack this view reads and writes. */
    public ItemStack toolbox() {
        return toolbox;
    }

    /** The current slot list (live references; call {@link #setChanged()} after mutating one). */
    public List<ItemStack> items() {
        pull();
        return items;
    }

    private void pull() {
        final ItemContainerContents now = toolbox.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (now == synced) return;
        if (synced == null) {
            now.copyInto(items);
            synced = now;
            return;
        }
        final NonNullList<ItemStack> base = NonNullList.withSize(ToolboxContents.SIZE, ItemStack.EMPTY);
        final NonNullList<ItemStack> external = NonNullList.withSize(ToolboxContents.SIZE, ItemStack.EMPTY);
        synced.copyInto(base);
        now.copyInto(external);
        boolean local = false;
        for (int i = 0; i < items.size(); i++) {
            final ItemStack mine = items.get(i), theirs = external.get(i), was = base.get(i);
            if (ItemStack.matches(mine, was)) {
                items.set(i, theirs);                         // only they changed it (or nobody)
            } else if (ItemStack.matches(theirs, was)) {
                local = true;                                 // only we changed it: keep ours, write it back
            } else {
                // Both changed the same slot: never let the merge create items - keep the smaller of two stacks of the
                // same thing, otherwise the change that is already written.
                if (ItemStack.isSameItemSameComponents(mine, theirs) && mine.getCount() < theirs.getCount()) local = true;
                else items.set(i, theirs);
            }
        }
        synced = now;
        if (local) push();
    }

    private void push() {
        final ItemContainerContents next = ItemContainerContents.fromItems(items);
        if (next.equals(synced)) return;
        toolbox.set(DataComponents.CONTAINER, next);
        synced = next;
        if (onWrite != null) onWrite.run();
    }

    /** Writes in-place changes, if any, to the stack (merging with anything another view wrote meanwhile). */
    public void flush() {
        pull();
        push();
    }

    /** Server tick end: flush every view handed to the ops economy this tick. */
    static void flushPending() {
        synchronized (PENDING_FLUSH) {
            for (final ToolboxInventory inv : PENDING_FLUSH) inv.flush();
            PENDING_FLUSH.clear();
        }
    }

    ToolboxInventory flushAtTickEnd() {
        synchronized (PENDING_FLUSH) {
            PENDING_FLUSH.add(this);
        }
        return this;
    }

    @Override
    public int getContainerSize() {
        return ToolboxContents.SIZE;
    }

    @Override
    public boolean isEmpty() {
        pull();
        for (final ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(final int slot) {
        pull();
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(final int slot, final int amount) {
        pull();
        final ItemStack out = ContainerHelper.removeItem(items, slot, amount);
        if (!out.isEmpty()) push();
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(final int slot) {
        pull();
        final ItemStack out = ContainerHelper.takeItem(items, slot);
        push();
        return out;
    }

    @Override
    public void setItem(final int slot, final ItemStack stack) {
        pull();
        if (slot < 0 || slot >= items.size()) return;
        items.set(slot, stack);
        push();
    }

    @Override
    public int getMaxStackSize(final ItemStack stack) {
        return stack.getMaxStackSize();
    }

    @Override
    public boolean canPlaceItem(final int slot, final ItemStack stack) {
        pull();
        return ToolboxContents.mayPlace(slot, stack, items);
    }

    @Override
    public void setChanged() {
        push();
    }

    @Override
    public boolean stillValid(final Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        pull();
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
        push();
    }
}
