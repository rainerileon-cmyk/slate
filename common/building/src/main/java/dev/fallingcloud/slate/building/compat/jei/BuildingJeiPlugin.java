package dev.fallingcloud.slate.building.compat.jei;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.ServerSettingsClient;
import dev.fallingcloud.slate.building.item.ShapeBlockItem;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * JEI integration (optional): with {@code deleteNativeVariants}, native variant items (oak stairs, ...) disappear
 * from JEI's ingredient list too, replaced by our shape item in the same material (one "Oak Planks Stairs" entry), and
 * come back when the rule is turned off (re-evaluated whenever the server's rules change). JEI finds this class itself (NeoForge: {@link JeiPlugin}; Fabric: the {@code jei_mod_plugin}
 * entrypoint), so nothing else loads it and it is harmless without JEI.
 *
 * <p>Owner: A (variants).
 */
@JeiPlugin
public final class BuildingJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = SlateBuilding.id("jei");

    private static @Nullable IJeiRuntime runtime;
    private static final List<ItemStack> HIDDEN = new ArrayList<>();
    /** Our shape stacks shown in place of hidden natives (a deleted oak_stairs leaves an oak planks stairs entry). */
    private static final List<ItemStack> ADDED = new ArrayList<>();
    private static boolean listening;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(final IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        HIDDEN.clear();
        ADDED.clear();
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
        ADDED.clear();
    }

    /** Hides (or restores) native variants to match the rules in effect. */
    private static void refresh() {
        final IJeiRuntime rt = runtime;
        if (rt == null) return;
        try {
            final IIngredientManager ingredients = rt.getIngredientManager();
            if (!ADDED.isEmpty()) {
                ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.copyOf(ADDED));
                ADDED.clear();
            }
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
            // Each hidden native hands its place to our shape item in the same material (BuildingJeiSubtypes keeps the
            // materials apart), so JEI still lists one stairs/slab/... per material; ones already listed are skipped.
            final Set<String> listed = new HashSet<>();
            for (final ItemStack stack : ingredients.getAllIngredients(VanillaTypes.ITEM_STACK)) listed.add(key(stack));
            final List<ItemStack> add = new ArrayList<>();
            for (final ItemStack stack : remove) {
                registry.identify(stack).ifPresent(v -> {
                    final ItemStack ours = registry.listingStackFor(v.material(), v.shape());
                    if (!ours.isEmpty() && listed.add(key(ours))) add.add(ours);
                });
            }
            if (!add.isEmpty()) {
                ingredients.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, add);
                ADDED.addAll(add);
            }
            SlateBuilding.LOGGER.info("[Slate Building] JEI: hid {} native variant items, listed {} of ours in their place", remove.size(), add.size());
        } catch (final RuntimeException e) {
            SlateBuilding.LOGGER.warn("[Slate Building] JEI: could not update native variant visibility: {}", e.toString());
        }
    }

    /** Item id plus our material component: what BuildingJeiSubtypes tells apart. */
    private static String key(final ItemStack stack) {
        final Block material = ShapeBlockItem.material(stack);
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + (material == null ? "" : "/" + BuiltInRegistries.BLOCK.getKey(material));
    }
}
