package dev.fallingcloud.slate.building.ops.server;

import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.ServerOps;
import dev.fallingcloud.slate.building.net.ClipboardSync;
import dev.fallingcloud.slate.building.net.HistoryState;
import dev.fallingcloud.slate.building.net.OpProgress;
import dev.fallingcloud.slate.building.net.OpResult;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.Change;
import dev.fallingcloud.slate.building.ops.Clipboard;
import dev.fallingcloud.slate.building.ops.Limits;
import dev.fallingcloud.slate.building.ops.ModeKind;
import dev.fallingcloud.slate.building.ops.ModeParams;
import dev.fallingcloud.slate.building.ops.OpMessages;
import dev.fallingcloud.slate.building.ops.Palette;
import dev.fallingcloud.slate.building.ops.Plan;
import dev.fallingcloud.slate.building.ops.PlanContext;
import dev.fallingcloud.slate.building.ops.Planners;
import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.ops.plan.Box;
import dev.fallingcloud.slate.building.ops.plan.ClipboardPlanners;
import dev.fallingcloud.slate.building.platform.BuildingPlatform;
import dev.fallingcloud.slate.building.toolbox.ToolTier;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

/**
 * The building-operation server (server thread only): per-player sessions (running op, history, clipboard,
 * symmetry), validation, the tick loop that runs every op under the per-op and global block budgets, and the replies
 * ({@code OpProgress} every few ticks, {@code OpResult} at the end, {@code HistoryState} whenever the history moves,
 * {@code ClipboardSync} after a copy).
 */
public final class OpsServer {

    /** Extra reach tolerance for latency and movement between the click and the packet. */
    private static final double REACH_SLACK = 1.5;

    static final class Session {
        final History history = new History();
        @Nullable RunningOp running;
        @Nullable Clipboard clipboard;
        @Nullable Symmetry.Settings symmetry;
        @Nullable OpResult lastResult;
        long lastOpTick = Long.MIN_VALUE / 2;
        int nextOp = 1;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final List<RunningOp> RUNNING = new ArrayList<>();
    private static int roundRobin;

    // ---- lifecycle (OpsSystem) ----

    public static void onJoin(final ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        sendHistory(player, session(player));
    }

    /** Logout: the running op stops and settles (refunds paid out, history dropped with the session). */
    public static void onLeave(final ServerPlayer player) {
        final Session s = SESSIONS.remove(player.getUUID());
        if (s == null) return;
        if (s.running != null) {
            s.running.cancelled = true;
            finish(player.server, s.running, s, player);
        }
        s.history.clear();
    }

    public static void onStopping(final MinecraftServer server) {
        for (final RunningOp op : List.copyOf(RUNNING)) {
            final ServerPlayer p = server.getPlayerList().getPlayer(op.playerId);
            op.cancelled = true;
            final Session s = SESSIONS.get(op.playerId);
            if (p != null && s != null) finish(server, op, s, p);
        }
        RUNNING.clear();
        SESSIONS.clear();
    }

    static Session session(final ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
    }

