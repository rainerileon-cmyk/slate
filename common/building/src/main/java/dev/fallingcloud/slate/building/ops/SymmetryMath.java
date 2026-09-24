package dev.fallingcloud.slate.building.ops;

import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.toolbox.ToolTier;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Where the TOGGLE modes copy a placement or break to, on both sides (the server replays, the client previews).
 *
 * <p>Mirror: the planes pass through the CENTRE of the centre block. {@code axis} X flips east ↔ west (the plane
 * {@code x = centre.x + 0.5}), Z flips north ↔ south, XZ does both (three copies, the diagonal one turned 180°).
 * Radial: {@code slices} copies turned around the vertical axis through the centre block's middle, clockwise seen
 * from above; states turn by the nearest quarter turn. Only positions within the square tier's radius of the centre
 * ({@link #radius}, a cube around it) are copied.
 */
public final class SymmetryMath {

    /** One copy: its position and how the state is mirrored, then rotated. */
    public record Image(BlockPos pos, Mirror mirror, Rotation rotation) {
        public BlockState apply(final BlockState state) {
            return state.mirror(mirror).rotate(rotation);
        }
    }

    /** The copies of {@code pos} (never {@code pos} itself, no duplicates); empty for other modes or out of range. */
    public static List<Image> images(final BuildMode mode, final ModeParams params, final BlockPos centre, final int radius, final BlockPos pos) {
        final List<Image> out = new ArrayList<>(8);
        if (!inRange(centre, pos, radius)) return out;
        final int dx = pos.getX() - centre.getX();
        final int dz = pos.getZ() - centre.getZ();
        if (mode == BuildModes.MIRROR_MODE) {
            final String axis = params.getChoice("axis");
            final boolean x = "X".equals(axis) || "XZ".equals(axis);
            final boolean z = "Z".equals(axis) || "XZ".equals(axis);
            if (x) add(out, pos, new BlockPos(centre.getX() - dx, pos.getY(), pos.getZ()), Mirror.FRONT_BACK, Rotation.NONE);
            if (z) add(out, pos, new BlockPos(pos.getX(), pos.getY(), centre.getZ() - dz), Mirror.LEFT_RIGHT, Rotation.NONE);
            if (x && z) add(out, pos, new BlockPos(centre.getX() - dx, pos.getY(), centre.getZ() - dz), Mirror.NONE, Rotation.CLOCKWISE_180);
        } else if (mode == BuildModes.RADIAL) {
            final int n = Math.max(2, params.getInt("slices"));
            for (int k = 1; k < n; k++) {
                final double a = 2 * Math.PI * k / n;
                final double cos = Math.cos(a);
                final double sin = Math.sin(a);
                final int rx = (int) Math.round(dx * cos - dz * sin);
                final int rz = (int) Math.round(dx * sin + dz * cos);
                final int quarters = Math.floorMod((int) Math.round(4.0 * k / n), 4);
                add(out, pos, new BlockPos(centre.getX() + rx, pos.getY(), centre.getZ() + rz), Mirror.NONE, Rotation.values()[quarters]);
            }
        }
        return out;
    }

    private static void add(final List<Image> out, final BlockPos original, final BlockPos pos, final Mirror mirror, final Rotation rotation) {
        if (pos.equals(original)) return;
        for (final Image i : out) if (i.pos().equals(pos)) return;
        out.add(new Image(pos, mirror, rotation));
    }

    /** Whether {@code pos} is within {@code radius} of {@code centre} on every axis. */
    public static boolean inRange(final BlockPos centre, final BlockPos pos, final int radius) {
        return Math.abs(pos.getX() - centre.getX()) <= radius && Math.abs(pos.getY() - centre.getY()) <= radius
            && Math.abs(pos.getZ() - centre.getZ()) <= radius;
    }

    /** The symmetry radius of {@code player}: {@code symmetryRadius} at their square tier (tier 1 minimum). */
    public static int radius(final Player player) {
        final int tier = Math.max(ToolTier.MIN, ToolboxAccess.of(player).tier(ToolType.SQUARE));
        return ToolTier.index(BuildingServerSettings.effective(player).ops().symmetryRadius, tier);
    }

    /** The region symmetry covers around {@code centre} (for drawing it). */
    public static AABB region(final BlockPos centre, final int radius) {
        return new AABB(centre).inflate(radius);
    }

    private SymmetryMath() {}
}
