package dev.fallingcloud.slate.building.client.mode;

import dev.fallingcloud.slate.building.ops.Change;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * The economy as the client can see it (design §1): which material a planned placement is paid with, how many
 * units it costs, and how many units of each material the player carries (hotbar, main inventory and offhand, plus
 * the toolbox pouch; never the toolbox itself). A Supply Link container is on the server's side only.
 */
final class ModeMaterials {

    /** The material {@code c} is paid with, or null when it costs nothing the player could carry (breaks, fluids). */
    static @Nullable Block materialOf(final Change c) {
        if (c.kind() == Change.Kind.BREAK || c.target().isAir()) return null;
        if (c.targetVariant() != null) return c.targetVariant().material();
        final Block block = c.target().getBlock();
        final Optional<Variant> v = VariantRegistry.get().identify(c.target(), null);
        if (v.isPresent()) return v.get().material();
        return block.asItem() == Items.AIR ? null : block;
    }

    /** Material units {@code c} costs (a double slab 2, n layers n, else 1). */
    static int unitsOf(final Change c) {
        return Math.max(1, VariantRegistry.get().units(c.target(), null));
    }

    /** Units of each of {@code materials} the player carries. */
    static Map<Block, Integer> available(final Player player, final Set<Block> materials) {
        final Map<Block, Integer> out = new HashMap<>();
        if (materials.isEmpty()) return out;
        final Inventory inv = player.getInventory();
        for (final ItemStack s : inv.items) count(s, materials, out);
        for (final ItemStack s : inv.offhand) count(s, materials, out);
        final ItemStack toolbox = ToolboxAccess.find(player);
        if (!toolbox.isEmpty()) {
            final ItemContainerContents contents = toolbox.get(DataComponents.CONTAINER);
            if (contents != null) for (final ItemStack s : contents.nonEmptyItems()) count(s, materials, out);
        }
        return out;
    }

    /** A stack to show for {@code material} (its full block item, or the shape item when that is all there is). */
    static ItemStack icon(final Block material) {
        final ItemStack full = VariantRegistry.get().stackFor(material, Shape.FULL, 1);
        return full.isEmpty() ? new ItemStack(material) : full;
    }

    private static void count(final ItemStack s, final Set<Block> materials, final Map<Block, Integer> out) {
        if (s.isEmpty()) return;
        final Block material = materialOf(s);
        if (material != null && materials.contains(material)) out.merge(material, s.getCount(), Integer::sum);
    }

    private static @Nullable Block materialOf(final ItemStack s) {
        final Optional<Variant> v = VariantRegistry.get().identify(s);
        if (v.isPresent()) return v.get().material();
        return s.getItem() instanceof BlockItem bi ? bi.getBlock() : null;
    }

    private ModeMaterials() {}
}
