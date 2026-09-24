package dev.fallingcloud.slate.building.ops.server;

import dev.fallingcloud.slate.building.toolbox.ToolboxItem;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * What something is paid with (design §1). A {@link Material} key is paid with any stack that identifies as that
 * material, in any shape, one unit per item (oak stairs, oak slabs and oak planks all pay for oak); an
 * {@link Exact} key (blocks outside the variant system: torches, glass panes of mods ...) only with that item.
 */
public sealed interface CostKey {

    /** Whether {@code stack} pays for this key (one unit per item). */
    boolean matches(ItemStack stack);

    /** {@code count} units of this key as an item stack (the full material block for materials). */
    ItemStack stack(int count);

    /** Display name for messages. */
    Component name();

    /** Any shape of {@code material}. */
    record Material(Block material) implements CostKey {
        @Override public boolean matches(final ItemStack stack) {
            if (stack.isEmpty()) return false;
            final Optional<Variant> v = VariantRegistry.get().identify(stack);
            return v.isPresent() ? v.get().material() == material : stack.is(material.asItem());
        }

        @Override public ItemStack stack(final int count) {
            final ItemStack s = VariantRegistry.get().stackFor(material, dev.fallingcloud.slate.building.variant.Shape.FULL, count);
            return s.isEmpty() ? new ItemStack(material, count) : s;
        }

        @Override public Component name() {
            return material.getName();
        }
    }

    /** Exactly this item (with these components, or any components for plain block items). */
    record Exact(ItemStack template) implements CostKey {
        public Exact {
            template = template.copyWithCount(1);
        }

        @Override public boolean matches(final ItemStack stack) {
            if (stack.isEmpty() || !ItemStack.isSameItem(template, stack)) return false;
            // Container blocks must match exactly: a filled shulker box never pays for an empty one.
            return ItemStack.isSameItemSameComponents(template, stack)
                || !(stack.getItem() instanceof BlockItem item && item.getBlock() instanceof EntityBlock);
        }

        @Override public ItemStack stack(final int count) {
            return template.copyWithCount(count);
        }

        @Override public Component name() {
            return template.getHoverName();
        }

        @Override public boolean equals(final Object o) {
            return o instanceof Exact e && ItemStack.isSameItemSameComponents(template, e.template);
        }

        @Override public int hashCode() {
            return ItemStack.hashItemAndComponents(template);
        }
    }

    /** The key {@code stack} pays for; null for empty stacks and toolboxes. */
    static @Nullable CostKey of(final ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof ToolboxItem) return null;
        final Optional<Variant> v = VariantRegistry.get().identify(stack);
        return v.isPresent() ? new Material(v.get().material()) : new Exact(stack);
    }

    /** The key placing {@code state} (variant {@code variant}) costs; null for blocks without an item (fire, fluids). */
    static @Nullable CostKey of(final BlockState state, final @Nullable Variant variant) {
        if (variant != null) return new Material(variant.material());
        final Item item = state.getBlock().asItem();
        return item == Items.AIR ? null : new Exact(new ItemStack(item));
    }
}
