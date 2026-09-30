package dev.fallingcloud.slate.building.ops;

import dev.fallingcloud.slate.building.compat.SubLevels;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a {@link ModePlanner} may look at. Built the same way on the client (preview) and the server
 * (execution), so plans agree: use {@link #create} on both sides.
 *
 * <p>Anchor conventions (the planners document the details):
 * <ul>
 *   <li>AREA modes: {@code anchors = [cornerA, cornerB]} (one anchor = a single block). Cylinder: A = base centre,
 *       B = radius + height. Sphere: A = centre, B = a point on the surface. Replace / clear / reshape with a
 *       CLICKED filter read the block at corner A.</li>
 *   <li>POINT modes: {@code anchors = [clickedBlock]}, {@code face} = the clicked face. Extend grows out of that
 *       face; paste sits against it (on top for UP, centred on the clicked block).</li>
 *   <li>MOVE: {@code anchors = [cornerA, cornerB, destinationBlock]}, {@code face} = the face clicked for the
 *       destination; the moved box sits against it like a paste.</li>
 *   <li>TOGGLE (mirror / radial): {@code anchors = [centre]}.</li>
 * </ul>
 *
 * @param level     the level to read (never written by planners)
 * @param player    the acting player
 * @param mode      the mode being planned
 * @param params    its parameter values
 * @param anchors   selection anchors in click order (corner A, corner B; or a single point / paste origin)
 * @param face      the face clicked for the first anchor (extend direction, paste orientation, ...)
 * @param palette   what to place
 * @param clipboard the player's clipboard (paste / move), else null
 * @param limits    the player's limits (volume/span caps)
 * @param destructive a left-click selection: the plan's places and replaces become breaks of what stands there
 *                    ({@link Planners}); the palette is then only a stand-in for the planner's geometry
 */
public record PlanContext(Level level, Player player, BuildMode mode, ModeParams params, List<BlockPos> anchors,
                          Direction face, Palette palette, @Nullable Clipboard clipboard, Limits limits, boolean destructive) {

    public PlanContext {
        anchors = List.copyOf(anchors);
    }

    /**
     * The context both sides use: palette from hotbar {@code slot} ({@link Palette#resolve}), limits from the
     * player's toolbox under the rules of the player's side ({@link BuildingServerSettings#effective}). A destructive
     * run needs no block in hand: an empty palette is replaced by a stand-in, since only the geometry is used.
     */
    public static PlanContext create(final Player player, final BuildMode mode, final ModeParams params, final List<BlockPos> anchors,
                                     final Direction face, final int slot, final @Nullable Clipboard clipboard, final boolean destructive) {
        final Limits limits = ToolboxAccess.of(player).limits(BuildingServerSettings.effective(player));
        Palette palette = Palette.resolve(player, mode, params, slot);
        if (destructive && palette.isEmpty()) palette = Palette.standIn();
        return new PlanContext(player.level(), player, mode, params, anchors, face, palette, clipboard, limits, destructive);
    }

    /** Anchor {@code i}, or the last anchor when there are fewer (so a one-click selection is a single block). */
    public BlockPos anchor(final int i) {
        return anchors.get(Math.min(i, anchors.size() - 1));
    }

    /** The horizontal direction the player faces ({@code Player.getDirection()}), in the space of the selection. */
    public Direction facing() {
        return facing(level, player, anchors);
    }

    /** The nearest of the six directions to the player's view ({@code Player.getNearestViewDirection()}), in the space of the selection. */
    public Direction view() {
        return view(level, player, anchors);
    }

    /**
     * The horizontal direction {@code player} faces, as the space of a selection has it: the world's own directions,
     * unless the first anchor lies on a Sable sub-level, which has axes of its own (north on a ship is where its
     * plot's north is).
     */
    public static Direction facing(final Level level, final Player player, final List<BlockPos> anchors) {
        final SubLevels.Pose space = anchors.isEmpty() || !SubLevels.present() ? null : SubLevels.at(level, anchors.get(0));
        if (space == null) return player.getDirection();
        final Vec3 look = space.dirToLocal(player.getLookAngle());
        return Math.abs(look.x) + Math.abs(look.z) < 1.0E-4 ? player.getDirection() : Direction.getNearest(look.x, 0.0, look.z);
    }

    /** The nearest of the six directions to {@code player}'s view, as the space of a selection has it. */
    public static Direction view(final Level level, final Player player, final List<BlockPos> anchors) {
        final SubLevels.Pose space = anchors.isEmpty() || !SubLevels.present() ? null : SubLevels.at(level, anchors.get(0));
        if (space == null) return player.getNearestViewDirection();
        final Vec3 look = space.dirToLocal(player.getLookAngle());
        return Direction.getNearest(look.x, look.y, look.z);
    }

    /**
     * The per-operation seed of random palettes: derived from the mode and the anchors only, so the client preview
     * and the server pick the same block for every position.
     */
    public long seed() {
        long h = mode.id().hashCode();
        for (final BlockPos p : anchors) h = Palette.mix(h * 31 + p.asLong());
        return h;
    }
}
