package dev.fallingcloud.slate.building.client;

import dev.fallingcloud.slate.building.chisel.client.ChiselClient;
import dev.fallingcloud.slate.building.client.mode.ClientModeState;
import dev.fallingcloud.slate.building.net.ChiselGroupsSync;
import dev.fallingcloud.slate.building.net.ClipboardSync;
import dev.fallingcloud.slate.building.net.HistoryState;
import dev.fallingcloud.slate.building.net.OpProgress;
import dev.fallingcloud.slate.building.net.OpResult;
import dev.fallingcloud.slate.building.net.SymmetryState;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.ModeParams;

/**
 * Client handlers of the clientbound payloads (render thread, from {@code BuildingNetwork}). The building-mode
 * reports land in {@link ClientModeState}, where the mode controller (D2), the HUD and build menu (C) and the
 * renderers (B) read them and get change notifications; chisel data goes to the chisel client (I). Owners react
 * through those, so this routing file does not need to change.
 */
public final class ClientActions {

    public static void chiselGroupsSync(final ChiselGroupsSync p) {
        ChiselClient.onGroupsSync(p);
    }

    public static void historyState(final HistoryState p) {
        ClientModeState.setHistory(p.undo(), p.redo(), p.label());
    }

    public static void opProgress(final OpProgress p) {
        ClientModeState.setProgress(new ClientModeState.Progress(p.op(), p.done(), p.total(), p.mode()));
    }

    public static void opResult(final OpResult p) {
        ClientModeState.onResult(p);
    }

    public static void clipboardSync(final ClipboardSync p) {
        ClientModeState.setClipboard(p.tag());
    }

    /** An empty or unknown mode id means symmetry is off. */
    public static void symmetryState(final SymmetryState p) {
        final BuildMode mode = BuildModes.byId(p.mode());
        ClientModeState.setSymmetry(mode == null ? null : new ClientModeState.Symmetry(mode, ModeParams.fromTag(mode, p.params()), p.centre()));
    }

    private ClientActions() {}
}
