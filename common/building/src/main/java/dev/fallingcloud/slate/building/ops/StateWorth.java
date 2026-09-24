package dev.fallingcloud.slate.building.ops;

import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.List;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * What a block state is worth to the operation economy (design §1), shared by the planners (client preview and
 * server), the executor, symmetry and the cost HUD so they all price a state the same way.
 *
 * <ul>
 *   <li>{@link #units}: how many items the state stands for. Our shapes and native double slabs through
 *       {@link VariantRegistry#units}; vanilla blocks that stack several items into one position (candles, sea
 *       pickles, pink petals, turtle eggs, snow layers, multi-face lichen / sculk veins) by their count.</li>
 *   <li>{@link #priced}: whether a survival player can pay for placing it at all. Blocks without an item of their own
 *       (filled cauldrons, potted plants, candle cakes, fluids, fire, {@code *_plant} stems, portals ...) cannot; an
 *       operation never places those for free.</li>
 *   <li>{@link #fresh}: the state a paid copy gets. Growth and fill levels (crop age, berries, composter / honey /
 *       respawn-anchor levels, cake bites) go back to the block's default, since the item only pays for a fresh one.</li>
 *   <li>{@link #sameWorth}: whether two states of a position are worth the same (undo / redo check this before they
 *       refund or charge what they recorded).</li>
 * </ul>
 */
public final class StateWorth {

    /** Properties that count items stacked into one position. */
    private static final List<IntegerProperty> COUNTS = List.of(BlockStateProperties.CANDLES, BlockStateProperties.PICKLES,
        BlockStateProperties.FLOWER_AMOUNT, BlockStateProperties.EGGS, BlockStateProperties.LAYERS);

    /** Properties a block grows or fills into after placement, and cake bites: reset on paid copies, compared by undo. */
    private static final List<Property<?>> GROWN = List.of(BlockStateProperties.BERRIES, BlockStateProperties.LEVEL_COMPOSTER,
        BlockStateProperties.LEVEL_HONEY, BlockStateProperties.RESPAWN_ANCHOR_CHARGES, BlockStateProperties.LEVEL_CAULDRON,
        BlockStateProperties.BITES);

    /** Items {@code state} stands for (at least 1). */
    public static int units(final BlockState state) {
        final Block block = state.getBlock();
        if (block instanceof ShapeBlock) return Math.max(1, VariantRegistry.get().units(state, null));
        int n = VariantRegistry.get().units(state, null);
        for (final IntegerProperty p : COUNTS) {
            if (state.hasProperty(p)) n = state.getValue(p);
        }
        if (block instanceof MultifaceBlock) n = Math.max(n, MultifaceBlock.availableFaces(state).size());
        return Math.max(1, n);
    }

    /**
     * Whether survival players can pay for placing {@code state} ({@code variant}: what it identifies as, null outside
     * the variant system): variants are paid with their material, other blocks with their own item. A shape block
     * without a material and blocks without an item cannot be paid for.
     */
    public static boolean priced(final BlockState state, final @Nullable Variant variant) {
        if (variant != null) return true;
        if (state.getBlock() instanceof ShapeBlock) return false;
        return state.getBlock().asItem() != Items.AIR;
    }

    /** {@code state} with its growth / fill properties back at the block's default (a fresh placement of it). */
    public static BlockState fresh(final BlockState state) {
        BlockState out = state;
        final BlockState def = state.getBlock().defaultBlockState();
        for (final Property<?> p : state.getProperties()) {
            if (isGrowth(p)) out = copy(def, p, out);
        }
        return out;
    }

    /**
     * Whether {@code a} and {@code b} are worth the same: the same block, the same unit count and the same growth /
     * fill / bite values. Shape-only differences (stair corners, fence connections, facing, open, lit, powered ...) do
     * not matter.
     */
    public static boolean sameWorth(final BlockState a, final BlockState b) {
        if (a == b) return true;
        if (a.getBlock() != b.getBlock() || units(a) != units(b)) return false;
        for (final Property<?> p : a.getProperties()) {
            if (isGrowth(p) && !a.getValue(p).equals(b.getValue(p))) return false;
        }
        return true;
    }

    private static boolean isGrowth(final Property<?> p) {
        return GROWN.contains(p) || (p instanceof IntegerProperty && "age".equals(p.getName()));
    }

    private static <T extends Comparable<T>> BlockState copy(final BlockState from, final Property<T> p, final BlockState to) {
        return from.hasProperty(p) ? to.setValue(p, from.getValue(p)) : to;
    }

    private StateWorth() {}
}
