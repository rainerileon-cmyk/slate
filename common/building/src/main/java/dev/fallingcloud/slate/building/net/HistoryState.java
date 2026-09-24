package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C (D1 → {@code ClientActions.historyState}): this player's undo/redo depth and the label of the next undo (for the build menu buttons and the HUD).
 *
 * <p>Wire id {@code slate:building/history_state}; registered by {@link BuildingNetwork}.
 */
public record HistoryState(int undo, int redo, String label) implements CustomPacketPayload {

    public static final Type<HistoryState> TYPE = new Type<>(Slate.id("building/history_state"));
    public static final StreamCodec<ByteBuf, HistoryState> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, HistoryState::undo,
        ByteBufCodecs.VAR_INT, HistoryState::redo,
        ByteBufCodecs.stringUtf8(BuildingNetwork.MAX_TEXT), HistoryState::label,
        HistoryState::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
