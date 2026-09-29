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
 *
 * <p>On a page every section is its own top tab, unless sections share a {@link #tab(String, Component) tab}:
 * then that tab holds all of them, shown as small secondary tabs or as headers (the page decides, see
 * {@link OptionPageBase#pills}).</p>
 */
public final class Section {

    public record Item(@Nullable OptionBinding binding, @Nullable IntFunction<AbstractWidget> custom) {}

    public final String id;
    public final Component title;
    @Nullable public final Component description;
    public final List<Item> items = new ArrayList<>();
    /** Only matters for sections shown under headers inside a tab (key categories): the header folds. */
    public boolean collapsible = true;
    /** The top tab this section belongs to; null = a tab of its own. */
    @Nullable public String tabId;
    @Nullable public Component tabTitle;
    /** Which of its rows count as advanced: each row for itself (default), all of them, or none of them. */
    public Level level = Level.PER_ROW;

    /** How a section answers "is this row advanced?" for the category / Advanced split. */
    public enum Level { PER_ROW, BASIC, ADVANCED }

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

    /** Every row of this section is advanced (shown in the Advanced category only). */
    public Section advanced() { this.level = Level.ADVANCED; return this; }

    /** Every row of this section stays on its category page, whatever the bindings say (accessibility, curated pages). */
    public Section basic() { this.level = Level.BASIC; return this; }

    /** Whether {@code item} belongs to the Advanced category, by the section's level or the binding's own flag. */
    public boolean isAdvanced(final Item item) {
        return switch (level) {
            case ADVANCED -> true;
            case BASIC -> false;
            case PER_ROW -> item.binding() != null && item.binding().advanced();
        };
    }

    /** A copy with the same identity, tab, description and folding, holding only the items {@code keep} accepts. */
    public Section filtered(final java.util.function.Predicate<Item> keep) {
        final Section s = new Section(id, title, description);
        s.collapsible = collapsible;
        s.tabId = tabId;
        s.tabTitle = tabTitle;
        s.level = level;
        for (final Item i : items) if (keep.test(i)) s.items.add(i);
        return s;
    }

    /** Put this section into the top tab {@code id} (sections with the same tab id share it). */
    public Section tab(final String id, final Component title) {
        this.tabId = id;
        this.tabTitle = title;
        return this;
    }

    public String tabKey() { return tabId != null ? tabId : id; }

    public Component tabLabel() { return tabTitle != null ? tabTitle : title; }

    public List<OptionBinding> bindings() {
        final List<OptionBinding> out = new ArrayList<>();
        for (final Item i : items) if (i.binding != null) out.add(i.binding);
        return out;
    }

    public boolean isEmpty() { return items.isEmpty(); }
}
