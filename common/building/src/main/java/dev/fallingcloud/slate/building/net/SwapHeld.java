package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S (owner A → {@code VariantActions.swapHeld}): turn the held stack in inventory slot {@code slot} into shape {@code shape} (a {@code Shape} id) of the same material, keeping the count (Alt wheel, build-menu wheel, pick-block).
 *
 * <p>Wire id {@code slate:building/swap_held}; registered by {@link BuildingNetwork}.
 */
public record SwapHeld(int slot, String shape) implements CustomPacketPayload {

    public static final Type<SwapHeld> TYPE = new Type<>(Slate.id("building/swap_held"));
    public static final StreamCodec<ByteBuf, SwapHeld> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, SwapHeld::slot,
        ByteBufCodecs.stringUtf8(BuildingNetwork.MAX_ID), SwapHeld::shape,
        SwapHeld::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
