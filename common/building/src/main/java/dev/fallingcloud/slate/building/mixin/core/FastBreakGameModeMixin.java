package dev.fallingcloud.slate.building.mixin.core;

import dev.fallingcloud.slate.building.client.place.AccuratePlacement;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Fast breaking ({@code placement.fastBreaking}): vanilla waits 5 ticks ({@code destroyDelay = 5}) after a block
 * breaks before the next one starts while attack is held; with the setting on there is no wait.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class FastBreakGameModeMixin {

    @ModifyConstant(method = "continueDestroyBlock", constant = @Constant(intValue = 5))
    private int slateBuilding$fastBreak(final int delay) {
        return AccuratePlacement.fastBreaking() ? 0 : delay;
    }
}
