package dev.fallingcloud.slate.building.compat.jei;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.item.ShapeBlockItem;
import dev.fallingcloud.slate.building.registry.BuildingItems;
import dev.fallingcloud.slate.building.registry.RegistryRef;
import dev.fallingcloud.slate.building.variant.Shape;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * JEI subtypes for our shape items: the material lives in a data component, which JEI ignores unless told, so every
 * "oak planks vertical slab" and "stone bricks vertical slab" collapsed into ONE JEI entry (JEI logged "N duplicate
 * items were found in 'Slate Building' creative tab's displayItems" in the DF pack). With the material as subtype
 * data each material/shape pair is its own ingredient, and recipes and lookups tell them apart.
 *
 * <p>A separate plugin from {@link BuildingJeiPlugin} so the two concerns stay independent; found by JEI the same way
 * (NeoForge: {@link JeiPlugin}; Fabric: the {@code jei_mod_plugin} entrypoint).
 */
@JeiPlugin
public final class BuildingJeiSubtypes implements IModPlugin {

    private static final ResourceLocation UID = SlateBuilding.id("jei_subtypes");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(final ISubtypeRegistration registration) {
        for (final Shape shape : Shape.values()) {
            final RegistryRef<ShapeBlockItem> item = BuildingItems.forShape(shape);
            if (item != null) registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, item.get(), MaterialSubtype.INSTANCE);
        }
    }

    /** The material's block id; none for a stack without a material (the bare shape item). */
    @SuppressWarnings("deprecation")   // getLegacyStringSubtypeInfo is still abstract in JEI 19
    private enum MaterialSubtype implements ISubtypeInterpreter<ItemStack> {
        INSTANCE;

        @Override
        public @Nullable Object getSubtypeData(final ItemStack stack, final UidContext context) {
            final Block material = ShapeBlockItem.material(stack);
            return material == null ? null : BuiltInRegistries.BLOCK.getKey(material);
        }

        @Override
        public String getLegacyStringSubtypeInfo(final ItemStack stack, final UidContext context) {
            final Object data = getSubtypeData(stack, context);
            return data == null ? "" : data.toString();
        }
    }
}
