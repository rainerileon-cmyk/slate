package dev.fallingcloud.slate.building.mixin.core;

import dev.fallingcloud.slate.building.client.input.BuildInput;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In-world keyboard input for {@link BuildInput}: raw presses, repeats and releases while no screen is open (Core's
 * own mixin keeps screen input). {@code keyPress(long,int,int,int,int)} is public and identical on both loaders.
 */
@Mixin(KeyboardHandler.class)
public abstract class BuildKeyboardHandlerMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void slateBuilding$keyPress(final long window, final int key, final int scancode, final int action, final int mods, final CallbackInfo ci) {
        if (window != this.minecraft.getWindow().getWindow() || this.minecraft.screen != null) return;
        if (BuildInput.fireKey(key, scancode, action, mods)) ci.cancel();
    }
}
