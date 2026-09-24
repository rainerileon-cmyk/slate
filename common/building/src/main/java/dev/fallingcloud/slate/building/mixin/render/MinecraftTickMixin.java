package dev.fallingcloud.slate.building.mixin.render;

import dev.fallingcloud.slate.building.client.render.BuildingRender;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets the client tick for the renderers' submission lifetimes: ghosts and overlays submitted inside a tick stay
 * up until the next tick starts, everything else lives for one render pass (see {@code GhostRenderer}).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void slateBuilding$tickStart(final CallbackInfo ci) {
        BuildingRender.onTickStart();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void slateBuilding$tickEnd(final CallbackInfo ci) {
        BuildingRender.onTickEnd();
    }
}
