package dev.fallingcloud.slate.building.compat.jei;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.ServerSettingsClient;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * JEI integration (optional): with {@code deleteNativeVariants}, native variant items (oak stairs, ...) disappear
 * from JEI's ingredient list too, and come back when the rule is turned off (re-evaluated whenever the server's
 * rules change). JEI finds this class itself (NeoForge: {@link JeiPlugin}; Fabric: the {@code jei_mod_plugin}
 * entrypoint), so nothing else loads it and it is harmless without JEI.
 *
 * <p>Owner: A (variants).
 */
@JeiPlugin
public final class BuildingJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = SlateBuilding.id("jei");

    private static @Nullable IJeiRuntime runtime;
    private static final List<ItemStack> HIDDEN = new ArrayList<>();
    private static boolean listening;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(final IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        HIDDEN.clear();
        if (!listening) {
            listening = true;
            ServerSettingsClient.onChange(() -> Minecraft.getInstance().execute(BuildingJeiPlugin::refresh));
        }
        refresh();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        HIDDEN.clear();
    }

    /** Hides (or restores) native variants to match the rules in effect. */
    private static void refresh() {
        final IJeiRuntime rt = runtime;
        if (rt == null) return;
        try {
            final IIngredientManager ingredients = rt.getIngredientManager();
            if (!HIDDEN.isEmpty()) {
                ingredients.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.copyOf(HIDDEN));
                HIDDEN.clear();
            }
            final VariantRegistry registry = VariantRegistry.get();
            if (!registry.deletesNatives()) return;
            final Set<Item> natives = registry.nativeVariantItems();
            final List<ItemStack> remove = new ArrayList<>();
            for (final ItemStack stack : ingredients.getAllIngredients(VanillaTypes.ITEM_STACK)) {
                if (natives.contains(stack.getItem())) remove.add(stack);
            }
            if (remove.isEmpty()) return;
            ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, remove);
            HIDDEN.addAll(remove);
            SlateBuilding.LOGGER.info("[Slate Building] JEI: hid {} native variant items", remove.size());
        } catch (final RuntimeException e) {
            SlateBuilding.LOGGER.warn("[Slate Building] JEI: could not update native variant visibility: {}", e.toString());
        }
    }
}
