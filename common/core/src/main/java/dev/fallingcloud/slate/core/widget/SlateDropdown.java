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
import net.minecraft.network.chat.Component;

/** A select box: shows the current value, opens a {@link MenuPopup} of options. Optional label prefix. */
public class SlateDropdown<T> extends SlateWidget {

    private final List<T> options;
    private final Function<T, Component> labeler;
    private T value;
    private final Consumer<T> onChange;
    private Component label = Component.empty();

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

    private Component display() {
        final Component v = value == null ? Component.literal("-") : labeler.apply(value);
        return label.getString().isEmpty() ? v : Component.empty().append(label).append(": ").append(v);
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        openMenu();
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible) return false;
        if (keyCode == 257 || keyCode == 32 || keyCode == 264) { openMenu(); return true; }
        return false;
    }

    private void openMenu() {
        final List<MenuPopup.Item> items = new ArrayList<>();
        for (final T opt : options) {
            final boolean cur = opt != null && opt.equals(value);
            items.add(MenuPopup.Item.checked(labeler.apply(opt), cur, () -> {
                value = opt;
                if (onChange != null) onChange.accept(opt);
            }));
        }
        Popups.open(new MenuPopup(getX(), getY() + getHeight() + 1, items, getWidth()));
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        final int fill = Colors.lerp(p.surface(), p.surfaceHover(), hover());
        final int border = Colors.lerp(Colors.lerp(p.border(), p.borderStrong(), hover()), p.accent(), focus());
        SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(fill, a), t.radius());
        SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(border, a), t.radius());
        final int fg = Colors.scaleAlpha(this.active ? p.text() : p.textDim(), a);
        g.drawString(SlateDraw.font(), SlateDraw.truncate(display(), w - 24), x + 6, y + (h - 9) / 2 + 1, fg, false);
        Icons.draw(g, Icon.CHEVRON_DOWN, x + w - 14, y + (h - 8) / 2, 8, Colors.scaleAlpha(p.textMuted(), a));
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        SlateDraw.vanillaButton(g, x, y, w, h, Math.max(hover(), focus()), this.active, a);
        final int fg = Colors.scaleAlpha(this.active ? 0xFFFFFFFF : 0xFFA0A0A0, a);
        g.drawString(SlateDraw.font(), SlateDraw.truncate(display(), w - 24), x + 6, y + (h - 9) / 2 + 1, fg, true);
        Icons.draw(g, Icon.CHEVRON_DOWN, x + w - 14, y + (h - 8) / 2, 8, fg);
    }
}
