package dev.fallingcloud.slate.multiplayer.social;

import dev.fallingcloud.slate.core.Slate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The in-game channel carrying {@link SocialMessage}s: {@code slate:social}, both directions, optional. */
public record SocialPayload(SocialMessage message) implements CustomPacketPayload {

    public static final Type<SocialPayload> TYPE = new Type<>(Slate.id("social"));

    public static final StreamCodec<FriendlyByteBuf, SocialPayload> CODEC = StreamCodec.of(
        (buf, p) -> SocialCodec.write(buf, p.message()),
        buf -> new SocialPayload(SocialCodec.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
