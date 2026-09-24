package dev.fallingcloud.slate.building.client.mode;

import dev.fallingcloud.slate.building.item.ShapeBlockItem;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.ModeParams;
import dev.fallingcloud.slate.building.ops.Palette;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link Palette} a preview plans with, from the player's hands and hotbar (mode parameter {@code palette}):
 * HELD = the main-hand block, else the offhand block; HOTBAR_RANDOM / HOTBAR_CHECKER = every block in the hotbar,
 * weighted by count. Only stacks that place something count (block items and anything that identifies as a
 * variant; a shape item without a material does not).
 *
 * <p><b>D2-local:</b> the ops server resolves its own palette from {@code ApplyOp.slot} (D1). This is the same rule
 * written on the client for the preview; if D1 exposes a shared resolver, the preview should call that instead.
 */
final class ClientPalette {

    /** Inventory slot index of the offhand, as sent in {@code ApplyOp.slot}. */
    static final int OFFHAND_SLOT = Inventory.SLOT_OFFHAND;

    /** A palette's identity for cache keys ({@code ItemStack} has no value equality). */
    record Signature(List<Entry> entries) {
        record Entry(Item item, @Nullable Variant variant, int weight) {}
    }

    static Palette resolve(final Player player, final BuildMode mode, final ModeParams params) {
        final String choice = mode.param("palette") != null ? params.getChoice("palette") : "HELD";
        if ("HOTBAR_RANDOM".equals(choice) || "HOTBAR_CHECKER".equals(choice)) {
            final List<Palette.WeightedEntry> entries = new ArrayList<>();
            final Inventory inv = player.getInventory();
            for (int i = 0; i < Inventory.getSelectionSize(); i++) {
                final ItemStack s = inv.getItem(i);
                if (placeable(s)) entries.add(new Palette.WeightedEntry(s, variantOf(s), Math.max(1, s.getCount())));
            }
            if (!entries.isEmpty()) return new Palette(entries);
        }
        final ItemStack held = heldBlock(player);
        return held.isEmpty() ? Palette.EMPTY : Palette.single(held, variantOf(held));
    }

    /** The block stack HELD uses: main hand, else offhand; empty when neither places anything. */
    static ItemStack heldBlock(final Player player) {
        if (placeable(player.getMainHandItem())) return player.getMainHandItem();
        if (placeable(player.getOffhandItem())) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /** The slot {@code ApplyOp} pays from: the selected hotbar slot, or the offhand when only it holds a block. */
    static int paySlot(final Player player) {
        if (!placeable(player.getMainHandItem()) && placeable(player.getOffhandItem())) return OFFHAND_SLOT;
        return player.getInventory().selected;
    }

    static boolean placeable(final ItemStack stack) {
        if (stack.isEmpty()) return false;
        final Optional<Variant> v = VariantRegistry.get().identify(stack);
        if (v.isPresent()) return true;
        return stack.getItem() instanceof BlockItem && !(stack.getItem() instanceof ShapeBlockItem);
    }

    static Signature signature(final Palette palette) {
        final List<Signature.Entry> out = new ArrayList<>(palette.entries().size());
        for (final Palette.WeightedEntry e : palette.entries()) out.add(new Signature.Entry(e.stack().getItem(), e.variant(), e.weight()));
        return new Signature(out);
    }

    private static @Nullable Variant variantOf(final ItemStack stack) {
        return VariantRegistry.get().identify(stack).orElse(null);
    }

    private ClientPalette() {}
}
