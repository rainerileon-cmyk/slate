package dev.fallingcloud.slate.building.client.wheel;

import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.core.gfx.Icon;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * One entry of a wheel: a shape of the current material, or a chisel-group member keeping the current shape.
 *
 * @param kind      what applying it does (swap/reshape the shape, or chisel to another material)
 * @param shape     the shape it turns into (for CHISEL: the kept shape)
 * @param block     CHISEL: the member material; SHAPE: the current material
 * @param stack     the real variant stack to render (may be empty: then {@code icon} is drawn)
 * @param icon      glyph fallback
 * @param label     display name ("Oak Stairs")
 * @param available the material has this shape (false only when unavailable shapes are shown greyed)
 * @param lock      why it cannot be applied right now ("Needs: Hammer"), null when it can
 * @param current   it is what the target already is (applying it does nothing)
 */
public record WheelSlice(Kind kind, Shape shape, Block block, ItemStack stack, Icon icon, Component label,
                         boolean available, @Nullable Component lock, boolean current) {

    public enum Kind { SHAPE, CHISEL }

    /** Whether picking it would change something. */
    public boolean actionable() {
        return available && lock == null && !current;
    }

    /** Lower-case text the build menu's search matches against (name, shape id, block id). */
    public String searchText() {
        final String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
        return (label.getString() + " " + shape.id().replace('_', ' ') + " " + id).toLowerCase(Locale.ROOT);
    }
}
