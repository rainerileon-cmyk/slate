package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S (owner D1 → {@code OpActions.applyOp}): run building mode {@code mode} with {@code params} (a {@code ModeParams} tag) on the selection {@code anchors} (1 or 2 corners, or a paste origin) clicked on {@code face}, paying from the stack in hotbar slot {@code slot}. The server re-plans and re-validates everything.
 *
 * <p>Wire id {@code slate:building/apply_op}; registered by {@link BuildingNetwork}.
 */
public record ApplyOp(String mode, CompoundTag params, List<BlockPos> anchors, Direction face, int slot) implements CustomPacketPayload {

    public static final Type<ApplyOp> TYPE = new Type<>(Slate.id("building/apply_op"));
    public static final StreamCodec<ByteBuf, ApplyOp> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(BuildingNetwork.MAX_ID), ApplyOp::mode,
        ByteBufCodecs.COMPOUND_TAG, ApplyOp::params,
        BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(BuildingNetwork.MAX_ANCHORS)), ApplyOp::anchors,
        Direction.STREAM_CODEC, ApplyOp::face,
        ByteBufCodecs.VAR_INT, ApplyOp::slot,
        ApplyOp::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
