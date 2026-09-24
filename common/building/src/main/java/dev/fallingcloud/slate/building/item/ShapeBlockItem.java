package dev.fallingcloud.slate.building.item;

import dev.fallingcloud.slate.building.block.ShapeBlockEntity;
import dev.fallingcloud.slate.building.registry.BuildingComponents;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.VariantNames;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
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
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The item of one shape block. The material is NOT part of the item: it rides on the stack in the
 * {@code slate_building:material} data component (a block id, set at runtime, never in {@code Item.Properties}), so
 * "Oak Planks Vertical Slab" and "Stone Vertical Slab" are the same item with different components and do not stack.
 *
 * <p>Placing copies the material into the block entity (vanilla {@code BlockItem.place} applies the stack's
 * components; the blocks' {@code setPlacedBy} makes sure of it). A stack without a material, or with one that is not
 * a material (another shape, a denied block), places nothing and says so in its tooltip. Names read
 * "{@code <material> <shape>}".
 *
 * <p>When the (material, shape) has a NATIVE block (oak planks + slab = {@code minecraft:oak_slab}) the stack places
 * that native, exactly as {@link VariantRegistry#stackFor} would have handed it out: same unit, and it merges into a
 * native half slab already in the world like the vanilla item does. Such stacks only exist from before a native was
 * mapped or from commands; the swap wheel never makes them. The one exception is completing one of OUR half shapes of
 * the same material (a double of ours stays ours).
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

    /**
     * The material block of {@code stack} (any shape item), or null when unset, unknown (mod removed), air or another
     * shape block ({@link ShapeBlockEntity#canHold}: a shape made of a shape would recurse into itself).
     */
    public static @Nullable Block material(final ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ShapeBlockItem)) return null;
        final ResourceLocation id = stack.get(BuildingComponents.MATERIAL.get());
        if (id == null) return null;
        final Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        return ShapeBlockEntity.canHold(block) ? block : null;
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
        final VariantRegistry registry = VariantRegistry.get();
        final InteractionResult asNative = placeAsNative(ctx, material, registry);
        if (asNative != null) return asNative;
        // A crafted component (creative, /give, an old stack after the admin denied the block) places nothing.
        if (!registry.isMaterial(material)) return InteractionResult.FAIL;
        PLACING.set(material.defaultBlockState());
        try {
            return super.place(ctx);
        } finally {
            PLACING.remove();
        }
    }

    /**
     * Places the native realisation of (material, this shape) instead, as if its item were in hand (same click, the
     * clicked block re-asked whether it can be replaced, so a native half slab merges), and takes from the held stack
     * what that placement used (nothing in creative). Null when there is no native, or when the click completes one of
     * our own half shapes of the same material.
     */
    private @Nullable InteractionResult placeAsNative(final BlockPlaceContext ctx, final Block material, final VariantRegistry registry) {
        final Block nativeBlock = registry.nativeBlock(material, shape());
        if (nativeBlock == null || nativeBlock instanceof ShapeBlock) return null;
        if (!(nativeBlock.asItem() instanceof BlockItem nativeItem) || nativeItem instanceof ShapeBlockItem) return null;
        if (ctx.replacingClickedOnBlock() && ctx.getLevel().getBlockState(ctx.getClickedPos()).getBlock() instanceof ShapeBlock) return null;
        final ItemStack held = ctx.getItemInHand();
        final ItemStack proxy = new ItemStack(nativeItem, held.getCount());
        final InteractionResult result = nativeItem.place(VariantRegistry.withStack(ctx, proxy));
        final int used = held.getCount() - proxy.getCount();
        if (used > 0) held.shrink(used);
        return result;
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
        return VariantNames.of(material, shape());
    }

    @Override
    public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final List<Component> lines, final TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        final Block material = material(stack);
        if (material != null) {
            final VariantRegistry registry = VariantRegistry.get();
            if (registry.nativeBlock(material, shape()) == null && !registry.isMaterial(material)) {
                lines.add(Component.translatable("slate_building.variant.not_material", material.getName()).withStyle(ChatFormatting.RED));
            }
            return;
        }
        final ResourceLocation id = stack.get(BuildingComponents.MATERIAL.get());
        lines.add(id == null
            ? Component.translatable("slate_building.variant.no_material").withStyle(ChatFormatting.RED)
            : Component.translatable("slate_building.variant.unknown_material", id.toString()).withStyle(ChatFormatting.RED));
    }
}