    /** The session of {@code player} if they have one (never creates one: fake players place blocks too). */
    static @Nullable Session existingSession(final ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    /** Whether {@code player} has an operation running (tests, commands). */
    public static boolean isBusy(final ServerPlayer player) {
        final Session s = SESSIONS.get(player.getUUID());
        return s != null && s.running != null;
    }

    public static @Nullable Clipboard clipboard(final ServerPlayer player) {
        final Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.clipboard;
    }

    public static int undoCount(final ServerPlayer player) {
        final Session s = SESSIONS.get(player.getUUID());
        return s == null ? 0 : s.history.undoCount();
    }

    public static int redoCount(final ServerPlayer player) {
        final Session s = SESSIONS.get(player.getUUID());
        return s == null ? 0 : s.history.redoCount();
    }

    // ---- requests ----

    /** {@code ApplyOp}: validate, plan, charge and start (copy runs at once). */
    public static void apply(final ServerPlayer player, final String modeId, final CompoundTag paramsTag, final List<BlockPos> anchors,
                             final Direction face, final int slot) {
        final BuildMode mode = BuildModes.byId(modeId);
        if (mode == null) return;
        final Session s = session(player);
        final int op = s.nextOp++;
        final ServerOps rules = BuildingServerSettings.local().ops();
        final ToolboxAccess.Capabilities caps = ToolboxAccess.of(player);
        final Limits limits = caps.limits(BuildingServerSettings.local());

        final Component refused = validate(player, mode, anchors, s, rules, caps, limits);
        if (refused != null) {
            fail(player, op, mode.id(), refused);
            return;
        }
        s.lastOpTick = player.server.getTickCount();

        final ModeParams params = ModeParams.fromTag(mode, paramsTag);
        final int paySlot = Inventory.isHotbarSlot(slot) ? slot : player.getInventory().selected;
        final PlanContext ctx = PlanContext.create(player, mode, params, anchors, face, paySlot, s.clipboard);
        final Plan plan = Planners.plan(ctx);
        if (!plan.ok()) {
            fail(player, op, mode.id(), plan.error());
            return;
        }
        final boolean free = caps.creative();

        if (mode == BuildModes.COPY || mode == BuildModes.CUT) {
            final Box box = Box.of(ctx.anchor(0), ctx.anchor(1));
            final Clipboard clip = Clipboard.read(player.serverLevel(), box.min(), box.max(),
                mode == BuildModes.COPY && params.getBool("includeAir"), free);
            s.clipboard = clip;
            SlateNetwork.get().sendToPlayer(player, new ClipboardSync(clip.toTag()));
            if (mode == BuildModes.COPY) {
                final int n = clip.blockCount();
                send(player, new OpResult(op, mode.id(), 0, 0, 0, "slate_building.result.copied", List.of(String.valueOf(n))));
                return;
            }
        }
        if (plan.isEmpty()) {
            send(player, new OpResult(op, mode.id(), 0, 0, 0, "slate_building.result.nothing", List.of()));
            return;
        }

        final Economy economy = new Economy(player, free, paySlot);
        if (!free) {
            final Component shortfall = affordability(mode, plan, economy, rules.placeWhatYouCan);
            if (shortfall != null) {
                fail(player, op, mode.id(), shortfall);
                return;
            }
        }

        final Long2ObjectMap<CompoundTag> data = free ? carriedData(player, mode, ctx, s) : null;
        final List<RunningOp.Step> steps = new ArrayList<>(plan.changes().size());
        for (final Change c : plan.changes()) steps.add(RunningOp.Step.of(c, data == null ? null : data.get(c.pos().asLong())));
        final Box credit = mode == BuildModes.MOVE ? Box.of(ctx.anchor(0), ctx.anchor(1)) : null;
        final List<ItemStack> prefer = new ArrayList<>();
        for (final Palette.WeightedEntry e : ctx.palette().entries()) prefer.add(e.stack());
        final RunningOp running = new RunningOp(op, RunningOp.Kind.APPLY, mode, player, player.level().dimension(), steps, economy,
            free, limits.blocksPerTick(), caps.tier(ToolType.HAMMER), credit, face, prefer);
        start(player, s, running);
    }

    /** {@code CancelOp}: stop the running operation; what was done stays and can be undone. */
    public static void cancel(final ServerPlayer player) {
        final Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.running == null) return;
        s.running.cancelled = true;
        finish(player.server, s.running, s, player);
    }

    public static void undo(final ServerPlayer player) {
        revert(player, true);
    }

    public static void redo(final ServerPlayer player) {
        revert(player, false);
    }

