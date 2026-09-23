package dev.fallingcloud.slate.building.variant;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Knows, for every block and item, which material and shape it is, and which shapes each material has. Built
 * deterministically from the registries on BOTH sides (no sync needed): vanilla {@code BlockFamilies}, every
 * {@code StairBlock}'s base state, name-based sibling discovery, server-config overrides; rebuilt when tags update.
 * See design §2 for the rules (materials, ties, re-pointed DiagonalBlocks twins).
 *
 * <p>Owner: A (variants). Skeleton stub with the final API: nothing identifies as a variant yet, FULL stacks are
 * the material's own item and units come from {@link ShapeBlock#units}. A replaces the bodies, not the signatures.
 */
public final class VariantRegistry {

    private static final VariantRegistry INSTANCE = new VariantRegistry();

    private VariantRegistry() {}

    /** The registry for the current registries/tags (built lazily after registries freeze; rebuilt on tag reload). */
    public static VariantRegistry get() {
        return INSTANCE;
    }

    /** planks → (planks, FULL); oak_stairs → (planks, STAIRS); a shape item → its material component. */
    public Optional<Variant> identify(final ItemStack stack) {
        return Optional.empty();
    }

    public Optional<Variant> identify(final BlockState state, final @Nullable BlockEntity be) {
        return Optional.empty();
    }

    public Optional<Variant> identify(final BlockGetter level, final BlockPos pos) {
        return identify(level.getBlockState(pos), level.getBlockEntity(pos));
    }

    /** Whether {@code block} is an eligible full block (design §2: full cube, model, has an item, not tagged out ...). */
    public boolean isMaterial(final Block block) {
        return false;
    }

    /** Shapes available for {@code material} (native or custom), in wheel order. */
    public List<Shape> shapesFor(final Block material) {
        return List.of();
    }

    public boolean isAvailable(final Block material, final Shape shape) {
        return shape == Shape.FULL && isMaterial(material);
    }

    /** The native block realising ({@code material}, {@code shape}) if one exists, e.g. oak_stairs for (oak_planks, STAIRS). */
    public @Nullable Block nativeBlock(final Block material, final Shape shape) {
        return shape == Shape.FULL ? material : null;
    }

    /** The native item when it exists, else Slate Building's shape item carrying the material component. */
    public ItemStack stackFor(final Block material, final Shape shape, final int count) {
        return shape == Shape.FULL ? new ItemStack(material, count) : ItemStack.EMPTY;
    }

    /** Material units the block at hand is worth: 1, 2 for doubles, n for layers. */
    public int units(final BlockState state, final @Nullable BlockEntity be) {
        return state.getBlock() instanceof ShapeBlock shape ? shape.units(state) : 1;
    }

    /** Every native variant item (what "delete native variants" hides and rebalances). */
    public Set<Item> nativeVariantItems() {
        return Set.of();
    }

    /**
     * The state to place for {@code v} from {@code ctx}; delegates to the native ITEM's {@code BlockItem} when one
     * exists so DiagonalBlocks' re-pointed items keep working. Null when it cannot be placed there.
     */
    public @Nullable BlockState placementState(final Variant v, final BlockPlaceContext ctx) {
        if (v.isFull() && v.material().asItem() instanceof BlockItem) return v.material().getStateForPlacement(ctx);
        return null;
    }

    /** Drops cached data; the next {@link #get()} rebuilds (tags/datapack reload, server config change). */
    public static void invalidate() {
    }
}
