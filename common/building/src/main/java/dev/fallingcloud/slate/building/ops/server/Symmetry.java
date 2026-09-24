package dev.fallingcloud.slate.building.ops.server;

import dev.fallingcloud.slate.building.block.ShapeBlockEntity;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.ServerOps;
import dev.fallingcloud.slate.building.net.SymmetryState;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.ModeKind;
import dev.fallingcloud.slate.building.ops.ModeParams;
import dev.fallingcloud.slate.building.ops.OpMessages;
import dev.fallingcloud.slate.building.ops.StateWorth;
import dev.fallingcloud.slate.building.ops.SymmetryMath;
import dev.fallingcloud.slate.building.ops.plan.PlanBuilder;
import dev.fallingcloud.slate.building.ops.plan.Placement;
import dev.fallingcloud.slate.building.platform.BuildingPlatform;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Mirror / radial symmetry on the server: the player's normal block placing ({@code BlockItem.place}) and, with
 * {@code mirrorBreaking}, breaking ({@code ServerPlayerGameMode.destroyBlock}) are replayed at the copies
 * {@link SymmetryMath} computes, under the same rules as the original: each copy is paid like a placement (what the
 * placed state is worth in the item used, from the same sources as operations), goes through spawn protection, the
 * world border and claims, must be able to stand there, and never overwrites anything but replaceable blocks (a merge,
 * slab onto slab, only onto the same material). Copied breaks go through the vanilla break path of the player (their
 * tool, drops, protection events). Replays never trigger further replays.
 *
 * <p>Placements are replayed after the click is over ({@link #flush}, at the end of
 * {@code ServerPlayerGameMode.useItemOn}): on NeoForge the click runs inside a block-snapshot capture whose place event
 * may still cancel it, so the copies wait for that verdict, are skipped when the original did not stay, and each fires
 * its own place event outside the capture.
 */
public final class Symmetry {

    /** The symmetry a player has switched on. */
    record Settings(BuildMode mode, ModeParams params, BlockPos centre, ResourceKey<Level> dimension) {}

    /** State of the block being placed, captured at {@code BlockItem.place} HEAD. */
    private record Pending(BlockPlaceContext ctx, BlockState before, ItemStack used) {}

    /** A placement whose copies wait for the click to be over. */
    private record Deferred(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState placed, BlockState before,
                            Direction face, ItemStack used) {}

    private static final ThreadLocal<Deque<Pending>> PENDING = ThreadLocal.withInitial(ArrayDeque::new);
    private static final List<Deferred> DEFERRED = new ArrayList<>();
    private static boolean replaying;

    /** {@code SetSymmetry}: an empty / unknown / non-toggle mode turns symmetry off. */
    public static void set(final ServerPlayer player, final String modeId, final CompoundTag paramsTag, final BlockPos centre) {
        final OpsServer.Session s = OpsServer.session(player);
        final BuildMode mode = BuildModes.byId(modeId);
        if (mode == null || mode.kind() != ModeKind.TOGGLE) {
            off(player, s);
            return;
        }
        Component refused = refusal(player, mode);
        if (refused == null && !player.canInteractWithBlock(centre, ToolboxAccess.of(player).limits(BuildingServerSettings.local()).reachBonus() + 4.0)) {
            refused = Component.translatable("slate_building.error.too_far");
        }
        if (refused != null) {
            OpsServer.send(player, OpMessages.error(0, mode.id(), refused));
            off(player, s);
            return;
        }
        final ModeParams params = ModeParams.fromTag(mode, paramsTag);
        s.symmetry = new Settings(mode, params, centre.immutable(), player.level().dimension());
        OpsServer.send(player, new SymmetryState(mode.id(), params.toTag(), centre));
    }

    /** Why {@code player} may not use symmetry {@code mode} right now (server switch, disabled mode, game mode, toolbox). */
    private static @Nullable Component refusal(final ServerPlayer player, final BuildMode mode) {
        final ServerOps rules = BuildingServerSettings.local().ops();
        if (!rules.enabled) return Component.translatable("slate_building.plan.disabled");
        if (rules.disabledModes != null && rules.disabledModes.contains(mode.id())) return Component.translatable("slate_building.plan.mode_disabled");
        if (player.isSpectator() || !player.mayBuild()) return Component.translatable("slate_building.error.game_mode");
        if (BuildingPlatform.get().isFakePlayer(player)) return Component.translatable("slate_building.error.game_mode");
        final ToolboxAccess.Capabilities caps = ToolboxAccess.of(player);
        if (!caps.unlocked(mode)) return OpsServer.lockError(mode);
        return null;
    }

    private static void off(final ServerPlayer player, final OpsServer.Session s) {
        s.symmetry = null;
        OpsServer.send(player, new SymmetryState("", new CompoundTag(), BlockPos.ZERO));
    }

    /**
     * The active symmetry of {@code player} in {@code level}. The rules are checked again every time (a mode disabled by
     * a reload, a switch to adventure / spectator, a toolbox that no longer unlocks it): symmetry switches off then.
     */
    private static @Nullable Settings active(final ServerPlayer player, final ServerLevel level) {
        final OpsServer.Session s = OpsServer.existingSession(player);
        if (s == null) return null;
        final Settings st = s.symmetry;
        if (st == null || st.dimension() != level.dimension()) return null;
        if (refusal(player, st.mode()) != null) {
            off(player, s);
            return null;
        }
        return st;
    }

    /** After a rules reload: switches off every active symmetry the rules no longer allow (the HUD updates at once). */
    static void recheckAll(final MinecraftServer server) {
        for (final ServerPlayer p : server.getPlayerList().getPlayers()) {
            final OpsServer.Session s = OpsServer.existingSession(p);
            if (s != null && s.symmetry != null && refusal(p, s.symmetry.mode()) != null) off(p, s);
        }
    }

    // ---- placing (mixin on BlockItem.place, flushed by the mixin on ServerPlayerGameMode.useItemOn) ----

    public static void beforePlace(final BlockPlaceContext ctx) {
        if (replaying || ctx.getLevel().isClientSide() || !(ctx.getPlayer() instanceof ServerPlayer)) return;
        PENDING.get().push(new Pending(ctx, ctx.getLevel().getBlockState(ctx.getClickedPos()), ctx.getItemInHand().copyWithCount(1)));
    }

    public static void afterPlace(final BlockPlaceContext ctx, final InteractionResult result) {
        if (replaying || ctx.getLevel().isClientSide()) return;
        final Deque<Pending> stack = PENDING.get();
        Pending pending = null;
        while (!stack.isEmpty()) {
            final Pending p = stack.pop();
            if (p.ctx() == ctx) { pending = p; break; }
        }
        if (pending == null || !result.consumesAction()) return;
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer player)) return;
        final OpsServer.Session session = OpsServer.existingSession(player);
        if (session == null || session.symmetry == null) return;
        final BlockPos pos = ctx.getClickedPos().immutable();
        final BlockState placed = level.getBlockState(pos);
        if (placed.isAir() || placed == pending.before()) return;
        // Replayed once the click is over (see the class doc), on the server thread.
        DEFERRED.add(new Deferred(player, level, pos, placed, pending.before(), ctx.getClickedFace(), pending.used()));
    }

    /**
     * Replays the placements waiting for their click to end: {@code ServerPlayerGameMode.useItemOn} RETURN, and the end
     * of every server tick for placements that came another way.
     */
    public static void flush() {
        if (DEFERRED.isEmpty() || replaying) return;
        final List<Deferred> todo = List.copyOf(DEFERRED);
        DEFERRED.clear();
        for (final Deferred d : todo) {
            try {
                replay(d);
            } catch (final RuntimeException e) {
                dev.fallingcloud.slate.building.SlateBuilding.LOGGER.error("[Slate Building] symmetry replay failed", e);
            }
        }
    }

    /** Server stop: placements still waiting belong to a world that is going away. */
    static void clearDeferred() {
        DEFERRED.clear();
    }

    /** Server tick end: the safety net of {@link #flush()}. */
    public static void flushAtTickEnd(final MinecraftServer server) {
        flush();
    }

    private static void replay(final Deferred d) {
        final ServerPlayer player = d.player();
        final ServerLevel level = d.level();
        if (player.isRemoved() || !player.isAlive()) return;
        // The original must still stand: a cancelled place event (claims) reverted it, and then nothing is copied. Its
        // shape may have followed its neighbours since (redstone, fences): the same block worth the same is enough.
        final BlockPos pos = d.pos();
        final BlockState placed = level.getBlockState(pos);
        if (placed == d.before() || !StateWorth.sameWorth(placed, d.placed())) return;
        final Settings st = active(player, level);
        if (st == null) return;
        final List<SymmetryMath.Image> images = SymmetryMath.images(st.mode(), st.params(), st.centre(), SymmetryMath.radius(player), pos);
        if (images.isEmpty()) return;

        final BlockState material = placed.getBlock() instanceof ShapeBlock ? ShapeBlock.material(level, pos) : null;
        final Variant variant = Placement.variantOf(placed, material);
        CostKey key = CostKey.of(d.used());
        if (key == null) key = CostKey.of(placed, variant);
        final boolean free = player.getAbilities().instabuild;
        if (key == null && !free) return;   // nothing to pay with: never copied for free in survival
        final boolean merged = !d.before().canBeReplaced();
        final int units = Math.max(1, StateWorth.units(placed) - (merged ? StateWorth.units(d.before()) : 0));
        final ServerOps rules = BuildingServerSettings.local().ops();
        final BuildingPlatform platform = BuildingPlatform.get();
        final Economy economy = new Economy(player, free, player.getInventory().selected);
        replaying = true;
        try {
            for (final SymmetryMath.Image image : images) {
                final BlockPos ip = image.pos();
                if (!PlanBuilder.loaded(level, ip) || !level.getWorldBorder().isWithinBounds(ip) || !level.mayInteract(player, ip)) continue;
                final BlockState existing = level.getBlockState(ip);
                // A merge (slab onto slab) is copied only onto the mirrored half of the same material (a shape's material
                // lives in its block entity, so the state alone would let a dirt slab become a diamond one); a fresh
                // placement only into free space.
                if (merged) {
                    if (existing != image.apply(d.before())) continue;
                    if (material != null) {
                        final BlockState there = ShapeBlock.material(level, ip);
                        if (there == null || there.getBlock() != material.getBlock()) continue;
                    }
                } else if (!existing.canBeReplaced()) {
                    continue;
                }
                BlockState target = image.apply(placed);
                if (!target.canSurvive(level, ip)) continue;
                target = Block.updateFromNeighbourShapes(target, level, ip);
                if (target.isAir() || !level.isUnobstructed(target, ip, CollisionContext.empty())) continue;
                if (key != null && !economy.charge(key, units, d.used())) break;
                final Direction face = image.rotation().rotate(image.mirror().mirror(d.face()));
                final boolean ok = rules.respectClaims ? platform.tryPlace(player, level, ip, target, face, Block.UPDATE_ALL)
                    : level.setBlock(ip, target, Block.UPDATE_ALL);
                if (!ok) {
                    if (key != null) economy.refund(key, units);
                    continue;
                }
                if (!merged && material != null && level.getBlockEntity(ip) instanceof ShapeBlockEntity be) be.setMaterial(material);
                final SoundType sound = target.getSoundType();
                level.playSound(null, ip, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            }
        } finally {
            replaying = false;
            economy.settle();
            player.getInventory().setChanged();
        }
    }

    // ---- breaking (mixin on ServerPlayerGameMode.destroyBlock) ----

    public static void afterBreak(final ServerPlayer player, final ServerLevel level, final BlockPos pos, final BlockState broken) {
        if (replaying || broken.isAir()) return;
        final Settings st = active(player, level);
        if (st == null || !st.params().getBool(BuildModes.MIRROR_BREAKING.id())) return;
        final List<SymmetryMath.Image> images = SymmetryMath.images(st.mode(), st.params(), st.centre(), SymmetryMath.radius(player), pos);
        if (images.isEmpty()) return;
        replaying = true;
        try {
            for (final SymmetryMath.Image image : images) {
                final BlockPos ip = image.pos();
                if (!PlanBuilder.loaded(level, ip) || !level.getWorldBorder().isWithinBounds(ip) || !level.mayInteract(player, ip)) continue;
                final BlockState existing = level.getBlockState(ip);
                if (existing.isAir() || existing.getBlock() != broken.getBlock()) continue;
                player.gameMode.destroyBlock(ip);
            }
        } finally {
            replaying = false;
        }
    }

    private Symmetry() {}
}
