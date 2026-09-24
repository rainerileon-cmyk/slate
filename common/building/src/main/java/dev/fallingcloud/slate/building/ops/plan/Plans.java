package dev.fallingcloud.slate.building.ops.plan;

import dev.fallingcloud.slate.building.ops.Limits;
import dev.fallingcloud.slate.building.ops.Plan;
import dev.fallingcloud.slate.building.ops.PlanContext;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** Checks and small helpers shared by the planners. */
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
        return box.volume() > limits.maxVolume() ? PlanErrors.tooMany((int) Math.min(Integer.MAX_VALUE, box.volume()), limits.maxVolume()) : null;
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
