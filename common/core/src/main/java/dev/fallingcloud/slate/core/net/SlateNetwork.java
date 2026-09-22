package dev.fallingcloud.slate.core.net;

import dev.fallingcloud.slate.core.platform.Services;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-agnostic custom payloads.
 *
 * <p>Rules: register during a module's {@code init()} (both sides, same set on each). Payload ids MUST
 * use the {@code slate} namespace (NeoForge ties a registrar to the mod that fires the event, and Core
 * is the one that registers everything). Channels are optional on both loaders: a client without the mod
 * may join, and a server without it never sees the channel. Keep one payload under ~1.5 MiB on the wire
 * (the singleplayer pipe has no limit and hides oversize bugs); use {@code core.net.blob} for big data.</p>
 */
public interface SlateNetwork {

    static SlateNetwork get() {
        return Services.load(SlateNetwork.class);
    }

    <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,
                                                  StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                  Flow flow, PayloadHandler<T> handler);

    /** Client: send to the current server. No-op when not connected or the server lacks the channel. */
    void sendToServer(CustomPacketPayload payload);

    /** Server: send to one player. No-op when that client lacks the channel. */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Server: whether the client registered the channel (i.e. has Slate + the module). */
    boolean canSendToPlayer(ServerPlayer player, CustomPacketPayload.Type<?> type);

    /** Client: whether the current server registered the channel. */
    boolean serverHasChannel(CustomPacketPayload.Type<?> type);
}
