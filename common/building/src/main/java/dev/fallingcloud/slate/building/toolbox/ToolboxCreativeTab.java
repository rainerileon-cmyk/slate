package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.registry.BuildingItems;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Toolbox half of the "Slate Building" creative tab: an empty Builder's Toolbox, a fully kitted one (every Netherite
 * tool, Reach/Capacity/Speed/Memory) for creative builders, then every tool (by type, then tier) and every upgrade.
 */
public final class ToolboxCreativeTab {

    public static void fill(final CreativeModeTab.ItemDisplayParameters params, final CreativeModeTab.Output output) {
        output.accept(BuildingItems.TOOLBOX.get());
        output.accept(kitted());
        for (final ToolType type : ToolType.values()) {
            for (final ToolTier tier : ToolTier.values()) output.accept(BuildingItems.tool(type, tier).get());
        }
        for (final UpgradeType type : UpgradeType.values()) output.accept(BuildingItems.upgrade(type).get());
    }

    /** A toolbox with every tool at Netherite and the four limit upgrades. */
    public static ItemStack kitted() {
        final ItemStack box = new ItemStack(BuildingItems.TOOLBOX.get());
        final NonNullList<ItemStack> items = NonNullList.withSize(ToolboxContents.SIZE, ItemStack.EMPTY);
        for (final ToolType type : ToolType.values()) {
            items.set(ToolboxContents.toolSlot(type), new ItemStack(BuildingItems.tool(type, ToolTier.NETHERITE).get()));
        }
        final List<UpgradeType> ups = List.of(UpgradeType.REACH, UpgradeType.CAPACITY, UpgradeType.SPEED, UpgradeType.MEMORY);
        for (int i = 0; i < ups.size(); i++) items.set(ToolboxContents.FIRST_UPGRADE + i, new ItemStack(BuildingItems.upgrade(ups.get(i)).get()));
        ToolboxContents.write(box, items);
        return box;
    }

    private ToolboxCreativeTab() {}
}
