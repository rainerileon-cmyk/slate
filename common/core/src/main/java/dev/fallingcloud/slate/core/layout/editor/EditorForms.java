package dev.fallingcloud.slate.core.layout.editor;

import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.layout.LayoutStore;
import dev.fallingcloud.slate.core.layout.action.ActionType.Arg;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateToggle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * Turns an {@link Arg} schema entry into a form widget: TEXT/NUMBER fields, BOOL toggles, CHOICE and
 * SCREEN_ID dropdowns, MULTILINE and ACTION_LIST boxes. Shared by element properties and action args.
 */
final class EditorForms {

    static final int FIELD_H = 20, MULTILINE_H = 56, ACTION_LIST_H = 44;

    static int height(final Arg.Kind kind) {
        return switch (kind) {
            case MULTILINE -> MULTILINE_H;
            case ACTION_LIST -> ACTION_LIST_H;
            default -> FIELD_H;
        };
    }

    /** BOOL toggles carry their own label; everything else gets a caption row above. */
    static boolean captioned(final Arg.Kind kind) {
        return kind != Arg.Kind.BOOL;
    }

    static AbstractWidget field(final int x, final int y, final int w, final Arg arg, final String value, final Consumer<String> onChange) {
        final String v = value == null ? "" : value;
        return switch (arg.kind()) {
            case BOOL -> new SlateToggle(x, y, w, arg.label(), Boolean.parseBoolean(v), b -> onChange.accept(Boolean.toString(b)));
            case NUMBER -> new EditorTextField(x, y, w, arg.label()).decimal().text(v).onChange(onChange);
            case MULTILINE -> new MultiLineField(x, y, w, MULTILINE_H, arg.label()).text(v).onChange(onChange);
            case ACTION_LIST -> new MultiLineField(x, y, w, ACTION_LIST_H, arg.label()).text(v)
                .placeholder(EditorText.t("props.action_list_hint")).onChange(onChange);
            case CHOICE -> {
                final List<String> opts = new ArrayList<>(arg.choices());
                if (!v.isEmpty() && !opts.contains(v)) opts.add(0, v);
                final String cur = opts.contains(v) ? v : opts.isEmpty() ? v : opts.get(0);
                yield new SlateDropdown<>(x, y, w, opts, cur, s -> Component.literal(s), onChange);
            }
            case SCREEN_ID -> new SlateDropdown<>(x, y, w, screenIds(v), v, EditorForms::screenLabel, onChange);
            default -> new EditorTextField(x, y, w, arg.label()).text(v).onChange(onChange);
        };
    }

    /** Custom screen ids (without the {@code custom:} prefix) that have a saved layout. */
    static List<String> customScreens() {
        final List<String> out = new ArrayList<>();
        try {
            for (final String id : LayoutStore.saved()) if (id.startsWith("custom:")) out.add(id.substring(7));
        } catch (final Exception ignored) {}
        out.sort(null);
        return out;
    }

    /** Every screen id the open_screen action can target: factories first, then custom screens, then known ids. */
    static List<String> screenIds(final String include) {
        final Set<String> ids = new LinkedHashSet<>();
        if (include != null && !include.isBlank()) ids.add(include);
        try { ids.addAll(CoreActions.SCREEN_FACTORIES.keySet()); } catch (final Exception ignored) {}
        for (final String c : customScreens()) ids.add("custom:" + c);
        try { ids.addAll(ScreenIds.known()); } catch (final Exception ignored) {}
        ids.remove("slate:custom");                       // the class id of custom screens; never a target
        return new ArrayList<>(ids);
    }

    static Component screenLabel(final String id) {
        if (id == null || id.isEmpty()) return Component.literal("-");
        if (id.startsWith("custom:")) return EditorText.t("props.custom_screen", id.substring(7));
        final String display = ScreenIds.display(id);
        final String path = id.substring(id.indexOf(':') + 1);
        return Component.literal(display.equals(path) ? id : display + "  (" + id + ")");
    }

    private EditorForms() {}
}
