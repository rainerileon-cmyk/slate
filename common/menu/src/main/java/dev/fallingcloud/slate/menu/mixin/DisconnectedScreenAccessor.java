package dev.fallingcloud.slate.menu.mixin;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reason + parent of the vanilla disconnected screen, so the Slate one can show the same information. */
@Mixin(DisconnectedScreen.class)
public interface DisconnectedScreenAccessor {

    @Accessor("parent")
    Screen slate$parent();

    @Accessor("details")
    DisconnectionDetails slate$details();
}
