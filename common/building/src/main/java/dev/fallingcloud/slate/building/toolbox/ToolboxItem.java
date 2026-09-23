package dev.fallingcloud.slate.building.toolbox;

import net.minecraft.world.item.Item;

/**
 * The Builder's Toolbox ({@code slate_building:toolbox}): 6 tool slots (one per {@code ToolType}), 4 upgrade slots
 * and 9 pouch slots in vanilla {@code DataComponents.CONTAINER}; right-click opens {@link ToolboxMenu}.
 *
 * <p>Owner: E (toolbox). Skeleton placeholder (a plain unstackable item); keep the constructor signature.
 */
public class ToolboxItem extends Item {

    public ToolboxItem(final Item.Properties properties) {
        super(properties);
    }
}
