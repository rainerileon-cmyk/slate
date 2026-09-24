package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S (owner D1 → {@code OpActions.cancelOp}): stop this player's running operation; what was already done stays (and can be undone).
 *
 * <p>Wire id {@code slate:building/cancel_op}; registered by {@link BuildingNetwork}. Carries no data.
 */
public record CancelOp() implements CustomPacketPayload {

    public static final CancelOp INSTANCE = new CancelOp();
    public static final Type<CancelOp> TYPE = new Type<>(Slate.id("building/cancel_op"));
    public static final StreamCodec<ByteBuf, CancelOp> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
