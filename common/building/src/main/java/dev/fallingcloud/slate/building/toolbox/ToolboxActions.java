package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.building.net.OpenToolbox;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server handlers of the toolbox payloads (server thread, from {@code BuildingNetwork}).
 *
 * <p>{@link OpenToolbox#slot()}: an inventory index (0..35, 40 = offhand), {@link ToolboxAccess#SLOT_BETTER_INVENTORY}
 * (-1) for BetterInventory's toolbox slot, or {@link ToolboxAccess#SLOT_FIND} (-2) for "the toolbox
 * {@link ToolboxAccess#find} picks" - what UI buttons such as the build menu's toolbox chip send.
 */
public final class ToolboxActions {

    /** Opens the requested toolbox; tells the player when they carry none. */
    public static void openToolbox(final OpenToolbox p, final ServerPlayer player) {
        if (player.isSpectator()) return;
        int slot = p.slot();
        if (slot == ToolboxAccess.SLOT_FIND) {
            final ToolboxAccess.Located at = ToolboxAccess.locate(player);
            if (at == null) {
                player.displayClientMessage(Component.translatable("slate_building.toolbox.none").withStyle(ChatFormatting.GRAY), true);
                return;
            }
            slot = at.slot();
        }
        ToolboxMenu.open(player, slot);
    }

    private ToolboxActions() {}
}
