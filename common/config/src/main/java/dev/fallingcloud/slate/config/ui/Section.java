package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.config.option.OptionBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A titled group of rows on an option page. Items are bindings (rendered as {@link OptionRow}s) or
 * custom widget factories (given the row width) for things like swatch rows and language lists.
 */
public final class Section {

    public record Item(@Nullable OptionBinding binding, @Nullable IntFunction<AbstractWidget> custom) {}

    public final String id;
    public final Component title;
    @Nullable public final Component description;
    public final List<Item> items = new ArrayList<>();
    public boolean collapsible = true;

    public Section(final String id, final Component title, @Nullable final Component description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    public static Section of(final String id, final Component title) {
        return new Section(id, title, null);
    }

    public static Section of(final String id, final Component title, final List<? extends OptionBinding> bindings) {
        final Section s = new Section(id, title, null);
        for (final OptionBinding b : bindings) s.add(b);
        return s;
    }

    public Section add(final OptionBinding b) {
        if (b != null) items.add(new Item(b, null));
        return this;
    }

    public Section addAll(final List<? extends OptionBinding> bs) {
        for (final OptionBinding b : bs) add(b);
        return this;
    }

    public Section custom(final IntFunction<AbstractWidget> factory) {
        items.add(new Item(null, factory));
        return this;
    }

    public Section fixed() { this.collapsible = false; return this; }

    public List<OptionBinding> bindings() {
        final List<OptionBinding> out = new ArrayList<>();
        for (final Item i : items) if (i.binding != null) out.add(i.binding);
        return out;
    }

    public boolean isEmpty() { return items.isEmpty(); }
}
