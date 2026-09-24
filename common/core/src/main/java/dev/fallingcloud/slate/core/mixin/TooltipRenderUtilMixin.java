package dev.fallingcloud.slate.core.mixin;

import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.reskin.ReskinDraw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reskin of vanilla tooltip boxes on the dark skin: the purple-bordered gradient becomes Slate's
 * pixel-cornered panel. Vanilla's positioner and text rendering are untouched; only the background
 * painter is replaced. NeoForge routes tooltips through an overload that also takes the (event-adjusted)
 * colours; that one is hooked by {@code NeoTooltipRenderUtilMixin} in the NeoForge-only
 * {@code slate.neoforge.mixins.json} (it does not exist on Fabric).
 */
@Mixin(TooltipRenderUtil.class)
public abstract class TooltipRenderUtilMixin {

    @Inject(method = "renderTooltipBackground(Lnet/minecraft/client/gui/GuiGraphics;IIIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void slate$background(final GuiGraphics g, final int x, final int y, final int w, final int h, final int z, final CallbackInfo ci) {
        if (!Reskin.dark()) return;
        ci.cancel();
        ReskinDraw.tooltipBackground(g, x, y, w, h, z);
    }
}
