package dev.fallingcloud.slate.config.option;

import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A live handle on one setting, wherever it lives (options.txt, Sodium's config model, a mod's TOML,
 * a JSON file, Slate's own config, a key mapping). Rows, favourites, presets, search and the curated
 * pages all speak this interface and nothing else. {@link #set} applies AND persists the value.
 */
public interface OptionBinding {

    /** Stable path such as {@code optionsTxt:renderDistance} or {@code toml:lucid:lucid-client.toml:filtering.mode}. */
    String id();

    Component label();

    @Nullable
    Component tooltip();

    OptionType type();

    /** The current value; see {@link OptionType} for the Java type per kind. */
    @Nullable
    Object get();

    /** Apply and persist. Implementations coerce reasonable inputs (Number for INT, String for CHOICE...). */
    void set(@Nullable Object value);

    /** The default, or {@code null} when unknown (then reset is unavailable). */
    @Nullable
    default Object defaultValue() { return null; }

    default boolean hasDefault() { return defaultValue() != null; }

    default boolean isDefault() {
        final Object d = defaultValue();
        return d == null || Objects.equals(OptionValues.normalize(get()), OptionValues.normalize(d));
    }

    default void reset() {
        if (hasDefault()) set(defaultValue());
    }

    /** Numeric bounds for INT/DOUBLE, or null (unbounded -> text field). */
    @Nullable
    default NumberRange range() { return null; }

    /** The options of a CHOICE. */
    default List<Choice> choices() { return List.of(); }

    /** Changing this needs a game (or world) restart to take effect. */
    default boolean requiresRestart() { return false; }

    /** False greys the row out (dependency not met, server config not loaded...). */
    default boolean enabled() { return true; }

    /** The action of an ACTION binding. */
    @Nullable
    default Runnable action() { return null; }

    /** Text on the button of an ACTION binding. */
    default Component actionLabel() { return Component.translatable("slate_config.row.open"); }

    /** Icon on the button of an ACTION binding (most open another screen). */
    default dev.fallingcloud.slate.core.gfx.Icon actionIcon() { return dev.fallingcloud.slate.core.gfx.Icon.EXTERNAL; }

    /** How a value is shown next to sliders / in search results. */
    default Component valueText(@Nullable final Object value) {
        return OptionValues.defaultText(this, value);
    }

    /** Text the search index matches against (label + tooltip by default). */
    default String searchText() {
        final Component t = tooltip();
        return label().getString() + (t == null ? "" : " " + t.getString());
    }
}
