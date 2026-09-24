package dev.fallingcloud.slate.building.ops;

import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
 */
public record PlanContext(Level level, Player player, BuildMode mode, ModeParams params, List<BlockPos> anchors,
                          Direction face, Palette palette, @Nullable Clipboard clipboard, Limits limits) {

    public PlanContext {
        anchors = List.copyOf(anchors);
    }

    /**
     * The context both sides use: palette from hotbar {@code slot} ({@link Palette#resolve}), limits from the
     * player's toolbox under the rules of the player's side ({@link BuildingServerSettings#effective}).
     */
    public static PlanContext create(final Player player, final BuildMode mode, final ModeParams params, final List<BlockPos> anchors,
                                     final Direction face, final int slot, final @Nullable Clipboard clipboard) {
        final Limits limits = ToolboxAccess.of(player).limits(BuildingServerSettings.effective(player));
        return new PlanContext(player.level(), player, mode, params, anchors, face, Palette.resolve(player, mode, params, slot),
            clipboard, limits);
    }

    /** Anchor {@code i}, or the last anchor when there are fewer (so a one-click selection is a single block). */
    public BlockPos anchor(final int i) {
        return anchors.get(Math.min(i, anchors.size() - 1));
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
