package dev.fallingcloud.slate.building.ops.plan;

import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.ops.Palette;
import dev.fallingcloud.slate.building.ops.Plan;
import dev.fallingcloud.slate.building.ops.PlanContext;
import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.toolbox.ToolTier;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Extend (construction wand): every block connected to the clicked one across the clicked face's plane that matches
 * it ({@code match}: EXACT state, same BLOCK, or ANY solid block) and has free space in front gets one block added in
 * front of it. Connectivity includes diagonals; {@code lock} keeps it to one row: HORIZONTAL (for a top/bottom face:
 * the row across your view) or VERTICAL (for a top/bottom face: the line along your view). The number of blocks is
 * capped by the trowel tier ({@code extendMax}: 16/64/256/1024), nearest first, and stops at unloaded chunks.
 *
 * <p>What is added: the held block when the player holds one, else a copy of each source block (same state, same
 * material), so a row of stairs extends as stairs facing the same way. In survival such copies are paid like any
 * placement: blocks without an item are skipped and grown crops are copied as fresh ones.
 */
public final class ExtendPlanner {

    public static Plan plan(final PlanContext ctx) {
        final Level level = ctx.level();
        final BlockPos start = ctx.anchor(0);
        final Direction face = ctx.face();
        final BlockState source = level.getBlockState(start);
        if (source.isAir() || source.canBeReplaced()) return Plans.error(PlanErrors.pickBlock(), null);
        final String match = ctx.params().getChoice("match");
        final Variant sourceVariant = Placement.variantAt(level, start, source);
        final int tier = Math.max(ToolTier.MIN, ToolboxAccess.of(ctx.player()).tier(ToolType.TROWEL));
        final int max = Math.min(ctx.limits().maxVolume(), ToolTier.index(BuildingServerSettings.effective(level).ops().extendMax, tier));
        final List<Vec3i> steps = steps(face, ctx.params().getChoice("lock"), ctx.player().getDirection());
        final Palette.WeightedEntry held = ctx.palette().first();

        final PlanBuilder pb = new PlanBuilder(ctx);
        final LongOpenHashSet seen = new LongOpenHashSet();
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.asLong());
        final List<BlockPos> accepted = new ArrayList<>();
        while (!queue.isEmpty() && accepted.size() < max) {
            final BlockPos pos = queue.poll();
            // Never read (and so load or generate) a chunk that is not loaded: the walk stops at the loaded edge. The walk
            // is otherwise bounded by the count: every accepted block is one step from another, so none is further than
            // max blocks from the clicked one.
            if (!pb.readable(pos)) continue;
            final BlockState state = level.getBlockState(pos);
            if (!matches(level, pos, state, source, sourceVariant, match)) continue;
            final BlockPos front = pos.relative(face);
            if (!pb.readable(front) || !level.getBlockState(front).canBeReplaced()) continue;
            accepted.add(pos);
            for (final Vec3i step : steps) {
                final BlockPos next = pos.offset(step);
                if (seen.add(next.asLong())) queue.add(next);
            }
        }
        for (final BlockPos pos : accepted) {
            final BlockPos front = pos.relative(face);
            pb.want();
            if (held != null) {
                pb.place(front, Placement.of(ctx, held, front, face), held.variant(), ReplacePolicy.REPLACEABLE);
            } else {
                final BlockState state = level.getBlockState(pos);
                final BlockState material = state.getBlock() instanceof ShapeBlock ? ShapeBlock.material(level, pos) : null;
                pb.place(front, pb.copyOf(state), Placement.variantOf(state, material), ReplacePolicy.REPLACEABLE);
            }
        }
        return pb.build();
    }

    private static boolean matches(final Level level, final BlockPos pos, final BlockState state, final BlockState source,
                                   final Variant sourceVariant, final String match) {
        if (state.isAir() || state.canBeReplaced()) return false;
        return switch (match) {
            case "ANY" -> true;
            case "EXACT" -> state == source && sameMaterial(level, pos, state, sourceVariant);
            default -> state.getBlock() == source.getBlock() && sameMaterial(level, pos, state, sourceVariant);
        };
    }

    private static boolean sameMaterial(final Level level, final BlockPos pos, final BlockState state, final Variant sourceVariant) {
        if (!(state.getBlock() instanceof ShapeBlock)) return true;
        final Variant v = Placement.variantAt(level, pos, state);
        return v == null ? sourceVariant == null : sourceVariant != null && v.material() == sourceVariant.material();
    }

    /** In-plane neighbour steps for {@code face} under {@code lock}; {@code facing} is the player's horizontal facing. */
    static List<Vec3i> steps(final Direction face, final String lock, final Direction facing) {
        final Vec3i u;
        final Vec3i v;
        switch (face.getAxis()) {
            case X -> { u = new Vec3i(0, 0, 1); v = new Vec3i(0, 1, 0); }
            case Z -> { u = new Vec3i(1, 0, 0); v = new Vec3i(0, 1, 0); }
            default -> { u = new Vec3i(1, 0, 0); v = new Vec3i(0, 0, 1); }
        }
        final List<Vec3i> out = new ArrayList<>(8);
        final boolean flat = face.getAxis() == Direction.Axis.Y;
        final Vec3i along = facing.getAxis() == Direction.Axis.X ? new Vec3i(1, 0, 0) : new Vec3i(0, 0, 1);
        final Vec3i across = facing.getAxis() == Direction.Axis.X ? new Vec3i(0, 0, 1) : new Vec3i(1, 0, 0);
        switch (lock) {
            case "HORIZONTAL" -> {
                final Vec3i w = flat ? across : u;
                out.add(w);
                out.add(w.multiply(-1));
            }
            case "VERTICAL" -> {
                final Vec3i w = flat ? along : v;
                out.add(w);
                out.add(w.multiply(-1));
            }
            default -> {
                for (int a = -1; a <= 1; a++) {
                    for (int b = -1; b <= 1; b++) {
                        if (a == 0 && b == 0) continue;
                        out.add(new Vec3i(u.getX() * a + v.getX() * b, u.getY() * a + v.getY() * b, u.getZ() * a + v.getZ() * b));
                    }
                }
            }
        }
        return out;
    }

    private ExtendPlanner() {}
}
