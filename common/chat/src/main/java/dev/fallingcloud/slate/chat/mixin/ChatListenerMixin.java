package dev.fallingcloud.slate.chat.mixin;

import com.mojang.authlib.GameProfile;
import dev.fallingcloud.slate.chat.client.SenderResolver;
import java.time.Instant;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures who is speaking right before vanilla hands the line to the chat: {@code showMessageToPlayer}
 * calls {@code ChatComponent.addMessage} synchronously with the sender's {@link GameProfile} in hand
 * (the server's profile, never anything a client wrote), and disguised chat carries the chat type's bound
 * name. The resolver consumes the capture in {@code addMessage} and clears it at RETURN so a delayed or
 * suppressed line can never mislabel the next one. Cosmetic: {@code require = 0}.
 */
@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {

    @Inject(method = "showMessageToPlayer", at = @At("HEAD"), require = 0)
    private void slate$captureSender(final ChatType.Bound bound, final PlayerChatMessage message, final Component decorated,
                                     final GameProfile profile, final boolean onlyShowSecure, final Instant timestamp,
                                     final CallbackInfoReturnable<Boolean> cir) {
        SenderResolver.pending(profile);
    }

    @Inject(method = "showMessageToPlayer", at = @At("RETURN"), require = 0)
    private void slate$releaseSender(final ChatType.Bound bound, final PlayerChatMessage message, final Component decorated,
                                     final GameProfile profile, final boolean onlyShowSecure, final Instant timestamp,
                                     final CallbackInfoReturnable<Boolean> cir) {
        SenderResolver.clear();
    }

    @Inject(method = "handleDisguisedChatMessage", at = @At("HEAD"), require = 0)
    private void slate$captureDisguised(final Component message, final ChatType.Bound bound, final CallbackInfo ci) {
        SenderResolver.pendingName(bound.name().getString());
    }

    @Inject(method = "handleDisguisedChatMessage", at = @At("RETURN"), require = 0)
    private void slate$releaseDisguised(final Component message, final ChatType.Bound bound, final CallbackInfo ci) {
        SenderResolver.clear();
    }
}
