package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.popup.ColorPickerPopup;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A swatch + hex text; click the swatch for the picker popup, or type a hex value. */
public class SlateColorField extends SlateWidget {

    private int color;
    private final IntConsumer onChange;
    private final SlateTextField hex;

    public SlateColorField(final int x, final int y, final int width, final int color, final IntConsumer onChange) {
        super(x, y, width, 20, Component.translatable("slate.color"));
        this.color = color | 0xFF000000;
        this.onChange = onChange;
        this.hex = new SlateTextField(x + 24, y, width - 24, Component.translatable("slate.color"));
        this.hex.setValue(Colors.toHex(this.color));
        this.hex.maxLength(9);
        this.hex.onChange(s -> {
            final int c = Colors.fromHex(s, Integer.MIN_VALUE);
            hex.setInvalid(c == Integer.MIN_VALUE);
            if (c != Integer.MIN_VALUE && c != this.color) { this.color = c; if (onChange != null) onChange.accept(c); }
        });
    }

    public int color() { return color; }

    public void setColor(final int argb) {
        color = argb | 0xFF000000;
        hex.setValue(Colors.toHex(color));
    }

    /** The inner text field, so screens can add it as a focusable child. */
    public SlateTextField hexField() { return hex; }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        if (mouseX < getX() + 22) {
            Popups.open(new ColorPickerPopup(getX(), getY() + 22, color, c -> {
                setColor(c);
                if (onChange != null) onChange.accept(c);
            }));
        }
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (hex.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        return hex.isFocused() ? hex.keyPressed(keyCode, scanCode, modifiers) : super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(final char c, final int modifiers) {
        return hex.isFocused() && hex.charTyped(c, modifiers);
    }

    @Override
    public void setFocused(final boolean focused) {
        super.setFocused(focused);
        if (!focused) hex.setFocused(false);
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        hex.setX(getX() + 24);
        hex.setY(getY() + 5);
        SlateDraw.pixelRound(g, getX(), getY(), 20, 20, color, t.radius());
        SlateDraw.outline(g, getX(), getY(), 20, 20, Colors.lerp(p.borderStrong(), p.text(), hover()), t.radius());
        hex.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        renderDark(g, mouseX, mouseY, partialTick);
    }
}
