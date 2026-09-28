package dev.fallingcloud.slate.building.ops.plan;

import dev.fallingcloud.slate.building.ops.Palette;
import dev.fallingcloud.slate.building.ops.Plan;
import dev.fallingcloud.slate.building.ops.PlanContext;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Modes that edit what is already there: replace, overlay, clear and reshape. */
public final class EditPlanners {

    /**
     * Replace: every block in the box matching the filter becomes the held block. {@code filter}: CLICKED (the block
     * at corner A: same material when it is a variant and keepShape is on, same variant with keepShape off, else the
     * same block), ANY_SOLID, or OFFHAND (the block in the off hand). With {@code keepShape} and both blocks being
     * variants, the new block keeps the old one's shape and orientation (oak stairs → spruce stairs facing the same
     * way); when the held material has no such shape the position is left alone.
     */
    public static Plan replace(final PlanContext ctx) {
        final Box box = Plans.box(ctx);
        final Component err = Plans.readable(ctx.level(), box, ctx.limits());
        if (err != null) return Plans.error(err, box.aabb());
        final Palette.WeightedEntry held = ctx.palette().first();
        if (held == null) return Plans.error(PlanErrors.noBlock(), box.aabb());
        final Level level = ctx.level();
        final boolean keepShape = ctx.params().getBool("keepShape");
        final String filter = ctx.params().getChoice("filter");

        final BlockState filterState;
        final Variant filterVariant;
        switch (filter) {
            case "ANY_SOLID" -> { filterState = null; filterVariant = null; }
            case "OFFHAND" -> {
                final ItemStack off = ctx.player().getOffhandItem();
                if (!(off.getItem() instanceof BlockItem item)) return Plans.error(PlanErrors.noOffhand(), box.aabb());
                filterState = item.getBlock().defaultBlockState();
                filterVariant = VariantRegistry.get().identify(off).orElse(null);
            }
            default -> {
                final BlockPos clicked = ctx.anchor(0);
                filterState = level.getBlockState(clicked);
                if (filterState.isAir()) return Plans.error(PlanErrors.pickBlock(), box.aabb());
                filterVariant = Placement.variantAt(level, clicked, filterState);
            }
        }

        final Variant heldVariant = held.variant();
        final PlanBuilder pb = new PlanBuilder(ctx).bounds(box.aabb());
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    p.set(x, y, z);
                    final BlockState existing = level.getBlockState(p);
                    if (existing.isAir()) continue;
                    final Variant existingVariant = filterState == null && !keepShape ? null : Placement.variantAt(level, p, existing);
                    if (!matches(existing, existingVariant, filterState, filterVariant, keepShape)) continue;
                    pb.want();
                    final BlockPos pos = p.immutable();
                    if (keepShape && heldVariant != null && existingVariant != null) {
                        final Variant target = new Variant(heldVariant.material(), existingVariant.shape());
                        pb.replace(pos, Placement.ofVariant(ctx, target, pos, ctx.face(), existing), target);
                    } else {
                        pb.replace(pos, Placement.of(ctx, held, pos, ctx.face()), heldVariant);
                    }
                }
            }
        }
        return pb.build();
    }

    private static boolean matches(final BlockState existing, final @Nullable Variant existingVariant, final @Nullable BlockState filterState,
                                   final @Nullable Variant filterVariant, final boolean keepShape) {
        if (filterState == null) return !existing.canBeReplaced() && !Plans.isFluid(existing);
        if (existingVariant != null && filterVariant != null) {
            return keepShape ? existingVariant.material() == filterVariant.material() : existingVariant.equals(filterVariant);
        }
        return existing.getBlock() == filterState.getBlock();
    }

    /**
     * Overlay: finds the top surface of every column of the box (the highest solid block inside it) and either puts
     * {@code depth} layers of the held block on top (ON_TOP, may rise above the box) or turns the top {@code depth}
     * blocks into it (REPLACE_TOP).
     */
    public static Plan overlay(final PlanContext ctx) {
        final Box box = Plans.box(ctx);
        final int depth = Math.max(1, ctx.params().getInt("depth"));
        Component err = Plans.span(box, ctx.limits());
        if (err == null) err = Plans.tooMany((long) box.sizeX() * box.sizeZ() * depth, ctx.limits());
        if (err == null) err = Plans.unloaded(ctx.level(), box);
        if (err != null) return Plans.error(err, box.aabb());
        final Palette.WeightedEntry held = ctx.palette().first();
        if (held == null) return Plans.error(PlanErrors.noBlock(), box.aabb());
        final Level level = ctx.level();
        // A breaking overlay strips the top layers: ON_TOP's positions are the air above them.
        final boolean onTop = !ctx.destructive() && !"REPLACE_TOP".equals(ctx.params().getChoice("mode"));
        final PlanBuilder pb = new PlanBuilder(ctx).bounds(onTop ? box.aabb().expandTowards(0, depth, 0) : box.aabb());
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int z = box.minZ(); z <= box.maxZ(); z++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                int top = Integer.MIN_VALUE;
                for (int y = box.maxY(); y >= box.minY(); y--) {
                    final BlockState s = level.getBlockState(p.set(x, y, z));
                    if (!s.isAir() && !s.canBeReplaced()) { top = y; break; }
                }
                if (top == Integer.MIN_VALUE) continue;
                for (int d = 0; d < depth; d++) {
                    final BlockPos pos = new BlockPos(x, onTop ? top + 1 + d : top - d, z);
                    pb.want();
                    if (onTop) {
                        if (!pb.open(pos, ReplacePolicy.REPLACEABLE)) break;
                        pb.place(pos, Placement.of(ctx, held, pos, Direction.UP), held.variant(), ReplacePolicy.REPLACEABLE);
                    } else {
                        final BlockState s = level.getBlockState(pos);
                        if (s.isAir() || s.canBeReplaced()) break;
                        pb.replace(pos, Placement.of(ctx, held, pos, Direction.UP), held.variant());
                    }
                }
            }
        }
        return pb.build();
    }

    /**
     * Clear: breaks everything in the box (top layer first, so sand does not fall into the gap), drops go to the
     * player. {@code filter}: ALL, CLICKED (same block / same material as corner A) or PLANTS (grass, flowers,
     * leaves, crops, vines, saplings ...). {@code keepFluids} leaves water and lava (and the water of waterlogged
     * blocks) in place.
     */
    public static Plan clear(final PlanContext ctx) {
        final Box box = Plans.box(ctx);
        final Component err = Plans.readable(ctx.level(), box, ctx.limits());
        if (err != null) return Plans.error(err, box.aabb());
        final Level level = ctx.level();
        final String filter = ctx.params().getChoice("filter");
        final boolean keepFluids = ctx.mode().param("keepFluids") == null || ctx.params().getBool("keepFluids");
        BlockState clicked = null;
        Variant clickedVariant = null;
        if ("CLICKED".equals(filter)) {
            clicked = level.getBlockState(ctx.anchor(0));
            if (clicked.isAir()) return Plans.error(PlanErrors.pickBlock(), box.aabb());
            clickedVariant = Placement.variantAt(level, ctx.anchor(0), clicked);
        }
        final PlanBuilder pb = new PlanBuilder(ctx).bounds(box.aabb());
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = box.maxY(); y >= box.minY(); y--) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    p.set(x, y, z);
                    final BlockState s = level.getBlockState(p);
                    if (s.isAir()) continue;
                    if (keepFluids && Plans.isFluid(s)) continue;
                    if (clicked != null && !Placement.sameKind(clickedVariant, clicked, Placement.variantAt(level, p, s), s)) continue;
                    if ("PLANTS".equals(filter) && !isPlant(s)) continue;
                    pb.want();
                    pb.breakAt(p.immutable(), Plans.leftBehind(s, keepFluids));
                }
            }
        }
        return pb.build();
    }

    /** Vegetation for the PLANTS filter. */
    public static boolean isPlant(final BlockState s) {
        final Block b = s.getBlock();
        if (b instanceof SnowLayerBlock || Plans.isFluid(s)) return false;
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.CROPS)
            || s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.CAVE_VINES) || b instanceof BushBlock || b instanceof VineBlock
            || b instanceof CactusBlock || b instanceof SugarCaneBlock || b instanceof BambooStalkBlock || (s.canBeReplaced() && !s.isAir());
    }

    /**
     * Reshape: every variant / material block in the box ({@code filter} ALL, or CLICKED_MATERIAL = only the
     * material at corner A) takes the held item's shape, keeping its material and, where the shapes share them, its
     * orientation. Units are settled by the server (a double slab turned into stairs refunds one). In survival the
     * executor leaves blocks alone whose loot a change in place would skip (natural stone, glass, ores: {@code LootGuard}),
     * and the preview marks them invalid; they stay in the plan so they show.
     */
    public static Plan reshape(final PlanContext ctx) {
        final Box box = Plans.box(ctx);
        final Component err = Plans.readable(ctx.level(), box, ctx.limits());
        if (err != null) return Plans.error(err, box.aabb());
        final Palette.WeightedEntry held = ctx.palette().first();
        if (held == null || held.variant() == null) return Plans.error(PlanErrors.noShape(), box.aabb());
        final Level level = ctx.level();
        Block onlyMaterial = null;
        if ("CLICKED_MATERIAL".equals(ctx.params().getChoice("filter"))) {
            final BlockState clicked = level.getBlockState(ctx.anchor(0));
            final Variant v = Placement.variantAt(level, ctx.anchor(0), clicked);
            if (v == null) return Plans.error(PlanErrors.pickBlock(), box.aabb());
            onlyMaterial = v.material();
        }
        final VariantRegistry reg = VariantRegistry.get();
        final PlanBuilder pb = new PlanBuilder(ctx).bounds(box.aabb());
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    p.set(x, y, z);
                    final BlockState s = level.getBlockState(p);
                    if (s.isAir()) continue;
                    final Variant v = Placement.variantAt(level, p, s);
                    if (v == null || (onlyMaterial != null && v.material() != onlyMaterial)) continue;
                    pb.want();
                    if (v.shape() == held.variant().shape()) continue;
                    if (!held.variant().isFull() && !reg.isAvailable(v.material(), held.variant().shape())) continue;
                    final Variant target = v.withShape(held.variant().shape());
                    final BlockPos pos = p.immutable();
                    pb.replace(pos, Placement.ofVariant(ctx, target, pos, ctx.face(), s), target);
                }
            }
        }
        return pb.build();
    }

    private EditPlanners() {}
}
