package dev.fallingcloud.slate.building.neoforge.compat;

import dev.fallingcloud.slate.building.compat.jei.BuildingJeiPlugin;
import dev.fallingcloud.slate.building.item.ShapeBlockItem;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/** Dev harness access to JEI's live ingredient list (through our own plugin's runtime). Only loaded with JEI. */
final class JeiProbe {

    static @Nullable IJeiRuntime runtime() {
        try {
            final Field f = BuildingJeiPlugin.class.getDeclaredField("runtime");
            f.setAccessible(true);
            return (IJeiRuntime) f.get(null);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static Collection<ItemStack> items() {
        final IJeiRuntime rt = runtime();
        return rt == null ? List.of() : rt.getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK);
    }

    static boolean has(final Item item) {
        for (final ItemStack s : items()) if (s.is(item)) return true;
        return false;
    }

    static int count(final Item item) {
        int n = 0;
        for (final ItemStack s : items()) if (s.is(item)) n++;
        return n;
    }

    /** The materials JEI lists for one of our shape items ("-" for a stack without one). */
    static Set<String> materials(final Item shapeItem) {
        final Set<String> out = new TreeSet<>();
        for (final ItemStack s : items()) {
            if (!s.is(shapeItem)) continue;
            final Block m = ShapeBlockItem.material(s);
            out.add(m == null ? "-" : BuiltInRegistries.BLOCK.getKey(m).toString());
        }
        return out;
    }

    static void filter(final String text) {
        final IJeiRuntime rt = runtime();
        if (rt != null) rt.getIngredientFilter().setFilterText(text);
    }

    private JeiProbe() {}
}
