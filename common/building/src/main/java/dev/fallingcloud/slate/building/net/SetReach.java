package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S (owner D1 → {@code OpActions.setReach}): turn the Extended mode (kind REACH) on or off. While on, the server
 * adds the player's toolbox reach bonus to their block interaction range; it answers with {@link ReachState}.
 *
 * <p>Wire id {@code slate:building/set_reach}; registered by {@link BuildingNetwork}.
 */
public record SetReach(boolean on) implements CustomPacketPayload {

    public static final Type<SetReach> TYPE = new Type<>(Slate.id("building/set_reach"));
    public static final StreamCodec<ByteBuf, SetReach> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, SetReach::on,
        SetReach::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
