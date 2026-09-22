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

/**
 * A swatch + hex text; click the swatch (or press Enter with the field focused and the hex untouched)
 * for the picker popup, or type a hex value. The hex field is an inner widget positioned by this one.
 */
public class SlateColorField extends SlateWidget {

    private static final int SWATCH = 20, GAP = 4;

    private int color;
    private final IntConsumer onChange;
    private final SlateTextField hex;
    private boolean pickerOpen;

    public SlateColorField(final int x, final int y, final int width, final int color, final IntConsumer onChange) {
        super(x, y, width, 20, Component.translatable("slate.color"));
        this.color = color | 0xFF000000;
        this.onChange = onChange;
        this.hex = new SlateTextField(x + SWATCH + GAP, y, width - SWATCH - GAP, Component.translatable("slate.color"));
        this.hex.setValue(Colors.toHex(this.color));
        this.hex.maxLength(9);
        this.hex.onChange(s -> {
            final int c = Colors.fromHex(s, Integer.MIN_VALUE);
            hex.setInvalid(c == Integer.MIN_VALUE);
            if (c != Integer.MIN_VALUE && (c | 0xFF000000) != this.color) {
                this.color = c | 0xFF000000;
                if (onChange != null) onChange.accept(this.color);
            }
        });
    }

    public int color() { return color; }

    public void setColor(final int argb) {
        color = argb | 0xFF000000;
        hex.setValue(Colors.toHex(color));
        hex.setInvalid(false);
    }

    /** The inner text field, so screens can add it as a focusable child. */
    public SlateTextField hexField() { return hex; }

    private boolean overSwatch(final double mx, final double my) {
        return mx >= getX() && mx < getX() + SWATCH && my >= getY() && my < getY() + getHeight();
    }

    private void openPicker() {
        if (pickerOpen) return;
        pickerOpen = true;
        Popups.open(new ColorPickerPopup(getX(), getY() + getHeight() + 2, color, c -> {
            setColor(c);
            if (onChange != null) onChange.accept(color);
        }) {
            @Override public void onClose() { pickerOpen = false; }
        });
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        if (overSwatch(mouseX, mouseY)) openPicker();
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.active || !this.visible) return false;
        if (hex.mouseClicked(mouseX, mouseY, button)) { hex.setFocused(true); return true; }
        if (overSwatch(mouseX, mouseY) && button == 0) {
            hex.setFocused(false);
            return super.mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible) return false;
        if (hex.isFocused() && hex.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == 257 || keyCode == 335 || (keyCode == 32 && !hex.isFocused())) { openPicker(); return true; }
        return false;
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

    private void place() {
        hex.setX(getX() + SWATCH + GAP);
        hex.setY(getY());
        hex.setWidth(Math.max(1, getWidth() - SWATCH - GAP));
        hex.setHeight(getHeight());
        hex.active = this.active;
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        place();
        final int x = getX(), y = getY() + enterOffset(), h = getHeight();
        final boolean over = this.isHovered() && overSwatch(mouseX, mouseY);
        final float lift = Math.max(over ? hover() : 0f, pickerOpen ? 1f : 0f);
        if (!t.isVanilla()) SlateDraw.shadow(g, x, y, SWATCH, h, 0.3f * a);
        SlateDraw.pixelRound(g, x, y, SWATCH, h, Colors.scaleAlpha(color, a), t.radius());
        // Inner highlight so the swatch reads as a button, not a flat patch.
        SlateDraw.hline(g, x + 2, y + 1, SWATCH - 4, Colors.scaleAlpha(Colors.withAlpha(0xFFFFFF, 0x30), a));
        final int border = t.isVanilla() ? Colors.lerp(0xFF000000, 0xFFFFFFFF, lift) : Colors.lerp(p.borderStrong(), p.text(), lift);
        SlateDraw.outline(g, x, y, SWATCH, h, Colors.scaleAlpha(border, a), t.radius());
        if (isFocused() && !hex.isFocused()) SlateDraw.focusRing(g, x, y, SWATCH, h, a);
        hex.setAlpha(a);
        hex.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        renderDark(g, mouseX, mouseY, partialTick);
    }
}
