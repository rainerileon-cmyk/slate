package dev.fallingcloud.slate.building.client.mode;

import dev.fallingcloud.slate.building.compat.SubLevels;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.ModeParams;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where the crosshair points for a building mode: the block a click would use as an anchor, the face, and whether
 * it is an "air" point. Reach is vanilla block reach plus the tool-tier reach bonus.
 *
 * <p>Which block a corner lands on depends on the mode: modes that build new blocks (fill, walls, line, hollow,
 * outline, cylinder, sphere) take the block in front of the clicked face, like normal placing (a clicked
 * replaceable such as grass is used directly); modes that work on existing blocks (replace, clear, copy, ...) and
 * any mode whose {@code replace} rule is "everything" take the clicked block itself.
 *
 * <p>Paste and the move destination follow the ops server's convention ({@code PlanContext}): the anchor is the
 * clicked block and the box sits against the clicked face ({@code ClipboardPlanners.origin}). A clicked replaceable
 * block is built into, so the anchor is the block behind it; on air the anchor is the block under the air point with
 * face UP, so the box stands on the air point.
 *
 * <p>On air, the first corner sits {@code airDistance} blocks along the view; the moving second corner prefers the
 * axis plane through corner A that faces the camera most directly (so a box drawn in the air stays a flat floor or
 * wall until the view tilts), within reach.
 *
 * <p>With Sable a corner may lie on a sub-level (a ship): its position is then one of the ship's plot. A selection
 * lies in one space, the one of its first corner. The second corner is looked for there: a block of another space
 * under the crosshair is not a corner of it, and the point in the air is found with the eye and the view as the
 * ship has them, so the box is drawn along the ship's own axes, however the ship is turned.
 *
 * @param pos     the anchor a click would set
 * @param face    the clicked face, or the face towards the player on air (UP for paste / move destinations)
 * @param air     true when nothing within reach was hit
 * @param clicked the block actually hit (null on air)
 */
record ModeTarget(BlockPos pos, Direction face, boolean air, @Nullable BlockPos clicked) {

    /** What a click is about to set. */
    enum Role { CORNER, POINT, DESTINATION, CENTRE }

    static ModeTarget compute(final Player player, final BuildMode mode, final ModeParams params, final Role role,
                              final @Nullable BlockPos planeAnchor, final float partialTick) {
        final Level level = player.level();
        double reach = ModeRules.reach(player);
        Vec3 eye = SubLevels.eye(player, partialTick);
        Vec3 look = player.getViewVector(partialTick);
        final HitResult hit = player.pick(reach, partialTick, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK
            && (planeAnchor == null || SubLevels.same(level, planeAnchor, bhr.getBlockPos()))) {
            final BlockPos clicked = bhr.getBlockPos();
            final Direction face = bhr.getDirection();
            if (against(mode, role)) {
                final BlockPos anchor = level.getBlockState(clicked).canBeReplaced() ? clicked.relative(face.getOpposite()) : clicked;
                return new ModeTarget(anchor, face, false, clicked);
            }
            if (!onFace(mode, params, role)) return new ModeTarget(clicked, face, false, clicked);
            final BlockState state = level.getBlockState(clicked);
            final BlockPos pos = state.canBeReplaced() ? clicked : clicked.relative(face);
            return new ModeTarget(pos, face, false, clicked);
        }
        // In the air. A second corner is found in the space of the first: on a sub-level, as the sub-level sees it.
        final SubLevels.Pose space = planeAnchor == null ? null : SubLevels.renderAt(planeAnchor);
        if (space != null) {
            eye = space.toLocal(eye);
            look = space.dirToLocal(look);
            reach /= space.size();
        }
        final Direction towardsPlayer = Direction.getNearest(look.x, look.y, look.z).getOpposite();
        if (planeAnchor != null) {
            final BlockPos onPlane = onAnchorPlane(eye, look, planeAnchor, reach);
            if (onPlane != null) return new ModeTarget(onPlane, towardsPlayer, true, null);
        }
        final int distance = ClientModeState.settings().airDistance((int) Math.floor(reach));
        final BlockPos pos = BlockPos.containing(eye.add(look.scale(distance)));
        if (against(mode, role)) return new ModeTarget(pos.below(), Direction.UP, true, null);
        return new ModeTarget(pos, towardsPlayer, true, null);
    }

