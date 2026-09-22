package dev.fallingcloud.slate.core.fabric;

import dev.fallingcloud.slate.core.net.NetContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client-only half of {@link FabricNetwork}; never loaded on a dedicated server. */
final class FabricClientNetwork {

    static <T extends CustomPacketPayload> void registerReceiver(final FabricNetwork.Registration<T> r) {
        ClientPlayNetworking.registerGlobalReceiver(r.type(), (payload, context) -> r.handler().handle(payload, NetContext.client()));
    }

    static void send(final CustomPacketPayload payload) {
        if (ClientPlayNetworking.canSend(payload.type())) ClientPlayNetworking.send(payload);
    }

    static boolean canSend(final CustomPacketPayload.Type<?> type) {
        try {
            return ClientPlayNetworking.canSend(type);
        } catch (final IllegalStateException notConnected) {
            return false;
        }
    }

    private FabricClientNetwork() {}
}
