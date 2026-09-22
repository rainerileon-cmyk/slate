package dev.fallingcloud.slate.config.option;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The general-purpose {@link OptionBinding}: lambdas for get/set plus fluent metadata. Every resolver
 * and page builds these; only exotic sources (Sodium options, key mappings) subclass it.
 */
public class Binding implements OptionBinding {

    private final String id;
    private final OptionType type;
    private Component label;
    @Nullable private Component tooltip;
    private Supplier<Object> getter = () -> null;
    private Consumer<Object> setter = v -> {};
    @Nullable private Object defaultValue;
    @Nullable private NumberRange range;
    private List<Choice> choices = List.of();
    private boolean restart;
    private BooleanSupplier enabled = () -> true;
    @Nullable private Runnable action;
    @Nullable private Component actionLabel;
    @Nullable private Function<Object, Component> format;
    @Nullable private String extraSearch;

    public Binding(final String id, final OptionType type, final Component label) {
        this.id = id;
        this.type = type;
        this.label = label;
    }

    public static Binding of(final String id, final OptionType type, final Component label) {
        return new Binding(id, type, label);
    }

    public static Binding of(final String id, final OptionType type, final String label) {
        return new Binding(id, type, Component.literal(label));
    }

    // ------------------------------------------------------------------ fluent

    public Binding label(final Component c) { this.label = c; return this; }

    public Binding tooltip(@Nullable final Component c) { this.tooltip = c; return this; }

    public Binding tooltip(@Nullable final String s) { this.tooltip = s == null || s.isBlank() ? null : Component.literal(s); return this; }

    public Binding getter(final Supplier<Object> g) { this.getter = g; return this; }

    public Binding setter(final Consumer<Object> s) { this.setter = s; return this; }

    public Binding def(@Nullable final Object d) { this.defaultValue = d; return this; }

    public Binding range(@Nullable final NumberRange r) { this.range = r; return this; }

    public Binding range(final double min, final double max, final double step) { this.range = new NumberRange(min, max, step); return this; }

    public Binding choices(final List<Choice> c) { this.choices = c == null ? List.of() : List.copyOf(c); return this; }

    public Binding restart(final boolean r) { this.restart = r; return this; }

    public Binding enabledIf(final BooleanSupplier s) { this.enabled = s; return this; }

    public Binding action(final Runnable r) { this.action = r; return this; }

    public Binding action(final Component buttonLabel, final Runnable r) { this.actionLabel = buttonLabel; this.action = r; return this; }

    public Binding format(final Function<Object, Component> f) { this.format = f; return this; }

    /** Extra words the search index should match (file name, section, mod id). */
    public Binding searchWords(final String words) { this.extraSearch = words; return this; }

    // ------------------------------------------------------------------ OptionBinding

    @Override public String id() { return id; }
    @Override public Component label() { return label; }
    @Override public @Nullable Component tooltip() { return tooltip; }
    @Override public OptionType type() { return type; }
    @Override public @Nullable Object get() { return getter.get(); }
    @Override public void set(@Nullable final Object value) { setter.accept(value); }
    @Override public @Nullable Object defaultValue() { return defaultValue; }
    @Override public @Nullable NumberRange range() { return range; }
    @Override public List<Choice> choices() { return choices; }
    @Override public boolean requiresRestart() { return restart; }
    @Override public boolean enabled() { return enabled.getAsBoolean(); }
    @Override public @Nullable Runnable action() { return action; }
    @Override public Component actionLabel() { return actionLabel != null ? actionLabel : OptionBinding.super.actionLabel(); }

    @Override
    public Component valueText(@Nullable final Object value) {
        return format != null && value != null ? format.apply(value) : OptionValues.defaultText(this, value);
    }

    @Override
    public String searchText() {
        return OptionBinding.super.searchText() + (extraSearch == null ? "" : " " + extraSearch);
    }

    /**
     * A wrapper that overrides label/tooltip/range/choices/restart of another binding (curated pages).
     * Nulls keep the original.
     */
    public static OptionBinding override(final OptionBinding base, @Nullable final Component label, @Nullable final Component tooltip,
                                         @Nullable final NumberRange range, @Nullable final List<Choice> choices, @Nullable final Boolean restart) {
        return new OptionBinding() {
            @Override public String id() { return base.id(); }
            @Override public Component label() { return label != null ? label : base.label(); }
            @Override public @Nullable Component tooltip() { return tooltip != null ? tooltip : base.tooltip(); }
            @Override public OptionType type() {
                // A range on a STRING/unknown numeric turns it into a slider; choices turn a string into a dropdown.
                if (choices != null && !choices.isEmpty() && base.type() == OptionType.STRING) return OptionType.CHOICE;
                return base.type();
            }
            @Override public @Nullable Object get() { return base.get(); }
            @Override public void set(@Nullable final Object value) { base.set(value); }
            @Override public @Nullable Object defaultValue() { return base.defaultValue(); }
            @Override public @Nullable NumberRange range() { return range != null ? range : base.range(); }
            @Override public List<Choice> choices() {
                if (choices != null && !choices.isEmpty()) return choices;
                return base.choices();
            }
            @Override public boolean requiresRestart() { return restart != null ? restart : base.requiresRestart(); }
            @Override public boolean enabled() { return base.enabled(); }
            @Override public @Nullable Runnable action() { return base.action(); }
            @Override public Component actionLabel() { return base.actionLabel(); }
            @Override public Component valueText(@Nullable final Object value) { return base.valueText(value); }
            @Override public String searchText() {
                final List<String> parts = new ArrayList<>();
                parts.add(label().getString());
                if (tooltip() != null) parts.add(tooltip().getString());
                parts.add(base.searchText());
                return String.join(" ", parts);
            }
        };
    }
}
