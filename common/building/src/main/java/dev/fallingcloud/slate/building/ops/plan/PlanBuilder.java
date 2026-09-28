package dev.fallingcloud.slate.building.ops.plan;

import dev.fallingcloud.slate.building.ops.Change;
import dev.fallingcloud.slate.building.ops.Palette;
import dev.fallingcloud.slate.building.ops.Plan;
import dev.fallingcloud.slate.building.ops.PlanContext;
import dev.fallingcloud.slate.building.ops.StateWorth;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Collects the {@link Change}s of one plan with the rules every planner shares: the replace policy, no-op
 * detection (the same state, and for our shape blocks the same material, is already there), blocks that operations
 * never touch (unbreakable ones, containers unless the player is in creative or the server allows them, game-master
 * blocks for players who may not use them), positions outside the build height or in chunks that are not loaded
 * (planners never load or generate a chunk), what survival players cannot pay for (blocks without an item: filled
 * cauldrons, potted plants, fluids ...), and the volume limit: {@link #want()} gives up with {@link PlanOverflow} as
 * soon as a planner visits more positions than the player may change, so no request can make a planner walk an
 * unbounded region.
 */
public final class PlanBuilder {

    private final PlanContext ctx;
    private final Level level;
    private final boolean blockEntities;
    private final boolean free;
    private final boolean gameMaster;
    /** A left-click selection: every position the geometry places or replaces is broken instead (see {@link #breaksAt}). */
    private final boolean destructive;
    private final List<Change> changes = new ArrayList<>();
    private int requested;
    private @Nullable AABB bounds;
    private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    public PlanBuilder(final PlanContext ctx) {
        this.ctx = ctx;
        this.level = ctx.level();
        this.blockEntities = Palette.mayUseBlockEntities(ctx.player());
        this.free = ToolboxAccess.of(ctx.player()).creative();
        this.gameMaster = ctx.player().canUseGameMasterBlocks();
        this.destructive = ctx.destructive();
    }

    /** The bounds shown for the plan (the selection box); defaults to the bounds of the changes. */
    public PlanBuilder bounds(final AABB box) {
        this.bounds = box;
        return this;
    }

    /**
     * Counts one position the mode wanted (for the "384/512" stats), whether or not it ends up changing. Throws
     * {@link PlanOverflow} once more positions were wanted than the player's volume limit.
     */
    public void want() {
        final int max = ctx.limits().maxVolume();
        if (++requested > max) throw new PlanOverflow(requested, max, bounds);
    }

    public int size() {
        return changes.size();
    }

    /** Whether the operation runs without costs (creative): copies keep their state, anything may be placed. */
    public boolean free() {
        return free;
    }

    /**
     * Whether {@code pos} may be read and changed: inside the build height, and its chunk and the chunks of its
     * neighbours are loaded (placement states and block updates read the neighbours). Never loads a chunk.
     */
    public boolean readable(final BlockPos pos) {
        return loaded(level, pos);
    }

    /** {@link #readable} for any level: the executor and symmetry use the same rule when they change a position. */
    public static boolean loaded(final Level level, final BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return false;
        final int x0 = (pos.getX() - 1) >> 4, x1 = (pos.getX() + 1) >> 4;
        final int z0 = (pos.getZ() - 1) >> 4, z1 = (pos.getZ() + 1) >> 4;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                if (!level.hasChunk(cx, cz)) return false;
            }
        }
        return true;
    }

    /** Cheap pre-check before computing a placement state: whether {@code policy} lets anything go to {@code pos}. */
    public boolean open(final BlockPos pos, final ReplacePolicy policy) {
        if (destructive) return breaksAt(pos);
        return readable(pos) && policy.allows(level.getBlockState(pos));
    }

    /**
     * A breaking plan: whether the block at {@code pos} is one to break. Only the geometry decides the positions; the
     * held block and the replace policy must not (the policy keeps just air and plants, and a block like the held one
     * counts as already placed, so solid blocks were never broken). Air and pure fluids are left alone, as Clear keeps
     * fluids.
     */
    private boolean breaksAt(final BlockPos pos) {
        if (!readable(pos)) return false;
        final BlockState existing = level.getBlockState(pos);
        return !existing.isAir() && !Plans.isFluid(existing) && breakable(pos, existing);
    }

    /** A breaking plan's change at {@code pos}: the block goes, a waterlogged block's water stays. */
    private boolean breakHere(final BlockPos pos) {
        if (!breaksAt(pos)) return false;
        add(new Change(pos.immutable(), Plans.leftBehind(level.getBlockState(pos), true), null, Change.Kind.BREAK));
        return true;
    }

    /**
     * The state a copy of {@code state} (extend with an empty hand, paste, stack) places: as it is in creative, as a
     * fresh placement otherwise (crops at age 0, empty composters ...), since one item only pays for a fresh one.
     */
    public BlockState copyOf(final BlockState state) {
        return free ? state : StateWorth.fresh(state);
    }

    /**
     * Puts {@code target} (variant {@code variant}) at {@code pos} if {@code policy} lets it overwrite what is there.
     * PLACE when the position is replaceable, REPLACE otherwise. Returns whether a change was added.
     */
    public boolean place(final BlockPos pos, final @Nullable BlockState target, final @Nullable Variant variant, final ReplacePolicy policy) {
        if (destructive) return breakHere(pos);
        final BlockState existing = placeable(pos, target, variant, policy);
        if (existing == null) return false;
        add(new Change(pos, target, variant, existing.canBeReplaced() ? Change.Kind.PLACE : Change.Kind.REPLACE));
        return true;
    }

    /** Whether {@link #place} would add a change (the same checks, nothing added). */
    public boolean wouldPlace(final BlockPos pos, final @Nullable BlockState target, final @Nullable Variant variant, final ReplacePolicy policy) {
        if (destructive) return breaksAt(pos);
        return placeable(pos, target, variant, policy) != null;
    }

    /** What is at {@code pos} when {@code target} may go there, else null. */
    private @Nullable BlockState placeable(final BlockPos pos, final @Nullable BlockState target, final @Nullable Variant variant,
                                           final ReplacePolicy policy) {
        if (target == null || target.isAir() || !readable(pos)) return null;
        if (!mayPlace(target, variant)) return null;
        final BlockState existing = level.getBlockState(pos);
        if (!policy.allows(existing)) return null;
        if (isSame(pos, existing, target, variant)) return null;
        return existing.canBeReplaced() || breakable(pos, existing) ? existing : null;
    }

    /** Overwrites the (non-air, breakable) block at {@code pos} with {@code target}; a REPLACE. */
    public boolean replace(final BlockPos pos, final @Nullable BlockState target, final @Nullable Variant variant) {
        if (destructive) return breakHere(pos);
        if (target == null || target.isAir() || !readable(pos) || !mayPlace(target, variant)) return false;
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
        if (!readable(pos)) return false;
        final BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || existing == leave || !breakable(pos, existing)) return false;
        add(new Change(pos, leave, null, Change.Kind.BREAK));
        return true;
    }

    /**
     * Whether operations may remove {@code state} at {@code pos}: not unbreakable, no foreign block entity unless
     * allowed, no game-master block (command, structure, jigsaw) unless the player may use those.
     */
    public boolean breakable(final BlockPos pos, final BlockState state) {
        if (state.getBlock() instanceof GameMasterBlock && !gameMaster) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        return !state.hasBlockEntity() || state.getBlock() instanceof ShapeBlock || blockEntities;
    }

    /**
     * Whether operations may place {@code state}: a foreign block entity only in creative or when allowed, a
     * game-master block only for players who may use those (vanilla's rule for placing them), and in survival only
     * what has an item to pay with (shapes are priced by their material, see {@link #mayPlace(BlockState, Variant)}).
     */
    public boolean mayPlace(final BlockState state) {
        if (state.getBlock() instanceof GameMasterBlock && !gameMaster) return false;
        if (state.hasBlockEntity() && !(state.getBlock() instanceof ShapeBlock) && !blockEntities) return false;
        return free || state.getBlock() instanceof ShapeBlock || state.getBlock().asItem() != Items.AIR;
    }

    /** {@link #mayPlace(BlockState)} plus: in survival the state must be priceable ({@link StateWorth#priced}). */
    public boolean mayPlace(final BlockState state, final @Nullable Variant variant) {
        return mayPlace(state) && (free || StateWorth.priced(state, variant));
    }

    /**
     * Whether a move may carry {@code state}: what may be placed, and in survival no foreign block entity (a lifted
     * container would lose its contents; creative moves carry their data).
     */
    public boolean mayMove(final BlockState state) {
        if (!mayPlace(state)) return false;
        return free || !state.hasBlockEntity() || state.getBlock() instanceof ShapeBlock;
    }

    /** Whether {@code pos} already holds {@code target} (and for our shapes, the material of {@code variant}). */
    public boolean holds(final BlockPos pos, final BlockState target, final @Nullable Variant variant) {
        return readable(pos) && isSame(pos, level.getBlockState(pos), target, variant);
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