    private static void revert(final ServerPlayer player, final boolean undo) {
        final Session s = session(player);
        final int op = s.nextOp++;
        final String label = undo ? "undo" : "redo";
        final ServerOps rules = BuildingServerSettings.local().ops();
        if (!rules.enabled) {
            fail(player, op, label, Component.translatable("slate_building.plan.disabled"));
            return;
        }
        if (player.isSpectator() || !player.mayBuild() || BuildingPlatform.get().isFakePlayer(player)) {
            fail(player, op, label, Component.translatable("slate_building.error.game_mode"));
            return;
        }
        if (s.running != null) {
            fail(player, op, label, Component.translatable("slate_building.error.busy"));
            return;
        }
        final ToolboxAccess.Capabilities caps = ToolboxAccess.of(player);
        final boolean free = caps.creative();
        final History.Entry next = undo ? s.history.peekUndo() : s.history.peekRedo();
        if (next == null) {
            fail(player, op, label, Component.translatable(undo ? "slate_building.error.nothing_to_undo" : "slate_building.error.nothing_to_redo"));
            return;
        }
        // Creative work never turns into free survival blocks (nor refunds): it can only be reverted in creative.
        if (next.free() && !free) {
            fail(player, op, label, Component.translatable("slate_building.error.creative_history"));
            return;
        }
        final History.Entry entry = undo ? s.history.popUndo() : s.history.popRedo();
        final ServerLevel level = player.server.getLevel(entry.dimension());
        if (level == null) {
            sendHistory(player, s);
            return;
        }
        final List<RunningOp.Step> steps = new ArrayList<>(entry.size());
        for (int i = entry.records().size() - 1; i >= 0; i--) steps.add(RunningOp.Step.revert(entry.records().get(i)));
        final Limits limits = caps.limits(BuildingServerSettings.local());
        final RunningOp running = new RunningOp(op, undo ? RunningOp.Kind.UNDO : RunningOp.Kind.REDO, entry.mode(), player,
            entry.dimension(), steps, new Economy(player, free, player.getInventory().selected), free,
            Math.max(limits.blocksPerTick(), ToolTier.index(rules.blocksPerTick, ToolTier.MIN)), caps.tier(ToolType.HAMMER), null,
            Direction.UP, List.of());
        running.sourceFree = entry.free();
        start(player, s, running);
    }

    private static void start(final ServerPlayer player, final Session s, final RunningOp running) {
        s.running = running;
        RUNNING.add(running);
        send(player, new OpProgress(running.id, 0, running.steps.size(), running.mode.id()));
        // Small operations finish in the tick they were requested in: no progress bar flicker, instant feedback.
        final ServerOps rules = BuildingServerSettings.local().ops();
        if (running.steps.size() <= running.blocksPerTick()) {
            final ServerLevel level = player.server.getLevel(running.dimension);
            if (level != null) running.tick(level, player, running.blocksPerTick(), rules);
            if (running.done()) finish(player.server, running, s, player);
        }
    }

    // ---- tick ----

    /** Runs every operation for one server tick, sharing {@code globalBlocksPerTick} fairly (round robin). */
    public static void tick(final MinecraftServer server) {
        if (RUNNING.isEmpty()) return;
        final ServerOps rules = BuildingServerSettings.local().ops();
        int budget = Math.max(1, rules.globalBlocksPerTick);
        final List<RunningOp> ops = List.copyOf(RUNNING);
        final int n = ops.size();
        final int share = Math.max(1, budget / n);
        roundRobin = (roundRobin + 1) % n;
        for (int k = 0; k < n; k++) {
            final RunningOp op = ops.get((roundRobin + k) % n);
            final ServerPlayer player = server.getPlayerList().getPlayer(op.playerId);
            final Session s = SESSIONS.get(op.playerId);
            if (player == null || s == null) {
                RUNNING.remove(op);
                continue;
            }
            final ServerLevel level = server.getLevel(op.dimension);
            if (level == null) {
                op.cancelled = true;
                finish(server, op, s, player);
                continue;
            }
            if (budget > 0 && !op.done()) {
                final int allowed = Math.min(op.blocksPerTick(), Math.min(share, budget));
                budget -= op.tick(level, player, allowed, rules);
            }
            if (op.done()) finish(server, op, s, player);
            else if (op.progressDue()) send(player, new OpProgress(op.id, op.index, op.steps.size(), op.mode.id()));
        }
    }