    /** Whether a click of {@code role} in {@code mode} anchors a box against the clicked face (paste, move destination). */
    static boolean against(final BuildMode mode, final Role role) {
        return role == Role.DESTINATION || role == Role.POINT && mode == BuildModes.PASTE;
    }

    /** Whether a click of {@code role} in {@code mode} takes the block in front of the clicked face. */
    static boolean onFace(final BuildMode mode, final ModeParams params, final Role role) {
        return switch (role) {
            case DESTINATION, CENTRE, POINT -> false;
            case CORNER -> {
                if (mode.param("replace") != null && "ALL".equals(params.getChoice("replace"))) yield false;
                yield mode == BuildModes.FILL || mode == BuildModes.WALLS || mode == BuildModes.LINE
                    || mode == BuildModes.HOLLOW_BOX || mode == BuildModes.OUTLINE || mode == BuildModes.CYLINDER
                    || mode == BuildModes.SPHERE;
            }
        };
    }

    /**
     * The block where the view ray meets the axis plane through {@code anchor}'s centre that faces the camera most
     * directly (largest |look · normal|), if that point is within {@code reach}; else null.
     */
    private static @Nullable BlockPos onAnchorPlane(final Vec3 eye, final Vec3 look, final BlockPos anchor, final double reach) {
        final Vec3 c = Vec3.atCenterOf(anchor);
        final double[] dirs = {look.x, look.y, look.z};
        final double[] eyes = {eye.x, eye.y, eye.z};
        final double[] centres = {c.x, c.y, c.z};
        int best = -1;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dirs[axis]) < 1.0E-4) continue;
            if (best < 0 || Math.abs(dirs[axis]) > Math.abs(dirs[best])) best = axis;
        }
        if (best < 0) return null;
        final double t = (centres[best] - eyes[best]) / dirs[best];
        if (t <= 0 || t > reach) return null;
        final Vec3 p = eye.add(look.scale(t));
        // Snap the plane axis exactly onto the anchor so rounding never tips the corner into the next layer.
        return switch (best) {
            case 0 -> BlockPos.containing(c.x, p.y, p.z);
            case 1 -> BlockPos.containing(p.x, c.y, p.z);
            default -> BlockPos.containing(p.x, p.y, c.z);
        };
    }

    /**
     * The face of {@code box} the player is looking at (the near face the view ray enters), or, when the ray misses
     * the box or starts inside it, the face in the view direction ("push the side I am looking towards").
     */
    static Direction lookedAtFace(final AABB box, final Vec3 worldEye, final Vec3 worldLook) {
        // A box on a sub-level is looked at as the sub-level sees the player.
        final SubLevels.Pose space = SubLevels.present() ? SubLevels.renderAt(BlockPos.containing(box.getCenter())) : null;
        final Vec3 eye = space == null ? worldEye : space.toLocal(worldEye);
        final Vec3 look = space == null ? worldLook : space.dirToLocal(worldLook);
        if (!box.contains(eye)) {
            final Optional<Vec3> hit = box.clip(eye, eye.add(look.scale(256)));
            if (hit.isPresent()) {
                final Vec3 p = hit.get();
                final double eps = 1.0E-4;
                if (Math.abs(p.x - box.minX) < eps) return Direction.WEST;
                if (Math.abs(p.x - box.maxX) < eps) return Direction.EAST;
                if (Math.abs(p.y - box.minY) < eps) return Direction.DOWN;
                if (Math.abs(p.y - box.maxY) < eps) return Direction.UP;
                if (Math.abs(p.z - box.minZ) < eps) return Direction.NORTH;
                if (Math.abs(p.z - box.maxZ) < eps) return Direction.SOUTH;
            }
        }
        return Direction.getNearest(look.x, look.y, look.z);
    }

    /**
     * The horizontal direction the player faces (arrow-key "forward"), as the space {@code anchor} lies in has it:
     * the world's, or a sub-level's own.
     */
    static Direction horizontalFacing(final Player player, final @Nullable BlockPos anchor) {
        final SubLevels.Pose space = anchor == null || !SubLevels.present() ? null : SubLevels.renderAt(anchor);
        if (space == null) return player.getDirection();
        final Vec3 look = space.dirToLocal(player.getLookAngle());
        return Math.abs(look.x) + Math.abs(look.z) < 1.0E-4 ? player.getDirection() : Direction.getNearest(look.x, 0.0, look.z);
    }
}
