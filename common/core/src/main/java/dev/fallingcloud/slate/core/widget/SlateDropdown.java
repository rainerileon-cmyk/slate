package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A select box: shows the current value, opens a {@link MenuPopup} of options. Optional label prefix.
 * Keyboard: Enter/Space/Down open the menu, Left/Right cycle the value in place.
 */
public class SlateDropdown<T> extends SlateWidget {

    private final List<T> options;
    private final Function<T, Component> labeler;
    private T value;
    private final Consumer<T> onChange;
    private Component label = Component.empty();
    private boolean open;

    public SlateDropdown(final int x, final int y, final int width, final List<T> options, final T value,
                         final Function<T, Component> labeler, final Consumer<T> onChange) {
        super(x, y, width, 20, Component.empty());
        this.options = new ArrayList<>(options);
        this.labeler = labeler;
        this.value = value;
        this.onChange = onChange;
    }

    /** Draw "Label: value" instead of just the value. */
    public SlateDropdown<T> label(final Component label) { this.label = label; return this; }

    public T value() { return value; }

    public SlateDropdown<T> setValue(final T v) { this.value = v; return this; }

    public void setOptions(final List<T> opts) { options.clear(); options.addAll(opts); }

    public List<T> options() { return options; }

    private Component display() {
        final Component v = value == null ? Component.literal("-") : labeler.apply(value);
        return label.getString().isEmpty() ? v : Component.empty().append(label).append(": ").append(v);
    }

    private void choose(final T opt, final boolean sound) {
        if (opt == null ? value == null : opt.equals(value)) return;
        value = opt;
        if (sound) SlateSounds.tick();
        if (onChange != null) onChange.accept(opt);
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        openMenu();
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible) return false;
        if (keyCode == 257 || keyCode == 32 || keyCode == 335 || keyCode == 264) { openMenu(); return true; }
        final int i = options.indexOf(value);
        if (keyCode == 263 && !options.isEmpty()) { choose(options.get(Math.max(0, i - 1)), true); return true; }
        if (keyCode == 262 && !options.isEmpty()) { choose(options.get(Math.min(options.size() - 1, i + 1)), true); return true; }
        return false;
    }

    private void openMenu() {
        if (open) return;
        final List<MenuPopup.Item> items = new ArrayList<>();
        int current = -1;
        for (int i = 0; i < options.size(); i++) {
            final T opt = options.get(i);
            final boolean cur = opt != null && opt.equals(value);
            if (cur) current = i;
            items.add(MenuPopup.Item.checked(labeler.apply(opt), cur, () -> choose(opt, false)));
        }
        final MenuPopup menu = new MenuPopup(getX(), getY() + getHeight() + 1, items, getWidth()) {
            @Override public void onClose() { open = false; }
        };
        if (current >= 0) menu.highlight(current);
        open = true;
        Popups.open(menu);
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        final float lift = Math.max(hover(), open ? 1f : 0f);
        int fill = Colors.lerp(p.surface(), p.surfaceHover(), lift);
        int border = Colors.lerp(Colors.lerp(p.border(), p.borderStrong(), lift), p.accent(), Math.max(focus(), open ? 1f : 0f));
        if (!this.active) { fill = Colors.withAlpha(p.surface(), 0x80); border = Colors.withAlpha(p.border(), 0x80); }
        SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(fill, a), t.radius());
        SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(border, a), t.radius());
        final int fg = Colors.scaleAlpha(this.active ? p.text() : p.textDim(), a);
        g.drawString(SlateDraw.font(), SlateDraw.truncate(display(), w - 24), x + 6, SlateDraw.textY(y, h), fg, false);
        Icons.draw(g, open ? Icon.CHEVRON_UP : Icon.CHEVRON_DOWN, x + w - 14, y + (h - 8) / 2, 8, Colors.scaleAlpha(this.active ? p.textMuted() : p.textDim(), a));
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        SlateDraw.vanillaButton(g, x, y, w, h, this.active ? Math.max(hover(), Math.max(focus(), open ? 1f : 0f)) : 0f, this.active, a);
        final int fg = Colors.scaleAlpha(this.active ? 0xFFFFFFFF : 0xFFA0A0A0, a);
        g.drawString(SlateDraw.font(), SlateDraw.truncate(display(), w - 24), x + 6, SlateDraw.textY(y, h), fg, true);
        Icons.draw(g, open ? Icon.CHEVRON_UP : Icon.CHEVRON_DOWN, x + w - 14, y + (h - 8) / 2, 8, fg);
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.button", display()));
        if (this.active) {
            out.add(NarratedElementType.USAGE, Component.translatable(this.isFocused() ? "narration.button.usage.focused" : "narration.button.usage.hovered"));
        }
    }
}
