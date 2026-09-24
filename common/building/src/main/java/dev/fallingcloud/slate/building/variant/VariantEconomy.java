package dev.fallingcloud.slate.building.variant;

import dev.fallingcloud.slate.building.toolbox.SlotRef;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.toolbox.UpgradeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Material units for in-world reshaping (design §1): any stack that identifies as the material (the full block or
 * any shape of it) is worth one unit per item. Taken from the toolbox pouch, then the inventory (hotbar, main,
 * offhand; the toolbox itself never identifies as a material), plain blocks before shaped ones. Refunds go to the
 * pouch first with a Magnet upgrade, then the inventory, then the player's feet.
 *
 * <p>Owner A. Deliberately small and local: the building-ops economy (D1) handles plans, linked containers and
 * batching; reshaping only ever moves a unit or two.
 */
public final class VariantEconomy {

    private VariantEconomy() {}

    /** Units of {@code material} the player can pay with right now. */
    public static int available(final ServerPlayer player, final Block material) {
        int units = 0;
        for (final SlotRef slot : sources(player)) units += unitsIn(slot.get(), material);
        return units;
    }

    /** Takes {@code units} of {@code material}; false (and nothing taken) when the player has fewer. */
    public static boolean charge(final ServerPlayer player, final Block material, final int units) {
        if (units <= 0) return true;
        final List<SlotRef> sources = sources(player);
        int have = 0;
        for (final SlotRef slot : sources) have += unitsIn(slot.get(), material);
        if (have < units) return false;
        int left = units;
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            final boolean wantFull = pass == 0;
            for (final SlotRef slot : sources) {
                if (left <= 0) break;
                final ItemStack stack = slot.get();
                final Optional<Variant> v = VariantRegistry.get().identify(stack);
                if (v.isEmpty() || v.get().material() != material || v.get().isFull() != wantFull) continue;
                final int take = Math.min(stack.getCount(), left);
                final ItemStack rest = stack.copy();
                rest.shrink(take);
                slot.set(rest.isEmpty() ? ItemStack.EMPTY : rest);
                left -= take;
            }
        }
        player.getInventory().setChanged();
        return true;
    }

    /** Gives back {@code units} of {@code material} as the material's own item. */
    public static void refund(final ServerPlayer player, final Block material, final int units) {
        if (units <= 0 || material.asItem() == Items.AIR) return;
        int left = units;
        final int max = new ItemStack(material).getMaxStackSize();
        while (left > 0) {
            final int n = Math.min(left, max);
            left -= n;
            ItemStack stack = new ItemStack(material, n);
            if (ToolboxAccess.of(player).upgrade(UpgradeType.MAGNET) > 0) stack = insert(ToolboxAccess.pouch(player), stack);
            if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
        }
    }

    private static int unitsIn(final ItemStack stack, final Block material) {
        if (stack.isEmpty()) return 0;
        final Optional<Variant> v = VariantRegistry.get().identify(stack);
        return v.isPresent() && v.get().material() == material ? stack.getCount() : 0;
    }

    /** Pouch, hotbar, main inventory, offhand. */
    private static List<SlotRef> sources(final ServerPlayer player) {
        final List<SlotRef> out = new ArrayList<>(ToolboxAccess.pouch(player));
        final Inventory inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) out.add(new SlotRef(inv, i));
        out.add(new SlotRef(inv, Inventory.SLOT_OFFHAND));
        return out;
    }

    /** Merges {@code stack} into matching then empty slots; returns what did not fit. */
    private static ItemStack insert(final List<SlotRef> slots, final ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (final SlotRef slot : slots) {
                if (rest.isEmpty()) break;
                final ItemStack there = slot.get();
                if (pass == 0 && !there.isEmpty() && ItemStack.isSameItemSameComponents(there, rest) && there.getCount() < there.getMaxStackSize()) {
                    final int move = Math.min(rest.getCount(), there.getMaxStackSize() - there.getCount());
                    slot.set(there.copyWithCount(there.getCount() + move));
                    rest.shrink(move);
                } else if (pass == 1 && there.isEmpty()) {
                    slot.set(rest);
                    rest = ItemStack.EMPTY;
                }
            }
        }
        return rest;
    }
}
