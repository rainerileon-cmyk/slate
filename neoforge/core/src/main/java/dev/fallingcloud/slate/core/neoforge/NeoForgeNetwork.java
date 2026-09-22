package dev.fallingcloud.slate.core.neoforge;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.net.Flow;
import dev.fallingcloud.slate.core.net.NetContext;
import dev.fallingcloud.slate.core.net.PayloadHandler;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registrations are queued during mod construction and flushed in RegisterPayloadHandlersEvent under
 * one optional registrar owned by Core (hence the {@code slate} namespace rule on payload ids).
 */
public final class NeoForgeNetwork implements SlateNetwork {

    private record Pending<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                          StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                          Flow flow, PayloadHandler<T> handler) {
        void register(final PayloadRegistrar r) {
            switch (flow) {
                case C2S -> r.playToServer(type, codec, (p, ctx) -> handler.handle(p, NetContext.server((ServerPlayer) ctx.player())));
                case S2C -> r.playToClient(type, codec, (p, ctx) -> handler.handle(p, NetContext.client()));
                case BOTH -> r.playBidirectional(type, codec, (p, ctx) -> {
                    if (ctx.flow().isServerbound()) handler.handle(p, NetContext.server((ServerPlayer) ctx.player()));
                    else handler.handle(p, NetContext.client());
                });
            }
        }
    }

    private static final List<Pending<?>> PENDING = new ArrayList<>();
    private static boolean flushed;

    static void flush(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar r = event.registrar("1").optional();
        synchronized (PENDING) {
            for (final Pending<?> p : PENDING) p.register(r);
            PENDING.clear();
            flushed = true;
        }
    }

    @Override
    public <T extends CustomPacketPayload> void register(final CustomPacketPayload.Type<T> type,
                                                         final StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                         final Flow flow, final PayloadHandler<T> handler) {
        if (!Slate.MOD_ID.equals(type.id().getNamespace())) {
            throw new IllegalArgumentException("Slate payload ids must use the 'slate' namespace: " + type.id());
        }
        synchronized (PENDING) {
            if (flushed) throw new IllegalStateException("Payload " + type.id() + " registered after network registration closed; register in SlateModule.init()");
            PENDING.add(new Pending<>(type, codec, flow, handler));
        }
    }

    @Override
    public void sendToServer(final CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() == null || !serverHasChannel(payload.type())) return;
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(final ServerPlayer player, final CustomPacketPayload payload) {
        if (!canSendToPlayer(player, payload.type())) return;
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public boolean canSendToPlayer(final ServerPlayer player, final CustomPacketPayload.Type<?> type) {
        return player.connection != null && NetworkRegistry.hasChannel(player.connection, type.id());
    }

    @Override
    public boolean serverHasChannel(final CustomPacketPayload.Type<?> type) {
        final var conn = Minecraft.getInstance().getConnection();
        return conn != null && NetworkRegistry.hasChannel(conn, type.id());
    }
}
