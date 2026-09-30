package dev.fallingcloud.slate.profile.mixin;

import com.mojang.authlib.GameProfile;
import dev.fallingcloud.slate.profile.client.AppliedLooks;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player who wears a look is drawn with the look's skin wherever the game asks what a player looks like: in the
 * world, in the player list, in the first-person hand. The cape and the rest stay what the account has.
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {

    @Shadow @Final private GameProfile profile;

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void slate$look(final CallbackInfoReturnable<PlayerSkin> cir) {
        final AppliedLooks.Applied look = AppliedLooks.of(profile.getId());
        if (look != null) cir.setReturnValue(look.over(cir.getReturnValue()));
    }
}
