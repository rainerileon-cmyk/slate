package dev.fallingcloud.slate.building.chisel;

import dev.fallingcloud.slate.building.registry.BuildingTags;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * The safety rules every chisel group obeys, whatever its source (design §1 and §9, pitfalls in
 * {@code docs/building-maps/chisel-compat.md}). A chisel swap is free and one-for-one, so a group may only hold blocks
 * that really are worth the same:
 * <ul>
 *   <li><b>Plain full blocks only</b> ({@link #eligible}): no block entities (their contents would be lost or
 *       duplicated), no doors/beds/tall plants (two blocks, one item), no gravity blocks (they fall out of a chiselled
 *       wall), no shapes (slabs, stairs, walls, fences, panes: shapes are the variant wheel's business), nothing that
 *       is invisible or in {@code #slate_building:not_material}.</li>
 *   <li><b>Copper never crosses oxidation or wax states</b> ({@link #copperKey}): otherwise the wheel would be free
 *       wax, un-wax and de-oxidation. Each group is split per state; blocks that do not weather at all only group with
 *       each other.</li>
 *   <li><b>No member-to-member recipe may be anything but one-for-one</b> ({@link BadPairs}): the stonecutter turns
 *       one copper block into four cut copper, so if both shared a group, four cut copper would chisel back into four
 *       copper blocks. Any group holding such a pair loses members until no pair is left.</li>
 * </ul>
 */
final class ChiselRules {

    private ChiselRules() {}

    // ------------------------------------------------------------------ members

    /** Whether {@code block} may be a chisel group member at all. */
    static boolean eligible(final Block block) {
        final BlockState state = block.defaultBlockState();
        if (state.isAir()) return false;
        if (!(block.asItem() instanceof BlockItem item) || item.getBlock() != block) return false;
        if (block instanceof ShapeBlock || block instanceof EntityBlock || block instanceof Fallable) return false;
        if (block instanceof DoorBlock || block instanceof BedBlock) return false;
        if (block instanceof SlabBlock || block instanceof StairBlock || block instanceof WallBlock
            || block instanceof FenceBlock || block instanceof FenceGateBlock || block instanceof IronBarsBlock) return false;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART)) return false;
        if (state.hasBlockEntity() || state.getRenderShape() != RenderShape.MODEL) return false;
        if (state.is(BuildingTags.NOT_MATERIAL)) return false;
        try {
            if (!Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))) return false;
            // A block the variant registry reads as a SHAPE of another material (e.g. a modded vertical slab) is not
            // a texture variant, whatever its outline says.
            if (VariantRegistry.get().identify(state, null).map(v -> !v.isFull()).orElse(false)) return false;
        } catch (final RuntimeException e) {
            return false;   // a modded block whose shape needs a real level: not a plain block
        }
        return true;
    }

    // ------------------------------------------------------------------ copper

    /**
     * The oxidation stage (0 = unaffected .. 3 = oxidized) and wax state of a copper-like block, from the vanilla
     * weathering / waxing maps (NeoForge patches {@code WeatheringCopper.getPrevious} and
     * {@code HoneycombItem.getWaxed} to read its data maps, Fabric's registry feeds the vanilla maps, so modded copper
     * is covered on both loaders).
     */
    record CopperKey(int age, boolean waxed) {}

    /** The copper state of {@code block}, or null when it neither weathers nor is waxed. */
    static @Nullable CopperKey copperKey(final Block block) {
        final Block unwaxed = unwaxed(block);
        final boolean waxed = unwaxed != null;
        final Block base = waxed ? unwaxed : block;
        final boolean weathers = base instanceof WeatheringCopper
            || WeatheringCopper.getNext(base).isPresent()
            || WeatheringCopper.getPrevious(base).isPresent()
            || HoneycombItem.getWaxed(base.defaultBlockState()).isPresent();
        if (!waxed && !weathers) return null;
        int age = 0;
        Block cur = base;
        for (Block prev = WeatheringCopper.getPrevious(cur).orElse(null); prev != null && age < 8;
             prev = WeatheringCopper.getPrevious(cur).orElse(null)) {
            age++;
            cur = prev;
        }
        return new CopperKey(age, waxed);
    }

    /** Loader override for "the unwaxed form of a block" (NeoForge: its waxables data map); null = vanilla map. */
    static volatile @Nullable Function<Block, Block> unwaxLookup;

    private static @Nullable Block unwaxed(final Block block) {
        try {
            final Function<Block, Block> hook = unwaxLookup;
            final Block viaHook = hook != null ? hook.apply(block) : null;
            return viaHook != null ? viaHook : HoneycombItem.WAX_OFF_BY_BLOCK.get().get(block);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 1:N links

    /**
     * Pairs of blocks joined by a recipe that is not one-for-one ({@code input → output × n}, n ≠ 1 per input slot),
     * directed from input to output. Read from the stonecutter and crafting recipes (the index builder fills it).
     */
    static final class BadPairs {

        private final Map<Block, Set<Block>> out = new IdentityHashMap<>();

        void add(final Block input, final Block output) {
            if (input == output) return;
            out.computeIfAbsent(input, k -> java.util.Collections.newSetFromMap(new IdentityHashMap<>())).add(output);
        }

        /** Whether a recipe turns {@code a} into {@code b} or {@code b} into {@code a} at a ratio other than 1. */
        boolean between(final Block a, final Block b) {
            return outputs(a, b) || outputs(b, a);
        }

        boolean outputs(final Block input, final Block output) {
            final Set<Block> s = out.get(input);
            return s != null && s.contains(output);
        }

        int size() {
            int n = 0;
            for (final Set<Block> s : out.values()) n += s.size();
            return n;
        }
    }

    // ------------------------------------------------------------------ group cleanup

    /**
     * Applies every rule to one raw group: drops ineligible / excluded / duplicate members, splits by copper state,
     * then removes members until no bad pair is left (the member in the most bad pairs first; ties go to the recipe's
     * output, then to the later member, so the group's "root" block stays). Returns the resulting groups (each with at
     * least two members), member order preserved.
     */
    static List<List<Block>> clean(final List<Block> raw, final Predicate<Block> allowed, final BadPairs bad) {
        final Map<Block, Boolean> seen = new IdentityHashMap<>();
        final Map<Object, List<Block>> byCopper = new LinkedHashMap<>();
        for (final Block b : raw) {
            if (b == null || seen.put(b, Boolean.TRUE) != null || !allowed.test(b)) continue;
            final CopperKey key = copperKey(b);
            byCopper.computeIfAbsent(key == null ? NO_COPPER : key, k -> new ArrayList<>()).add(b);
        }
        final List<List<Block>> result = new ArrayList<>();
        for (final List<Block> part : byCopper.values()) {
            final List<Block> kept = removeBadPairs(part, bad);
            if (kept.size() >= 2) result.add(List.copyOf(kept));
        }
        return result;
    }

    private static final Object NO_COPPER = new Object();

    private static List<Block> removeBadPairs(final List<Block> group, final BadPairs bad) {
        final List<Block> g = new ArrayList<>(group);
        while (g.size() >= 2) {
            int worst = -1;
            int worstCount = 0;
            boolean worstIsOutput = false;
            for (int i = 0; i < g.size(); i++) {
                final Block b = g.get(i);
                int count = 0;
                boolean isOutput = false;
                for (final Block o : g) {
                    if (o == b) continue;
                    if (bad.outputs(o, b)) { count++; isOutput = true; }
                    else if (bad.outputs(b, o)) count++;
                }
                if (count == 0) continue;
                // >= on equal counts: later members lose ties, and outputs lose ties against inputs.
                if (count > worstCount || (count == worstCount && (isOutput || !worstIsOutput))) {
                    worst = i;
                    worstCount = count;
                    worstIsOutput = isOutput;
                }
            }
            if (worst < 0) break;
            g.remove(worst);
        }
        return g;
    }

    // ------------------------------------------------------------------ block states

    /**
     * {@code to} with every property it shares with {@code from} copied over (axis, facing, half, waterlogged ...).
     * Properties match by identity, or by name + value type when a mod declares its own instance.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static BlockState copyShared(final BlockState from, final BlockState to) {
        BlockState out = to;
        final Collection<Property<?>> target = to.getProperties();
        for (final Property<?> p : from.getProperties()) {
            Property<?> q = target.contains(p) ? p : null;
            if (q == null) {
                for (final Property<?> t : target) {
                    if (t.getName().equals(p.getName()) && t.getValueClass() == p.getValueClass()) { q = t; break; }
                }
            }
            if (q == null) continue;
            final Comparable value = from.getValue(p);
            if (!q.getPossibleValues().contains(value)) continue;
            out = out.setValue((Property) q, value);
        }
        return out;
    }

    /** Registry order, the tie-breaker that keeps every list deterministic for a given mod set. */
    static int order(final Block block) {
        return BuiltInRegistries.BLOCK.getId(block);
    }

    static boolean sameBlocks(final List<Block> a, final List<Block> b) {
        return a.size() == b.size() && Objects.equals(asSet(a), asSet(b));
    }

    static Set<Block> asSet(final List<Block> blocks) {
        final Set<Block> s = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        s.addAll(blocks);
        return s;
    }
}
