package dev.fallingcloud.slate.building.block;

import dev.fallingcloud.slate.building.mixin.variant.VoxelShapeAccessor;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.SliceShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The outline shape of a fence, wall or pane with diagonal arms: everything the game computes with it (collision,
 * ray casts, face joins, moving, bounds) comes from {@code union}, the axis-aligned stepped approximation of the arms
 * (what the Diagonal mods' twins collide with too), but the outline the game draws when the block is looked at is
 * the straight part's own edges plus the arms as proper turned boxes: a clean diagonal prism from the post to the
 * block corner instead of a staircase. This is exactly how the DiagonalBlocks library draws its twins (a collision
 * shape with custom outline edges), so ours look the same when selected.
 *
 * <p>Built on {@link SliceShape} only because {@code VoxelShape}'s constructor is not reachable from a mod; every
 * geometry method is overridden to delegate to {@code union}, and {@code move} hands out {@code union}'s plain
 * moved shape, so anything that joins shapes (entity collision, occlusion checks) never sees the slice underneath.
 */
public final class DiagonalVoxelShape extends SliceShape {

    private final VoxelShape straight;
    private final VoxelShape union;
    /** {x0, y0, z0, x1, y1, z1} per arm edge, block units. */
    private final double[][] armEdges;

    DiagonalVoxelShape(final VoxelShape straight, final VoxelShape union, final double[][] armEdges) {
        super(union, Direction.Axis.X, 0);
        this.straight = straight;
        this.union = union;
        this.armEdges = armEdges;
    }

    /** The stepped union this outline stands in for. */
    public VoxelShape union() {
        return union;
    }

    @Override
    public DoubleList getCoords(final Direction.Axis axis) {
        return ((VoxelShapeAccessor) union).slate$getCoords(axis);
    }

    @Override
    public boolean isEmpty() {
        return union.isEmpty();
    }

    @Override
    public AABB bounds() {
        return union.bounds();
    }

    @Override
    public double min(final Direction.Axis axis) {
        return union.min(axis);
    }

    @Override
    public double max(final Direction.Axis axis) {
        return union.max(axis);
    }

    @Override
    public VoxelShape move(final double x, final double y, final double z) {
        return union.move(x, y, z);
    }

    @Override
    public VoxelShape optimize() {
        return this;
    }

    @Override
    public void forAllBoxes(final Shapes.DoubleLineConsumer consumer) {
        union.forAllBoxes(consumer);
    }

    @Override
    public List<AABB> toAabbs() {
        return union.toAabbs();
    }

    @Override
    public VoxelShape getFaceShape(final Direction side) {
        return union.getFaceShape(side);
    }

    @Override
    public double collide(final Direction.Axis axis, final AABB box, final double maxDist) {
        return union.collide(axis, box, maxDist);
    }

    @Override
    public @Nullable BlockHitResult clip(final Vec3 from, final Vec3 to, final BlockPos pos) {
        return union.clip(from, to, pos);
    }

    @Override
    public Optional<Vec3> closestPointTo(final Vec3 point) {
        return union.closestPointTo(point);
    }

    /** The straight part's edges (post and cardinal arms), then the twelve edges of every turned arm box. */
    @Override
    public void forAllEdges(final Shapes.DoubleLineConsumer consumer) {
        straight.forAllEdges(consumer);
        for (final double[] e : armEdges) consumer.consume(e[0], e[1], e[2], e[3], e[4], e[5]);
    }

    @Override
    public String toString() {
        return "DiagonalVoxelShape[" + union + ", " + armEdges.length + " arm edges]";
    }
}
