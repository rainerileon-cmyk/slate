package dev.fallingcloud.slate.building.variant;

import dev.fallingcloud.slate.building.ops.server.CostKey;
import dev.fallingcloud.slate.building.ops.server.Economy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Material units for in-world reshaping (design §1), paid through the building-ops economy ({@link Economy}) so
 * reshaping and operations follow one rule: any stack that identifies as the material (the full block or any shape of
 * it) is worth one unit per item, taken from the toolbox pouch, then the inventory (never the toolbox itself), then a
 * Supply Link container; plain blocks go first. Refunds go to the pouch / linked container with a Magnet upgrade, then
 * the inventory, then the player's feet.
 */
public final class VariantEconomy {

    private VariantEconomy() {}

    /** Units of {@code material} the player can pay with right now. */
    public static int available(final ServerPlayer player, final Block material) {
        return economy(player).available(new CostKey.Material(material));
    }

    /** Takes {@code units} of {@code material}; false (and nothing taken) when the player has fewer. */
    public static boolean charge(final ServerPlayer player, final Block material, final int units) {
        if (units <= 0) return true;
        final boolean ok = economy(player).charge(new CostKey.Material(material), units, new ItemStack(material));
        if (ok) player.getInventory().setChanged();
        return ok;
    }

    /** Gives back {@code units} of {@code material} as the material's own item. */
    public static void refund(final ServerPlayer player, final Block material, final int units) {
        if (units <= 0) return;
        final Economy economy = economy(player);
        economy.refund(new CostKey.Material(material), units);
        economy.settle();
    }

    private static Economy economy(final ServerPlayer player) {
        return new Economy(player, false, player.getInventory().selected);
    }
}
