package dev.fallingcloud.slate.core.screen.slot;

import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * One logical menu (title, worlds, options, friends, ...) that can be shown in up to three {@link Layout}s.
 *
 * @param id             stable id, {@code minecraft:title} for menus Minecraft has, {@code <modid>:<name>} for new ones
 * @param name           what the settings tables call it
 * @param owner          the module whose screens implement it (its Custom and Overhaul layouts); {@code slate} for Core's own
 * @param vanillaMenu    Minecraft has this menu, so the {@link Layout#VANILLA} layout exists
 * @param vanillaClasses fully qualified names of the vanilla screen classes the slot replaces (exact class match); empty for
 *                       menus that are drawn over rather than swapped (loading screens) and for new menus
 */
public record MenuSlot(String id, Component name, String owner, boolean vanillaMenu, List<String> vanillaClasses) {

    public MenuSlot {
        vanillaClasses = List.copyOf(vanillaClasses);
    }

    /** A menu Minecraft has, replaced by swapping the listed vanilla classes. */
    public static MenuSlot vanilla(final String id, final Component name, final String owner, final String... vanillaClasses) {
        return new MenuSlot(id, name, owner, true, List.of(vanillaClasses));
    }

    /** A menu that does not exist in vanilla (no vanilla layout; it is opened through {@link MenuSlots#open}). */
    public static MenuSlot custom(final String id, final Component name, final String owner) {
        return new MenuSlot(id, name, owner, false, List.of());
    }
}
