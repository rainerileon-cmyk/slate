package dev.fallingcloud.slate.building.ops.server;

import dev.fallingcloud.slate.building.block.ShapeBlockEntity;
import dev.fallingcloud.slate.building.config.ServerOps;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.Change;
import dev.fallingcloud.slate.building.ops.plan.Box;
import dev.fallingcloud.slate.building.ops.plan.Placement;
import dev.fallingcloud.slate.building.platform.BuildingPlatform;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 */
final class RunningOp {

    enum Kind { APPLY, UNDO, REDO }

    /**
     * One position to change.
     *
     * @param expect        for reverts: the block that must still be there (same block + material), else null
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
    private final @Nullable Box creditBox;
    private final Direction face;
    private final List<ItemStack> prefer;
    final List<History.BlockRecord> records = new ArrayList<>();
    /** Reverts only: records that could not be paid for, to go back on the stack they came from. */
    private final List<History.BlockRecord> unpaid = new ArrayList<>();

    int index;
    int placed;
    int replaced;
    int removed;
    int skipped;
    int missing;
    int ticks;
    boolean cancelled;
    /** Reverts only: whether the history entry being reverted was made without costs (creative). */
    boolean sourceFree;

    RunningOp(final int id, final Kind kind, final BuildMode mode, final ServerPlayer player, final ResourceKey<Level> dimension,
              final List<Step> steps, final Economy economy, final boolean recordData, final int blocksPerTick, final int hammerTier,
              final @Nullable Box creditBox, final Direction face, final List<ItemStack> prefer) {
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
        this.creditBox = creditBox;
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
        while (index < steps.size() && changedNow < budget && processed < maxProcessed) {
            final Step step = steps.get(index++);
            processed++;
            final Outcome o = apply(level, player, step, rules, rules.effects && changedNow % stride == 0);
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
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)) return Outcome.SKIPPED;
        final BlockState existing = level.getBlockState(pos);
        final boolean existingShape = existing.getBlock() instanceof ShapeBlock;
        final BlockState existingMaterial = existingShape ? ShapeBlock.material(level, pos) : null;
        final boolean revert = st.expect() != null;

        // "Still what the operation left there": same block (fence and stair connections may differ) and material.
        if (revert && (existing.getBlock() != st.expect().getBlock() || !sameMaterial(existingMaterial, st.expectMaterial()))) {
            return Outcome.SKIPPED;
        }

        // The new state, shaped by its neighbours (fence connections, stair corners). A removal leaves air, or the
        // fluid of a waterlogged block; that is not a placement.
        final boolean placing = !st.target().isAir() && (revert || st.kind() != Change.Kind.BREAK);
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
                if (existing.hasBlockEntity() && !existingShape && !economy.isFree() && !rules.allowBlockEntities) return Outcome.SKIPPED;
            }
        }

        final BuildingPlatform platform = BuildingPlatform.get();
        if (breaking && rules.respectClaims && !platform.canBreak(player, level, pos, existing)) return Outcome.SKIPPED;

        // Economy (creative accounts are free).
        List<Economy.Cost> paid = List.of();
        List<Economy.Cost> gained = List.of();
        final BlockEntity existingBe = existing.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        if (!economy.isFree()) {
            if (revert) {
                if (!economy.chargeAll(st.charge())) return Outcome.MISSING;
                paid = st.charge();
                gained = st.refund();
            } else {
                final Variant existingVariant = breaking ? Placement.variantOf(existing, existingMaterial) : null;
                final boolean reshaping = breaking && placing && st.variant() != null && existingVariant != null
                    && existingVariant.material() == st.variant().material();
                if (reshaping) {
                    // Same material, other shape: only the unit difference moves (double slab → stairs refunds one).
                    final CostKey key = new CostKey.Material(st.variant().material());
                    final int diff = units(target) - units(existing);
                    if (diff > 0) {
                        if (!economy.charge(key, diff, preferFor(st.variant()))) return Outcome.MISSING;
                        paid = List.of(new Economy.Cost(key, diff));
                    } else if (diff < 0) {
                        gained = List.of(new Economy.Cost(key, -diff));
                    }
                } else {
                    if (breaking) {
                        if (creditBox != null && creditBox.contains(pos)) {
                            // A moved block: lifted as itself, to pay for its own landing spot.
                            final CostKey key = CostKey.of(existing, existingVariant);
                            if (key != null) gained = List.of(new Economy.Cost(key, units(existing)));
                        } else {
                            final ItemStack tool = Drops.tool(hammerTier, existing);
                            if (!Drops.canHarvest(existing, tool)) return Outcome.SKIPPED;
                            gained = costs(Drops.of(level, pos, existing, existingBe, player, tool));
                        }
                    }
                    if (placing) {
                        final CostKey key = CostKey.of(target, st.variant());
                        if (key != null) {
                            final int units = units(target);
                            if (!economy.charge(key, units, preferFor(st.variant()))) return Outcome.MISSING;
                            paid = List.of(new Economy.Cost(key, units));
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
                be.loadWithComponents(st.targetData(), level.registryAccess());
                be.setChanged();
                level.sendBlockUpdated(pos, target, target, Block.UPDATE_CLIENTS);
            }
        }
        economy.refundAll(gained);

        final BlockState after = level.getBlockState(pos);
        records.add(new History.BlockRecord(pos, existing, existingMaterial, beforeData, after,
            after.getBlock() instanceof ShapeBlock ? st.targetMaterial() : null, paid, gained));
        if (!placing) removed++;
        else if (replaceable) placed++;
        else replaced++;
        if (effect) effect(level, pos, existing, after, placing);
        return Outcome.CHANGED;
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

    private static int units(final BlockState state) {
        return Math.max(1, VariantRegistry.get().units(state, null));
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
