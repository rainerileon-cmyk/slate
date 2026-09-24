package dev.fallingcloud.slate.building.ops.plan;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** An inclusive block box, the selection of AREA modes. */
public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** The box spanned by two corners, in any order. */
    public static Box of(final BlockPos a, final BlockPos b) {
        return new Box(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
            Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    /** A box of {@code sx × sy × sz} blocks whose minimum corner is {@code min}. */
    public static Box sized(final BlockPos min, final int sx, final int sy, final int sz) {
        return new Box(min.getX(), min.getY(), min.getZ(), min.getX() + sx - 1, min.getY() + sy - 1, min.getZ() + sz - 1);
    }

    public int sizeX() { return maxX - minX + 1; }

    public int sizeY() { return maxY - minY + 1; }

    public int sizeZ() { return maxZ - minZ + 1; }

    /** Size along an axis index: 0 = X, 1 = Y, 2 = Z. */
    public int size(final net.minecraft.core.Direction.Axis axis) {
        return switch (axis) {
            case X -> sizeX();
            case Y -> sizeY();
            case Z -> sizeZ();
        };
    }

    /** The longest edge. */
    public int maxEdge() { return Math.max(sizeX(), Math.max(sizeY(), sizeZ())); }

    public long volume() { return (long) sizeX() * sizeY() * sizeZ(); }

    public BlockPos min() { return new BlockPos(minX, minY, minZ); }

    public BlockPos max() { return new BlockPos(maxX, maxY, maxZ); }

    public boolean contains(final int x, final int y, final int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(final BlockPos p) {
        return contains(p.getX(), p.getY(), p.getZ());
    }

    /** World-space bounds (the far corner is exclusive, as for rendering). */
    public AABB aabb() {
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    /** The smallest box containing both. */
    public Box union(final Box o) {
        return new Box(Math.min(minX, o.minX), Math.min(minY, o.minY), Math.min(minZ, o.minZ),
            Math.max(maxX, o.maxX), Math.max(maxY, o.maxY), Math.max(maxZ, o.maxZ));
    }
}
