package dev.fallingcloud.slate.building.ops.plan;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.ops.Palette;
import dev.fallingcloud.slate.building.ops.PlanContext;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * Placement states for planners: palette entries placed as the player would place them, variants in a given shape,
 * and orientation carried over from the block being replaced (replace with keepShape, reshape).
 */
public final class Placement {

    private static boolean warned;

    /**
     * The state {@code entry} places at {@code pos} against {@code face}: a variant through
     * {@link VariantRegistry#placementState} (our shape blocks and native variants alike), anything else through its
     * block's {@code getStateForPlacement}. Null when it cannot be placed there.
     */
    public static @Nullable BlockState of(final PlanContext ctx, final Palette.WeightedEntry entry, final BlockPos pos, final Direction face) {
        final PlanPlaceContext pctx = new PlanPlaceContext(ctx.level(), ctx.player(), entry.stack(), pos, face);
        try {
            if (entry.variant() != null) {
                final BlockState s = VariantRegistry.get().placementState(entry.variant(), pctx);
                if (s != null) return s;
            }
            return entry.stack().getItem() instanceof BlockItem item ? item.getBlock().getStateForPlacement(pctx) : null;
        } catch (final RuntimeException e) {
            warnOnce(entry.stack(), e);
            return null;
        }
    }

    /**
     * The state of variant {@code v} at {@code pos} (as if the player placed it against {@code face}), with the
     * orientation of {@code orientFrom} carried over when given (facing, half, axis, waterlogged ... by property
     * name). Null when the variant does not exist or cannot be placed.
     */
    public static @Nullable BlockState ofVariant(final PlanContext ctx, final Variant v, final BlockPos pos, final Direction face,
                                                 final @Nullable BlockState orientFrom) {
        final VariantRegistry reg = VariantRegistry.get();
        if (!v.isFull() && !reg.isAvailable(v.material(), v.shape())) return null;
        final ItemStack stack = reg.stackFor(v.material(), v.shape(), 1);
        if (stack.isEmpty()) return null;
        BlockState state;
        try {
            final PlanPlaceContext pctx = new PlanPlaceContext(ctx.level(), ctx.player(), stack, pos, face);
            state = reg.placementState(v, pctx);
            if (state == null && stack.getItem() instanceof BlockItem item) state = item.getBlock().getStateForPlacement(pctx);
        } catch (final RuntimeException e) {
            warnOnce(stack, e);
            return null;
        }
        if (state == null) return null;
        return orientFrom == null ? state : carry(orientFrom, state);
    }

    /**
     * {@code to} with every property of {@code from} that {@code to} also has (matched by name and value name, so a
     * native stair's facing carries onto our stair block and a slab's "double" onto a vertical slab's).
     */
    public static BlockState carry(final BlockState from, final BlockState to) {
        BlockState out = to;
        for (final Property<?> p : from.getProperties()) {
            final Property<?> target = to.getBlock().getStateDefinition().getProperty(p.getName());
            if (target != null) out = copy(from, p, out, target);
        }
        return out;
    }

    private static <A extends Comparable<A>, B extends Comparable<B>> BlockState copy(final BlockState from, final Property<A> p,
                                                                                         final BlockState to, final Property<B> target) {
        final String name = p.getName(from.getValue(p));
        final Optional<B> value = target.getValue(name);
        return value.isPresent() ? to.setValue(target, value.get()) : to;
    }

    /** What the block at {@code pos} (with state {@code state}) identifies as, reading our shape material; null if nothing. */
    public static @Nullable Variant variantAt(final Level level, final BlockPos pos, final BlockState state) {
        return VariantRegistry.get().identify(state, state.hasBlockEntity() ? level.getBlockEntity(pos) : null).orElse(null);
    }

    /**
     * The variant a copied state stands for: our shape block with its stored {@code material}, or whatever the state
     * identifies as (a native stair → planks STAIRS). Null for blocks outside the variant system.
     */
    public static @Nullable Variant variantOf(final BlockState state, final @Nullable BlockState material) {
        if (state.getBlock() instanceof ShapeBlock shape) {
            return material == null ? null : new Variant(material.getBlock(), shape.shape());
        }
        return VariantRegistry.get().identify(state, null).orElse(null);
    }

    /** Whether two blocks count as "the same" for CLICKED filters: same material when both are variants, else same block. */
    public static boolean sameKind(final @Nullable Variant a, final BlockState sa, final @Nullable Variant b, final BlockState sb) {
        if (a != null && b != null) return a.material() == b.material();
        return sa.getBlock() == sb.getBlock();
    }

    private static void warnOnce(final ItemStack stack, final RuntimeException e) {
        if (warned) return;
        warned = true;
        SlateBuilding.LOGGER.warn("[Slate Building] placement state of {} failed while planning; such blocks are skipped", stack, e);
    }

    private Placement() {}
}
