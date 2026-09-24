package dev.fallingcloud.slate.building.mixin.core;

import dev.fallingcloud.slate.building.client.BuildingHarness;
import dev.fallingcloud.slate.building.client.input.BuildInput;
import dev.fallingcloud.slate.building.client.place.AccuratePlacement;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla's use / attack / pick-block entry points for {@link BuildInput}, plus the dev harness frame clock. All
 * targets are private methods with identical signatures in the NeoForge and Fabric jars (javap-checked); NeoForge's
 * own input events fire inside them, after our HEAD hooks.
 */
@Mixin(Minecraft.class)
public abstract class BuildMinecraftMixin {

    @Shadow private int rightClickDelay;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void slateBuilding$startUseItem(final CallbackInfo ci) {
        // Accurate placement took this tick's use over (it calls startUseItem itself, with the suppression lifted).
        if (AccuratePlacement.suppressVanillaUse()) {
            ci.cancel();
            return;
        }
        if (BuildInput.fireUse()) {
            // What vanilla sets first thing: holding right-click then repeats every 4 ticks, not every tick.
            this.rightClickDelay = 4;
            ci.cancel();
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void slateBuilding$startAttack(final CallbackInfoReturnable<Boolean> cir) {
        if (BuildInput.fireAttack()) cir.setReturnValue(false);
    }

    /** Holding left-click with {@code suppressContinueAttack}: vanilla runs as if the button were up (it also stops the break). */
    @ModifyVariable(method = "continueAttack", at = @At("HEAD"), argsOnly = true)
    private boolean slateBuilding$continueAttack(final boolean leftClick) {
        return leftClick && !BuildInput.fireSuppressContinueAttack();
    }

    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void slateBuilding$pickBlock(final CallbackInfo ci) {
        if (BuildInput.firePickBlock()) ci.cancel();
    }

    /** Dev harness clock (inactive in normal play). Cosmetic: never fail a launch over it. */
    @Inject(method = "runTick", at = @At("HEAD"), require = 0)
    private void slateBuilding$runTick(final boolean renderLevel, final CallbackInfo ci) {
        BuildingHarness.onFrame();
    }
}
