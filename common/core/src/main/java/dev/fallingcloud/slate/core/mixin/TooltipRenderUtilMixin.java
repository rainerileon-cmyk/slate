package dev.fallingcloud.slate.core.mixin;

import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.reskin.ReskinDraw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reskin of vanilla tooltip boxes on the dark skin: the purple-bordered gradient becomes Slate's
 * pixel-cornered panel. Vanilla's positioner and text rendering are untouched; only the background
 * painter is replaced. NeoForge routes tooltips through an overload that takes the (event-adjusted)
 * colours, so that one is hooked too; on Fabric it does not exist and the injection is skipped.
 */
@Mixin(TooltipRenderUtil.class)
public abstract class TooltipRenderUtilMixin {

    @Inject(method = "renderTooltipBackground(Lnet/minecraft/client/gui/GuiGraphics;IIIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void slate$background(final GuiGraphics g, final int x, final int y, final int w, final int h, final int z, final CallbackInfo ci) {
        if (!Reskin.dark()) return;
        ci.cancel();
        ReskinDraw.tooltipBackground(g, x, y, w, h, z);
    }

    @Dynamic("NeoForge overload with event colours; absent on Fabric")
    @Inject(method = "renderTooltipBackground(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIIII)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void slate$backgroundColored(final GuiGraphics g, final int x, final int y, final int w, final int h, final int z,
                                                final int bgTop, final int bgBottom, final int borderTop, final int borderBottom, final CallbackInfo ci) {
        if (!Reskin.dark()) return;
        ci.cancel();
        ReskinDraw.tooltipBackground(g, x, y, w, h, z);
    }
}
