package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S (owner E → {@code ToolboxActions.openToolbox}): open the toolbox in inventory slot {@code slot} (or the BetterInventory toolbox slot, E's convention for negative values).
 *
 * <p>Wire id {@code slate:building/open_toolbox}; registered by {@link BuildingNetwork}.
 */
public record OpenToolbox(int slot) implements CustomPacketPayload {

    public static final Type<OpenToolbox> TYPE = new Type<>(Slate.id("building/open_toolbox"));
    public static final StreamCodec<ByteBuf, OpenToolbox> CODEC = ByteBufCodecs.VAR_INT.map(OpenToolbox::new, OpenToolbox::slot);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
