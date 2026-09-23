package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S (owner I → {@code ChiselActions.chiselTarget}): chisel the block at {@code pos} in the world into group member {@code material} (Chisel tier 2).
 *
 * <p>Wire id {@code slate:building/chisel_target}; registered by {@link BuildingNetwork}.
 */
public record ChiselTarget(BlockPos pos, ResourceLocation material) implements CustomPacketPayload {

    public static final Type<ChiselTarget> TYPE = new Type<>(Slate.id("building/chisel_target"));
    public static final StreamCodec<ByteBuf, ChiselTarget> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, ChiselTarget::pos,
        ResourceLocation.STREAM_CODEC, ChiselTarget::material,
        ChiselTarget::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
