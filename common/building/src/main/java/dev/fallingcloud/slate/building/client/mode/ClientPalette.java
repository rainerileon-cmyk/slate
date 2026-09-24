package dev.fallingcloud.slate.building.client.mode;

import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.ModeParams;
import dev.fallingcloud.slate.building.ops.Palette;
import dev.fallingcloud.slate.building.variant.Variant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link Palette} a preview plans with. It is resolved by the ops server's own resolver
 * ({@link Palette#resolve}) from the same slot {@code ApplyOp} sends ({@link #paySlot}), so the preview and the server
 * use the same entries in the same order, and the seeded random/checker patterns pick the same block per position.
 */
final class ClientPalette {

    /** A palette's identity for cache keys ({@code ItemStack} has no value equality). */
    record Signature(List<Entry> entries, Palette.Pattern pattern) {
        record Entry(Item item, @Nullable Variant variant, int weight) {}
    }

    static Palette resolve(final Player player, final BuildMode mode, final ModeParams params) {
        return Palette.resolve(player, mode, params, paySlot(player));
    }

    /**
     * The slot {@code ApplyOp} pays from (and HELD palettes place): the selected hotbar slot, or the offhand
     * ({@link Inventory#SLOT_OFFHAND}) when only the offhand holds something placeable.
     */
    static int paySlot(final Player player) {
        if (!placeable(player, player.getMainHandItem()) && placeable(player, player.getOffhandItem())) return Inventory.SLOT_OFFHAND;
        return player.getInventory().selected;
    }

    /** Whether operations can build with {@code stack} (the server's rule: {@link Palette#entryOf}). */
    static boolean placeable(final Player player, final ItemStack stack) {
        return Palette.entryOf(stack, player) != null;
    }

    static Signature signature(final Palette palette) {
        final List<Signature.Entry> out = new ArrayList<>(palette.entries().size());
        for (final Palette.WeightedEntry e : palette.entries()) out.add(new Signature.Entry(e.stack().getItem(), e.variant(), e.weight()));
        return new Signature(out, palette.pattern());
    }

    private ClientPalette() {}
}