    private static void finish(final MinecraftServer server, final RunningOp op, final Session s, final ServerPlayer player) {
        RUNNING.remove(op);
        if (s.running == op) s.running = null;
        op.economy.settle();
        final ServerOps rules = BuildingServerSettings.local().ops();
        final Limits limits = ToolboxAccess.of(player).limits(BuildingServerSettings.local());
        final int depth = Math.max(1, limits.undoDepth());
        final History.Entry done = new History.Entry(op.mode, op.dimension, List.copyOf(op.records), op.economy.isFree());
        // A revert moves what it reverted to the other stack; what it could not pay for (or did not reach) stays where
        // it was, so getting the items and pressing undo again finishes the job.
        final History.Entry rest = op.kind == RunningOp.Kind.APPLY ? null
            : new History.Entry(op.mode, op.dimension, op.leftover(), op.sourceFree);
        switch (op.kind) {
            case APPLY -> s.history.pushNew(done, depth, rules.maxUndoBlocks);
            case UNDO -> {
                s.history.pushRedo(done, depth, rules.maxUndoBlocks);
                s.history.pushUndo(rest, depth, rules.maxUndoBlocks);
            }
            case REDO -> {
                s.history.pushUndo(done, depth, rules.maxUndoBlocks);
                s.history.pushRedo(rest, depth, rules.maxUndoBlocks);
            }
        }
        send(player, OpMessages.result(op.id, op.mode, op.kind.name(), op.placed, op.replaced, op.removed, op.skipped, op.missing,
            op.cancelled && op.index < op.steps.size(), op.index, op.steps.size()));
        sendHistory(player, s);
    }

    // ---- validation ----

    private static @Nullable Component validate(final ServerPlayer player, final BuildMode mode, final List<BlockPos> anchors, final Session s,
                                                final ServerOps rules, final ToolboxAccess.Capabilities caps, final Limits limits) {
        if (!rules.enabled) return Component.translatable("slate_building.plan.disabled");
        if (BuildingPlatform.get().isFakePlayer(player) || player.isSpectator()) return Component.translatable("slate_building.error.game_mode");
        if (mode.changesWorld() && !player.mayBuild()) return Component.translatable("slate_building.error.game_mode");
        if (mode.kind() == ModeKind.TOGGLE || mode.kind() == ModeKind.MEASURE) return Component.translatable("slate_building.error.not_applicable");
        if (rules.disabledModes != null && rules.disabledModes.contains(mode.id())) return Component.translatable("slate_building.plan.mode_disabled");
        if (!caps.unlocked(mode)) return lockError(mode);
        if (s.running != null) return Component.translatable("slate_building.error.busy");
        if (player.server.getTickCount() - s.lastOpTick < Math.max(0, rules.minTicksBetweenOps)) return Component.translatable("slate_building.error.too_fast");
        if (mode == BuildModes.PASTE && rules.pasteOpLevel > 0 && !player.hasPermissions(rules.pasteOpLevel)) {
            return Component.translatable("slate_building.error.permission");
        }
        final int needed = mode.kind() == ModeKind.MOVE ? 3 : 1;
        if (anchors.size() < needed) return Component.translatable("slate_building.plan.no_anchor");
        boolean near = false;
        for (final BlockPos a : anchors) {
            if (!player.level().isLoaded(a)) return Component.translatable("slate_building.error.unloaded");
            if (player.canInteractWithBlock(a, limits.reachBonus() + REACH_SLACK)) near = true;
            if (!player.canInteractWithBlock(a, limits.reachBonus() + limits.maxSpan() + REACH_SLACK)) {
                return Component.translatable("slate_building.error.too_far");
            }
        }
        return near ? null : Component.translatable("slate_building.error.too_far");
    }

    /** "Needs: Iron Hammer" in a form {@link OpMessages#error} can send (plain lang-key arguments). */
    static Component lockError(final BuildMode mode) {
        final ToolType tool = mode.tool();
        if (tool == null) return Component.translatable("slate_building.plan.failed");
        return Component.translatable("slate_building.error.locked", ToolTier.byLevel(mode.minTier()).displayName(), tool.displayName());
    }

