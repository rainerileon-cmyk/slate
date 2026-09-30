package dev.fallingcloud.slate.building.ops.server;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.block.ShapeBlockEntity;
import dev.fallingcloud.slate.building.compat.SubLevels;
import dev.fallingcloud.slate.building.config.ServerOps;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.Change;
import dev.fallingcloud.slate.building.ops.StateWorth;
import dev.fallingcloud.slate.building.ops.plan.PlanBuilder;
import dev.fallingcloud.slate.building.ops.plan.Placement;
import dev.fallingcloud.slate.building.platform.BuildingPlatform;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.variant.LootGuard;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * One operation being executed over server ticks: a planned operation (APPLY), or reverting a history entry (UNDO /
 * REDO). Every position is re-checked when its turn comes (loaded, world border, spawn protection, claims through
 * {@link BuildingPlatform}, unbreakable blocks, containers, support), paid through the op's {@link Economy}, changed,
 * and recorded for history. The world may change while an op runs; positions that no longer fit are skipped.
 *
 * <p>Economy rules enforced here (design §1), whatever the plan says: a placement is paid for what the state is worth
 * ({@link StateWorth#units}) and a state nothing can pay for is never placed in survival; a removal pays out the block's
 * loot, rolled with the hammer-tier tool and only when that tool can harvest it (for our shapes: their material); a
 * move's lifted blocks pay only for their own landing, and what never landed turns into its normal loot at the end
 * ({@link #convertUnlanded}); undo / redo only touch positions still worth what they left there, and carry container
 * contents in and out through the items they charge and refund.
 */
final class RunningOp {

    enum Kind { APPLY, UNDO, REDO }

    /**
     * One position to change.
     *
     * @param expect        for reverts: the block that must still be there (same block, material and worth), else null
     * @param target        the state to set (air / fluid for removals)
     * @param targetMaterial our shape block's material to store, else null
     * @param targetData    block-entity data to load after placing (creative), else null
     * @param variant       what the target is, for costs (null outside the variant system)
     * @param kind          PLACE / REPLACE / BREAK as planned
     * @param charge        for reverts: what to charge (the gains being given back), else null = computed
     * @param refund        for reverts: what to refund (the payment being returned), else null = computed
     * @param origin        for reverts: the history record this step reverts, else null
     */
    record Step(BlockPos pos, @Nullable BlockState expect, @Nullable BlockState expectMaterial, BlockState target,
                @Nullable BlockState targetMaterial, @Nullable CompoundTag targetData, @Nullable Variant variant, Change.Kind kind,
                @Nullable List<Economy.Cost> charge, @Nullable List<Economy.Cost> refund, @Nullable History.BlockRecord origin) {

        static Step of(final Change c, final @Nullable CompoundTag data) {
            final BlockState material = c.target().getBlock() instanceof ShapeBlock && c.targetVariant() != null
                ? c.targetVariant().material().defaultBlockState() : null;
            return new Step(c.pos(), null, null, c.target(), material, data, c.targetVariant(), c.kind(), null, null, null);
        }

        /** Undoing {@code r}: put {@code before} back where {@code after} still is. */
        static Step revert(final History.BlockRecord r) {
            final Change.Kind kind = r.before().isAir() ? Change.Kind.BREAK : r.after().isAir() ? Change.Kind.PLACE : Change.Kind.REPLACE;
            return new Step(r.pos(), r.after(), r.afterMaterial(), r.before(), r.beforeMaterial(), r.beforeData(), null, kind, r.gained(), r.paid(), r);
        }
    }

    /** A block a move lifted as itself (see {@link Economy#carry}), in case it never lands. */
    private record Lift(int record, BlockPos pos, BlockState state, @Nullable BlockState material, @Nullable BlockEntity be,
                        CostKey key, int units) {}

    private enum Outcome { CHANGED, NOOP, SKIPPED, MISSING }

    private static final int PROGRESS_EVERY = 5;
    private static final int EFFECTS_PER_TICK = 3;

    final int id;
    final Kind kind;
    final BuildMode mode;
    final UUID playerId;
    final ResourceKey<Level> dimension;
    final List<Step> steps;
    final Economy economy;
    private final boolean recordData;
    private final int blocksPerTick;
    private final int hammerTier;
    /** Move only: the source positions whose blocks travel (credited as themselves when lifted), else null. */
    private final @Nullable LongSet carried;
    private final Direction face;
    private final List<ItemStack> prefer;
    final List<History.BlockRecord> records = new ArrayList<>();
    /** Reverts only: records that could not be paid for, to go back on the stack they came from. */
    private final List<History.BlockRecord> unpaid = new ArrayList<>();
    private final List<Lift> lifts = new ArrayList<>();

    int index;
    int placed;
    int replaced;
    int removed;
    int skipped;
    int missing;
    int ticks;
    boolean cancelled;
    /** Whether a step that threw was logged already (once per operation). */
    private boolean stepErrorLogged;
    /** Reverts only: whether the history entry being reverted was made without costs (creative). */
    boolean sourceFree;

    RunningOp(final int id, final Kind kind, final BuildMode mode, final ServerPlayer player, final ResourceKey<Level> dimension,
              final List<Step> steps, final Economy economy, final boolean recordData, final int blocksPerTick, final int hammerTier,
              final @Nullable LongSet carried, final Direction face, final List<ItemStack> prefer) {
        this.id = id;
        this.kind = kind;
        this.mode = mode;
        this.playerId = player.getUUID();
        this.dimension = dimension;
        this.steps = steps;
        this.economy = economy;
        this.recordData = recordData;
        this.blocksPerTick = Math.max(1, blocksPerTick);
        this.hammerTier = hammerTier;
        this.carried = carried;
        this.face = face;
        this.prefer = prefer;
    }

    boolean done() {
        return cancelled || index >= steps.size();
    }

    int changed() {
        return placed + replaced + removed;
    }

    int blocksPerTick() {
        return blocksPerTick;
    }

    /**
     * Reverts only: the records still to revert (not paid for, or not reached when cancelled), in their original
     * history order, so they can go back on the stack they came from and be retried.
     */
    List<History.BlockRecord> leftover() {
        final List<History.BlockRecord> out = new ArrayList<>(unpaid);
        for (int i = index; i < steps.size(); i++) {
            final History.BlockRecord r = steps.get(i).origin();
            if (r != null) out.add(r);
        }
        Collections.reverse(out);
        return out;
    }

    /** Works through up to {@code budget} changes; returns how many blocks it changed. */
    int tick(final ServerLevel level, final ServerPlayer player, final int budget, final ServerOps rules) {
        ticks++;
        final int maxProcessed = Math.max(budget, 1) * 4;
        final int stride = Math.max(1, budget / EFFECTS_PER_TICK);
        int changedNow = 0;
        int processed = 0;
        economy.beginBatch();
        try {
            while (index < steps.size() && changedNow < budget && processed < maxProcessed) {
                final Step step = steps.get(index++);
                processed++;
                Outcome o;
                try {
                    o = apply(level, player, step, rules, rules.effects && changedNow % stride == 0);
                } catch (final RuntimeException e) {
                    // Another mod's hook threw while this position changed (Sable's physics once did, on our walls):
                    // skip it and go on, instead of losing the rest of this tick for every running operation.
                    if (!stepErrorLogged) {
                        stepErrorLogged = true;
                        SlateBuilding.LOGGER.error("[Slate Building] {} failed at {}; skipped, the operation goes on", mode.id(), step.pos(), e);
                    }
                    o = Outcome.SKIPPED;
                }
                switch (o) {
                    case CHANGED -> changedNow++;
                    case SKIPPED -> skipped++;
                    case MISSING -> {
                        missing++;
                        if (step.origin() != null) unpaid.add(step.origin());
                    }
                    default -> { }
                }
            }
        } finally {
            economy.endBatch();
        }
        if (changedNow > 0 && kind == Kind.APPLY && !economy.isFree() && mode.tool() != null) {
            ToolboxAccess.damageTool(player, mode.tool(), changedNow);
        }
        return changedNow;
    }

    boolean progressDue() {
        return ticks % PROGRESS_EVERY == 0;
    }

    private Outcome apply(final ServerLevel level, final ServerPlayer player, final Step st, final ServerOps rules, final boolean effect) {
        final BlockPos pos = st.pos();
        // Loaded with its neighbours (block updates read them): the executor never loads a chunk either.
        if (!PlanBuilder.loaded(level, pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)) return Outcome.SKIPPED;
        final BlockState existing = level.getBlockState(pos);
        final boolean existingShape = existing.getBlock() instanceof ShapeBlock;
        final BlockState existingMaterial = existingShape ? ShapeBlock.material(level, pos) : null;
        final boolean revert = st.expect() != null;
        final boolean free = economy.isFree();
        // What was done on a Sable sub-level is undone on that sub-level only, not in whatever has its plot by now.
        if (revert && st.origin() != null && !Objects.equals(st.origin().space(), SubLevels.idAt(level, pos))) return Outcome.SKIPPED;

        // "Still what the operation left there": same block and material, and worth the same (a bitten cake, a split
        // double slab, grown crops or extra candles are not; stair corners and fence connections may differ).
        if (revert && (!StateWorth.sameWorth(existing, st.expect()) || !sameMaterial(existingMaterial, st.expectMaterial()))) {
            return Outcome.SKIPPED;
        }

        // The new state, shaped by its neighbours (fence connections, stair corners). A removal leaves air, or the
        // fluid of a waterlogged block; that is not a placement.
        final boolean placing = !st.target().isAir() && (revert || st.kind() != Change.Kind.BREAK);
        // Survival never places what nothing can pay for (filled cauldrons, potted plants, fluids ...), whatever the plan.
        final CostKey placeKey = placing && !revert && !free ? CostKey.of(st.target(), st.variant()) : null;
        if (placing && !revert && !free && placeKey == null) return Outcome.SKIPPED;
        BlockState target = st.target();
        if (placing) {
            if (!target.canSurvive(level, pos)) return Outcome.SKIPPED;
            target = Block.updateFromNeighbourShapes(target, level, pos);
            if (target.isAir()) return Outcome.SKIPPED;
            // Never build into a player or mob (vanilla placing refuses the same).
            if (!level.isUnobstructed(target, pos, CollisionContext.empty())) return Outcome.SKIPPED;
        }
        if (existing == target && sameMaterial(existingMaterial, st.targetMaterial())) return Outcome.NOOP;

        // What happens to the block that is there now.
        final boolean replaceable = existing.isAir() || existing.canBeReplaced();
        final boolean breaking;
        if (revert) {
            breaking = !existing.isAir();
        } else {
            if (st.kind() == Change.Kind.PLACE && !replaceable) return Outcome.SKIPPED;
            if (st.kind() == Change.Kind.BREAK && existing.isAir()) return Outcome.NOOP;
            breaking = st.kind() == Change.Kind.BREAK ? !existing.isAir() : !replaceable;
            if (breaking) {
                if (existing.getDestroySpeed(level, pos) < 0) return Outcome.SKIPPED;
                if (existing.hasBlockEntity() && !existingShape && !free && !rules.allowBlockEntities) return Outcome.SKIPPED;
            }
        }

        final BuildingPlatform platform = BuildingPlatform.get();
        if (breaking && rules.respectClaims && !platform.canBreak(player, level, pos, existing)) return Outcome.SKIPPED;

        // Economy (creative accounts are free).
        List<Economy.Cost> paid = List.of();
        List<Economy.Cost> gained = List.of();
        Lift lift = null;
        final BlockEntity existingBe = existing.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        if (!free) {
            if (revert) {
                // A container taken away hands back the item it was paid with; if what is inside changed since (a
                // shulker box emptied), that item would not be what is there any more: leave it.
                if (breaking && existingBe != null && !existingShape && !liveMatches(level, pos, existing, existingBe, player, st.refund())) {
                    return Outcome.SKIPPED;
                }
                if (!economy.chargeAll(st.charge())) return Outcome.MISSING;
                paid = st.charge();
                gained = st.refund();
            } else {
                // A moving source (lifted, or overwritten by another moved block): its block is carried, not harvested.
                final boolean carriedHere = !existing.isAir() && carried != null && carried.contains(pos.asLong());
                final Variant existingVariant = breaking ? Placement.variantOf(existing, existingMaterial) : null;
                boolean reshaping = breaking && placing && !carriedHere && st.variant() != null && existingVariant != null
                    && existingVariant.material() == st.variant().material();
                // Changing a block in place must not skip its loot (natural stone -> cobblestone, glass, ores, grass:
                // a free silk touch), the hammer's in-world rule. Reshape leaves such a block alone; any other mode
                // breaks it for real (loot with the hammer tier) and pays the full placement.
                if (reshaping && !LootGuard.keepsLoot(level, pos, existing, existingBe)) {
                    if (mode == BuildModes.RESHAPE) return Outcome.SKIPPED;
                    reshaping = false;
                }
                if (reshaping) {
                    // Same material, other shape: only the unit difference moves (double slab → stairs refunds one).
                    final CostKey key = new CostKey.Material(st.variant().material());
                    final int diff = StateWorth.units(target) - StateWorth.units(existing);
                    if (diff > 0) {
                        if (!economy.charge(key, diff, preferFor(st.variant()))) return Outcome.MISSING;
                        paid = List.of(new Economy.Cost(key, diff));
                    } else if (diff < 0) {
                        gained = List.of(new Economy.Cost(key, -diff));
                    }
                } else {
                    if (carriedHere) {
                        // A moved block: lifted as itself, to pay for its own landing (never paid out as an item).
                        final CostKey key = CostKey.of(existing, existingVariant);
                        if (key == null) return Outcome.SKIPPED;
                        final int units = StateWorth.units(existing);
                        lift = new Lift(records.size(), pos.immutable(), existing, existingMaterial, existingBe, key, units);
                        gained = List.of(new Economy.Cost(key, units));
                    } else if (breaking) {
                        // Harvest rules follow the material for our shapes (they need no tool themselves).
                        final BlockState harvest = existingShape && existingMaterial != null ? existingMaterial : existing;
                        final ItemStack tool = Drops.tool(hammerTier, harvest);
                        if (!Drops.canHarvest(harvest, tool)) return Outcome.SKIPPED;
                        gained = costs(Drops.of(level, pos, existing, existingBe, player, tool));
                    }
                    if (placing) {
                        int units = StateWorth.units(target);
                        // Topping up a replaceable block of the same kind (snow layers, lichen faces): pay the difference.
                        if (!breaking && !carriedHere && existing.getBlock() == target.getBlock()) units = Math.max(0, units - StateWorth.units(existing));
                        if (units > 0) {
                            if (!economy.charge(placeKey, units, preferFor(st.variant()))) return Outcome.MISSING;
                            paid = List.of(new Economy.Cost(placeKey, units));
                        }
                    }
                }
            }
        }

        // Block-entity data travels with creative operations; take it out first so containers do not spill.
        CompoundTag beforeData = null;
        if (recordData && existingBe != null && !existingShape) {
            beforeData = existingBe.saveWithoutMetadata(level.registryAccess());
            level.removeBlockEntity(pos);
        }

        final boolean ok;
        if (placing) {
            ok = rules.respectClaims ? platform.tryPlace(player, level, pos, target, face, Block.UPDATE_ALL)
                : level.setBlock(pos, target, Block.UPDATE_ALL);
        } else {
            ok = level.setBlock(pos, target, Block.UPDATE_ALL);
        }
        if (!ok) {
            economy.refundAll(paid);
            if (beforeData != null) {
                // Refused (claims): the block stays, give its block entity its contents back.
                final BlockEntity restored = level.getBlockEntity(pos);
                if (restored != null) {
                    restored.loadWithComponents(beforeData, level.registryAccess());
                    restored.setChanged();
                }
            }
            return Outcome.SKIPPED;
        }
        if (placing) {
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShapeBlockEntity shape && st.targetMaterial() != null) {
                shape.setMaterial(st.targetMaterial());
            } else if (be != null && st.targetData() != null) {
                // Data only operators may set (command blocks, spawners ...) only for players who may set it.
                if (!be.onlyOpCanSetNbt() || player.canUseGameMasterBlocks()) {
                    be.loadWithComponents(st.targetData(), level.registryAccess());
                    be.setChanged();
                    level.sendBlockUpdated(pos, target, target, Block.UPDATE_CLIENTS);
                }
            } else if (be != null && revert && !free) {
                // A container put back by undo gets what the item it was paid with holds (a filled shulker box).
                final ItemStack source = itemOf(paid, target);
                if (source != null) {
                    be.applyComponentsFromItemStack(source);
                    be.setChanged();
                    level.sendBlockUpdated(pos, target, target, Block.UPDATE_CLIENTS);
                }
            }
        }
        if (lift != null) {
            economy.carry(lift.key(), lift.units());
            lifts.add(lift);
        } else {
            economy.refundAll(gained);
        }

        final BlockState after = level.getBlockState(pos);
        records.add(new History.BlockRecord(pos, SubLevels.idAt(level, pos), existing, existingMaterial, beforeData, after,
            after.getBlock() instanceof ShapeBlock ? st.targetMaterial() : null, paid, gained));
        if (!placing) removed++;
        else if (replaceable) placed++;
        else replaced++;
        if (effect) effect(level, pos, existing, after, placing);
        return Outcome.CHANGED;
    }

    /**
     * Moves only, before settling: the lifted blocks no landing used (the landing was refused, blocked or never
     * reached) become what mining them gives: their loot rolled with the hammer-tier tool, nothing when that tool cannot
     * harvest them. Never the block itself (no free silk touch, no budding amethyst). Their history records are updated
     * to what the player got, so undo charges back exactly that and puts the block back.
     */
    void convertUnlanded(final @Nullable ServerLevel level, final ServerPlayer player) {
        final Map<CostKey, Integer> left = economy.takeUnspentCarried();
        if (left.isEmpty() || level == null) return;
        for (int i = lifts.size() - 1; i >= 0 && !left.isEmpty(); i--) {
            final Lift l = lifts.get(i);
            final Integer have = left.get(l.key());
            if (have == null) continue;
            final int take = Math.min(have, l.units());
            if (have - take <= 0) left.remove(l.key());
            else left.put(l.key(), have - take);

            final BlockState harvest = l.material() != null ? l.material() : l.state();
            final ItemStack tool = Drops.tool(hammerTier, harvest);
            final List<Economy.Cost> loot = Drops.canHarvest(harvest, tool)
                ? costs(Drops.of(level, l.pos(), l.state(), l.be(), player, tool)) : List.of();
            final List<Economy.Cost> converted = new ArrayList<>(loot.size());
            for (final Economy.Cost c : loot) {
                final int n = take == l.units() ? c.units() : c.units() * take / l.units();
                if (n > 0) converted.add(new Economy.Cost(c.key(), n));
            }
            economy.refundAll(converted);

            final List<Economy.Cost> gained = new ArrayList<>(converted);
            if (take < l.units()) gained.add(0, new Economy.Cost(l.key(), l.units() - take));
            final History.BlockRecord r = records.get(l.record());
            records.set(l.record(), new History.BlockRecord(r.pos(), r.space(), r.before(), r.beforeMaterial(), r.beforeData(), r.after(),
                r.afterMaterial(), r.paid(), List.copyOf(gained)));
        }
    }

    /**
     * Reverts only: whether the container at {@code pos} still is what {@code refund} hands back for it: when its loot
     * drops its own item carrying data (a shulker box with contents, a named container), that item must equal the
     * refunded one. Blocks whose loot does not drop the item itself (their contents spill) always match.
     */
    private boolean liveMatches(final ServerLevel level, final BlockPos pos, final BlockState existing, final BlockEntity be,
                                final ServerPlayer player, final @Nullable List<Economy.Cost> refund) {
        final ItemStack refunded = itemOf(refund, existing);
        if (refunded == null) return true;
        for (final ItemStack drop : Drops.of(level, pos, existing, be, player, Drops.tool(hammerTier, existing))) {
            if (drop.is(refunded.getItem())) return ItemStack.isSameItemSameComponents(drop, refunded);
        }
        return true;
    }

    /** The exact item among {@code costs} that is {@code state}'s own item (a container paid or refunded as itself). */
    private static @Nullable ItemStack itemOf(final @Nullable List<Economy.Cost> costs, final BlockState state) {
        if (costs == null) return null;
        for (final Economy.Cost c : costs) {
            if (c.key() instanceof CostKey.Exact e && e.template().is(state.getBlock().asItem())) return e.template();
        }
        return null;
    }

    private static void effect(final ServerLevel level, final BlockPos pos, final BlockState before, final BlockState after, final boolean placing) {
        if (!before.isAir() && !before.canBeReplaced()) {
            level.levelEvent(2001, pos, Block.getId(before));
        }
        if (placing) {
            final SoundType sound = after.getSoundType();
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        }
    }

    private @Nullable ItemStack preferFor(final @Nullable Variant v) {
        if (prefer.isEmpty()) return null;
        if (v == null || prefer.size() == 1) return prefer.get(0);
        for (final ItemStack s : prefer) {
            if (VariantRegistry.get().identify(s).map(v::equals).orElse(false)) return s;
        }
        return null;
    }

    private static List<Economy.Cost> costs(final List<ItemStack> drops) {
        if (drops.isEmpty()) return List.of();
        final List<Economy.Cost> out = new ArrayList<>(drops.size());
        for (final ItemStack d : drops) {
            final CostKey key = CostKey.of(d);
            if (key != null && d.getCount() > 0) out.add(new Economy.Cost(key, d.getCount()));
        }
        return out;
    }

    private static boolean sameMaterial(final @Nullable BlockState a, final @Nullable BlockState b) {
        return Objects.equals(a == null ? null : a.getBlock(), b == null ? null : b.getBlock());
    }
}
