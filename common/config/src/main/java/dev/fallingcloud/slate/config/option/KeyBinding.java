package dev.fallingcloud.slate.config.option;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link KeyMapping} as a binding: the value is the key's save name ({@code key.keyboard.w}), so
 * presets and favourites can carry key binds too. Id {@code key:<mapping name>}.
 */
public final class KeyBinding extends Binding {

    private final KeyMapping mapping;

    public KeyBinding(final KeyMapping mapping) {
        super("key:" + mapping.getName(), OptionType.KEYBIND, Component.translatable(mapping.getName()));
        this.mapping = mapping;
        getter(mapping::saveString);
        setter(v -> {
            mapping.setKey(parse(OptionValues.asString(v)));
            KeyMapping.resetMapping();
            Minecraft.getInstance().options.save();
        });
        def(mapping.getDefaultKey().getName());
        tooltip(Component.translatable(mapping.getCategory()));
        searchWords(Component.translatable(mapping.getCategory()).getString() + " key bind");
    }

    public KeyMapping mapping() { return mapping; }

    /**
     * Also every name of the key it is on right now ("left alt", "lmb", "ctrl+r"), so search finds binds by key. Not
     * the save name: "key.keyboard.r" put "keyboard" in every bind, so a search for "r" found nearly all of them.
     */
    @Override
    public String searchText() {
        return super.searchText() + " " + KeySearch.text(mapping);
    }

    public static InputConstants.Key parse(@Nullable final String name) {
        if (name == null || name.isEmpty()) return InputConstants.UNKNOWN;
        try { return InputConstants.getKey(name); } catch (final Exception e) { return InputConstants.UNKNOWN; }
    }

    @Override
    public Component valueText(@Nullable final Object value) {
        return parse(OptionValues.asString(value)).getDisplayName();
    }
}
