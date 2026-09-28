package dev.fallingcloud.slate.building.client.mode;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.render.GhostRenderer;
import dev.fallingcloud.slate.building.client.render.WorldChanges;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.PreviewSettings;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.Change;
import dev.fallingcloud.slate.building.ops.Clipboard;
import dev.fallingcloud.slate.building.ops.Limits;
import dev.fallingcloud.slate.building.ops.ModeKind;
import dev.fallingcloud.slate.building.ops.ModeParams;
import dev.fallingcloud.slate.building.ops.Palette;
import dev.fallingcloud.slate.building.ops.Plan;
import dev.fallingcloud.slate.building.ops.PlanContext;
import dev.fallingcloud.slate.building.ops.Planners;
import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.ops.plan.Placement;
import dev.fallingcloud.slate.building.ops.server.Drops;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.toolbox.UpgradeType;
import dev.fallingcloud.slate.building.variant.LootGuard;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The live preview of the current selection: runs {@code Planners.of(mode).plan(ctx)} on the client level with the
 * same inputs the server will use, keeps the plan until an input changes, colours every planned change (placement,
 * replacement, removal, or invalid: not affordable with the carried materials, or skipped by the server whatever the
 * player carries: see {@link Blocker}), counts what the operation needs against what the player carries, and hands the ghosts to
 * {@link GhostRenderer#submitCached} (only up to {@code preview.maxBlocks}; above that the box alone is drawn).
 *
 * <p>When it re-plans: at once when the selection itself changes (mode, parameters, anchors, face, the held
 * palette, clipboard, limits, dimension, game mode); and, paced by what planning this selection costs, when
 * something the planners read beside the selection changed: the player's stance (facing, view direction, rotation
 * segment, sneaking: they orient stairs, logs and the stack direction), the off-hand block (the replace filter), the
 * tool tiers, or a block in or next to the planned region ({@link WorldChanges}). Nothing is re-planned while none of
 * these change. A re-plan that comes out identical keeps the plan, its colours and the renderer's cached buffers.
 * A selection is never planned when its estimated size exceeds the player's limits (the error says so instead);
 * while the box still follows the crosshair only small selections are planned, at most every
 * {@value #LIVE_INTERVAL_MS} ms. Stats are rebuilt only when the plan, its colours, the carried materials or the
 * selection's geometry change.
 */
final class ModePreview {

    /** Largest estimated selection planned while it still follows the crosshair. */
    static final int LIVE_LIMIT = 4096;
    static final long LIVE_INTERVAL_MS = 90;
    /** Planners read neighbours of the planned blocks (connections, flood-fill borders): watch this far around. */
    private static final int WATCH_MARGIN = 2;
    private static final Object GHOST_KEY = "slate_building:mode_preview";

    /** Everything a plan depends on that the player changes on purpose (re-planned at once). */
    private record Key(String mode, ModeParams params, List<BlockPos> anchors, Direction face, ClientPalette.Signature palette,
                       int clipboard, Limits limits, ResourceKey<Level> dimension, boolean creative) {}

    /** What planners read from the player besides the selection (re-planned at a pace the plan's cost allows). */
    private record Stance(Direction facing, Direction view, int rotation, boolean secondary, Item offhand, int offhandComponents,
                          Map<ToolType, Integer> tiers) {
        static Stance of(final Player player) {
            final ItemStack off = player.getOffhandItem();
            return new Stance(player.getDirection(), player.getNearestViewDirection(), RotationSegment.convertToSegment(player.getYRot()),
                player.isSecondaryUseActive(), off.getItem(), off.isEmpty() ? 0 : ItemStack.hashItemAndComponents(off),
                ModeRules.capabilities(player).tiers());
        }
    }

    private static @Nullable Key key;
    private static @Nullable Stance stance;
    private static long worldSeen;
    private static @Nullable Plan plan;
    private static GhostRenderer.Style[] styles = new GhostRenderer.Style[0];
    /** Per change: INVALID because the server will skip it whatever the player carries (not for lack of materials). */
    private static boolean[] blockedAt = new boolean[0];
    private static Map<Block, Integer> needs = Map.of();
    private static ModeGeometry.Shape shape = ModeGeometry.Shape.NONE;
    private static @Nullable Component preError;
    private static long plannedAtMs;
    private static long planCostMs;
    private static int version;
    private static @Nullable List<GhostRenderer.Ghost> ghosts;
    private static int ghostsVersion = -1;
    private static Map<Block, Integer> available = Map.of();
    private static int availableStamp;
    private static final Map<Block, ItemStack> ICONS = new HashMap<>();
    private static ClientModeState.Stats stats = ClientModeState.Stats.EMPTY;
    private static int statsVersion = -1;
    private static int statsAvailable = -1;
    private static ModeGeometry.Shape statsShape = ModeGeometry.Shape.NONE;
    private static @Nullable Plan placedFor;
    private static @Nullable AABB placed;
    /** Dev harness: how many times the planner ran. */
    static int planRuns;

    /** The plan being previewed (null: nothing planned). */
    static @Nullable Plan plan() { return plan; }

    /** The selection's geometry as last computed. */
    static ModeGeometry.Shape shape() { return shape; }

    /** Changes whenever the ghosts (plan or colours) change; the renderer rebuilds its buffers only then. */
    static int version() { return version; }

    /** Why the selection cannot be applied (pre-checks first, then the planner's error), or null. */
    static @Nullable Component error() {
        if (preError != null) return preError;
        return plan != null ? plan.error() : null;
    }

    /** Whether the current plan can be sent: planned, no error, and it does something (copy always can). */
    static boolean applicable(final BuildMode mode) {
        if (mode.kind() == ModeKind.MEASURE || preError != null || plan == null || !plan.ok()) return false;
        return !mode.changesWorld() || !plan.isEmpty();
    }

    /** Drops the plan (mode left, selection cleared). */
    static void clear() {
        key = null;
        stance = null;
        preError = null;
        shape = ModeGeometry.Shape.NONE;
        setPlan(null, 0);
        stats = ClientModeState.Stats.EMPTY;
        statsVersion = -1;
    }

    /** Forces the next {@link #update} to re-plan (e.g. right before applying, or after a world change we caused). */
    static void invalidate() {
        key = null;
    }

    /**
     * Brings the preview up to date for {@code anchors} (committed anchors plus the live one) and returns the stats
     * for the HUD. {@code live}: the selection still follows the crosshair.
     */
    static ClientModeState.Stats update(final Player player, final BuildMode mode, final List<BlockPos> anchors,
                                        final Direction face, final boolean live) {
        final Level level = player.level();
        final ModeParams params = ClientModeState.params(mode);
        final Clipboard clipboard = mode == BuildModes.PASTE ? ClipboardReader.current(level) : null;
        shape = ModeGeometry.shape(mode, params, anchors, face, clipboard);
        if (mode.kind() == ModeKind.MEASURE) {
            // Client-only: geometry, no plan.
            if (plan != null || preError != null) { preError = null; setPlan(null, 0); }
            key = null;
            return stats(player, mode);
        }
        final Limits limits = ModeRules.limits(player);
        final Palette palette = ClientPalette.resolve(player, mode, params);
        final boolean creative = player.isCreative();
        final Key next = new Key(mode.id(), params.copy(), List.copyOf(anchors), face, ClientPalette.signature(palette),
            ClientModeState.clipboardVersion(), limits, level.dimension(), creative);
        final Stance nextStance = Stance.of(player);

        final boolean changed = !next.equals(key);
        final boolean drifted = plan != null && (!nextStance.equals(stance) || WorldChanges.count() != worldSeen);
        if (!changed && !drifted) return stats(player, mode);
        final long now = Util.getMillis();
        if (!changed) {
            // Only the player's stance or the blocks around the selection changed: follow them at a pace the plan's
            // cost allows (turning on the spot next to a big fill must not re-plan it every few frames). A skipped
            // frame keeps the change pending, so the last state is planned once the interval has passed.
            final long interval = heavy() ? Math.max(1000L, planCostMs * 40L) : Math.max(LIVE_INTERVAL_MS, planCostMs * 8L);
            if (now - plannedAtMs < interval) return stats(player, mode);
        }
        final Component pre = precheck(player, mode, anchors, palette, clipboard, limits);
        if (pre != null || anchors.isEmpty()) {
            preError = pre;
            setPlan(null, now);
            accept(next, nextStance, null);
        } else if (live && (!ClientModeState.settings().livePreview || shape.estimate() > LIVE_LIMIT)) {
            preError = null;
            setPlan(null, now);               // the box alone until the selection is fixed
            accept(next, nextStance, null);
        } else if (!live || plan == null || now - plannedAtMs >= LIVE_INTERVAL_MS) {
            preError = null;
            final Plan fresh = runPlanner(player, mode, params, anchors, face, palette, clipboard, limits);
            accept(next, nextStance, fresh);
            if (plan != null && fresh.equals(plan)) {
                // Same result: keep the plan object, its colours and the renderer's buffers.
                plannedAtMs = now;
                recolour(player);             // bumps the version only if a colour actually changed
            } else {
                setPlan(fresh, now);
                refreshAvailability(player, true);
            }
        }
        // else: a throttled live update; the previous plan stays up and the next frame retries.
        return stats(player, mode);
    }

    /** Records the inputs a plan was made from and watches the blocks it read. */
    private static void accept(final Key next, final Stance nextStance, final @Nullable Plan planned) {
        key = next;
        stance = nextStance;
        AABB region = shape.box();
        if (planned != null && !planned.isEmpty() && planned.bounds() != null) {
            region = region == null ? planned.bounds() : region.minmax(planned.bounds());
        }
        WorldChanges.watch(planned == null ? null : region, WATCH_MARGIN);
        worldSeen = WorldChanges.count();
    }

    /** A plan too costly to follow the player's every move: above the live limit, or drawn as its box only. */
    private static boolean heavy() {
        return plan != null && (plan.changes().size() > LIVE_LIMIT || ghostsHidden());
    }

    /** Re-counts carried materials (called every few ticks) and re-colours the ghosts when something changed. */
    static void refreshAvailability(final Player player, final boolean force) {
        if (plan == null || plan.isEmpty()) return;
        final Map<Block, Integer> now = ModeMaterials.available(player, needs.keySet());
        if (!force && now.equals(available)) return;
        if (!now.equals(available)) availableStamp++;
        available = now;
        recolour(player);
    }

    /** Hands this frame's ghosts to the renderer (cached by version; nothing above {@code preview.maxBlocks}). */
    static void submitGhosts() {
        if (!ghostsShown()) return;
        final int v = version;
        GhostRenderer.submitCached(GHOST_KEY, v, () -> {
            if (ghosts == null || ghostsVersion != v) {
                ghosts = buildGhosts();
                ghostsVersion = v;
            }
            return ghosts;
        });
    }

    /** Whether the plan is drawn as ghost blocks (planned, not empty, within {@code preview.maxBlocks}). */
    static boolean ghostsShown() {
        return plan != null && !plan.isEmpty() && !ghostsHidden();
    }

    static boolean ghostsHidden() {
        final PreviewSettings preview = SlateBuilding.config().preview;
        final int max = preview == null ? 4096 : Math.max(0, preview.maxBlocks);
        return plan != null && plan.changes().size() > max;
    }

    /**
     * Bounds of the planned placements and replacements (where a paste or move lands), or null. The overlay asks
     * every frame; it is computed once per plan.
     */
    static @Nullable AABB placedBounds() {
        final Plan p = plan;
        if (p != placedFor) {
            placedFor = p;
            placed = p == null ? null : placedBoundsOf(p);
        }
        return placed;
    }

    private static @Nullable AABB placedBoundsOf(final Plan p) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean any = false;
        for (final Change c : p.changes()) {
            if (c.kind() == Change.Kind.BREAK) continue;
            final BlockPos pos = c.pos();
            any = true;
            minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX()); maxY = Math.max(maxY, pos.getY()); maxZ = Math.max(maxZ, pos.getZ());
        }
        return any ? new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1) : null;
    }

    // ---- internals ----

    private static Plan runPlanner(final Player player, final BuildMode mode, final ModeParams params, final List<BlockPos> anchors,
                                   final Direction face, final Palette palette, final @Nullable Clipboard clipboard, final Limits limits) {
        final long t0 = System.nanoTime();
        planRuns++;
        Plan p;
        try {
            final boolean destructive = ClientModeState.destructive();
            final Palette pal = destructive && palette.isEmpty() ? Palette.standIn() : palette;
            p = Planners.of(mode).plan(new PlanContext(player.level(), player, mode, params.copy(), anchors, face, pal, clipboard, limits, destructive));
            if (p == null) p = Plan.EMPTY;
        } catch (final RuntimeException e) {
            SlateBuilding.LOGGER.error("[Slate Building] preview planning of {} failed", mode.id(), e);
            p = Plan.error(Component.translatable("slate_building.notice.plan_failed", mode.name()));
        }
        if (p.ok() && limits.maxVolume() > 0 && p.changes().size() > limits.maxVolume()) {
            p = Plan.error(Component.translatable("slate_building.notice.too_big", p.changes().size(), limits.maxVolume()));
        }
        planCostMs = (System.nanoTime() - t0) / 1_000_000L;
        return p;
    }

    /** Installs {@code p} (null: nothing planned); {@link #planCostMs} was set by {@link #runPlanner}. */
    private static void setPlan(final @Nullable Plan p, final long now) {
        plan = p;
        plannedAtMs = now;
        if (p == null) {
            planCostMs = 0;
            WorldChanges.watch(null, 0);
        }
        available = Map.of();
        needs = p == null ? Map.of() : needsOf(p);
        styles = new GhostRenderer.Style[p == null ? 0 : p.changes().size()];
        ghosts = null;
        version++;
    }

    private static @Nullable Component precheck(final Player player, final BuildMode mode, final List<BlockPos> anchors,
                                                final Palette palette, final @Nullable Clipboard clipboard, final Limits limits) {
        final Component locked = ModeRules.capabilities(player).lockReason(mode);
        if (locked != null) return locked;
        if (mode.changesWorld() && !player.mayBuild()) return Component.translatable("slate_building.notice.cannot_build");
        if (mode == BuildModes.PASTE && (clipboard == null || clipboard.isEmpty())) {
            return Component.translatable("slate_building.notice.empty_clipboard");
        }
        // A breaking selection needs nothing in hand (its geometry runs with a stand-in block, see runPlanner).
        if (needsPalette(mode) && palette.isEmpty() && !ClientModeState.destructive()) return Component.translatable("slate_building.notice.hold_block");
        if (anchors.isEmpty()) return null;
        if (limits.maxVolume() > 0 && shape.estimate() > limits.maxVolume()) {
            return Component.translatable("slate_building.notice.too_big", shape.estimate(), limits.maxVolume());
        }
        if (limits.maxSpan() > 0 && shape.span() > limits.maxSpan()) {
            return Component.translatable("slate_building.notice.too_long", shape.span(), limits.maxSpan());
        }
        return null;
    }

    /** Modes that place the held block / hotbar mix and need one. */
    static boolean needsPalette(final BuildMode mode) {
        return mode == BuildModes.FILL || mode == BuildModes.WALLS || mode == BuildModes.LINE || mode == BuildModes.HOLLOW_BOX
            || mode == BuildModes.OUTLINE || mode == BuildModes.CYLINDER || mode == BuildModes.SPHERE
            || mode == BuildModes.REPLACE_BLOCKS || mode == BuildModes.OVERLAY || mode == BuildModes.RESHAPE;
    }

    private static Map<Block, Integer> needsOf(final Plan p) {
        final Map<Block, Integer> out = new LinkedHashMap<>();
        for (final Change c : p.changes()) {
            final Block m = ModeMaterials.materialOf(c);
            if (m != null) out.merge(m, ModeMaterials.unitsOf(c), Integer::sum);
        }
        return out;
    }

    /**
     * Styles every change: invalid (blocked / unaffordable) first, then by kind. Bumps the ghost version only when a
     * style actually changed (a plan just installed always counts: its styles start empty).
     */
    private static void recolour(final Player player) {
        if (plan == null) return;
        final boolean creative = player.isCreative();
        final Blocker blocker = new Blocker(player, key == null ? null : BuildModes.byId(key.mode()));
        final Map<Block, Integer> left = new HashMap<>(available);
        final List<Change> changes = plan.changes();
        final GhostRenderer.Style[] next = new GhostRenderer.Style[changes.size()];
        final boolean[] nextBlocked = new boolean[changes.size()];
        for (int i = 0; i < changes.size(); i++) {
            final Change c = changes.get(i);
            if (blocker.blocked(c)) { next[i] = GhostRenderer.Style.INVALID; nextBlocked[i] = true; continue; }
            if (c.kind() == Change.Kind.BREAK) { next[i] = GhostRenderer.Style.REMOVE; continue; }
            if (!creative) {
                final Block m = ModeMaterials.materialOf(c);
                if (m != null) {
                    final int units = ModeMaterials.unitsOf(c);
                    final int have = left.getOrDefault(m, 0);
                    if (have < units) { next[i] = GhostRenderer.Style.INVALID; continue; }
                    left.put(m, have - units);
                }
            }
            next[i] = c.kind() == Change.Kind.REPLACE ? GhostRenderer.Style.REPLACE : GhostRenderer.Style.PLACE;
        }
        blockedAt = nextBlocked;
        if (Arrays.equals(next, styles)) return;
        styles = next;
        ghosts = null;
        version++;
    }

    /**
     * Positions the server's executor ({@code RunningOp}) skips whatever the player carries: outside the world or the
     * border, unbreakable. Without the creative bypass also: what the hammer tier cannot harvest (for Slate shapes:
     * their stored material, as the server decides), containers when the server does not allow block entities, and,
     * for Reshape, blocks whose own loot a change in place would skip ({@link LootGuard}: natural stone, glass, ores).
     */
    private static final class Blocker {
        private final Level level;
        private final @Nullable BuildMode mode;
        private final boolean free;
        private final int hammer;
        private final boolean allowBlockEntities;
        private final Map<BlockState, Boolean> harvestable = new HashMap<>();

        Blocker(final Player player, final @Nullable BuildMode mode) {
            this.level = player.level();
            this.mode = mode;
            final ToolboxAccess.Capabilities caps = ModeRules.capabilities(player);
            this.free = caps.creative();
            this.hammer = caps.tier(ToolType.HAMMER);
            this.allowBlockEntities = BuildingServerSettings.effective(player).ops().allowBlockEntities;
        }

        boolean blocked(final Change c) {
            final BlockPos pos = c.pos();
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return true;
            if (c.kind() == Change.Kind.PLACE) return false;
            final BlockState existing = level.getBlockState(pos);
            if (existing.isAir()) return false;
            if (existing.getDestroySpeed(level, pos) < 0) return true;
            // Creative costs nothing and harvests nothing; a move carries its blocks instead of harvesting them.
            if (free || mode == BuildModes.MOVE) return false;
            // Replacing grass tufts or snow layers is a placement: nothing is broken.
            if (c.kind() == Change.Kind.REPLACE && existing.canBeReplaced()) return false;
            final boolean shape = existing.getBlock() instanceof ShapeBlock;
            if (existing.hasBlockEntity() && !shape && !allowBlockEntities) return true;
            final BlockState material = shape ? ShapeBlock.material(level, pos) : null;
            if (c.kind() == Change.Kind.REPLACE && c.targetVariant() != null) {
                final Variant was = Placement.variantOf(existing, material);
                if (was != null && was.material() == c.targetVariant().material()) {
                    // Same material, new shape: changed in place, nothing harvested, unless that would skip its loot.
                    if (LootGuard.keepsLoot(level, pos, existing, null)) return false;
                    if (mode == BuildModes.RESHAPE) return true;
                    // Any other mode breaks it for real: the harvest rule below applies.
                }
            }
            final BlockState harvest = material != null ? material : existing;
            return !harvestable.computeIfAbsent(harvest, s -> Drops.canHarvest(s, Drops.tool(hammer, s)));
        }
    }

    private static List<GhostRenderer.Ghost> buildGhosts() {
        final Plan p = plan;
        if (p == null) return List.of();
        final List<Change> changes = p.changes();
        final List<GhostRenderer.Ghost> out = new ArrayList<>(changes.size());
        final Level level = net.minecraft.client.Minecraft.getInstance().level;
        for (int i = 0; i < changes.size(); i++) {
            final Change c = changes.get(i);
            final GhostRenderer.Style style = i < styles.length && styles[i] != null ? styles[i] : defaultStyle(c);
            if (c.kind() == Change.Kind.BREAK) {
                // A removal shows the block that goes away.
                final BlockState existing = level == null ? c.target() : level.getBlockState(c.pos());
                final BlockState material = level == null ? null : ShapeBlock.material(level, c.pos());
                out.add(new GhostRenderer.Ghost(c.pos(), existing.isAir() ? c.target() : existing, material, style));
                continue;
            }
            final BlockState material = c.target().getBlock() instanceof ShapeBlock && c.targetVariant() != null
                ? c.targetVariant().material().defaultBlockState() : null;
            out.add(new GhostRenderer.Ghost(c.pos(), c.target(), material, style));
        }
        return out;
    }

    private static GhostRenderer.Style defaultStyle(final Change c) {
        return switch (c.kind()) {
            case PLACE -> GhostRenderer.Style.PLACE;
            case REPLACE -> GhostRenderer.Style.REPLACE;
            case BREAK -> GhostRenderer.Style.REMOVE;
        };
    }

    private static ClientModeState.Stats stats(final Player player, final BuildMode mode) {
        if (statsVersion == version && statsAvailable == availableStamp && shape.equals(statsShape)) return stats;
        statsVersion = version;
        statsAvailable = availableStamp;
        statsShape = shape;
        final boolean creative = player.isCreative();
        int place = 0, replace = 0, remove = 0, missing = 0, blocked = 0;
        final List<ClientModeState.MaterialNeed> materials = new ArrayList<>();
        final Plan p = plan;
        if (p != null) {
            final List<Change> changes = p.changes();
            for (int i = 0; i < changes.size(); i++) {
                final Change c = changes.get(i);
                switch (c.kind()) {
                    case PLACE -> place++;
                    case REPLACE -> replace++;
                    case BREAK -> remove++;
                }
                if (i < styles.length && styles[i] == GhostRenderer.Style.INVALID) {
                    if (i < blockedAt.length && blockedAt[i]) blocked++;
                    else missing++;
                }
            }
            if (!creative) {
                for (final Map.Entry<Block, Integer> e : needs.entrySet()) {
                    materials.add(new ClientModeState.MaterialNeed(e.getKey(), ICONS.computeIfAbsent(e.getKey(), ModeMaterials::icon),
                        e.getValue(), available.getOrDefault(e.getKey(), 0)));
                }
                materials.sort(Comparator.comparingInt(ClientModeState.MaterialNeed::needed).reversed());
            }
        }
        Component error = error();
        if (error == null && p != null && p.ok() && mode.changesWorld() && p.isEmpty()) {
            error = Component.translatable("slate_building.notice.nothing");
        }
        AABB box = shape.box();
        if (mode.kind() == ModeKind.POINT && p != null && !p.isEmpty()) box = p.bounds();
        final int sx = box == null ? 0 : (int) Math.round(box.getXsize());
        final int sy = box == null ? 0 : (int) Math.round(box.getYsize());
        final int sz = box == null ? 0 : (int) Math.round(box.getZsize());
        final boolean supply = !creative && ModeRules.capabilities(player).upgrade(UpgradeType.SUPPLY_LINK) > 0;
        final double distance = mode.kind() == ModeKind.MEASURE || mode == BuildModes.LINE ? shape.distance() : 0;
        stats = new ClientModeState.Stats(sx, sy, sz, (long) sx * sy * sz, p == null ? 0 : p.changes().size(),
            p == null ? 0 : p.requestedCount(), place, replace, remove, missing, blocked, materials, p != null && p.ok(),
            ghostsHidden(), creative, supply, distance, shape.radius(), shape.height(), error);
        return stats;
    }

    private ModePreview() {}
}
