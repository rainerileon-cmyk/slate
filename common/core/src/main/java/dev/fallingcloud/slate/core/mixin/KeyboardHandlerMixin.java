package dev.fallingcloud.slate.core.mixin;

import dev.fallingcloud.slate.core.client.ScreenInput;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keyboard counterpart of {@link MouseHandlerMixin}: popups and the editor get keys before the screen. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void slate$keyPress(final long window, final int key, final int scancode, final int action, final int modifiers, final CallbackInfo ci) {
        if (window != this.minecraft.getWindow().getWindow() || this.minecraft.screen == null || this.minecraft.getOverlay() != null) return;
        if (action == 1 || action == 2) {
            if (ScreenInput.keyPressed(key, scancode, modifiers)) ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void slate$charTyped(final long window, final int codePoint, final int modifiers, final CallbackInfo ci) {
        if (window != this.minecraft.getWindow().getWindow() || this.minecraft.screen == null || this.minecraft.getOverlay() != null) return;
        if (Character.charCount(codePoint) == 1) {
            if (ScreenInput.charTyped((char) codePoint, modifiers)) ci.cancel();
        }
    }
}
