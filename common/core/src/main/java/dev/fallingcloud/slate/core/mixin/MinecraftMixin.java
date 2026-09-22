package dev.fallingcloud.slate.core.mixin;

import dev.fallingcloud.slate.core.client.SlateClient;
import dev.fallingcloud.slate.core.screen.ScreenSwaps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Screen swapping and the screen-changed hook.
 *
 * <p>Swapping re-enters {@code setScreen} with the replacement and cancels the original call, which is
 * the only way to catch vanilla's own {@code null -> TitleScreen} substitution (it happens inside the
 * method, after the parameter is read). The re-entrant call cannot match again because replacements are
 * Slate classes.</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow public ClientLevel level;
    @Shadow public Screen screen;
    @Shadow public abstract void setScreen(Screen screen);

    @Unique private boolean slate$reentering;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void slate$swapScreen(final Screen guiScreen, final CallbackInfo ci) {
        if (slate$reentering) return;
        Screen candidate = guiScreen;
        if (candidate == null && this.level == null) candidate = new TitleScreen();   // vanilla's substitution
        final Screen swapped = ScreenSwaps.apply(candidate);
        if (swapped != null && swapped != candidate) {
            ci.cancel();
            slate$reentering = true;
            try { this.setScreen(swapped); } finally { slate$reentering = false; }
        }
    }

    // No re-entry guard here: a cancelled outer call never reaches TAIL, so this runs exactly once
    // per effective screen change (in the inner call when a swap happened).
    @Inject(method = "setScreen", at = @At("TAIL"))
    private void slate$screenChanged(final Screen guiScreen, final CallbackInfo ci) {
        SlateClient.onScreenChanged(this.screen);
    }
}
