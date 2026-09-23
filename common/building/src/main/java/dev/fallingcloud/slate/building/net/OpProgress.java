package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C (D1 → {@code ClientActions.opProgress}): progress of running operation {@code op} (sent every ~5 ticks).
 *
 * <p>Wire id {@code slate:building/op_progress}; registered by {@link BuildingNetwork}.
 */
public record OpProgress(int op, int done, int total, String mode) implements CustomPacketPayload {

    public static final Type<OpProgress> TYPE = new Type<>(Slate.id("building/op_progress"));
    public static final StreamCodec<ByteBuf, OpProgress> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, OpProgress::op,
        ByteBufCodecs.VAR_INT, OpProgress::done,
        ByteBufCodecs.VAR_INT, OpProgress::total,
        ByteBufCodecs.stringUtf8(BuildingNetwork.MAX_ID), OpProgress::mode,
        OpProgress::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
