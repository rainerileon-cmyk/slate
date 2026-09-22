package dev.fallingcloud.slate.core.mixin;

import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.reskin.ReskinDraw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The vanilla title screen keeps its panorama on the dark skin; Slate only lays its vignette over it so
 * the restyled buttons read on top. Fades with the screen's own panorama fade-in.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    @Shadow private float panoramaFade;

    protected TitleScreenMixin(final Component title) {
        super(title);
    }

    @Inject(method = "renderPanorama", at = @At("TAIL"), require = 0)
    private void slate$veil(final GuiGraphics g, final float partialTick, final CallbackInfo ci) {
        if (!Reskin.dark()) return;
        ReskinDraw.panoramaVeil(g, this.width, this.height, this.panoramaFade);
    }
}
