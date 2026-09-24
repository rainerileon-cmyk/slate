package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C (skeleton → {@code ServerSettingsClient}): the server's {@code building-server.json} (see {@code BuildingServerSettings#toTag}), sent on join and after a reload. Server-built, so the tag is read without a size accounter; it stays far below the 1 MiB clientbound payload limit.
 *
 * <p>Wire id {@code slate:building/server_settings_sync}; registered by {@link BuildingNetwork}.
 */
public record ServerSettingsSync(CompoundTag tag) implements CustomPacketPayload {

    public static final Type<ServerSettingsSync> TYPE = new Type<>(Slate.id("building/server_settings_sync"));
    public static final StreamCodec<ByteBuf, ServerSettingsSync> CODEC = ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(ServerSettingsSync::new, ServerSettingsSync::tag);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
