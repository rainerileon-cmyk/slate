package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import dev.fallingcloud.slate.core.widget.popup.ColorPickerPopup;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Swatch + hex field that survives being moved; click the swatch (or press Enter on it) for Core's picker popup. */
public class ConfigColorField extends SlateWidget {

    private static final int SWATCH = 20;

    private int color;
    private final IntConsumer onChange;
    private final ConfigTextField hex;

    public ConfigColorField(final int x, final int y, final int width, final int color, final IntConsumer onChange) {
        super(x, y, width, 20, Component.translatable("slate.color"));
        this.color = color | 0xFF000000;
        this.onChange = onChange;
        this.hex = new ConfigTextField(x + SWATCH + 4, y, Math.max(40, width - SWATCH - 4), Component.translatable("slate.color"));
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
        if (!hex.getValue().equalsIgnoreCase(Colors.toHex(color))) hex.setValue(Colors.toHex(color));
        hex.setInvalid(false);
    }

    private void layout() {
        hex.setX(getX() + SWATCH + 4);
        hex.setY(getY());
        hex.setWidth(Math.max(40, getWidth() - SWATCH - 4));
    }

    private void openPicker() {
        Popups.open(new ColorPickerPopup(getX(), getY() + 22, color, c -> {
            setColor(c);
            if (onChange != null) onChange.accept(c);
        }));
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.active || !this.visible) return false;
        layout();
        if (hex.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && contains(mouseX, mouseY) && mouseX < getX() + SWATCH + 2) {
            hex.setFocused(false);
            openPicker();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (hex.box().isFocused()) return hex.keyPressed(keyCode, scanCode, modifiers);
        if (this.active && this.visible && (keyCode == 257 || keyCode == 32)) { openPicker(); return true; }
        return false;
    }

    @Override
    public boolean charTyped(final char c, final int modifiers) {
        return hex.charTyped(c, modifiers);
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
        layout();
        SlateDraw.pixelRound(g, getX(), getY(), SWATCH, SWATCH, color, t.radius());
        SlateDraw.outline(g, getX(), getY(), SWATCH, SWATCH, Colors.lerp(p.borderStrong(), p.text(), hover()), t.radius());
        SlateDraw.focusRing(g, getX(), getY(), SWATCH, SWATCH, (isFocused() && !hex.box().isFocused() ? 1f : 0f) * effectiveAlpha());
        hex.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        layout();
        SlateDraw.pixelRound(g, getX(), getY(), SWATCH, SWATCH, color, 0);
        SlateDraw.outline(g, getX(), getY(), SWATCH, SWATCH, hover() > 0.5f || isFocused() ? 0xFFFFFFFF : 0xFF000000, 0);
        hex.render(g, mouseX, mouseY, partialTick);
    }
}
