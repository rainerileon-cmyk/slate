package dev.fallingcloud.slate.building.ops;

import dev.fallingcloud.slate.building.net.ApplyOp;
import dev.fallingcloud.slate.building.net.CancelOp;
import dev.fallingcloud.slate.building.net.Redo;
import dev.fallingcloud.slate.building.net.SetSymmetry;
import dev.fallingcloud.slate.building.net.Undo;
import dev.fallingcloud.slate.building.ops.server.OpsServer;
import dev.fallingcloud.slate.building.ops.server.Symmetry;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server handlers of the building-mode payloads (server thread, from {@code BuildingNetwork}): validate (mode
 * unlocked, reach, limits, game mode, rate limit), re-plan with the same {@link Planners} the client previewed with,
 * charge and execute over ticks, keep history, answer with {@code HistoryState} / {@code OpProgress} /
 * {@code OpResult} / {@code ClipboardSync} / {@code SymmetryState}. The work is done by {@link OpsServer} and
 * {@link Symmetry}.
 *
 * <p>Replies: every {@code ApplyOp} / {@code Undo} / {@code Redo} ends in exactly one {@code OpResult} (a refusal
 * has an {@code slate_building.error.*} / {@code .plan.*} key, see {@link OpMessages}); {@code HistoryState.label} is
 * the next undo's mode name and block count ("Fill 125").
 */
public final class OpActions {

    public static void applyOp(final ApplyOp p, final ServerPlayer player) {
        OpsServer.apply(player, p.mode(), p.params(), p.anchors(), p.face(), p.slot());
    }

    public static void cancelOp(final CancelOp p, final ServerPlayer player) {
        OpsServer.cancel(player);
    }

    public static void undo(final Undo p, final ServerPlayer player) {
        OpsServer.undo(player);
    }

    public static void redo(final Redo p, final ServerPlayer player) {
        OpsServer.redo(player);
    }

    public static void setSymmetry(final SetSymmetry p, final ServerPlayer player) {
        Symmetry.set(player, p.mode(), p.params(), p.centre());
    }

    private OpActions() {}
}
