package dev.fallingcloud.slate.core.mixin;

import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.screen.reskin.ReskinDraw;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reskin of vanilla screen backgrounds on the dark skin: the stone tile becomes Slate's solid bg with a
 * vignette (or the translucent overlay in a world), the transparent gradient becomes a dark veil, and
 * the panorama + blur passes are skipped where a solid fill covers them anyway. Slate's own screens
 * paint their backgrounds themselves and are left alone; the title screen keeps its panorama (see
 * {@link TitleScreenMixin}).
 */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {

    @Shadow protected Minecraft minecraft;
    @Shadow public int width;
    @Shadow public int height;

    @Unique
    private boolean slate$own() {
        return (Object) this instanceof SlateScreen;
    }

    @Inject(method = "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;IIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void slate$menuBackground(final GuiGraphics g, final int x, final int y, final int w, final int h, final CallbackInfo ci) {
        if (slate$own() || !Reskin.dark()) return;
        ci.cancel();
        ReskinDraw.menuBackground(g, x, y, w, h, this.minecraft.level != null);
    }

    @Inject(method = "renderTransparentBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void slate$transparentBackground(final GuiGraphics g, final CallbackInfo ci) {
        if (slate$own() || !Reskin.dark()) return;
        ci.cancel();
        ReskinDraw.transparentBackground(g, this.width, this.height);
    }

    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true, require = 0)
    private void slate$panorama(final GuiGraphics g, final float partialTick, final CallbackInfo ci) {
        // Only the generic path: TitleScreen (and others) override renderPanorama and never reach this body.
        if (slate$own() || (Object) this instanceof TitleScreen || !Reskin.dark()) return;
        ci.cancel();
    }

    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void slate$blur(final float partialTick, final CallbackInfo ci) {
        if (slate$own() || !Reskin.dark()) return;
        // Out of a world the solid fill covers everything; in one the blur is a user preference.
        if (this.minecraft.level == null || !Theme.current().blurInGame()) ci.cancel();
    }
}
