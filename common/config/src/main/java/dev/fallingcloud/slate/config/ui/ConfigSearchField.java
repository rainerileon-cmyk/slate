package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.core.gfx.Icon;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

/** A {@link ConfigTextField} preset for searching: icon, placeholder, clear button; Escape clears then blurs. */
public class ConfigSearchField extends ConfigTextField {

    public ConfigSearchField(final int x, final int y, final int width, final Consumer<String> onChange) {
        super(x, y, width, HEIGHT, Component.translatable("slate.search"));
        icon(Icon.SEARCH);
        clearButton(true);
        placeholder(Component.translatable("slate.search.placeholder"));
        onChange(onChange);
        onEscape(() -> { if (!getValue().isEmpty()) setValue(""); else setFocused(false); });
    }
}
