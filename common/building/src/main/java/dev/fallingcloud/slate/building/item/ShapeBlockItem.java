package dev.fallingcloud.slate.building.item;

import dev.fallingcloud.slate.building.registry.BuildingComponents;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The item of one shape block. The material is NOT part of the item: it rides on the stack in the
 * {@code slate_building:material} data component (a block id, set at runtime, never in {@code Item.Properties}), so
 * "Oak Planks Vertical Slab" and "Stone Vertical Slab" are the same item with different components and do not stack.
 *
 * <p>Placing copies the material into the block entity (vanilla {@code BlockItem.place} applies the stack's
 * components; the blocks' {@code setPlacedBy} makes sure of it). A stack without a material places nothing and says
 * so in its tooltip. Names read "{@code <material> <shape>}".
 */
public class ShapeBlockItem extends BlockItem {

    /** The material being placed on this thread, for the place sound (vanilla asks for it with the state only). */
    private static final ThreadLocal<BlockState> PLACING = new ThreadLocal<>();

    public ShapeBlockItem(final Block block, final Item.Properties properties) {
        super(block, properties);
    }

    /** The shape this item places. */
    public Shape shape() {
        return ((ShapeBlock) getBlock()).shape();
    }

    /** The material block of {@code stack} (any shape item), or null when unset, unknown (mod removed) or air. */
    public static @Nullable Block material(final ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ShapeBlockItem)) return null;
        final ResourceLocation id = stack.get(BuildingComponents.MATERIAL.get());
        if (id == null) return null;
        final Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        return block == null || block == Blocks.AIR ? null : block;
    }

    /** {@code stack} with its material set to {@code material} (returns the same stack). */
    public static ItemStack withMaterial(final ItemStack stack, final Block material) {
        stack.set(BuildingComponents.MATERIAL.get(), BuiltInRegistries.BLOCK.getKey(material));
        return stack;
    }

    @Override
    public InteractionResult place(final BlockPlaceContext ctx) {
        final Block material = material(ctx.getItemInHand());
        if (material == null) return InteractionResult.FAIL;
        PLACING.set(material.defaultBlockState());
        try {
            return super.place(ctx);
        } finally {
            PLACING.remove();
        }
    }

    @Override
    protected SoundEvent getPlaceSound(final BlockState state) {
        final BlockState material = PLACING.get();
        return material != null ? material.getSoundType().getPlaceSound() : super.getPlaceSound(state);
    }

    @Override
    public Component getName(final ItemStack stack) {
        final Block material = material(stack);
        if (material == null) return shape().displayName();
        return Component.translatable("slate_building.variant_name", material.getName(), shape().displayName());
    }

    @Override
    public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final List<Component> lines, final TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        if (material(stack) != null) return;
        final ResourceLocation id = stack.get(BuildingComponents.MATERIAL.get());
        lines.add(id == null
            ? Component.translatable("slate_building.variant.no_material").withStyle(ChatFormatting.RED)
            : Component.translatable("slate_building.variant.unknown_material", id.toString()).withStyle(ChatFormatting.RED));
    }
}
