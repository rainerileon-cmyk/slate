package dev.fallingcloud.slate.building.mixin.core;

import dev.fallingcloud.slate.building.client.place.AccuratePlacement;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every block the local player places goes through {@code useItemOn}, whoever asked for it: vanilla's own use, its
 * hold-to-repeat, a reach-around mod (Bridging Mod), or {@link AccuratePlacement} itself. Accurate placement counts the
 * ones it did not make as "the block just placed" too.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class PlacedBlockTrackerMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void slateBuilding$beforeUseOn(final LocalPlayer player, final InteractionHand hand, final BlockHitResult hit,
                                           final CallbackInfoReturnable<InteractionResult> cir) {
        AccuratePlacement.beforeUseOn(player, hand, hit);
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void slateBuilding$afterUseOn(final LocalPlayer player, final InteractionHand hand, final BlockHitResult hit,
                                          final CallbackInfoReturnable<InteractionResult> cir) {
        AccuratePlacement.afterUseOn(player);
    }
}