    /**
     * Refuses an operation the player cannot pay: with {@code placeWhatYouCan} only when they have none of any
     * material it needs, otherwise when anything is short.
     */
    private static @Nullable Component affordability(final BuildMode mode, final Plan plan, final Economy economy, final boolean placeWhatYouCan) {
        // Reshape and move pay (partly) with what they lift; they settle block by block.
        if (mode == BuildModes.RESHAPE || mode == BuildModes.MOVE) return null;
        final Map<CostKey, Integer> need = new HashMap<>();
        for (final Change c : plan.changes()) {
            if (c.kind() == Change.Kind.BREAK) continue;
            final CostKey key = CostKey.of(c.target(), c.targetVariant());
            if (key != null) need.merge(key, 1, Integer::sum);
        }
        if (need.isEmpty()) return null;
        boolean any = false;
        for (final Map.Entry<CostKey, Integer> e : need.entrySet()) {
            final int have = economy.available(e.getKey());
            if (have > 0) any = true;
            if (!placeWhatYouCan && have < e.getValue()) {
                return Component.translatable("slate_building.error.not_enough", e.getKey().name(), have, e.getValue());
            }
        }
        if (!any) return Component.translatable("slate_building.error.no_materials", need.keySet().iterator().next().name());
        return null;
    }

    // ---- creative block-entity data ----

    /** Creative paste / move carry block-entity contents: target position → data. */
    private static @Nullable Long2ObjectMap<CompoundTag> carriedData(final ServerPlayer player, final BuildMode mode, final PlanContext ctx,
                                                                   final Session s) {
        final Long2ObjectMap<CompoundTag> out = new Long2ObjectOpenHashMap<>();
        if (mode == BuildModes.PASTE && s.clipboard != null) {
            final Rotation rotation = Clipboard.rotation(ctx.params().getChoice("rotation"));
            final Mirror mirror = Clipboard.mirror(ctx.params().getChoice("mirror"));
            final Clipboard t = s.clipboard.transformed(rotation, mirror);
            final BlockPos origin = ClipboardPlanners.origin(ctx.anchor(0), ctx.face(), t.size());
            for (final Clipboard.Entry e : t.entries()) if (e.blockEntity() != null) out.put(origin.offset(e.offset()).asLong(), e.blockEntity());
        } else if (mode == BuildModes.MOVE && ctx.anchors().size() >= 3) {
            final Box src = Box.of(ctx.anchor(0), ctx.anchor(1));
            final Clipboard clip = Clipboard.read(player.serverLevel(), src.min(), src.max(), false, true);
            final Rotation rotation = Clipboard.rotation(ctx.params().getChoice("rotation"));
            final Mirror mirror = Clipboard.mirror(ctx.params().getChoice("mirror"));
            final Clipboard t = clip.transformed(rotation, mirror);
            final BlockPos origin = ClipboardPlanners.origin(ctx.anchors().get(2), ctx.face(), t.size());
            for (final Clipboard.Entry e : t.entries()) if (e.blockEntity() != null) out.put(origin.offset(e.offset()).asLong(), e.blockEntity());
        }
        return out.isEmpty() ? null : out;
    }

    // ---- replies ----

    private static void fail(final ServerPlayer player, final int op, final String mode, final @Nullable Component reason) {
        send(player, OpMessages.error(op, mode, reason));
    }

    static void sendHistory(final ServerPlayer player, final Session s) {
        final History.Entry top = s.history.peekUndo();
        send(player, new HistoryState(s.history.undoCount(), s.history.redoCount(), top == null ? "" : label(top)));
    }

    /** "Fill 125": the mode's name in the server's language (the client's own in singleplayer) and the block count. */
    private static String label(final History.Entry e) {
        final String key = "slate_building.mode." + e.mode().id();
        final String id = e.mode().id();
        final String fallback = Character.toUpperCase(id.charAt(0)) + id.substring(1);
        return Language.getInstance().getOrDefault(key, fallback) + " " + e.size();
    }

    static void send(final ServerPlayer player, final CustomPacketPayload payload) {
        if (payload instanceof OpResult r) {
            final Session s = SESSIONS.get(player.getUUID());
            if (s != null) s.lastResult = r;
        }
        SlateNetwork.get().sendToPlayer(player, payload);
    }

    /** The last {@code OpResult} sent to {@code player} (commands, self-test). */
    public static @Nullable OpResult lastResult(final ServerPlayer player) {
        final Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.lastResult;
    }

    private OpsServer() {}
}
