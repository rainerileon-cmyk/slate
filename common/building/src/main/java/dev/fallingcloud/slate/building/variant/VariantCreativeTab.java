package dev.fallingcloud.slate.building.variant;

import dev.fallingcloud.slate.building.SlateBuilding;
import java.util.Set;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Variant half of the "Slate Building" creative tab, and the creative side of "delete native variants".
 *
 * <p>{@link #fill}: one showcase stack per shape in oak planks and in stone bricks (design §8), exactly what the swap
 * wheel hands out ({@link VariantRegistry#stackFor}: oak stairs are vanilla's, an oak vertical slab is ours).
 * {@link #stripNatives}: with {@code deleteNativeVariants}, native variant items leave every tab and the search.
 */
public final class VariantCreativeTab {

    private static final Block[] SHOWCASE = {Blocks.OAK_PLANKS, Blocks.STONE_BRICKS};
    private static boolean warnedImmutable;

    private VariantCreativeTab() {}

    public static void fill(final CreativeModeTab.ItemDisplayParameters params, final CreativeModeTab.Output output) {
        final VariantRegistry registry = VariantRegistry.get();
        final Set<ItemStack> added = ItemStackLinkedSet.createTypeAndComponentsSet();
        for (final Block material : SHOWCASE) {
            for (final Shape shape : Shape.values()) {
                if (!shape.custom()) continue;
                final ItemStack stack = registry.stackFor(material, shape, 1);
                if (!stack.isEmpty() && stack.getItem().isEnabled(params.enabledFeatures()) && added.add(stack)) output.accept(stack);
            }
        }
    }

    /** Removes native variant items from {@code tab} when natives are deleted (CreativeModeTabMixin, TAIL of buildContents). */
    public static void stripNatives(final CreativeModeTab tab) {
        final VariantRegistry registry = VariantRegistry.get();
        if (!registry.deletesNatives()) return;
        final Set<Item> natives = registry.nativeVariantItems();
        if (natives.isEmpty()) return;
        try {
            tab.getDisplayItems().removeIf(stack -> natives.contains(stack.getItem()));
            tab.getSearchTabDisplayItems().removeIf(stack -> natives.contains(stack.getItem()));
        } catch (final UnsupportedOperationException e) {
            if (!warnedImmutable) {
                warnedImmutable = true;
                SlateBuilding.LOGGER.warn("[Slate Building] a creative tab's contents are immutable; native variants stay visible there ({})", tab.getDisplayName().getString());
            }
        }
    }
}
