package dev.fallingcloud.slate.building.client.mode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.config.ModeSettings;
import dev.fallingcloud.slate.building.net.OpResult;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.ModeParams;
import dev.fallingcloud.slate.core.event.SlateEvents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * The client's building-mode state, shared by the mode controller (D2, which owns this class after the skeleton),
 * the build menu and HUD (C, read-only users) and the renderers (B): the active mode, per-mode parameters
 * (persisted in {@link ModeSettings}), the selection being made, and what the server reported (history depth,
 * progress, last result, clipboard, symmetry). Everything is render-thread only. Listeners get a {@link Change}
 * tag per update so the UI can animate exactly what changed.
 *
 * <p>Parameter edits are saved to {@code building.json} at most once per client tick (sliders fire per frame).
 * Leaving a world clears everything except the persisted parameters and the last mode id.
 */
public final class ClientModeState {

    /** Where the selection state machine is. D2 may add states at the end. */
    public enum Pending {
        /** No selection yet (or no mode). */
        NONE,
        /** AREA / MOVE: corner A placed, the box follows the crosshair. */
        FIRST_ANCHOR,
        /** AREA: both corners placed, the plan is previewed; the next confirm applies. */
        SELECTED,
        /** POINT: the ghost is shown at the clicked point; the next click applies. */
        PREVIEW,
        /** MOVE: selection made, choosing the destination. */
        DESTINATION,
        /** Sent to the server, waiting for its result. */
        APPLYING
    }

    /** What an update touched. */
    public enum Change { MODE, PARAMS, SELECTION, HISTORY, PROGRESS, RESULT, CLIPBOARD, SYMMETRY }

    /** Server-reported progress of a running operation. */
    public record Progress(int op, int done, int total, String mode) {
        public float fraction() {
            return total <= 0 ? 0F : Math.min(1F, done / (float) total);
        }
    }

    /** The symmetry the server applies (TOGGLE modes). */
    public record Symmetry(BuildMode mode, ModeParams params, BlockPos centre) {}

    private static @Nullable BuildMode current;
    private static final Map<String, ModeParams> PARAMS = new HashMap<>();
    private static final List<BlockPos> ANCHORS = new ArrayList<>();
    private static @Nullable Direction face;
    private static Pending pending = Pending.NONE;
    private static int undoCount;
    private static int redoCount;
    private static String undoLabel = "";
    private static @Nullable Progress progress;
    private static @Nullable OpResult lastResult;
    private static long lastResultAtMs;
    private static @Nullable CompoundTag clipboard;
    private static @Nullable Symmetry symmetry;
    private static boolean paramsDirty;
    private static final List<Consumer<Change>> LISTENERS = new CopyOnWriteArrayList<>();
    private static boolean initialised;

