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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * The client's building-mode state, shared by the mode controller ({@link ModeController}, D2), the build menu and
 * HUD (C, read-only users apart from {@link #setMode}/{@link #setParam}) and the renderers (B): the active mode,
 * per-mode parameters (persisted in {@link ModeSettings}), the selection being made, the live preview numbers
 * ({@link #stats()}, {@link #hints()}), short notices, and what the server reported (history depth, progress, last
 * result, clipboard, symmetry). Everything is render-thread only. Listeners get a {@link Change} tag per update so
 * the UI can animate exactly what changed.
 *
 * <p>Activating a mode goes through {@link #unavailableReason}: a mode the server cannot run (no Slate Building
 * there, building disabled, the mode disabled, spectator, a missing tool) is refused with a {@link #notice()} that
 * says why, and the current mode stays. Measure is client-only and always available.
 *
 * <p>Parameter edits are saved to {@code building.json} at most once per client tick (sliders fire per frame).
 * Leaving a world clears everything except the persisted parameters and the last mode id.
 */
public final class ClientModeState {

    /** Where the selection state machine is. */
    public enum Pending {
        /** No selection yet (or no mode). */
        NONE,
        /** AREA / MOVE / MEASURE: corner A placed, the box follows the crosshair. */
        FIRST_ANCHOR,
        /** AREA / MOVE / MEASURE: the selection is complete and previewed; the next confirm applies. */
        SELECTED,
        /** POINT: the ghost is shown at the clicked point; the next click applies. */
        PREVIEW,
        /** MOVE: selection made, the destination follows the crosshair. */
        DESTINATION,
        /** Sent to the server, waiting for its result (a new click starts a new selection). */
        APPLYING
    }

    /** What an update touched. */
    public enum Change { MODE, PARAMS, SELECTION, HISTORY, PROGRESS, RESULT, CLIPBOARD, SYMMETRY, STATS, NOTICE }

    /** Server-reported progress of a running operation. */
    public record Progress(int op, int done, int total, String mode) {
        public float fraction() {
            return total <= 0 ? 0F : Math.min(1F, done / (float) total);
        }
    }

    /** The symmetry the server applies (TOGGLE modes). */
    public record Symmetry(BuildMode mode, ModeParams params, BlockPos centre) {}

    /**
     * One material the previewed operation needs (the HUD's "384/512 Oak Planks").
     *
     * @param material  the full block paid with (any shape of it counts)
     * @param icon      a stack to draw for it
     * @param needed    material units the plan places
     * @param available units carried right now (inventory + toolbox pouch; a Supply Link container is not visible
     *                  to the client, see {@link Stats#supplyLink()})
     */
    public record MaterialNeed(Block material, ItemStack icon, int needed, int available) {
        public boolean enough() { return available >= needed; }

        public Component name() { return icon.getHoverName(); }
    }

    /** A key hint for the HUD chip: {@code key} ("RMB", "Ctrl + Scroll", a bound key's name) and what it does. */
    public record Hint(Component key, Component action) {}

    /** How loud a {@link Notice} is. */
    public enum Severity { INFO, WARNING, ERROR }

    /** A short message about the last thing the player tried (refused activation, nothing to apply, ...). */
    public record Notice(Component text, Severity severity, long atMs) {}

    /**
     * Live numbers of the current selection, computed client-side from the preview plan and the inventory.
     *
     * @param sizeX       selection width in blocks (0: no box yet)
     * @param sizeY       height
     * @param sizeZ       length
     * @param volume      {@code sizeX * sizeY * sizeZ}
     * @param blocks      positions the plan changes (0 until planned)
     * @param requested   positions the mode wanted before filters and limits ({@code Plan.requestedCount})
     * @param place       planned placements into air / replaceables
     * @param replace     planned replacements of existing blocks
     * @param remove      planned removals
     * @param missing     placements the carried materials do not cover (shown as invalid ghosts)
     * @param blocked     positions that cannot change here (outside the world border / build height, unbreakable)
     * @param materials   materials needed, most needed first (empty in creative)
     * @param planned     whether {@code blocks..materials} come from a plan (false: too large for a live preview, or
     *                    the selection is not complete)
     * @param ghostsHidden the plan is larger than {@code preview.maxBlocks}: only the box is drawn
     * @param creative    no cost, no drops
     * @param supplyLink  a Supply Link upgrade may pay what the inventory lacks
     * @param distance    A→B distance between block centres (measure, line), 0 otherwise
     * @param radius      sphere / cylinder radius, 0 otherwise
     * @param height      cylinder height, 0 otherwise
     * @param error       why the selection cannot be applied, null when it can
     */
    public record Stats(int sizeX, int sizeY, int sizeZ, long volume, int blocks, int requested, int place, int replace,
                        int remove, int missing, int blocked, List<MaterialNeed> materials, boolean planned,
                        boolean ghostsHidden, boolean creative, boolean supplyLink, double distance, int radius,
                        int height, @Nullable Component error) {

        public static final Stats EMPTY = new Stats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), false, false, false,
            false, 0, 0, 0, null);

        public Stats {
            materials = List.copyOf(materials);
        }

        public boolean hasBox() { return sizeX > 0 && sizeY > 0 && sizeZ > 0; }

        public boolean ok() { return error == null; }

        /** "12 × 4 × 8" (empty without a box). */
        public Component sizeText() {
            return hasBox() ? Component.translatable("slate_building.stats.size", sizeX, sizeY, sizeZ) : Component.empty();
        }

        /**
         * One line for the HUD chip: "12 × 4 × 8 · 384 blocks · 384/512 Oak Planks", or the error, or the measure
         * numbers. Empty when there is nothing to say yet.
         */
        public Component summary() {
            if (error != null) return hasBox() ? join(sizeText(), error.copy()) : error.copy();
            MutableComponent out = Component.empty();
            boolean any = false;
            if (hasBox()) { out.append(sizeText()); any = true; }
            if (radius > 0) {
                out = any ? join(out, radiusText()) : radiusText();
                any = true;
            }
            if (planned) {
                final Component count = Component.translatable("slate_building.stats.blocks", blocks);
                out = any ? join(out, count) : count.copy();
                any = true;
                if (!creative && !materials.isEmpty()) {
                    final MaterialNeed top = materials.get(0);
                    out = join(out, Component.translatable("slate_building.stats.material", Math.min(top.available(), top.needed()), top.needed(), top.name()));
                    if (materials.size() > 1) out.append(Component.translatable("slate_building.stats.more_materials", materials.size() - 1));
                }
            } else if (hasBox() && volume > 1) {
                out = join(out, Component.translatable("slate_building.stats.volume", volume));
            }
            if (distance > 0) {
                final Component d = Component.translatable("slate_building.stats.distance", String.format(java.util.Locale.ROOT, "%.1f", distance));
                out = any ? join(out, d) : d.copy();
            }
            return out;
        }

        private MutableComponent radiusText() {
            return height > 0
                ? Component.translatable("slate_building.stats.radius_height", radius, height)
                : Component.translatable("slate_building.stats.radius", radius);
        }

        private static MutableComponent join(final Component a, final Component b) {
            return Component.empty().append(a).append(Component.translatable("slate_building.stats.separator")).append(b);
        }
    }

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
    private static int clipboardVersion;
    private static @Nullable Symmetry symmetry;
    private static Stats stats = Stats.EMPTY;
    private static List<Hint> hints = List.of();
    private static @Nullable Notice notice;
    private static boolean paramsDirty;
    private static boolean resetting;
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

    /**
     * Why {@code mode} cannot be activated right now (shown in the build menu and as a notice), or null when it can:
     * not in a world, the server lacks Slate Building, building modes or this mode are disabled there, spectator,
     * paste needs a permission level, or the toolbox lacks the tool ({@code Capabilities.lockReason}).
     */
    public static @Nullable Component unavailableReason(final BuildMode mode) {
        return ModeRules.unavailableReason(mode);
    }

    /**
     * Activates {@code mode} (null = leave building mode); clears the selection and remembers the mode id. A mode that
     * is {@link #unavailableReason unavailable} is refused with a notice and the current mode stays.
     */
    public static void setMode(final @Nullable BuildMode mode) {
        activate(mode);
    }

    /** {@link #setMode} that says whether {@code mode} is now current. */
    public static boolean activate(final @Nullable BuildMode mode) {
        if (mode == current) return true;
        if (mode != null) {
            final Component why = unavailableReason(mode);
            if (why != null) {
                notice(why, Severity.ERROR);
                ModeSounds.refused();
                return false;
            }
        }
        current = mode;
        clearSelectionSilently();
        if (mode != null) {
            final ModeSettings s = settings();
            if (!mode.id().equals(s.lastMode)) {
                s.lastMode = mode.id();
                paramsDirty = true;
            }
        }
        stats = Stats.EMPTY;
        fire(Change.MODE);
        fire(Change.SELECTION);
        fire(Change.STATS);
        return true;
    }

    /** The mode keybind action: activates {@code mode}, or leaves it when it is already current. */
    public static void toggle(final BuildMode mode) {
        activate(current == mode ? null : mode);
    }

    /** Leaves building mode (no-op when none is active). */
    public static void deactivate() {
        activate(null);
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
        final ModeParams p = params(mode);
        final Object before = p.get(paramId);
        p.set(paramId, value);
        if (java.util.Objects.equals(before, p.get(paramId))) return;
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

    /** Whether a selection is being made or previewed (left-click then cancels it, right-click never places). */
    public static boolean selectionPending() {
        return pending != Pending.NONE && pending != Pending.APPLYING;
    }

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

    /** Sets anchors, face and state in one step (one {@link Change#SELECTION} event). */
    public static void setSelection(final List<BlockPos> anchors, final @Nullable Direction newFace, final Pending newPending) {
        ANCHORS.clear();
        for (final BlockPos p : anchors) ANCHORS.add(p.immutable());
        face = newFace;
        pending = newPending;
        fire(Change.SELECTION);
    }

    public static void clearSelection() {
        clearSelectionSilently();
        fire(Change.SELECTION);
    }

    /** Distance of a corner placed on air (persisted in {@code modes.airDistance}). */
    public static int airDistance() {
        return Math.max(1, settings().airDistance);
    }

    public static void setAirDistance(final int blocks) {
        final ModeSettings s = settings();
        final int v = Math.max(1, blocks);
        if (s.airDistance == v) return;
        s.airDistance = v;
        paramsDirty = true;
        fire(Change.SELECTION);
    }

    /** The {@code modes} section of the client config (never null). */
    public static ModeSettings settings() {
        // A hand-edited file may say "modes": null or "params": null; Gson keeps that null.
        if (SlateBuilding.config().modes == null) SlateBuilding.config().modes = new ModeSettings();
        final ModeSettings s = SlateBuilding.config().modes;
        if (s.params == null) s.params = new JsonObject();
        return s;
    }

    // ---- preview numbers, hints, notices (written by the mode controller) ----

    /** Live numbers of the current selection ({@link Stats#EMPTY} without one). */
    public static Stats stats() { return stats; }

    public static void setStats(final Stats newStats) {
        if (newStats.equals(stats)) return;
        stats = newStats;
        fire(Change.STATS);
    }

    /** Key hints for the current state of the selection, most important first (empty without a mode). */
    public static List<Hint> hints() { return hints; }

    public static void setHints(final List<Hint> newHints) {
        final List<Hint> copy = List.copyOf(newHints);
        if (copy.equals(hints)) return;
        hints = copy;
        fire(Change.STATS);
    }

    /** The last notice (check {@link Notice#atMs()} to fade it), null when none. */
    public static @Nullable Notice notice() { return notice; }

    /**
     * Tells the player something about the building mode: stored for the HUD ({@link Change#NOTICE}) and shown on the
     * vanilla action bar, so it is visible even with the HUD chip hidden. Never a toast.
     */
    public static void notice(final Component text, final Severity severity) {
        notice = new Notice(text, severity, Util.getMillis());
        ModeRules.showActionBar(text, severity);
        fire(Change.NOTICE);
    }

    // ---- server reports ----

    public static int undoCount() { return undoCount; }

    public static int redoCount() { return redoCount; }

    /** Label of the operation the next undo would revert ("Fill 384"), empty when unknown. */
    public static String undoLabel() { return undoLabel; }

    public static void setHistory(final int undo, final int redo, final String label) {
        undoCount = Math.max(0, undo);
        redoCount = Math.max(0, redo);
        undoLabel = label == null ? "" : label;
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

    /** Bumped on every clipboard sync (cache key for the decoded clipboard and the paste preview). */
    public static int clipboardVersion() { return clipboardVersion; }

    public static void setClipboard(final @Nullable CompoundTag tag) {
        clipboard = tag == null || tag.isEmpty() ? null : tag;
        clipboardVersion++;
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
        clipboardVersion++;
        symmetry = null;
        stats = Stats.EMPTY;
        hints = List.of();
        notice = null;
        resetting = true;
        try {
            for (final Change c : Change.values()) fire(c);
        } finally {
            resetting = false;
        }
    }

    /** True while {@link #resetSession} notifies listeners (the connection is going away: send nothing, play nothing). */
    public static boolean isResetting() {
        return resetting;
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
