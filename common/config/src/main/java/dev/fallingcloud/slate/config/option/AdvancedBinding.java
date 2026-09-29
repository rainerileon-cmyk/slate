package dev.fallingcloud.slate.config.option;

import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Flags an existing binding as advanced without touching it (Sodium's options and other bindings that are not a
 * {@link Binding} cannot be marked in place). Everything else delegates.
 */
public final class AdvancedBinding implements OptionBinding {

    private final OptionBinding base;

    private AdvancedBinding(final OptionBinding base) {
        this.base = base;
    }

    /** {@code base} as an advanced option; a binding that already is one comes back as is. */
    public static OptionBinding of(final OptionBinding base) {
        return base.advanced() ? base : new AdvancedBinding(base);
    }

    public OptionBinding base() { return base; }

    @Override public String id() { return base.id(); }
    @Override public Component label() { return base.label(); }
    @Override public @Nullable Component tooltip() { return base.tooltip(); }
    @Override public OptionType type() { return base.type(); }
    @Override public @Nullable Object get() { return base.get(); }
    @Override public void set(@Nullable final Object value) { base.set(value); }
    @Override public @Nullable Object defaultValue() { return base.defaultValue(); }
    @Override public @Nullable NumberRange range() { return base.range(); }
    @Override public List<Choice> choices() { return base.choices(); }
    @Override public boolean requiresRestart() { return base.requiresRestart(); }
    @Override public boolean enabled() { return base.enabled(); }
    @Override public boolean advanced() { return true; }
    @Override public @Nullable Runnable action() { return base.action(); }
    @Override public Component actionLabel() { return base.actionLabel(); }
    @Override public dev.fallingcloud.slate.core.gfx.Icon actionIcon() { return base.actionIcon(); }
    @Override public Component valueText(@Nullable final Object value) { return base.valueText(value); }
    @Override public String searchText() { return base.searchText(); }
}
