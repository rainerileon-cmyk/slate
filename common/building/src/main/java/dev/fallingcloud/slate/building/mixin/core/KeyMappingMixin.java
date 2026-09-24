package dev.fallingcloud.slate.building.mixin.core;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fallingcloud.slate.building.client.input.ExclusiveKeys;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds {@link ExclusiveKeys}: the static {@code KeyMapping.set(Key, boolean)} and {@code click(Key)} are how
 * vanilla (keyboard and mouse) presses every mapping bound to a key. NeoForge patches their BODIES (modifier-aware
 * lookup) but keeps both signatures, so HEAD injections fit both loaders. The {@code setAll}/{@code releaseAll}
 * hooks only keep exclusivity tidy across screens and are optional.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private static void slateBuilding$set(final InputConstants.Key key, final boolean down, final CallbackInfo ci) {
        if (ExclusiveKeys.onSet(key, down)) ci.cancel();
    }

    @Inject(method = "click", at = @At("HEAD"), cancellable = true)
    private static void slateBuilding$click(final InputConstants.Key key, final CallbackInfo ci) {
        if (ExclusiveKeys.onClick(key)) ci.cancel();
    }

    @Inject(method = "setAll", at = @At("TAIL"), require = 0)
    private static void slateBuilding$setAll(final CallbackInfo ci) {
        ExclusiveKeys.afterSetAll();
    }

    @Inject(method = "releaseAll", at = @At("TAIL"), require = 0)
    private static void slateBuilding$releaseAll(final CallbackInfo ci) {
        ExclusiveKeys.afterReleaseAll();
    }
}
