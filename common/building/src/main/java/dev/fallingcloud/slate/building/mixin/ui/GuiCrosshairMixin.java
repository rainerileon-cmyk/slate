package dev.fallingcloud.slate.building.mixin.ui;

import dev.fallingcloud.slate.building.client.wheel.WheelOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the vanilla crosshair while the quick-swap wheel is open: the wheel's centre disc sits exactly on it.
 * {@code Gui.renderCrosshair(GuiGraphics, DeltaTracker)} is private with the same signature on NeoForge and Fabric
 * (javap-checked); NeoForge's crosshair GUI layer calls the same method.
 */
@Mixin(Gui.class)
public abstract class GuiCrosshairMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void slateBuilding$hideCrosshair(final GuiGraphics g, final DeltaTracker delta, final CallbackInfo ci) {
        if (WheelOverlay.INSTANCE.hidesCrosshair()) ci.cancel();
    }
}
