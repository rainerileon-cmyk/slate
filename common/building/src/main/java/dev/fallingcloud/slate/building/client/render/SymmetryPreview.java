package dev.fallingcloud.slate.building.client.render;

import dev.fallingcloud.slate.building.client.mode.ClientModeState;
import dev.fallingcloud.slate.building.ops.BuildModes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where the active symmetry (mirror / radial TOGGLE mode) would copy a placement, for the mirrored placement ghosts.
 * The math follows the server's replay rules exactly (ops {@code SymmetryMath}, design §7): mirror planes pass
 * through the CENTRE of the centre block (X flips east ↔ west, Z north ↔ south, XZ adds the diagonal copy turned
 * 180°); radial copies turn clockwise seen from above around the centre block's vertical axis, positions rounded,
 * states turned by the nearest quarter turn.
 *
 * <p>Kept inside the render package on purpose (ops is another owner's area): if {@code ops.SymmetryMath} changes,
 * this must follow. The server's radius limit (square tier) is not known to the preview; copies are shown up to the
 * largest radius, {@link #MAX_RADIUS}.
 */
final class SymmetryPreview {

    /** The largest symmetry radius any tier grants (design §7: 16/32/64/128). */
    static final int MAX_RADIUS = 128;

    /** One copy: where it goes and the state it gets. */
    record Image(BlockPos pos, BlockState state) {}

    /** The copies of placing {@code state} at {@code pos} under {@code symmetry}; never the original, no duplicates. */
    static List<Image> images(final ClientModeState.Symmetry symmetry, final BlockPos pos, final BlockState state) {
        final List<Image> out = new ArrayList<>(8);
        final BlockPos centre = symmetry.centre();
        if (Math.abs(pos.getX() - centre.getX()) > MAX_RADIUS || Math.abs(pos.getY() - centre.getY()) > MAX_RADIUS
            || Math.abs(pos.getZ() - centre.getZ()) > MAX_RADIUS) return out;
        final int dx = pos.getX() - centre.getX();
        final int dz = pos.getZ() - centre.getZ();
        if (symmetry.mode() == BuildModes.MIRROR_MODE) {
            final String axis = symmetry.params().getChoice("axis");
            final boolean x = "X".equals(axis) || "XZ".equals(axis);
            final boolean z = "Z".equals(axis) || "XZ".equals(axis);
            if (x) add(out, pos, new BlockPos(centre.getX() - dx, pos.getY(), pos.getZ()), state.mirror(Mirror.FRONT_BACK));
            if (z) add(out, pos, new BlockPos(pos.getX(), pos.getY(), centre.getZ() - dz), state.mirror(Mirror.LEFT_RIGHT));
            if (x && z) add(out, pos, new BlockPos(centre.getX() - dx, pos.getY(), centre.getZ() - dz), state.rotate(Rotation.CLOCKWISE_180));
        } else if (symmetry.mode() == BuildModes.RADIAL) {
            final int n = Math.max(2, symmetry.params().getInt("slices"));
            for (int k = 1; k < n; k++) {
                final double a = 2 * Math.PI * k / n;
                final double cos = Math.cos(a), sin = Math.sin(a);
                final int rx = (int) Math.round(dx * cos - dz * sin);
                final int rz = (int) Math.round(dx * sin + dz * cos);
                final int quarters = Math.floorMod((int) Math.round(4.0 * k / n), 4);
                add(out, pos, new BlockPos(centre.getX() + rx, pos.getY(), centre.getZ() + rz), state.rotate(Rotation.values()[quarters]));
            }
        }
        return out;
    }

    private static void add(final List<Image> out, final BlockPos original, final BlockPos pos, final BlockState state) {
        if (pos.equals(original)) return;
        for (final Image i : out) if (i.pos().equals(pos)) return;
        out.add(new Image(pos, state));
    }

    private SymmetryPreview() {}
}
