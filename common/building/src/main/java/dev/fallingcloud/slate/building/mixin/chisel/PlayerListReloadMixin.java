package dev.fallingcloud.slate.building.mixin.chisel;

import dev.fallingcloud.slate.building.chisel.ChiselSystem;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Datapack reload hook for the chisel groups. Vanilla calls {@code PlayerList.reloadResources()} once the reloaded
 * datapacks are live (new recipes, tags bound), to resend tags and recipes to everyone; at its TAIL the chisel index is
 * rebuilt and resent. Same public method on both loaders (javap-checked); Core has no reload event of its own.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListReloadMixin {

    @Inject(method = "reloadResources", at = @At("TAIL"))
    private void slateBuilding$rebuildChiselGroups(final CallbackInfo ci) {
        ChiselSystem.onDatapackReload(((PlayerList) (Object) this).getServer());
    }
}
