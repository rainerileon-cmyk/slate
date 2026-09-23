package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S (owner I → {@code ChiselActions.chiselHeld}): swap the held stack in slot {@code slot} to the chisel-group member {@code material}, keeping its shape and count.
 *
 * <p>Wire id {@code slate:building/chisel_held}; registered by {@link BuildingNetwork}.
 */
public record ChiselHeld(int slot, ResourceLocation material) implements CustomPacketPayload {

    public static final Type<ChiselHeld> TYPE = new Type<>(Slate.id("building/chisel_held"));
    public static final StreamCodec<ByteBuf, ChiselHeld> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ChiselHeld::slot,
        ResourceLocation.STREAM_CODEC, ChiselHeld::material,
        ChiselHeld::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
