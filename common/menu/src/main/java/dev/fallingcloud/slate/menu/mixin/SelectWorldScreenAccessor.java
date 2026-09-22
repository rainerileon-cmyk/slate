package dev.fallingcloud.slate.menu.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The screen swap needs the vanilla screen's parent so "back" lands where vanilla would have gone. */
@Mixin(SelectWorldScreen.class)
public interface SelectWorldScreenAccessor {

    @Accessor("lastScreen")
    Screen slate$lastScreen();
}
