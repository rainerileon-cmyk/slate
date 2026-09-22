package dev.fallingcloud.slate.menu.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Parent of the vanilla options screen (title or pause menu), for the swap. */
@Mixin(OptionsScreen.class)
public interface OptionsScreenAccessor {

    @Accessor("lastScreen")
    Screen slate$lastScreen();
}
