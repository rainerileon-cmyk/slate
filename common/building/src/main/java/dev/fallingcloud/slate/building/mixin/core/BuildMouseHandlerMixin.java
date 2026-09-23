package dev.fallingcloud.slate.building.mixin.core;

import dev.fallingcloud.slate.building.client.input.BuildInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In-world mouse input for {@link BuildInput} (screen-open input stays with Core's own mixin). All three targets are
 * private vanilla methods with identical signatures in the NeoForge and Fabric jars (checked with javap). Cancelling
 * at HEAD means vanilla never sets its key mappings / hotbar / camera for a consumed event.
 */
@Mixin(MouseHandler.class)
public abstract class BuildMouseHandlerMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Shadow public abstract boolean isMouseGrabbed();

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void slateBuilding$onPress(final long window, final int button, final int action, final int mods, final CallbackInfo ci) {
        // Only while playing with a grabbed cursor: the first click after alt-tab must still grab the mouse.
        if (window != this.minecraft.getWindow().getWindow() || this.minecraft.screen != null || !this.isMouseGrabbed()) return;
        if (BuildInput.fireMouseButton(button, action, mods)) ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void slateBuilding$onScroll(final long window, final double xOffset, final double yOffset, final CallbackInfo ci) {
        if (window != this.minecraft.getWindow().getWindow() || this.minecraft.screen != null) return;
        // Same scaling vanilla applies before it scrolls the hotbar.
        final boolean discrete = this.minecraft.options.discreteMouseScroll().get();
        final double sensitivity = this.minecraft.options.mouseWheelSensitivity().get();
        final double dx = (discrete ? Math.signum(xOffset) : xOffset) * sensitivity;
        final double dy = (discrete ? Math.signum(yOffset) : yOffset) * sensitivity;
        if (BuildInput.fireScroll(dx, dy)) ci.cancel();
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void slateBuilding$turnPlayer(final double movementTime, final CallbackInfo ci) {
        if (this.accumulatedDX == 0.0 && this.accumulatedDY == 0.0) return;
        if (BuildInput.fireMouseLook(this.accumulatedDX, this.accumulatedDY)) {
            this.accumulatedDX = 0.0;
            this.accumulatedDY = 0.0;
            ci.cancel();
        }
    }
}
