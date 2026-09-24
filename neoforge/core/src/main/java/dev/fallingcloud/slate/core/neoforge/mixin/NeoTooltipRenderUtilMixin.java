package dev.fallingcloud.slate.core.neoforge.mixin;

import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.reskin.ReskinDraw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge-only half of Core's tooltip reskin ({@code TooltipRenderUtilMixin} in common hooks vanilla's 5-int
 * painter): NeoForge routes tooltips through an overload that also takes the (event-adjusted) colours. It lives in
 * {@code slate.neoforge.mixins.json}, which only the NeoForge core jar declares, because the overload does not exist
 * on Fabric, where a descriptor-only match crashed the first tooltip of a dev client.
 */
@Mixin(TooltipRenderUtil.class)
public abstract class NeoTooltipRenderUtilMixin {

    @Inject(method = "renderTooltipBackground(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void slate$backgroundColored(final GuiGraphics g, final int x, final int y, final int w, final int h, final int z,
                                                final int bgTop, final int bgBottom, final int borderTop, final int borderBottom, final CallbackInfo ci) {
        if (!Reskin.dark()) return;
        ci.cancel();
        ReskinDraw.tooltipBackground(g, x, y, w, h, z);
    }
}
