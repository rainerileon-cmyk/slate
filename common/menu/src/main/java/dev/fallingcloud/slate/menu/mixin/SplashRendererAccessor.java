package dev.fallingcloud.slate.menu.mixin;

import net.minecraft.client.gui.components.SplashRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The splash string is private; the Slate title screen animates it itself. */
@Mixin(SplashRenderer.class)
public interface SplashRendererAccessor {

    @Accessor("splash")
    String slate$splash();
}
