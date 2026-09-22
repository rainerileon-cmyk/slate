package dev.fallingcloud.slate.core.fabric;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.net.Flow;
import dev.fallingcloud.slate.core.net.NetContext;
import dev.fallingcloud.slate.core.net.PayloadHandler;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric networking. Codecs are registered on both play registries (a dedicated server must know the
 * S2C codec to encode). Server receivers register here; client receivers go through
 * {@link FabricClientNetwork}, which is only touched on the client.
 */
public final class FabricNetwork implements SlateNetwork {

    record Registration<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                       StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                       Flow flow, PayloadHandler<T> handler) {}

    static final List<Registration<?>> CLIENT_PENDING = new ArrayList<>();
    private static boolean clientInstalled;

    @Override
    public <T extends CustomPacketPayload> void register(final CustomPacketPayload.Type<T> type,
                                                         final StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                         final Flow flow, final PayloadHandler<T> handler) {
        if (!Slate.MOD_ID.equals(type.id().getNamespace())) {
            throw new IllegalArgumentException("Slate payload ids must use the 'slate' namespace: " + type.id());
        }
        if (flow.toServer()) {
            PayloadTypeRegistry.playC2S().register(type, codec);
            ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.handle(payload, NetContext.server(context.player())));
        }
        if (flow.toClient()) {
            PayloadTypeRegistry.playS2C().register(type, codec);
            if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                final Registration<T> r = new Registration<>(type, codec, flow, handler);
                synchronized (CLIENT_PENDING) {
                    if (clientInstalled) FabricClientNetwork.registerReceiver(r);
                    else CLIENT_PENDING.add(r);
                }
            }
        }
    }

    /** Client entry point: install receivers for everything registered so far and from now on. */
    static void installClient() {
        synchronized (CLIENT_PENDING) {
            for (final Registration<?> r : CLIENT_PENDING) FabricClientNetwork.registerReceiver(r);
            CLIENT_PENDING.clear();
            clientInstalled = true;
        }
    }

    @Override
    public void sendToServer(final CustomPacketPayload payload) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) FabricClientNetwork.send(payload);
    }

    @Override
    public void sendToPlayer(final ServerPlayer player, final CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) ServerPlayNetworking.send(player, payload);
    }

    @Override
    public boolean canSendToPlayer(final ServerPlayer player, final CustomPacketPayload.Type<?> type) {
        return ServerPlayNetworking.canSend(player, type);
    }

    @Override
    public boolean serverHasChannel(final CustomPacketPayload.Type<?> type) {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT && FabricClientNetwork.canSend(type);
    }
}
