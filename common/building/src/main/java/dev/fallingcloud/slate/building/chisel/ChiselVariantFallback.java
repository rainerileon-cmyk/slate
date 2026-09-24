package dev.fallingcloud.slate.building.chisel;

import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * MINIMAL STAND-IN for owner A's {@link VariantRegistry}, for the chisel only (design §12: a missing API gets a clearly
 * marked minimal version in the needing owner's package). It answers only while {@code VariantRegistry} identifies
 * nothing, i.e. while it is still the skeleton stub: then the vanilla {@code BlockFamilies} shapes (stairs, slab,
 * wall, fence, fence gate) are known, so a held stone slab can still be chiselled into a stone brick slab. As soon as
 * the real registry identifies anything this class steps aside and every decision is A's.
 */
final class ChiselVariantFallback {

    private ChiselVariantFallback() {}

    private static volatile @Nullable ItemStack probe;
    private static volatile @Nullable Tables tables;

    private record Tables(Map<Block, Variant> shapes, Map<Block, Map<Shape, Block>> natives) {}

    /** Whether the real registry is still the stub (it cannot tell that stone stairs are stone). */
    static boolean active() {
        ItemStack p = probe;
        if (p == null) probe = p = new ItemStack(Items.STONE_STAIRS);
        return VariantRegistry.get().identify(p).isEmpty();
    }

    /** (base, shape) of a vanilla family shape block, e.g. stone_slab → (stone, SLAB). */
    static @Nullable Variant identify(final Block block) {
        return tables().shapes().get(block);
    }

    /** The vanilla family block for (material, shape), e.g. (stone_bricks, SLAB) → stone_brick_slab. */
    static @Nullable Block nativeBlock(final Block material, final Shape shape) {
        final Map<Shape, Block> m = tables().natives().get(material);
        return m == null ? null : m.get(shape);
    }

    private static Tables tables() {
        Tables t = tables;
        if (t == null) tables = t = build();
        return t;
    }

    private static Tables build() {
        final Map<Block, Variant> shapes = new IdentityHashMap<>();
        final Map<Block, Map<Shape, Block>> natives = new IdentityHashMap<>();
        final List<BlockFamily> families = new ArrayList<>(BlockFamilies.getAllFamilies().toList());
        families.sort(Comparator.comparingInt(f -> ChiselRules.order(f.getBaseBlock())));
        for (final BlockFamily family : families) {
            final Block base = family.getBaseBlock();
            for (final Map.Entry<BlockFamily.Variant, Block> e : family.getVariants().entrySet()) {
                final Shape shape = switch (e.getKey()) {
                    case STAIRS -> Shape.STAIRS;
                    case SLAB -> Shape.SLAB;
                    case WALL -> Shape.WALL;
                    case FENCE, CUSTOM_FENCE -> Shape.FENCE;
                    case FENCE_GATE, CUSTOM_FENCE_GATE -> Shape.FENCE_GATE;
                    default -> null;
                };
                if (shape == null) continue;
                shapes.putIfAbsent(e.getValue(), new Variant(base, shape));
                natives.computeIfAbsent(base, k -> new EnumMap<>(Shape.class)).putIfAbsent(shape, e.getValue());
            }
        }
        return new Tables(shapes, natives);
    }
}
