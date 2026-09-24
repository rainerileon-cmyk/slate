package dev.fallingcloud.slate.building.toolbox;

import java.util.List;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * The toolbox tooltip's picture: its tools with tier meters, the upgrades and how full the pouch is. Data only (common);
 * drawn by {@code toolbox.client.ClientToolboxTooltip}, registered per loader in the toolbox glue.
 *
 * @param items the toolbox's slots ({@link ToolboxContents} layout, copies)
 */
public record ToolboxTooltip(List<ItemStack> items) implements TooltipComponent {

    public ToolboxTooltip {
        items = List.copyOf(items);
    }
}
