package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.registry.BuildingItems;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Toolbox half of the "Slate Building" creative tab.
 *
 * <p>Owner: E (toolbox). Skeleton default: the toolbox, every tool (by type, then tier) and every upgrade, so the
 * items are reachable in creative from day one. E may reorder / add pre-filled toolboxes.
 */
public final class ToolboxCreativeTab {

    public static void fill(final CreativeModeTab.ItemDisplayParameters params, final CreativeModeTab.Output output) {
        output.accept(BuildingItems.TOOLBOX.get());
        for (final ToolType type : ToolType.values()) {
            for (final ToolTier tier : ToolTier.values()) output.accept(BuildingItems.tool(type, tier).get());
        }
        for (final UpgradeType type : UpgradeType.values()) output.accept(BuildingItems.upgrade(type).get());
    }

    private ToolboxCreativeTab() {}
}
