package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S (owner D1 → {@code OpActions.undo}): undo this player's last operation (refunds what it removes, re-charges what it restores).
 *
 * <p>Wire id {@code slate:building/undo}; registered by {@link BuildingNetwork}. Carries no data.
 */
public record Undo() implements CustomPacketPayload {

    public static final Undo INSTANCE = new Undo();
    public static final Type<Undo> TYPE = new Type<>(Slate.id("building/undo"));
    public static final StreamCodec<ByteBuf, Undo> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
