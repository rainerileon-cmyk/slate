package dev.fallingcloud.slate.building.ops;

import dev.fallingcloud.slate.building.net.ApplyOp;
import dev.fallingcloud.slate.building.net.CancelOp;
import dev.fallingcloud.slate.building.net.Redo;
import dev.fallingcloud.slate.building.net.SetSymmetry;
import dev.fallingcloud.slate.building.net.Undo;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server handlers of the building-mode payloads (server thread, from {@code BuildingNetwork}): validate (mode
 * unlocked, reach, limits, game mode, rate limit), re-plan with the same {@link Planners} the client previewed with,
 * charge and execute over ticks, keep history, answer with {@code HistoryState} / {@code OpProgress} /
 * {@code OpResult} / {@code ClipboardSync} / {@code SymmetryState}.
 *
 * <p>Owner: D1 (ops server). Skeleton stub: ignores the requests.
 */
public final class OpActions {

    public static void applyOp(final ApplyOp p, final ServerPlayer player) {
    }

    public static void cancelOp(final CancelOp p, final ServerPlayer player) {
    }

    public static void undo(final Undo p, final ServerPlayer player) {
    }

    public static void redo(final Redo p, final ServerPlayer player) {
    }

    public static void setSymmetry(final SetSymmetry p, final ServerPlayer player) {
    }

    private OpActions() {}
}
