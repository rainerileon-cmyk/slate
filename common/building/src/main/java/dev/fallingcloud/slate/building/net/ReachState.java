package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C (D1 → {@code ClientActions.reachState}): the extra block reach the server applies for this player's Extended
 * mode; {@code 0} means off (refused, turned off by the rules, or as asked).
 *
 * <p>Wire id {@code slate:building/reach_state}; registered by {@link BuildingNetwork}.
 */
public record ReachState(int bonus) implements CustomPacketPayload {

    public static final Type<ReachState> TYPE = new Type<>(Slate.id("building/reach_state"));
    public static final StreamCodec<ByteBuf, ReachState> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ReachState::bonus,
        ReachState::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
