package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.building.ops.ToolType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The toolbox's slot layout inside its vanilla {@code DataComponents.CONTAINER} and the rules of each slot, shared by
 * the menu, the click-to-insert shortcuts, the tooltip and {@link ToolboxAccess}:
 * <pre>
 *   0..5    one tool per ToolType (ordinal order), any tier, max 1
 *   6..9    upgrades, one per slot, at most UpgradeType.maxLevel() of a type
 *   10..18  pouch: building blocks (block items that may sit inside container items), full stacks
 * </pre>
 */
public final class ToolboxContents {

    public static final int TOOLS = ToolType.values().length;
    public static final int UPGRADES = 4;
    public static final int POUCH = 9;
    public static final int FIRST_UPGRADE = TOOLS;
    public static final int FIRST_POUCH = FIRST_UPGRADE + UPGRADES;
    public static final int SIZE = FIRST_POUCH + POUCH;

    public enum Kind { TOOL, UPGRADE, POUCH }

    public static Kind kind(final int slot) {
        if (slot < FIRST_UPGRADE) return Kind.TOOL;
        return slot < FIRST_POUCH ? Kind.UPGRADE : Kind.POUCH;
    }

    public static int toolSlot(final ToolType type) {
        return type.ordinal();
    }

    /** The tool type a tool slot holds. */
    public static ToolType toolType(final int slot) {
        return ToolType.values()[slot];
    }

    /** A fresh copy of the toolbox's contents, always {@link #SIZE} entries. */
    public static NonNullList<ItemStack> read(final ItemStack toolbox) {
        final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        toolbox.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        return items;
    }

    public static void write(final ItemStack toolbox, final List<ItemStack> items) {
        toolbox.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    /** Most items one slot holds. */
    public static int maxStack(final int slot, final ItemStack stack) {
        return kind(slot) == Kind.POUCH ? stack.getMaxStackSize() : 1;
    }

    /** Whether {@code stack} may go into {@code slot}, given the other slots' {@code items}. */
    public static boolean mayPlace(final int slot, final ItemStack stack, final List<ItemStack> items) {
        if (stack.isEmpty()) return true;
        return switch (kind(slot)) {
            case TOOL -> stack.getItem() instanceof BuildingToolItem tool && tool.type() == toolType(slot);
            case UPGRADE -> stack.getItem() instanceof UpgradeItem up && installed(items, up.type(), slot) < up.type().maxLevel();
            case POUCH -> isPouchMaterial(stack);
        };
    }

    /**
     * Building material for the pouch: any block item that may itself live inside a container item (so no shulker
     * boxes: vanilla's own nesting rule), never a toolbox.
     */
    public static boolean isPouchMaterial(final ItemStack stack) {
        return stack.getItem() instanceof BlockItem && stack.getItem().canFitInsideContainerItems() && !(stack.getItem() instanceof ToolboxItem);
    }

    /** Upgrades of {@code type} in the upgrade slots, not counting {@code exceptSlot} (-1: count all). */
    public static int installed(final List<ItemStack> items, final UpgradeType type, final int exceptSlot) {
        int n = 0;
        for (int i = FIRST_UPGRADE; i < FIRST_POUCH && i < items.size(); i++) {
            if (i != exceptSlot && items.get(i).getItem() instanceof UpgradeItem up && up.type() == type) n++;
        }
        return n;
    }

    /** Tool tiers present (a tool counts only in its own slot). */
    public static Map<ToolType, Integer> tiers(final List<ItemStack> items) {
        final Map<ToolType, Integer> tiers = new EnumMap<>(ToolType.class);
        for (final ToolType type : ToolType.values()) {
            final int slot = toolSlot(type);
            if (slot < items.size() && items.get(slot).getItem() instanceof BuildingToolItem tool && tool.type() == type) {
                tiers.put(type, tool.level());
            }
        }
        return tiers;
    }

    /** Upgrade levels installed, each capped at its {@link UpgradeType#maxLevel()}. */
    public static Map<UpgradeType, Integer> upgrades(final List<ItemStack> items) {
        final Map<UpgradeType, Integer> ups = new EnumMap<>(UpgradeType.class);
        for (final UpgradeType type : UpgradeType.values()) {
            final int n = Math.min(type.maxLevel(), installed(items, type, -1));
            if (n > 0) ups.put(type, n);
        }
        return ups;
    }

    /** Filled pouch slots. */
    public static int pouchUsed(final List<ItemStack> items) {
        int n = 0;
        for (int i = FIRST_POUCH; i < SIZE && i < items.size(); i++) if (!items.get(i).isEmpty()) n++;
        return n;
    }

    /**
     * Moves as much of {@code stack} into {@code items} as the slot rules allow (tools to their slot, upgrades to a
     * free upgrade slot, blocks into the pouch - merging first), shrinking {@code stack}. Returns how many moved.
     */
    public static int insert(final List<ItemStack> items, final ItemStack stack, final boolean pouchAllowed) {
        if (stack.isEmpty()) return 0;
        final int before = stack.getCount();
        if (stack.getItem() instanceof BuildingToolItem tool) {
            final int slot = toolSlot(tool.type());
            if (items.get(slot).isEmpty()) items.set(slot, stack.split(1));
        } else if (stack.getItem() instanceof UpgradeItem) {
            for (int i = FIRST_UPGRADE; i < FIRST_POUCH && !stack.isEmpty(); i++) {
                if (items.get(i).isEmpty() && mayPlace(i, stack, items)) items.set(i, stack.split(1));
            }
        } else if (pouchAllowed && isPouchMaterial(stack)) {
            for (int i = FIRST_POUCH; i < SIZE && !stack.isEmpty(); i++) {
                final ItemStack in = items.get(i);
                if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, stack)) {
                    final int move = Math.min(stack.getCount(), in.getMaxStackSize() - in.getCount());
                    if (move > 0) { in.grow(move); stack.shrink(move); }
                }
            }
            for (int i = FIRST_POUCH; i < SIZE && !stack.isEmpty(); i++) {
                if (items.get(i).isEmpty()) items.set(i, stack.split(stack.getCount()));
            }
        }
        return before - stack.getCount();
    }

    private ToolboxContents() {}
}
