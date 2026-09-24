package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C (D1 → {@code ClientActions.clipboardSync}): this player's clipboard for the client-side paste preview (empty tag = cleared). Capped by tier volume; must stay under the 1 MiB clientbound payload limit (use Core's blob channel for more).
 *
 * <p>Wire id {@code slate:building/clipboard_sync}; registered by {@link BuildingNetwork}.
 */
public record ClipboardSync(CompoundTag tag) implements CustomPacketPayload {

    public static final Type<ClipboardSync> TYPE = new Type<>(Slate.id("building/clipboard_sync"));
    public static final StreamCodec<ByteBuf, ClipboardSync> CODEC = ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(ClipboardSync::new, ClipboardSync::tag);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
