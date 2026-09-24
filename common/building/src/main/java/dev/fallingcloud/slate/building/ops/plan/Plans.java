package dev.fallingcloud.slate.building.ops.plan;

import dev.fallingcloud.slate.building.ops.Limits;
import dev.fallingcloud.slate.building.ops.Plan;
import dev.fallingcloud.slate.building.ops.PlanContext;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Checks and small helpers shared by the planners.
 *
 * <p>Every planner refuses a request before it walks the world when the request is bigger than the player may do:
 * the span check ({@link #span}) and a volume check against an estimate of the positions the mode touches
 * ({@link #tooMany}). The estimates use the same formulas as the client's pre-check ({@code ModeGeometry.estimate}),
 * so the server refuses exactly what an honest client would not send; {@link PlanBuilder#want()} is the hard stop
 * behind them. Planners that read a whole box refuse it when part of it is not loaded ({@link #unloaded}): planning
 * never loads or generates a chunk.
 */
final class Plans {

    /** The selection box of an AREA mode (corner A..B, or the single block A). */
    static Box box(final PlanContext ctx) {
        return Box.of(ctx.anchor(0), ctx.anchor(1));
    }

    /** A too-wide error when any edge of {@code box} exceeds the span limit, else null. */
    static @Nullable Component span(final Box box, final Limits limits) {
        final int edge = box.maxEdge();
        return edge > limits.maxSpan() ? PlanErrors.tooWide(edge, limits.maxSpan()) : null;
    }

    /** Span check plus a volume check for modes that touch every position of the box. */
    static @Nullable Component spanAndVolume(final Box box, final Limits limits) {
        final Component span = span(box, limits);
        if (span != null) return span;
        return tooMany(box.volume(), limits);
    }

    /** A too-many error when {@code estimate} positions exceed the volume limit, else null. */
    static @Nullable Component tooMany(final long estimate, final Limits limits) {
        return estimate > limits.maxVolume() ? PlanErrors.tooMany((int) Math.min(Integer.MAX_VALUE, estimate), limits.maxVolume()) : null;
    }

    /**
     * An "isn't loaded" error when a chunk under {@code box}, or next to its sides, is not loaded in {@code level} (never
     * loads one), else null: planners that read the whole box refuse it rather than leave out its edge
     * ({@link PlanBuilder#readable} needs the neighbours of a position loaded too).
     */
    static @Nullable Component unloaded(final Level level, final Box box) {
        for (int cx = (box.minX() - 1) >> 4; cx <= (box.maxX() + 1) >> 4; cx++) {
            for (int cz = (box.minZ() - 1) >> 4; cz <= (box.maxZ() + 1) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) return PlanErrors.unloaded();
            }
        }
        return null;
    }

    /** Span, loaded and volume checks for modes that read every position of the box. */
    static @Nullable Component readable(final Level level, final Box box, final Limits limits) {
        final Component err = spanAndVolume(box, limits);
        return err != null ? err : unloaded(level, box);
    }

    /** An error plan that still shows the selection box. */
    static Plan error(final Component error, final @Nullable AABB bounds) {
        return new Plan(List.of(), bounds == null ? Plan.EMPTY.bounds() : bounds, error, 0);
    }

    /** What a removed block leaves behind: its fluid when waterlogged and fluids are kept, else air. */
    static BlockState leftBehind(final BlockState existing, final boolean keepFluids) {
        return keepFluids ? existing.getFluidState().createLegacyBlock() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    /** A pure fluid block (water, lava; not a waterlogged block). */
    static boolean isFluid(final BlockState state) {
        return state.getBlock() instanceof LiquidBlock;
    }

    private Plans() {}
}
