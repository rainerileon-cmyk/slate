package dev.fallingcloud.slate.building.ops.plan;

import dev.fallingcloud.slate.building.ops.Change;
import dev.fallingcloud.slate.building.ops.Palette;
import dev.fallingcloud.slate.building.ops.Plan;
import dev.fallingcloud.slate.building.ops.PlanContext;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Collects the {@link Change}s of one plan with the rules every planner shares: the replace policy, no-op
 * detection (the same state, and for our shape blocks the same material, is already there), blocks that operations
 * never touch (unbreakable ones, and containers unless the player is in creative or the server allows them),
 * positions outside the build height, and the volume limit.
 */
public final class PlanBuilder {

    private final PlanContext ctx;
    private final Level level;
    private final boolean blockEntities;
    private final List<Change> changes = new ArrayList<>();
    private int requested;
    private @Nullable AABB bounds;
    private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    public PlanBuilder(final PlanContext ctx) {
        this.ctx = ctx;
        this.level = ctx.level();
        this.blockEntities = Palette.mayUseBlockEntities(ctx.player());
    }

    /** The bounds shown for the plan (the selection box); defaults to the bounds of the changes. */
    public PlanBuilder bounds(final AABB box) {
        this.bounds = box;
        return this;
    }

    /** Counts one position the mode wanted (for the "384/512" stats), whether or not it ends up changing. */
    public void want() {
        requested++;
    }

    public int size() {
        return changes.size();
    }

    /** Cheap pre-check before computing a placement state: whether {@code policy} lets anything go to {@code pos}. */
    public boolean open(final BlockPos pos, final ReplacePolicy policy) {
        return !level.isOutsideBuildHeight(pos) && policy.allows(level.getBlockState(pos));
    }

    /**
     * Puts {@code target} (variant {@code variant}) at {@code pos} if {@code policy} lets it overwrite what is there.
     * PLACE when the position is replaceable, REPLACE otherwise. Returns whether a change was added.
     */
    public boolean place(final BlockPos pos, final @Nullable BlockState target, final @Nullable Variant variant, final ReplacePolicy policy) {
        if (target == null || target.isAir() || level.isOutsideBuildHeight(pos)) return false;
        if (!mayPlace(target)) return false;
        final BlockState existing = level.getBlockState(pos);
        if (!policy.allows(existing)) return false;
        if (isSame(pos, existing, target, variant)) return false;
        final boolean replaceable = existing.canBeReplaced();
        if (!replaceable && !breakable(pos, existing)) return false;
        add(new Change(pos, target, variant, replaceable ? Change.Kind.PLACE : Change.Kind.REPLACE));
        return true;
    }

    /** Overwrites the (non-air, breakable) block at {@code pos} with {@code target}; a REPLACE. */
    public boolean replace(final BlockPos pos, final @Nullable BlockState target, final @Nullable Variant variant) {
        if (target == null || target.isAir() || level.isOutsideBuildHeight(pos) || !mayPlace(target)) return false;
        final BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || isSame(pos, existing, target, variant) || !breakable(pos, existing)) return false;
        add(new Change(pos, target, variant, existing.canBeReplaced() ? Change.Kind.PLACE : Change.Kind.REPLACE));
        return true;
    }

    /**
     * Removes the block at {@code pos}, leaving {@code leave} (air, or the fluid a waterlogged block held). Returns
     * whether a change was added.
     */
    public boolean breakAt(final BlockPos pos, final BlockState leave) {
        if (level.isOutsideBuildHeight(pos)) return false;
        final BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || existing == leave || !breakable(pos, existing)) return false;
        add(new Change(pos, leave, null, Change.Kind.BREAK));
        return true;
    }

    /** Whether operations may remove {@code state} at {@code pos}: not unbreakable, and no foreign block entity unless allowed. */
    public boolean breakable(final BlockPos pos, final BlockState state) {
        if (state.getDestroySpeed(level, pos) < 0) return false;
        return !state.hasBlockEntity() || state.getBlock() instanceof ShapeBlock || blockEntities;
    }

    /** Whether operations may place {@code state} (a foreign block entity only in creative or when allowed). */
    public boolean mayPlace(final BlockState state) {
        return !state.hasBlockEntity() || state.getBlock() instanceof ShapeBlock || blockEntities;
    }

    private boolean isSame(final BlockPos pos, final BlockState existing, final BlockState target, final @Nullable Variant variant) {
        if (existing != target) return false;
        if (!(target.getBlock() instanceof ShapeBlock)) return true;
        final BlockState material = ShapeBlock.material(level, pos);
        return variant != null && material != null && material.getBlock() == variant.material();
    }

    private void add(final Change c) {
        changes.add(c);
        final BlockPos p = c.pos();
        minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
        maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
    }

    /** The plan, or a too-many error when it exceeds the player's volume limit. */
    public Plan build() {
        final AABB box = bounds != null ? bounds
            : changes.isEmpty() ? Plan.EMPTY.bounds() : new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        final int max = ctx.limits().maxVolume();
        if (changes.size() > max) return new Plan(List.of(), box, PlanErrors.tooMany(changes.size(), max), Math.max(requested, changes.size()));
        return new Plan(changes, box, null, Math.max(requested, changes.size()));
    }
}
