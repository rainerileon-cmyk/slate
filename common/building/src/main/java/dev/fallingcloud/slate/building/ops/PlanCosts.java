package dev.fallingcloud.slate.building.ops;

import dev.fallingcloud.slate.building.ops.server.CostKey;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a plan costs and what the player has, per material, for the HUD's "384/512 Oak Planks" line. Works on both
 * sides; on the client "have" counts the synced inventory (hotbar, main, off hand), the server additionally draws on
 * the toolbox pouch and the Supply Link container, so the server may manage more than the preview says, never less.
 * Creative players pay nothing: the list is empty.
 */
public final class PlanCosts {

    /**
     * One material of a plan.
     *
     * @param name   the material's name
     * @param needed units the plan places
     * @param have   units the player carries
     */
    public record Need(Component name, int needed, int have) {
        public boolean enough() {
            return have >= needed;
        }
    }

    public static List<Need> of(final Player player, final Plan plan) {
        if (plan.isEmpty() || ToolboxAccess.of(player).creative()) return List.of();
        final Map<CostKey, Integer> needed = new LinkedHashMap<>();
        for (final Change c : plan.changes()) {
            if (c.kind() == Change.Kind.BREAK) continue;
            final CostKey key = CostKey.of(c.target(), c.targetVariant());
            if (key != null) needed.merge(key, StateWorth.units(c.target()), Integer::sum);
        }
        final Inventory inv = player.getInventory();
        final List<Need> out = new ArrayList<>(needed.size());
        for (final Map.Entry<CostKey, Integer> e : needed.entrySet()) {
            int have = 0;
            for (int i = 0; i < inv.items.size(); i++) have += count(e.getKey(), inv.items.get(i));
            have += count(e.getKey(), inv.getItem(Inventory.SLOT_OFFHAND));
            out.add(new Need(e.getKey().name(), e.getValue(), have));
        }
        return out;
    }

    private static int count(final CostKey key, final ItemStack stack) {
        return key.matches(stack) ? stack.getCount() : 0;
    }

    private PlanCosts() {}
}
