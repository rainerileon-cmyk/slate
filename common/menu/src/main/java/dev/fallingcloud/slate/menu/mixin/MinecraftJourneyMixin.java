package dev.fallingcloud.slate.menu.mixin;

import dev.fallingcloud.slate.menu.client.loading.journey.Journey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The moment a world is left, while the game still holds it: {@link Journey} takes the land around the player down
 * for the scene of leaving and for the next time the world is opened. Every way out of a world ends in this one
 * method.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftJourneyMixin {

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    private void slate_menu$leaving(final Screen next, final boolean keepResourcePacks, final CallbackInfo ci) {
        Journey.leaving();
    }
}
