package dev.fallingcloud.slate.building.ops.plan;

import dev.fallingcloud.slate.building.ops.Clipboard;
import dev.fallingcloud.slate.building.ops.Plan;
import dev.fallingcloud.slate.building.ops.PlanContext;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Blueprint modes: copy, cut, paste, stack and move.
 *
 * <p>Paste and move place a box against a clicked face ({@link #origin}): on top of the clicked block for UP, hanging
 * below it for DOWN, in front of it for the sides, centred on the clicked block across the face and resting at its
 * height for side faces. Rotation (clockwise from above) and mirroring apply first, inside the box.
 *
 * <p>Copies (paste, stack) in survival place what one item pays for: grown crops come back at age 0, blocks without
 * an item (filled cauldrons, potted plants, fluids) are left out ({@link PlanBuilder#copyOf}, {@link PlanBuilder#mayPlace}).
 */
public final class ClipboardPlanners {

    /** Copy: no world change; the plan's bounds are the box and its count the blocks that will be copied. */
    public static Plan copy(final PlanContext ctx) {
        final Box box = Plans.box(ctx);
        final Component err = Plans.readable(ctx.level(), box, ctx.limits());
        if (err != null) return Plans.error(err, box.aabb());
        final boolean includeAir = ctx.params().getBool("includeAir");
        int count = 0;
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    if (includeAir || !ctx.level().getBlockState(p.set(x, y, z)).isAir()) count++;
                }
            }
        }
        return new Plan(List.of(), box.aabb(), null, count);
    }

    /** Cut: the removal half of copy + clear (the server copies first); fluids stay. */
    public static Plan cut(final PlanContext ctx) {
        final Box box = Plans.box(ctx);
        final Component err = Plans.readable(ctx.level(), box, ctx.limits());
        if (err != null) return Plans.error(err, box.aabb());
        final Level level = ctx.level();
        final PlanBuilder pb = new PlanBuilder(ctx).bounds(box.aabb());
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = box.maxY(); y >= box.minY(); y--) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    final BlockState s = level.getBlockState(p.set(x, y, z));
                    if (s.isAir() || Plans.isFluid(s)) continue;
                    pb.want();
                    pb.breakAt(p.immutable(), Plans.leftBehind(s, true));
                }
            }
        }
        return pb.build();
    }

    /**
     * Paste the clipboard against the clicked face ({@code anchors[0]} + {@code face}), rotated and mirrored by the
     * parameters. {@code includeAir}: stored air clears what is there. {@code replace}: what may be overwritten.
     */
    public static Plan paste(final PlanContext ctx) {
        final Clipboard clip = ctx.clipboard();
        if (clip == null || clip.isEmpty()) return Plans.error(PlanErrors.noClipboard(), null);
        final Rotation rotation = Clipboard.rotation(ctx.params().getChoice("rotation"));
        final Mirror mirror = Clipboard.mirror(ctx.params().getChoice("mirror"));
        final Clipboard t = clip.transformed(rotation, mirror);
        final BlockPos origin = origin(ctx.anchor(0), ctx.face(), t.size());
        final Box box = Box.sized(origin, t.size().getX(), t.size().getY(), t.size().getZ());
        Component err = Plans.span(box, ctx.limits());
        if (err == null) err = Plans.tooMany(t.entries().size(), ctx.limits());
        if (err != null) return Plans.error(err, box.aabb());
        final boolean includeAir = ctx.params().getBool("includeAir");
        final ReplacePolicy policy = ReplacePolicy.of(ctx.params(), ReplacePolicy.REPLACEABLE);
        final PlanBuilder pb = new PlanBuilder(ctx).bounds(box.aabb());
        for (final Clipboard.Entry e : t.entries()) {
            final BlockPos pos = origin.offset(e.offset());
            if (e.state().isAir()) {
                if (includeAir && policy != ReplacePolicy.AIR) {
                    pb.want();
                    if (pb.readable(pos) && policy.allows(ctx.level().getBlockState(pos))) pb.breakAt(pos, Blocks.AIR.defaultBlockState());
                }
                continue;
            }
            pb.want();
            pb.place(pos, pb.copyOf(e.state()), Placement.variantOf(e.state(), e.material()), policy);
        }
        return pb.build();
    }

    /**
     * Stack: repeats the box {@code count} times along {@code direction} (LOOK = the way the player faces, including
     * up/down), {@code spacing} blocks apart. Copies only go into free (replaceable) space; air is not copied. Copies
     * that reach into chunks that are not loaded are left out there.
     */
    public static Plan stack(final PlanContext ctx) {
        final Box box = Plans.box(ctx);
        final int count = Math.max(1, ctx.params().getInt("count"));
        Component err = Plans.span(box, ctx.limits());
        if (err == null) err = Plans.tooMany(box.volume() * count, ctx.limits());
        if (err == null) err = Plans.unloaded(ctx.level(), box);
        if (err != null) return Plans.error(err, box.aabb());
        final int spacing = Math.max(0, ctx.params().getInt("spacing"));
        final Direction dir = direction(ctx.params().getChoice("direction"), ctx);
        final int step = box.size(dir.getAxis()) + spacing;
        final Level level = ctx.level();
        final Vec3i far = dir.getNormal().multiply(step * count);
        final Box all = box.union(new Box(box.minX() + far.getX(), box.minY() + far.getY(), box.minZ() + far.getZ(),
            box.maxX() + far.getX(), box.maxY() + far.getY(), box.maxZ() + far.getZ()));
        final PlanBuilder pb = new PlanBuilder(ctx).bounds(all.aabb());

        // The source, read once.
        final List<BlockPos> from = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        final List<Variant> variants = new ArrayList<>();
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    final BlockState s = level.getBlockState(p.set(x, y, z));
                    if (s.isAir() || Plans.isFluid(s)) continue;
                    final BlockState material = s.getBlock() instanceof ShapeBlock ? ShapeBlock.material(level, p) : null;
                    from.add(p.immutable());
                    states.add(pb.copyOf(s));
                    variants.add(Placement.variantOf(s, material));
                }
            }
        }
        for (int k = 1; k <= count; k++) {
            final Vec3i off = dir.getNormal().multiply(step * k);
            for (int i = 0; i < from.size(); i++) {
                pb.want();
                pb.place(from.get(i).offset(off), states.get(i), variants.get(i), ReplacePolicy.REPLACEABLE);
            }
        }
        return pb.build();
    }

    /**
     * Move: the box {@code anchors[0..1]} is lifted and put down against the destination ({@code anchors[2]} +
     * {@code face}, like a paste), rotated / mirrored by the parameters. Source positions the moved blocks do not land
     * on become air; the moved blocks overwrite whatever is at the destination. A block only travels when it can land:
     * fluids, blocks whose landing spot is unbreakable, a container, outside the world or not loaded, and (in survival)
     * containers and blocks without an item stay where they are. With only two anchors the plan is just the selection
     * box.
     */
    public static Plan move(final PlanContext ctx) {
        return move(ctx, null);
    }

    /**
     * The source positions a move of {@code ctx} carries (lifted, or overwritten by another moved block): the server
     * credits exactly these as the moved blocks themselves, so each pays for its own landing and nothing else does.
     * Same world, same answer as the plan.
     */
    public static LongOpenHashSet movingSources(final PlanContext ctx) {
        final LongOpenHashSet out = new LongOpenHashSet();
        try {
            move(ctx, out);
        } catch (final PlanOverflow e) {
            // Cannot happen after the same plan succeeded; what was collected is still the right set so far.
        }
        return out;
    }

    private static Plan move(final PlanContext ctx, final @Nullable LongOpenHashSet movingOut) {
        final Box src = Box.of(ctx.anchor(0), ctx.anchor(1));
        Component err = Plans.span(src, ctx.limits());
        if (err == null) err = Plans.tooMany(src.volume() * 2, ctx.limits());
        if (err == null) err = Plans.unloaded(ctx.level(), src);
        if (err != null) return Plans.error(err, src.aabb());
        if (ctx.anchors().size() < 3) return new Plan(List.of(), src.aabb(), null, 0);
        final Level level = ctx.level();
        final Clipboard clip = Clipboard.read(level, src.min(), src.max(), false, false);
        final Rotation rotation = Clipboard.rotation(ctx.params().getChoice("rotation"));
        final Mirror mirror = Clipboard.mirror(ctx.params().getChoice("mirror"));
        final Clipboard t = clip.transformed(rotation, mirror);
        final BlockPos origin = origin(ctx.anchors().get(2), ctx.face(), t.size());
        final Box dst = Box.sized(origin, t.size().getX(), t.size().getY(), t.size().getZ());
        final PlanBuilder pb = new PlanBuilder(ctx).bounds(src.union(dst).aabb());

        // Which source blocks may travel at all ...
        final LongOpenHashSet candidates = new LongOpenHashSet();
        for (final Clipboard.Entry e : clip.entries()) {
            final BlockPos from = src.min().offset(e.offset());
            if (travels(pb, from, e.state())) candidates.add(from.asLong());
        }
        // ... and which of them can land: the spot takes the block, or already holds it and is itself moving away.
        final LongOpenHashSet moving = movingOut != null ? movingOut : new LongOpenHashSet();
        final LongOpenHashSet landing = new LongOpenHashSet();
        for (int i = 0; i < clip.entries().size(); i++) {
            final BlockPos from = src.min().offset(clip.entries().get(i).offset());
            if (!candidates.contains(from.asLong())) continue;
            final Clipboard.Entry e = t.entries().get(i);
            final BlockPos to = origin.offset(e.offset());
            final Variant variant = Placement.variantOf(e.state(), e.material());
            if (!pb.wouldPlace(to, e.state(), variant, ReplacePolicy.ALL)
                && !(candidates.contains(to.asLong()) && pb.holds(to, e.state(), variant))) continue;
            moving.add(from.asLong());
            landing.add(to.asLong());
        }
        // Lift: top layer first.
        for (int i = clip.entries().size() - 1; i >= 0; i--) {
            final BlockPos from = src.min().offset(clip.entries().get(i).offset());
            if (!moving.contains(from.asLong()) || landing.contains(from.asLong())) continue;
            pb.want();
            pb.breakAt(from, Blocks.AIR.defaultBlockState());
        }
        // Put down: bottom layer first.
        for (int i = 0; i < t.entries().size(); i++) {
            final Clipboard.Entry from = clip.entries().get(i);
            if (!moving.contains(src.min().offset(from.offset()).asLong())) continue;
            final Clipboard.Entry e = t.entries().get(i);
            pb.want();
            pb.place(origin.offset(e.offset()), e.state(), Placement.variantOf(e.state(), e.material()), ReplacePolicy.ALL);
        }
        return pb.build();
    }

    private static boolean travels(final PlanBuilder pb, final BlockPos pos, final BlockState state) {
        return !Plans.isFluid(state) && pb.breakable(pos, state) && pb.mayMove(state);
    }

    /** The minimum corner of a {@code size} box placed against {@code face} of the block {@code clicked}. */
    public static BlockPos origin(final BlockPos clicked, final Direction face, final Vec3i size) {
        final BlockPos t = clicked.relative(face);
        int x = t.getX() - size.getX() / 2;
        int y = t.getY();
        int z = t.getZ() - size.getZ() / 2;
        switch (face) {
            case DOWN -> y = t.getY() - size.getY() + 1;
            case NORTH -> z = t.getZ() - size.getZ() + 1;
            case SOUTH -> z = t.getZ();
            case WEST -> x = t.getX() - size.getX() + 1;
            case EAST -> x = t.getX();
            default -> { }
        }
        return new BlockPos(x, y, z);
    }

    /** The stack direction a {@code direction} parameter names (LOOK: the nearest of the six to the player's view). */
    public static Direction direction(final String choice, final PlanContext ctx) {
        return switch (choice) {
            case "UP" -> Direction.UP;
            case "DOWN" -> Direction.DOWN;
            case "N" -> Direction.NORTH;
            case "S" -> Direction.SOUTH;
            case "E" -> Direction.EAST;
            case "W" -> Direction.WEST;
            default -> ctx.player().getNearestViewDirection();
        };
    }

    /** Variant of a stored clipboard entry (for previews that list materials); null outside the variant system. */
    public static @Nullable Variant variantOf(final Clipboard.Entry e) {
        return Placement.variantOf(e.state(), e.material());
    }

    private ClipboardPlanners() {}
}
