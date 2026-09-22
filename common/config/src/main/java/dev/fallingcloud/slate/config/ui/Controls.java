package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.config.option.Choice;
import dev.fallingcloud.slate.config.option.KeyBinding;
import dev.fallingcloud.slate.config.option.NumberRange;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.option.OptionValues;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateColorField;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateKeybindButton;
import dev.fallingcloud.slate.core.widget.SlateSlider;
import dev.fallingcloud.slate.core.widget.SlateTextField;
import dev.fallingcloud.slate.core.widget.SlateToggle;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Builds the right Slate control for a binding and knows how to push a fresh value back into it. */
public final class Controls {

    public static final int WIDTH = 150;

    /** @param onChange receives the new value; the caller applies it to the binding. */
    @Nullable
    public static AbstractWidget create(final OptionBinding b, final int x, final int y, final int w, final Consumer<Object> onChange) {
        final Object v = b.get();
        switch (b.type()) {
            case BOOLEAN -> {
                return new SlateToggle(x + w - SlateToggle.SWITCH_W, y, SlateToggle.SWITCH_W, Component.empty(), OptionValues.asBoolean(v, false), onChange::accept);
            }
            case INT, DOUBLE -> {
                final NumberRange r = b.range();
                final boolean integer = b.type() == OptionType.INT;
                if (r != null && r.isBounded()) {
                    final double step = integer ? Math.max(1, Math.round(r.effectiveStep())) : r.effectiveStep();
                    final SlateSlider s = new SlateSlider(x, y, w, Component.empty(), r.min(), r.max(), step, OptionValues.asDouble(v, r.min()),
                        d -> b.valueText(integer ? (Object) Math.round(d) : (Object) d).getString(),
                        d -> onChange.accept(integer ? (Object) Math.round(d) : (Object) d)).compact(true);
                    return s;
                }
                final SlateTextField f = new SlateTextField(x, y, w, Component.empty());
                f.setValue(v == null ? "" : integer ? Long.toString(OptionValues.asLong(v, 0)) : OptionValues.formatDouble(OptionValues.asDouble(v, 0)));
                f.maxLength(64);
                f.onChange(s -> {
                    final String t = s.trim();
                    boolean ok;
                    try {
                        if (integer) Long.parseLong(t); else Double.parseDouble(t);
                        ok = true;
                    } catch (final NumberFormatException e) { ok = false; }
                    f.setInvalid(!ok);
                    if (ok) ApplyQueue.later("field:" + b.id(), 400, () -> onChange.accept(integer ? (Object) Long.parseLong(t) : (Object) Double.parseDouble(t)));
                });
                return f;
            }
            case CHOICE -> {
                final List<Choice> choices = b.choices();
                Choice cur = null;
                final String id = OptionValues.asString(v);
                for (final Choice c : choices) if (c.id().equals(id)) { cur = c; break; }
                return new SlateDropdown<>(x, y, w, choices, cur, Choice::label, c -> onChange.accept(c.id()));
            }
            case STRING -> {
                final SlateTextField f = new SlateTextField(x, y, w, Component.empty());
                f.maxLength(4096);
                f.setValue(OptionValues.asString(v));
                f.onChange(s -> ApplyQueue.later("field:" + b.id(), 500, () -> onChange.accept(s)));
                return f;
            }
            case COLOR -> {
                return new SlateColorField(x, y, w, OptionValues.asColor(v, 0xFFFFFFFF), c -> onChange.accept(c));
            }
            case LIST -> {
                final List<String> items = OptionValues.asList(v);
                return new SlateButton(x, y, w, Component.translatable("slate_config.row.edit_list", items.size()),
                    () -> ListEditor.open(b.label(), OptionValues.asList(b.get()), onChange::accept)).icon(Icon.LIST);
            }
            case KEYBIND -> {
                if (b instanceof KeyBinding kb) {
                    return new SlateKeybindButton(x, y, w, kb.mapping(), m -> onChange.accept(m.saveString()));
                }
                return null;
            }
            case ACTION -> {
                final Runnable r = b.action();
                return new SlateButton(x, y, w, b.actionLabel(), r == null ? () -> {} : r).icon(Icon.EXTERNAL);
            }
            default -> { return null; }
        }
    }

    /** Push the binding's current value into an existing control (after reset / preset apply). */
    public static void refresh(final OptionBinding b, @Nullable final AbstractWidget control) {
        if (control == null) return;
        final Object v = b.get();
        if (control instanceof SlateToggle t) t.setValue(OptionValues.asBoolean(v, false));
        else if (control instanceof SlateSlider s) s.setValue(OptionValues.asDouble(v, 0));
        else if (control instanceof SlateDropdown<?> d) {
            @SuppressWarnings("unchecked") final SlateDropdown<Choice> dd = (SlateDropdown<Choice>) d;
            final String id = OptionValues.asString(v);
            for (final Choice c : b.choices()) if (c.id().equals(id)) { dd.setValue(c); break; }
        } else if (control instanceof SlateColorField c) c.setColor(OptionValues.asColor(v, 0xFFFFFFFF));
        else if (control instanceof SlateKeybindButton k) k.refresh();
        else if (control instanceof SlateTextField f) {
            final String text = b.type() == OptionType.INT ? Long.toString(OptionValues.asLong(v, 0))
                : b.type() == OptionType.DOUBLE ? OptionValues.formatDouble(OptionValues.asDouble(v, 0)) : OptionValues.asString(v);
            if (!f.getValue().equals(text)) f.setValue(text);
            f.setInvalid(false);
        } else if (control instanceof SlateButton btn && b.type() == OptionType.LIST) {
            btn.setMessage(Component.translatable("slate_config.row.edit_list", OptionValues.asList(v).size()));
        }
        control.active = b.enabled();
    }

    private Controls() {}
}
