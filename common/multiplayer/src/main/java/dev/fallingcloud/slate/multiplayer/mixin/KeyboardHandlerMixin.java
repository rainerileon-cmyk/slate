package dev.fallingcloud.slate.multiplayer.mixin;

import dev.fallingcloud.slate.multiplayer.client.MultiplayerClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Key mappings only "click" while no screen is open, but the friends hub should toggle from the title
 * screen and any menu too. Same recipe as Core's editor key: look at the raw key before the screen does,
 * skipping it while a text field has focus. Cosmetic: {@code require = 0}, the in-game path works without it.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true, require = 0)
    private void slate_multiplayer$friendsKey(final long window, final int key, final int scancode, final int action, final int modifiers, final CallbackInfo ci) {
        if (action != 1 || window != this.minecraft.getWindow().getWindow() || this.minecraft.screen == null || this.minecraft.getOverlay() != null) return;
        if (this.minecraft.screen.getFocused() instanceof EditBox eb && eb.isFocused()) return;
        if (MultiplayerClient.onScreenKey(key, scancode)) ci.cancel();
    }
}
