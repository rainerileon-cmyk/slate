package dev.fallingcloud.slate.core.layout.editor;

import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import dev.fallingcloud.slate.core.widget.popup.ColorPickerPopup;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Swatch + hex field for colour properties. The value is the raw text ({@code #RRGGBB},
 * {@code #AARRGGBB}, or empty = "theme default"); the swatch opens Slate's picker. Movable, so it lives
 * happily inside the properties panel.
 */
final class ColorRow extends SlateWidget {

    private static final int SWATCH = 20;

    private final EditorTextField hex;
    private final Consumer<String> onChange;
    private String value;

    ColorRow(final int x, final int y, final int w, final String value, final Consumer<String> onChange) {
        super(x, y, w, EditorTextField.H, Component.translatable("slate.color"));
        this.value = value == null ? "" : value;
        this.onChange = onChange;
        this.hex = new EditorTextField(x + SWATCH + 4, y, w - SWATCH - 4, Component.translatable("slate.color"));
        this.hex.setMaxLength(9);
        this.hex.text(this.value);
        this.hex.placeholder(EditorText.t("props.color_theme"));
        this.hex.onChange(s -> {
            final String v = s.trim();
            final boolean ok = v.isEmpty() || Colors.fromHex(v, Integer.MIN_VALUE) != Integer.MIN_VALUE;
            hex.setInvalid(!ok);
            if (ok && !v.equals(this.value)) { this.value = v; onChange.accept(v); }
        });
        silent();
    }

    EditorTextField field() { return hex; }

    private int swatchColor() {
        return value.isEmpty() ? Theme.current().surface() : Colors.fromHex(value, 0xFF000000);
    }

    private void layoutHex() {
        hex.setX(getX() + SWATCH + 4 + EditorTextField.PAD_X);
        hex.setY(getY() + EditorTextField.PAD_Y);
        hex.setWidth(Math.max(1, getWidth() - SWATCH - 4 - EditorTextField.PAD_X * 2));
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.visible || !this.active) return false;
        layoutHex();
        if (hex.mouseClicked(mouseX, mouseY, button)) { hex.setFocused(true); return true; }
        if (button == 0 && mouseX >= getX() && mouseX < getX() + SWATCH && mouseY >= getY() && mouseY < getY() + getHeight()) {
            hex.setFocused(false);
            Popups.open(new ColorPickerPopup(getX(), getY() + SWATCH + 2, swatchColor(), c -> {
                final String v = Colors.toHex(c);
                hex.text(v);                                            // fires onChange through the responder
            }));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        return hex.isFocused() && hex.keyPressed(keyCode, scanCode, modifiers);
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

    boolean textFocused() { return hex.isFocused(); }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, mouseX, mouseY, partialTick);
    }

    private void draw(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        layoutHex();
        final int c = swatchColor();
        // Checker under translucent colours so alpha is visible.
        g.fill(getX(), getY(), getX() + SWATCH, getY() + SWATCH, 0xFF9A9A9A);
        for (int yy = 0; yy < SWATCH; yy += 5) for (int xx = (yy / 5) % 2 * 5; xx < SWATCH; xx += 10) g.fill(getX() + xx, getY() + yy, getX() + Math.min(SWATCH, xx + 5), getY() + Math.min(SWATCH, yy + 5), 0xFF5A5A5A);
        SlateDraw.pixelRound(g, getX(), getY(), SWATCH, SWATCH, c, t.radius());
        SlateDraw.outline(g, getX(), getY(), SWATCH, SWATCH, Colors.lerp(p.borderStrong(), p.text(), hover()), t.radius());
        hex.render(g, mouseX, mouseY, partialTick);
    }
}
