package dev.fallingcloud.slate.core.mixin;

import dev.fallingcloud.slate.core.client.render.GameView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands {@link GameView} the finished level frame: in {@code render} vanilla rebinds the main target right after the
 * level pass and its post effects, before any GUI is drawn; that single {@code bindWrite(true)} is the moment.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V", shift = At.Shift.AFTER))
    private void slate$afterLevel(final DeltaTracker deltaTracker, final boolean renderLevel, final CallbackInfo ci) {
        GameView.afterLevelRender();
    }
}
