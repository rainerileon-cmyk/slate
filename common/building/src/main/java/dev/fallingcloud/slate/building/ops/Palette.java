package dev.fallingcloud.slate.building.ops;

import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * What an operation places, resolved from the held stack or the hotbar (mode parameter {@code palette}). Material
 * and shape aware: an entry that identifies as a variant (a held oak stairs = oak planks in the STAIRS shape) is
 * placed as that shape of that material and paid with any stack of the same material.
 *
 * <p>Deterministic on both sides: {@link #pick} chooses the entry for a position from the position and a seed the
 * planner derives from the selection (anchors + mode), so the client preview and the server agree block for block
 * without sending the random choices.
 *
 * @param entries the blocks in hotbar order
 * @param pattern how positions choose among them
 */
public record Palette(List<WeightedEntry> entries, Pattern pattern) {

    /** How {@link #pick} spreads the entries over positions. */
    public enum Pattern {
        /** Always the first entry. */
        SINGLE,
        /** Weighted random per position (weights = stack counts), seeded per operation. */
        RANDOM,
        /** Entries alternate like a 3D checkerboard ({@code (x + y + z) mod n}). */
        CHECKER
    }

    /**
     * One palette entry.
     *
     * @param stack   a template stack (count ignored) the block comes from
     * @param variant what it identifies as, null for blocks outside the variant system
     * @param weight  relative weight (HOTBAR_RANDOM uses stack counts)
     */
    public record WeightedEntry(ItemStack stack, @Nullable Variant variant, int weight) {
        public WeightedEntry {
            stack = stack.copyWithCount(1);
            weight = Math.max(1, weight);
        }

        /** The block the template stack places (air for a non-block stack). */
        public Block block() {
            return stack.getItem() instanceof BlockItem item ? item.getBlock() : Blocks.AIR;
        }
    }

    public static final Palette EMPTY = new Palette(List.of(), Pattern.SINGLE);

    public Palette {
        entries = List.copyOf(entries);
    }

    /** A palette of {@code entries}: SINGLE for one entry, RANDOM for more. */
    public Palette(final List<WeightedEntry> entries) {
        this(entries, entries.size() <= 1 ? Pattern.SINGLE : Pattern.RANDOM);
    }

    public static Palette single(final ItemStack stack, final @Nullable Variant variant) {
        return stack.isEmpty() ? EMPTY : new Palette(List.of(new WeightedEntry(stack, variant, 1)), Pattern.SINGLE);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The first entry (the held block for HELD palettes); null when empty. */
    public @Nullable WeightedEntry first() {
        return entries.isEmpty() ? null : entries.get(0);
    }

    /** The entry placed at {@code pos} for an operation seeded with {@code seed}; null when empty. */
    public @Nullable WeightedEntry pick(final BlockPos pos, final long seed) {
        final int n = entries.size();
        if (n == 0) return null;
        if (n == 1 || pattern == Pattern.SINGLE) return entries.get(0);
        if (pattern == Pattern.CHECKER) return entries.get(Math.floorMod(pos.getX() + pos.getY() + pos.getZ(), n));
        long total = 0;
        for (final WeightedEntry e : entries) total += e.weight();
        long r = Math.floorMod(mix(seed ^ mix(pos.asLong())), total);
        for (final WeightedEntry e : entries) {
            r -= e.weight();
            if (r < 0) return e;
        }
        return entries.get(n - 1);
    }

    /**
     * The palette an operation of {@code mode} uses for {@code player}: the stack in hotbar slot {@code slot} or the offhand ({@link Inventory#SLOT_OFFHAND}) (the
     * selected slot when out of range) for HELD and for modes without a {@code palette} parameter, or every usable
     * block on the hotbar for HOTBAR_RANDOM / HOTBAR_CHECKER. Stacks that cannot be built with (non-blocks, doors,
     * beds, container blocks in survival, shape items without a material) are left out; the result may be empty.
     */
    public static Palette resolve(final Player player, final BuildMode mode, final ModeParams params, final int slot) {
        final Inventory inv = player.getInventory();
        final String choice = mode.param(BuildModes.PALETTE.id()) != null ? params.getChoice(BuildModes.PALETTE.id()) : "HELD";
        if ("HOTBAR_RANDOM".equals(choice) || "HOTBAR_CHECKER".equals(choice)) {
            final List<WeightedEntry> out = new ArrayList<>();
            for (int i = 0; i < Inventory.getSelectionSize(); i++) {
                final ItemStack stack = inv.getItem(i);
                final WeightedEntry e = entryOf(stack, player);
                if (e == null) continue;
                int merged = -1;
                for (int j = 0; j < out.size(); j++) {
                    if (ItemStack.isSameItemSameComponents(out.get(j).stack(), stack)) { merged = j; break; }
                }
                if (merged >= 0) {
                    final WeightedEntry old = out.get(merged);
                    out.set(merged, new WeightedEntry(old.stack(), old.variant(), old.weight() + stack.getCount()));
                } else {
                    out.add(e);
                }
            }
            if (out.isEmpty()) return EMPTY;
            return new Palette(out, "HOTBAR_CHECKER".equals(choice) ? Pattern.CHECKER : Pattern.RANDOM);
        }
        final ItemStack held = Inventory.isHotbarSlot(slot) || slot == Inventory.SLOT_OFFHAND ? inv.getItem(slot) : player.getMainHandItem();
        final WeightedEntry e = entryOf(held, player);
        return e == null ? EMPTY : new Palette(List.of(e), Pattern.SINGLE);
    }

    /** A palette entry for {@code stack}, or null when it cannot be built with (see {@link #resolve}). */
    public static @Nullable WeightedEntry entryOf(final ItemStack stack, final Player player) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) return null;
        final Block block = item.getBlock();
        final BlockState def = block.defaultBlockState();
        if (def.isAir()) return null;
        // Two-block things (doors, beds, tall plants) would place their other half outside the plan.
        if (def.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || def.hasProperty(BlockStateProperties.BED_PART)) return null;
        final Variant variant = VariantRegistry.get().identify(stack).orElse(null);
        if (block instanceof ShapeBlock && variant == null) return null;   // a shape item without a material places nothing
        if (block instanceof EntityBlock && !(block instanceof ShapeBlock) && !mayUseBlockEntities(player)) return null;
        return new WeightedEntry(stack, variant, stack.getCount());
    }

    /** Whether {@code player} may place / break blocks with block entities (creative, or the server allows it). */
    public static boolean mayUseBlockEntities(final Player player) {
        return player.isCreative() || BuildingServerSettings.effective(player).ops().allowBlockEntities;
    }

    /** SplitMix64 finaliser: a well-spread hash of a 64-bit value. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
