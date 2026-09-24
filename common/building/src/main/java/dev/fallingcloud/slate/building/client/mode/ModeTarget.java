package dev.fallingcloud.slate.building.client.mode;

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
 * outline, cylinder, sphere, paste, move destination) take the block in front of the clicked face, like normal
 * placing (a clicked replaceable such as grass is used directly); modes that work on existing blocks (replace,
 * clear, copy, ...) and any mode whose {@code replace} rule is "everything" take the clicked block itself.
 *
 * <p>On air, the first corner sits {@code airDistance} blocks along the view; the moving second corner prefers the
 * axis plane through corner A that faces the camera most directly (so a box drawn in the air stays a flat floor or
 * wall until the view tilts), within reach.
 *
 * @param pos     the anchor a click would set
 * @param face    the clicked face, or the face towards the player on air
 * @param air     true when nothing within reach was hit
 * @param clicked the block actually hit (null on air)
 */
record ModeTarget(BlockPos pos, Direction face, boolean air, @Nullable BlockPos clicked) {

    /** What a click is about to set. */
    enum Role { CORNER, POINT, DESTINATION, CENTRE }

    static ModeTarget compute(final Player player, final BuildMode mode, final ModeParams params, final Role role,
                              final @Nullable BlockPos planeAnchor, final float partialTick) {
        final Level level = player.level();
        final double reach = ModeRules.reach(player);
        final Vec3 eye = player.getEyePosition(partialTick);
        final Vec3 look = player.getViewVector(partialTick);
        final HitResult hit = player.pick(reach, partialTick, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            final BlockPos clicked = bhr.getBlockPos();
            final Direction face = bhr.getDirection();
            if (!onFace(mode, params, role)) return new ModeTarget(clicked, face, false, clicked);
            final BlockState state = level.getBlockState(clicked);
            final BlockPos pos = state.canBeReplaced() ? clicked : clicked.relative(face);
            return new ModeTarget(pos, face, false, clicked);
        }
        final Direction towardsPlayer = Direction.getNearest(look.x, look.y, look.z).getOpposite();
        if (planeAnchor != null) {
            final BlockPos onPlane = onAnchorPlane(eye, look, planeAnchor, reach);
            if (onPlane != null) return new ModeTarget(onPlane, towardsPlayer, true, null);
        }
        final int distance = ClientModeState.settings().airDistance((int) Math.floor(reach));
        final BlockPos pos = BlockPos.containing(eye.add(look.scale(distance)));
        return new ModeTarget(pos, towardsPlayer, true, null);
    }

    /** Whether a click of {@code role} in {@code mode} takes the block in front of the clicked face. */
    static boolean onFace(final BuildMode mode, final ModeParams params, final Role role) {
        return switch (role) {
            case DESTINATION -> true;
            case CENTRE -> false;
            case POINT -> mode == BuildModes.PASTE;
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
    static Direction lookedAtFace(final AABB box, final Vec3 eye, final Vec3 look) {
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

    /** The horizontal direction the player faces (arrow-key "forward"). */
    static Direction horizontalFacing(final Player player) {
        return player.getDirection();
    }
}
