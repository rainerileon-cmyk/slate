package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S (owner D1 → {@code OpActions.redo}): redo the last undone operation.
 *
 * <p>Wire id {@code slate:building/redo}; registered by {@link BuildingNetwork}. Carries no data.
 */
public record Redo() implements CustomPacketPayload {

    public static final Redo INSTANCE = new Redo();
    public static final Type<Redo> TYPE = new Type<>(Slate.id("building/redo"));
    public static final StreamCodec<ByteBuf, Redo> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
