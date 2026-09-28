package dev.fallingcloud.slate.building.ops;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.ServerOps;
import dev.fallingcloud.slate.building.ops.plan.BoxPlanners;
import dev.fallingcloud.slate.building.ops.plan.ClipboardPlanners;
import dev.fallingcloud.slate.building.ops.plan.EditPlanners;
import dev.fallingcloud.slate.building.ops.plan.ExtendPlanner;
import dev.fallingcloud.slate.building.ops.plan.InfoPlanners;
import dev.fallingcloud.slate.building.ops.plan.PlanErrors;
import dev.fallingcloud.slate.building.ops.plan.PlanOverflow;
import dev.fallingcloud.slate.building.ops.plan.ShapePlanners;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Blocks;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link ModePlanner} of each {@link BuildMode}. Planners are deterministic and read-only: the client runs them
 * for the ghost preview (cache the result per selection + parameters, a large fill is tens of thousands of states),
 * the server runs them again for the real thing. Every planner is wrapped with the shared rules (server switch,
 * disabled modes, the toolbox lock) and never throws: a failure becomes an error plan.
 */
public final class Planners {

    private static final Map<String, ModePlanner> PLANNERS = new HashMap<>();

    static {
        put(BuildModes.FILL, BoxPlanners::fill);
        put(BuildModes.WALLS, BoxPlanners::walls);
        put(BuildModes.HOLLOW_BOX, BoxPlanners::hollow);
        put(BuildModes.OUTLINE, BoxPlanners::outline);
        put(BuildModes.LINE, ShapePlanners::line);
        put(BuildModes.CYLINDER, ShapePlanners::cylinder);
        put(BuildModes.SPHERE, ShapePlanners::sphere);
        put(BuildModes.EXTEND, ExtendPlanner::plan);
        put(BuildModes.REPLACE_BLOCKS, EditPlanners::replace);
        put(BuildModes.OVERLAY, EditPlanners::overlay);
        put(BuildModes.CLEAR, EditPlanners::clear);
        put(BuildModes.RESHAPE, EditPlanners::reshape);
        put(BuildModes.COPY, ClipboardPlanners::copy);
        put(BuildModes.PASTE, ClipboardPlanners::paste);
        put(BuildModes.CUT, ClipboardPlanners::cut);
        put(BuildModes.STACK, ClipboardPlanners::stack);
        put(BuildModes.MOVE, ClipboardPlanners::move);
        put(BuildModes.MIRROR_MODE, InfoPlanners::symmetry);
        put(BuildModes.RADIAL, InfoPlanners::symmetry);
        put(BuildModes.MEASURE, InfoPlanners::measure);
    }

    public static ModePlanner of(final BuildMode mode) {
        final ModePlanner planner = PLANNERS.get(mode.id());
        return planner != null ? planner : ctx -> Plan.error(Component.translatable("slate_building.plan.unavailable", mode.name()));
    }

    /** Plans {@code ctx} with its mode's planner (shorthand for {@code of(ctx.mode()).plan(ctx)}). */
    public static Plan plan(final PlanContext ctx) {
        return of(ctx.mode()).plan(ctx);
    }

    private static void put(final BuildMode mode, final ModePlanner planner) {
        PLANNERS.put(mode.id(), ctx -> {
            if (ctx.anchors().isEmpty()) return Plan.error(PlanErrors.noAnchor());
            final Component refused = refusal(ctx);
            if (refused != null) return Plan.error(refused);
            try {
                final Plan plan = planner.plan(ctx);
                return ctx.destructive() ? destructive(plan, ctx) : plan;
            } catch (final PlanOverflow e) {
                // More positions than the player may change: refused before the whole region was walked.
                return new Plan(List.of(), e.bounds() != null ? e.bounds() : Plan.EMPTY.bounds(), PlanErrors.tooMany(e.count(), e.max()), e.count());
            } catch (final RuntimeException e) {
                SlateBuilding.LOGGER.error("[Slate Building] planner {} failed", mode.id(), e);
                return Plan.error(PlanErrors.failed());
            }
        });
    }

    /**
     * A left-click selection: the same geometry, but every place / replace becomes a break of the block standing
     * there (air is skipped, breaks stay breaks). Fill clears the box, Walls tears them down, Replace removes the
     * matching blocks, Overlay strips the top layer. Drops and the harvest rules are the executor's, as for Clear.
     * {@link dev.fallingcloud.slate.building.ops.plan.PlanBuilder} already plans the breaks for a destructive context
     * (ignoring the held block and the replace policy); this pass is the safety net for anything planned around it.
     */
    static Plan destructive(final Plan plan, final PlanContext ctx) {
        if (plan == null || !plan.ok()) return plan;
        final List<Change> out = new ArrayList<>(plan.changes().size());
        for (final Change c : plan.changes()) {
            if (c.kind() == Change.Kind.BREAK) { out.add(c); continue; }
            if (ctx.level().getBlockState(c.pos()).isAir()) continue;
            out.add(new Change(c.pos(), Blocks.AIR.defaultBlockState(), null, Change.Kind.BREAK));
        }
        return new Plan(out, plan.bounds(), null, plan.requestedCount());
    }

    /** Why {@code ctx.player()} may not use the mode at all (server switch, disabled mode, toolbox lock), or null. */
    public static @Nullable Component refusal(final PlanContext ctx) {
        final BuildMode mode = ctx.mode();
        if (mode.tool() == null) return null;
        final ServerOps ops = BuildingServerSettings.effective(ctx.level()).ops();
        if (!ops.enabled) return PlanErrors.disabled();
        if (ops.disabledModes != null && ops.disabledModes.contains(mode.id())) return PlanErrors.modeDisabled();
        final Component lock = ToolboxAccess.of(ctx.player()).lockReason(mode);
        if (lock != null) return lock;
        return ctx.limits().maxVolume() <= 0 ? PlanErrors.needsToolbox() : null;
    }

    private Planners() {}
}
