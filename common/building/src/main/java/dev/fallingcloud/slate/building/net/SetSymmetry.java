package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S (owner D1 → {@code OpActions.setSymmetry}): turn a TOGGLE mode (mirror / radial) on around {@code centre} with {@code params}; an empty {@code mode} turns symmetry off.
 *
 * <p>Wire id {@code slate:building/set_symmetry}; registered by {@link BuildingNetwork}.
 */
public record SetSymmetry(String mode, CompoundTag params, BlockPos centre) implements CustomPacketPayload {

    public static final Type<SetSymmetry> TYPE = new Type<>(Slate.id("building/set_symmetry"));
    public static final StreamCodec<ByteBuf, SetSymmetry> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(BuildingNetwork.MAX_ID), SetSymmetry::mode,
        ByteBufCodecs.COMPOUND_TAG, SetSymmetry::params,
        BlockPos.STREAM_CODEC, SetSymmetry::centre,
        SetSymmetry::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
