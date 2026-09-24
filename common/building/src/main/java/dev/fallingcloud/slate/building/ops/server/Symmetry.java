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
import dev.fallingcloud.slate.building.ops.SymmetryMath;
import dev.fallingcloud.slate.building.ops.plan.Placement;
import dev.fallingcloud.slate.building.platform.BuildingPlatform;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
 * {@link SymmetryMath} computes, under the same rules as the original: each copy is paid like a placement (one unit of
 * the item used, from the same sources as operations), goes through spawn protection, the world border and claims,
 * must be able to stand there, and never overwrites anything but replaceable blocks. Copied breaks go through the
 * vanilla break path of the player (their tool, drops, protection events). Replays never trigger further replays.
 */
public final class Symmetry {

    /** The symmetry a player has switched on. */
    record Settings(BuildMode mode, ModeParams params, BlockPos centre, ResourceKey<Level> dimension) {}

    /** State of the block being placed, captured at {@code BlockItem.place} HEAD. */
    private record Pending(BlockPlaceContext ctx, BlockState before, ItemStack used) {}

    private static final ThreadLocal<Deque<Pending>> PENDING = ThreadLocal.withInitial(ArrayDeque::new);
    private static boolean replaying;

    /** {@code SetSymmetry}: an empty / unknown / non-toggle mode turns symmetry off. */
    public static void set(final ServerPlayer player, final String modeId, final CompoundTag paramsTag, final BlockPos centre) {
        final OpsServer.Session s = OpsServer.session(player);
        final BuildMode mode = BuildModes.byId(modeId);
        if (mode == null || mode.kind() != ModeKind.TOGGLE) {
            off(player, s);
            return;
        }
        final Component refused = refusal(player, mode, centre);
        if (refused != null) {
            OpsServer.send(player, OpMessages.error(0, mode.id(), refused));
            off(player, s);
            return;
        }
        final ModeParams params = ModeParams.fromTag(mode, paramsTag);
        s.symmetry = new Settings(mode, params, centre.immutable(), player.level().dimension());
        OpsServer.send(player, new SymmetryState(mode.id(), params.toTag(), centre));
    }

    private static @Nullable Component refusal(final ServerPlayer player, final BuildMode mode, final BlockPos centre) {
        final ServerOps rules = BuildingServerSettings.local().ops();
        if (!rules.enabled) return Component.translatable("slate_building.plan.disabled");
        if (rules.disabledModes != null && rules.disabledModes.contains(mode.id())) return Component.translatable("slate_building.plan.mode_disabled");
        if (player.isSpectator() || !player.mayBuild()) return Component.translatable("slate_building.error.game_mode");
        final ToolboxAccess.Capabilities caps = ToolboxAccess.of(player);
        if (!caps.unlocked(mode)) return OpsServer.lockError(mode);
        final int reach = caps.limits(BuildingServerSettings.local()).reachBonus();
        if (!player.canInteractWithBlock(centre, reach + 4.0)) return Component.translatable("slate_building.error.too_far");
        return null;
    }

    private static void off(final ServerPlayer player, final OpsServer.Session s) {
        s.symmetry = null;
        OpsServer.send(player, new SymmetryState("", new CompoundTag(), BlockPos.ZERO));
    }

    /** The active symmetry of {@code player} in {@code level}, switched off when their toolbox no longer unlocks it. */
    private static @Nullable Settings active(final ServerPlayer player, final ServerLevel level) {
        final OpsServer.Session s = OpsServer.existingSession(player);
        if (s == null) return null;
        final Settings st = s.symmetry;
        if (st == null || st.dimension() != level.dimension()) return null;
        if (!BuildingServerSettings.local().ops().enabled || !ToolboxAccess.of(player).unlocked(st.mode())) {
            off(player, s);
            return null;
        }
        return st;
    }

    // ---- placing (mixin on BlockItem.place) ----

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
        final Settings st = active(player, level);
        if (st == null) return;
        final BlockPos pos = ctx.getClickedPos();
        final BlockState placed = level.getBlockState(pos);
        if (placed.isAir() || placed == pending.before()) return;
        final List<SymmetryMath.Image> images = SymmetryMath.images(st.mode(), st.params(), st.centre(), SymmetryMath.radius(player), pos);
        if (images.isEmpty()) return;

        final BlockState material = placed.getBlock() instanceof ShapeBlock ? ShapeBlock.material(level, pos) : null;
        final Variant variant = Placement.variantOf(placed, material);
        CostKey key = CostKey.of(pending.used());
        if (key == null) key = CostKey.of(placed, variant);
        final boolean merged = !pending.before().canBeReplaced();
        final int units = Math.max(1, units(placed) - (merged ? units(pending.before()) : 0));
        final ServerOps rules = BuildingServerSettings.local().ops();
        final BuildingPlatform platform = BuildingPlatform.get();
        final Economy economy = new Economy(player, player.getAbilities().instabuild, player.getInventory().selected);
        replaying = true;
        try {
            for (final SymmetryMath.Image image : images) {
                final BlockPos ip = image.pos();
                if (!level.isLoaded(ip) || !level.getWorldBorder().isWithinBounds(ip) || !level.mayInteract(player, ip)) continue;
                final BlockState existing = level.getBlockState(ip);
                // A merge (slab onto slab) is copied only onto the mirrored half; a fresh placement only into free space.
                if (merged ? existing != image.apply(pending.before()) : !existing.canBeReplaced()) continue;
                BlockState target = image.apply(placed);
                if (!target.canSurvive(level, ip)) continue;
                target = Block.updateFromNeighbourShapes(target, level, ip);
                if (target.isAir() || !level.isUnobstructed(target, ip, CollisionContext.empty())) continue;
                if (key != null && !economy.charge(key, units, pending.used())) break;
                final Direction face = image.rotation().rotate(image.mirror().mirror(ctx.getClickedFace()));
                final boolean ok = rules.respectClaims ? platform.tryPlace(player, level, ip, target, face, Block.UPDATE_ALL)
                    : level.setBlock(ip, target, Block.UPDATE_ALL);
                if (!ok) {
                    if (key != null) economy.refund(key, units);
                    continue;
                }
                if (material != null && level.getBlockEntity(ip) instanceof ShapeBlockEntity be) be.setMaterial(material);
                final SoundType sound = target.getSoundType();
                level.playSound(null, ip, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            }
        } finally {
            replaying = false;
            economy.settle();
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
                if (!level.isLoaded(ip) || !level.getWorldBorder().isWithinBounds(ip) || !level.mayInteract(player, ip)) continue;
                final BlockState existing = level.getBlockState(ip);
                if (existing.isAir() || existing.getBlock() != broken.getBlock()) continue;
                player.gameMode.destroyBlock(ip);
            }
        } finally {
            replaying = false;
        }
    }

    private static int units(final BlockState state) {
        return Math.max(1, VariantRegistry.get().units(state, null));
    }

    private Symmetry() {}
}
