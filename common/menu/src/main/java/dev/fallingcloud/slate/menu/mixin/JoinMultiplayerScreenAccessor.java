package dev.fallingcloud.slate.menu.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Parent of the vanilla multiplayer screen, for the swap. */
@Mixin(JoinMultiplayerScreen.class)
public interface JoinMultiplayerScreenAccessor {

    @Accessor("lastScreen")
    Screen slate$lastScreen();
}