    /** Wires persistence and the leave-world reset. Called once from {@code BuildingClient.init()}. */
    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        SlateEvents.CLIENT_TICK_END.register(ClientModeState::flushParams);
        SlateEvents.CLIENT_LEFT_SERVER.register(ClientModeState::resetSession);
    }

    // ---- mode ----

    public static @Nullable BuildMode current() { return current; }

    public static boolean isActive() { return current != null; }

    /** Activates {@code mode} (null = leave building mode); clears the selection and remembers the mode id. */
    public static void setMode(final @Nullable BuildMode mode) {
        if (mode == current) return;
        current = mode;
        clearSelectionSilently();
        if (mode != null) {
            final ModeSettings s = settings();
            if (!mode.id().equals(s.lastMode)) {
                s.lastMode = mode.id();
                paramsDirty = true;
            }
        }
        fire(Change.MODE);
        fire(Change.SELECTION);
    }

    /** The last mode the player used (persisted), for "re-activate" shortcuts. */
    public static @Nullable BuildMode lastMode() {
        return BuildModes.byId(settings().lastMode);
    }

    // ---- parameters ----

    /** The live parameters of {@code mode} (loaded from the config on first use). Mutate only through {@link #setParam}. */
    public static ModeParams params(final BuildMode mode) {
        return PARAMS.computeIfAbsent(mode.id(), id -> {
            final JsonElement saved = settings().params.get(id);
            return saved instanceof JsonObject obj ? ModeParams.fromJson(mode, obj) : ModeParams.defaults(mode);
        });
    }

    public static void setParam(final BuildMode mode, final String paramId, final Object value) {
        params(mode).set(paramId, value);
        paramsDirty = true;
        fire(Change.PARAMS);
    }

    /** Replaces all parameters of a mode (e.g. "reset to defaults"). */
    public static void setParams(final ModeParams params) {
        PARAMS.put(params.mode().id(), params.copy());
        paramsDirty = true;
        fire(Change.PARAMS);
    }

    // ---- selection ----

    public static List<BlockPos> anchors() { return List.copyOf(ANCHORS); }

    public static @Nullable Direction face() { return face; }

    public static Pending pending() { return pending; }

    public static void addAnchor(final BlockPos pos) {
        ANCHORS.add(pos.immutable());
        fire(Change.SELECTION);
    }

    /** Replaces anchor {@code index} (e.g. pushing a box face with Ctrl+scroll); appends when {@code index == size}. */
    public static void setAnchor(final int index, final BlockPos pos) {
        if (index == ANCHORS.size()) ANCHORS.add(pos.immutable());
        else ANCHORS.set(index, pos.immutable());
        fire(Change.SELECTION);
    }

    public static void setAnchors(final List<BlockPos> anchors) {
        ANCHORS.clear();
        for (final BlockPos p : anchors) ANCHORS.add(p.immutable());
        fire(Change.SELECTION);
    }

    public static void setFace(final @Nullable Direction newFace) {
        face = newFace;
        fire(Change.SELECTION);
    }

    public static void setPending(final Pending newPending) {
        if (pending == newPending) return;
        pending = newPending;
        fire(Change.SELECTION);
    }

    public static void clearSelection() {
        clearSelectionSilently();
        fire(Change.SELECTION);
    }

    // ---- server reports ----

    public static int undoCount() { return undoCount; }

    public static int redoCount() { return redoCount; }

    /** Label of the operation the next undo would revert ("Fill 384"), empty when unknown. */
    public static String undoLabel() { return undoLabel; }

    public static void setHistory(final int undo, final int redo, final String label) {
        undoCount = Math.max(0, undo);
        redoCount = Math.max(0, redo);
        undoLabel = label;
        fire(Change.HISTORY);
    }

    public static @Nullable Progress progress() { return progress; }

    public static void setProgress(final @Nullable Progress newProgress) {
        progress = newProgress;
        fire(Change.PROGRESS);
    }

    public static @Nullable OpResult lastResult() { return lastResult; }

    /** {@code Util.getMillis()} when {@link #lastResult()} arrived (for fading the result line). */
    public static long lastResultAtMs() { return lastResultAtMs; }

    /** An operation finished: remembers the result and drops its progress bar. */
    public static void onResult(final OpResult result) {
        lastResult = result;
        lastResultAtMs = Util.getMillis();
        if (progress != null && progress.op() == result.op()) progress = null;
        fire(Change.PROGRESS);
        fire(Change.RESULT);
    }

    /** The clipboard as the server sent it (D1's format), null when empty. */
    public static @Nullable CompoundTag clipboard() { return clipboard; }

    public static void setClipboard(final @Nullable CompoundTag tag) {
        clipboard = tag == null || tag.isEmpty() ? null : tag;
        fire(Change.CLIPBOARD);
    }

    public static @Nullable Symmetry symmetry() { return symmetry; }

    public static void setSymmetry(final @Nullable Symmetry newSymmetry) {
        symmetry = newSymmetry;
        fire(Change.SYMMETRY);
    }

    // ---- listeners ----

    public static void addListener(final Consumer<Change> listener) { LISTENERS.add(listener); }

    public static void removeListener(final Consumer<Change> listener) { LISTENERS.remove(listener); }

    /** Leaving a world: drop the session state, keep the persisted parameters. */
    public static void resetSession() {
        current = null;
        clearSelectionSilently();
        undoCount = 0;
        redoCount = 0;
        undoLabel = "";
        progress = null;
        lastResult = null;
        clipboard = null;
        symmetry = null;
        for (final Change c : Change.values()) fire(c);
    }

    private static void clearSelectionSilently() {
        ANCHORS.clear();
        face = null;
        pending = Pending.NONE;
    }

    private static void flushParams() {
        if (!paramsDirty) return;
        paramsDirty = false;
        final ModeSettings s = settings();
        for (final ModeParams p : PARAMS.values()) s.params.add(p.mode().id(), p.toJson());
        SlateBuilding.configFile().save();
    }

    private static ModeSettings settings() {
        // A hand-edited file may say "modes": null or "params": null; Gson keeps that null.
        if (SlateBuilding.config().modes == null) SlateBuilding.config().modes = new ModeSettings();
        final ModeSettings s = SlateBuilding.config().modes;
        if (s.params == null) s.params = new JsonObject();
        return s;
    }

    private static void fire(final Change change) {
        for (final Consumer<Change> l : LISTENERS) {
            try {
                l.accept(change);
            } catch (final RuntimeException e) {
                SlateBuilding.LOGGER.error("[Slate Building] mode state listener failed on {}", change, e);
            }
        }
    }

    private ClientModeState() {}
}
