package dev.fallingcloud.slate.chat.mixin;

import dev.fallingcloud.slate.chat.client.GifRecorder;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The end of a frame, when the world, the HUD, the screen and the toasts are all drawn and the frame has not been
 * shown yet: where a recording takes its frames.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void slate$endOfFrame(final DeltaTracker deltaTracker, final boolean renderLevel, final CallbackInfo ci) {
        GifRecorder.endOfFrame();
    }
}
