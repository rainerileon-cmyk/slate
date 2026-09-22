package dev.fallingcloud.slate.core.mixin;

import dev.fallingcloud.slate.core.client.ScreenInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes mouse input to Slate's overlays (popups, editor, toasts) before the screen. Cancelling
 * {@code onPress} at HEAD is safe: vanilla only records the active button inside it, so a press Slate
 * consumed never produces vanilla drag/release events either; Slate tracks its own drags from onMove.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void slate$onPress(final long window, final int button, final int action, final int mods, final CallbackInfo ci) {
        if (window != this.minecraft.getWindow().getWindow() || this.minecraft.screen == null || this.minecraft.getOverlay() != null) return;
        if (action == 1) { if (ScreenInput.mousePressed(button)) ci.cancel(); }
        else if (action == 0) { if (ScreenInput.mouseReleased(button)) ci.cancel(); }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void slate$onScroll(final long window, final double xoffset, final double yoffset, final CallbackInfo ci) {
        if (window != this.minecraft.getWindow().getWindow() || this.minecraft.screen == null || this.minecraft.getOverlay() != null) return;
        final boolean discrete = this.minecraft.options.discreteMouseScroll().get();
        final double sens = this.minecraft.options.mouseWheelSensitivity().get();
        final double sx = (discrete ? Math.signum(xoffset) : xoffset) * sens;
        final double sy = (discrete ? Math.signum(yoffset) : yoffset) * sens;
        if (ScreenInput.mouseScrolled(sx, sy)) ci.cancel();
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    private void slate$onMove(final long window, final double xpos, final double ypos, final CallbackInfo ci) {
        if (window == this.minecraft.getWindow().getWindow()) ScreenInput.mouseMoved();
    }
}
