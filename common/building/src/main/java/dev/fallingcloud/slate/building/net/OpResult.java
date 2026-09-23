package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C (D1 → {@code ClientActions.opResult}): operation {@code op} of mode {@code mode} finished (or stopped):
 * counts of placed / broken / skipped positions and a translatable summary ({@code messageKey} with {@code args},
 * e.g. {@code slate_building.result.filled} + ["384"]) for the HUD's action-bar line.
 *
 * <p>Wire id {@code slate:building/op_result}; registered by {@link BuildingNetwork}. Seven fields, one more than
 * {@code StreamCodec.composite} takes, hence the hand-written codec.
 */
public record OpResult(int op, String mode, int placed, int broken, int skipped, String messageKey, List<String> args)
    implements CustomPacketPayload {

    public static final Type<OpResult> TYPE = new Type<>(Slate.id("building/op_result"));
    private static final StreamCodec<io.netty.buffer.ByteBuf, List<String>> ARGS =
        ByteBufCodecs.stringUtf8(BuildingNetwork.MAX_TEXT).apply(ByteBufCodecs.list(BuildingNetwork.MAX_ARGS));
    public static final StreamCodec<FriendlyByteBuf, OpResult> CODEC = StreamCodec.of(OpResult::write, OpResult::read);

    public OpResult {
        args = List.copyOf(args);
    }

    private static void write(final FriendlyByteBuf buf, final OpResult r) {
        buf.writeVarInt(r.op);
        buf.writeUtf(r.mode, BuildingNetwork.MAX_ID);
        buf.writeVarInt(r.placed);
        buf.writeVarInt(r.broken);
        buf.writeVarInt(r.skipped);
        buf.writeUtf(r.messageKey, BuildingNetwork.MAX_TEXT);
        ARGS.encode(buf, r.args);
    }

    private static OpResult read(final FriendlyByteBuf buf) {
        return new OpResult(buf.readVarInt(), buf.readUtf(BuildingNetwork.MAX_ID), buf.readVarInt(), buf.readVarInt(),
            buf.readVarInt(), buf.readUtf(BuildingNetwork.MAX_TEXT), ARGS.decode(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
