package dev.fallingcloud.slate.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.reskin.ReskinDraw;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Reskin of vanilla sliders (every {@code OptionInstance} slider in the options screens). The two sprite
 * blits (track, handle) are wrapped: dark skin = Slate track with the accent fill and a knob; vanilla
 * skin = the highlighted sprites cross-fade in. The label keeps vanilla's scrolling text, recoloured.
 */
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin extends AbstractWidget {

    @Shadow protected double value;

    protected AbstractSliderButtonMixin(final int x, final int y, final int width, final int height, final Component message) {
        super(x, y, width, height, message);
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"), require = 0)
    private void slate$sprite(final GuiGraphics g, final ResourceLocation sprite, final int x, final int y, final int w, final int h,
                              final Operation<Void> original) {
        if (!Reskin.active()) { original.call(g, sprite, x, y, w, h); return; }
        final boolean handle = sprite.getPath().startsWith("widget/slider_handle");
        final float hover = ReskinDraw.hover(this);
        if (Theme.current().isVanilla()) {
            if (!this.active) { original.call(g, sprite, x, y, w, h); return; }
            if (handle) SlateDraw.vanillaFace(g, SlateDraw.SLIDER_HANDLE, SlateDraw.SLIDER_HANDLE_HIGHLIGHTED, null, x, y, w, h, hover, true, this.alpha);
            else SlateDraw.vanillaFace(g, SlateDraw.SLIDER, SlateDraw.SLIDER_HIGHLIGHTED, null, x, y, w, h, hover, true, this.alpha);
            return;
        }
        if (handle) ReskinDraw.sliderHandle(g, x, y, w, h, hover, this.active);
        else ReskinDraw.sliderTrack(g, x, y, w, h, (float) this.value, hover, this.active);
    }

    @ModifyArg(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/components/AbstractSliderButton;renderScrollingString(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;II)V"),
        index = 3, require = 0)
    private int slate$textColor(final int color) {
        if (!Reskin.dark()) return color;
        final int rgb = color & 0xFFFFFF, a = color & 0xFF000000;
        if (rgb == 0xFFFFFF) return a | (Theme.current().palette().text() & 0xFFFFFF);
        if (rgb == 0xA0A0A0) return a | (Theme.current().palette().textDim() & 0xFFFFFF);
        return color;
    }
}
