package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.building.registry.BuildingMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The toolbox container menu ({@code BuildingMenus.TOOLBOX}): tool, upgrade and pouch slots of one toolbox stack
 * plus the player inventory.
 *
 * <p>Owner: E (toolbox). Skeleton placeholder with no slots. The {@code (int, Inventory)} constructor is the
 * client-side factory the {@code MenuType} uses and must stay; E adds a server constructor that binds the toolbox
 * stack, the slots, quick-move rules and a {@code Screen} registered in the loader glue.
 */
public class ToolboxMenu extends AbstractContainerMenu {

    public ToolboxMenu(final int containerId, final Inventory playerInventory) {
        super(BuildingMenus.TOOLBOX.get(), containerId);
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(final Player player) {
        return true;
    }
}
