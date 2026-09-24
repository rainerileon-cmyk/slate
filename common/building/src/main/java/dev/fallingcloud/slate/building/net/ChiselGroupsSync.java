package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C (I → {@code ClientActions.chiselGroupsSync}): the server's chisel group index, on join and after datapack reloads. Keep it under the 1 MiB clientbound payload limit (or split it).
 *
 * <p>Wire id {@code slate:building/chisel_groups_sync}; registered by {@link BuildingNetwork}.
 */
public record ChiselGroupsSync(CompoundTag tag) implements CustomPacketPayload {

    public static final Type<ChiselGroupsSync> TYPE = new Type<>(Slate.id("building/chisel_groups_sync"));
    public static final StreamCodec<ByteBuf, ChiselGroupsSync> CODEC = ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(ChiselGroupsSync::new, ChiselGroupsSync::tag);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
