package dev.fallingcloud.slate.core.mixin;

import dev.fallingcloud.slate.core.screen.reskin.BetterInventoryPalette;
import dev.fallingcloud.slate.core.screen.reskin.ContainerReskin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The drawing calls {@link ContainerReskin} rewrites while a container screen paints itself: texture blits (every
 * public {@code blit} overload ends in one of the two hooked here), sprite blits (the creative inventory's tabs and
 * scroller) and the text colour of labels (the {@code Component} overload delegates to the
 * {@code FormattedCharSequence} one). All of them are no-ops outside the windows {@code ContainerScreenMixin} opens.
 * The colours Better Inventory paints from code pass through {@link BetterInventoryPalette} while one of its screens is
 * open (every {@code fill} and {@code renderOutline} ends in the one hooked here).
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {

    /** {@code blit(atlas, x, y, w, h, u, v, uw, vh, tw, th)}: the 7- and 9-argument overloads land here. */
    @Inject(method = "blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void slate$containerBlit(final ResourceLocation atlas, final int x, final int y, final int w, final int h, final float u, final float v,
                                     final int uw, final int vh, final int tw, final int th, final CallbackInfo ci) {
        if (ContainerReskin.replaceBackground((GuiGraphics) (Object) this, atlas, x, y, w, h)) ci.cancel();
    }

    /** {@code blit(atlas, x, y, blitOffset, u, v, w, h, tw, th)}: the overload with a z offset. */
    @Inject(method = "blit(Lnet/minecraft/resources/ResourceLocation;IIIFFIIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void slate$containerBlitZ(final ResourceLocation atlas, final int x, final int y, final int blitOffset, final float u, final float v,
                                      final int w, final int h, final int tw, final int th, final CallbackInfo ci) {
        if (ContainerReskin.replaceBackground((GuiGraphics) (Object) this, atlas, x, y, w, h)) ci.cancel();
    }

    /** {@code blitSprite(sprite, x, y, blitOffset, w, h)}: the 5-argument overload lands here. */
    @Inject(method = "blitSprite(Lnet/minecraft/resources/ResourceLocation;IIIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void slate$containerSprite(final ResourceLocation sprite, final int x, final int y, final int blitOffset, final int w, final int h,
                                       final CallbackInfo ci) {
        if (ContainerReskin.replaceSprite((GuiGraphics) (Object) this, sprite, x, y, w, h)) ci.cancel();
    }

    @ModifyVariable(method = { "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
        "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I" },
        at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
    private int slate$containerLabel(final int color) {
        return ContainerReskin.labelColor(BetterInventoryPalette.text(color));
    }

    @ModifyVariable(method = "fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 5, require = 0)
    private int slate$modFill(final int color) {
        return BetterInventoryPalette.fill(color);
    }
}
