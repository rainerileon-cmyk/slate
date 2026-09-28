package dev.fallingcloud.slate.building.mixin.core;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.fallingcloud.slate.building.client.place.AccuratePlacement;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fast breaking ({@code placement.fastBreaking}): vanilla waits 5 ticks ({@code destroyDelay = 5}) after a block
 * breaks before the next one starts while attack is held; with the setting on there is no wait.
 * <p>
 * A MixinExtras modifier, not {@code @ModifyConstant}: a constant takes one {@code @ModifyConstant} only, and Accurate
 * Block Placement modifies this same one with a required injector, so claiming it failed that mod's construction.
 * This chains with it instead.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class FastBreakGameModeMixin {

    @ModifyExpressionValue(method = "continueDestroyBlock", at = @At(value = "CONSTANT", args = "intValue=5"))
    private int slateBuilding$fastBreak(final int delay) {
        return AccuratePlacement.fastBreaking() ? 0 : delay;
    }
}
