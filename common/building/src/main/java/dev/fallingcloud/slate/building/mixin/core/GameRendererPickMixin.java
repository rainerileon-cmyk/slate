package dev.fallingcloud.slate.building.mixin.core;

import dev.fallingcloud.slate.building.client.place.AccuratePlacement;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link AccuratePlacement} runs right after vanilla has updated {@code Minecraft.hitResult} (the crosshair target),
 * every frame and once more per tick, exactly where the Accurate Block Placement mod hooks in.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPickMixin {

    @Inject(method = "pick(F)V", at = @At("RETURN"))
    private void slateBuilding$afterPick(final float partialTick, final CallbackInfo ci) {
        AccuratePlacement.afterPick();
    }
}
