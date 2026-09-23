package dev.fallingcloud.slate.building.net;

import dev.fallingcloud.slate.building.chisel.ChiselActions;
import dev.fallingcloud.slate.building.ops.OpActions;
import dev.fallingcloud.slate.building.toolbox.ToolboxActions;
import dev.fallingcloud.slate.building.variant.VariantActions;
import dev.fallingcloud.slate.core.net.Flow;
import dev.fallingcloud.slate.core.net.NetContext;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers every Slate Building payload (design §11) with Core's {@link SlateNetwork} and routes it to the owning
 * area: serverbound payloads to {@code VariantActions} / {@code ChiselActions} / {@code OpActions} /
 * {@code ToolboxActions} with the sending player, clientbound ones to {@code client.ClientActions} through
 * {@link ClientSide} (so a dedicated server never loads client classes). Owners implement the action methods and
 * never edit this file. Ids are {@code slate:building/<name>} because Core's network owns the {@code slate}
 * namespace. Handlers run on the receiving side's main thread.
 *
 * <p>Sending: {@code SlateNetwork.get().sendToServer(new Undo())} / {@code sendToPlayer(player, payload)}; both are
 * no-ops when the other side lacks the channel.
 */
public final class BuildingNetwork {

    /** Max length of ids on the wire (shape, mode, ...). */
    public static final int MAX_ID = 64;
    /** Max length of free text on the wire (labels, lang keys, args). */
    public static final int MAX_TEXT = 256;
    /** Max anchors in one {@link ApplyOp}. */
    public static final int MAX_ANCHORS = 16;
    /** Max args of one {@link OpResult}. */
    public static final int MAX_ARGS = 16;

    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        c2s(SwapHeld.TYPE, SwapHeld.CODEC, VariantActions::swapHeld);
        c2s(ReshapeTarget.TYPE, ReshapeTarget.CODEC, VariantActions::reshapeTarget);
        c2s(ChiselHeld.TYPE, ChiselHeld.CODEC, ChiselActions::chiselHeld);
        c2s(ChiselTarget.TYPE, ChiselTarget.CODEC, ChiselActions::chiselTarget);
        c2s(ApplyOp.TYPE, ApplyOp.CODEC, OpActions::applyOp);
        c2s(CancelOp.TYPE, CancelOp.CODEC, OpActions::cancelOp);
        c2s(Undo.TYPE, Undo.CODEC, OpActions::undo);
        c2s(Redo.TYPE, Redo.CODEC, OpActions::redo);
        c2s(SetSymmetry.TYPE, SetSymmetry.CODEC, OpActions::setSymmetry);
        c2s(OpenToolbox.TYPE, OpenToolbox.CODEC, ToolboxActions::openToolbox);

        s2c(ServerSettingsSync.TYPE, ServerSettingsSync.CODEC, p -> ClientSide.serverSettings(p));
        s2c(ChiselGroupsSync.TYPE, ChiselGroupsSync.CODEC, p -> ClientSide.chiselGroups(p));
        s2c(HistoryState.TYPE, HistoryState.CODEC, p -> ClientSide.history(p));
        s2c(OpProgress.TYPE, OpProgress.CODEC, p -> ClientSide.progress(p));
        s2c(OpResult.TYPE, OpResult.CODEC, p -> ClientSide.result(p));
        s2c(ClipboardSync.TYPE, ClipboardSync.CODEC, p -> ClientSide.clipboard(p));
        s2c(SymmetryState.TYPE, SymmetryState.CODEC, p -> ClientSide.symmetry(p));
    }

    private static <T extends CustomPacketPayload> void c2s(final CustomPacketPayload.Type<T> type,
                                                            final StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                            final BiConsumer<T, ServerPlayer> action) {
        SlateNetwork.get().register(type, codec, Flow.C2S, (payload, ctx) -> {
            final ServerPlayer sender = ctx.sender();
            if (!ctx.isClient() && sender != null) action.accept(payload, sender);
        });
    }

    private static <T extends CustomPacketPayload> void s2c(final CustomPacketPayload.Type<T> type,
                                                            final StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                            final Consumer<T> action) {
        SlateNetwork.get().register(type, codec, Flow.S2C, (payload, ctx) -> dispatchClient(ctx, payload, action));
    }

    private static <T> void dispatchClient(final NetContext ctx, final T payload, final Consumer<T> action) {
        if (ctx.isClient()) action.accept(payload);
    }

    /** Client-only references live here so a dedicated server never resolves them. */
    private static final class ClientSide {
        static void serverSettings(final ServerSettingsSync p) { dev.fallingcloud.slate.building.client.ServerSettingsClient.accept(p); }
        static void chiselGroups(final ChiselGroupsSync p) { dev.fallingcloud.slate.building.client.ClientActions.chiselGroupsSync(p); }
        static void history(final HistoryState p) { dev.fallingcloud.slate.building.client.ClientActions.historyState(p); }
        static void progress(final OpProgress p) { dev.fallingcloud.slate.building.client.ClientActions.opProgress(p); }
        static void result(final OpResult p) { dev.fallingcloud.slate.building.client.ClientActions.opResult(p); }
        static void clipboard(final ClipboardSync p) { dev.fallingcloud.slate.building.client.ClientActions.clipboardSync(p); }
        static void symmetry(final SymmetryState p) { dev.fallingcloud.slate.building.client.ClientActions.symmetryState(p); }
    }

    private BuildingNetwork() {}
}
