package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S (owner A → {@code VariantActions.reshapeTarget}): reshape the variant block at {@code pos} into {@code shape} in the world, charging/refunding the unit difference (hammer / in-world wheel).
 *
 * <p>Wire id {@code slate:building/reshape_target}; registered by {@link BuildingNetwork}.
 */
public record ReshapeTarget(BlockPos pos, String shape) implements CustomPacketPayload {

    public static final Type<ReshapeTarget> TYPE = new Type<>(Slate.id("building/reshape_target"));
    public static final StreamCodec<ByteBuf, ReshapeTarget> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, ReshapeTarget::pos,
        ByteBufCodecs.stringUtf8(BuildingNetwork.MAX_ID), ReshapeTarget::shape,
        ReshapeTarget::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
