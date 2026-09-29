package dev.fallingcloud.slate.core.module;

import dev.fallingcloud.slate.core.gfx.Icon;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * One entry of {@link KnownModules}: a Slate module the suite knows about whether or not it is installed.
 *
 * @param id    the mod id ({@code slate_menu}, ...)
 * @param name  what it is called in tooltips and settings ("Slate UI")
 * @param icon  its glyph
 * @param pitch one line on what it adds, shown where the module is missing
 * @param url   where to get it, or null
 */
public record KnownModule(String id, Component name, Icon icon, Component pitch, @Nullable String url) {

    /** Whether the module is loaded right now. */
    public boolean installed() {
        return Modules.isLoaded(id);
    }
}
