package dev.fallingcloud.slate.building.ops;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a {@link ModePlanner} may look at. Built the same way on the client (preview) and the server
 * (execution), so plans agree.
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
}
