package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C (D1 → {@code ClientActions.symmetryState}): the symmetry the server applies for this player; an empty {@code mode} means off.
 *
 * <p>Wire id {@code slate:building/symmetry_state}; registered by {@link BuildingNetwork}.
 */
public record SymmetryState(String mode, CompoundTag params, BlockPos centre) implements CustomPacketPayload {

    public static final Type<SymmetryState> TYPE = new Type<>(Slate.id("building/symmetry_state"));
    public static final StreamCodec<ByteBuf, SymmetryState> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(BuildingNetwork.MAX_ID), SymmetryState::mode,
        ByteBufCodecs.TRUSTED_COMPOUND_TAG, SymmetryState::params,
        BlockPos.STREAM_CODEC, SymmetryState::centre,
        SymmetryState::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
