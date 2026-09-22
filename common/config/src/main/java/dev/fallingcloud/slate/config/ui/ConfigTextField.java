package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateSounds;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A text field whose frame IS the widget rectangle, so it can live inside scroll panels, rows, modals
 * and header bars that reposition their children. An unbordered vanilla {@link EditBox} does the text
 * work (cursor, selection, clipboard); this class draws the Slate frame, icon, placeholder and clear
 * button around it in both skins and forwards focus and input.
 */
public class ConfigTextField extends SlateWidget {

    public static final int HEIGHT = 20;
    private static final int PAD_X = 6, PAD_Y = 5;

    private final EditBox box;
    @Nullable private Icon icon;
    private boolean clearButton;
    @Nullable private Component placeholder;
    private boolean invalid;
    @Nullable private Runnable onEnter, onEscape;

    public ConfigTextField(final int x, final int y, final int width, final int height, final Component narration) {
        super(x, y, width, height, narration);
        box = new EditBox(SlateDraw.font(), 0, 0, 10, 10, narration);
        box.setBordered(false);
        box.setMaxLength(256);
        silent();
        layoutBox();
    }

    public ConfigTextField(final int x, final int y, final int width, final Component narration) {
        this(x, y, width, HEIGHT, narration);
    }

    // ------------------------------------------------------------------ fluent

    public ConfigTextField placeholder(@Nullable final Component text) { this.placeholder = text; return this; }

    public ConfigTextField icon(@Nullable final Icon icon) { this.icon = icon; layoutBox(); return this; }

    public ConfigTextField clearButton(final boolean on) { this.clearButton = on; layoutBox(); return this; }

    public ConfigTextField onChange(final Consumer<String> responder) { box.setResponder(responder); return this; }

    public ConfigTextField onEnter(@Nullable final Runnable r) { this.onEnter = r; return this; }

    public ConfigTextField onEscape(@Nullable final Runnable r) { this.onEscape = r; return this; }

    public ConfigTextField maxLength(final int n) { box.setMaxLength(n); return this; }

    public ConfigTextField text(@Nullable final String value) { box.setValue(value == null ? "" : value); return this; }

    public String getValue() { return box.getValue(); }

    public void setValue(@Nullable final String value) { box.setValue(value == null ? "" : value); }

    public void setInvalid(final boolean invalid) { this.invalid = invalid; }

    public void setEditable(final boolean editable) { box.setEditable(editable); }

    public EditBox box() { return box; }

    // ------------------------------------------------------------------ geometry

    private void layoutBox() {
        final int left = icon == null ? 0 : 16;
        final int right = clearButton ? 14 : 0;
        box.setX(getX() + PAD_X + left);
        box.setY(getY() + PAD_Y);
        box.setWidth(Math.max(1, getWidth() - PAD_X * 2 - left - right));
        box.setHeight(Math.max(1, getHeight() - PAD_Y * 2));
    }

    @Override public void setX(final int x) { super.setX(x); layoutBox(); }
    @Override public void setY(final int y) { super.setY(y); layoutBox(); }
    @Override public void setWidth(final int w) { super.setWidth(w); layoutBox(); }
    @Override public void setHeight(final int h) { super.setHeight(h); layoutBox(); }

    private boolean onClear(final double mx, final double my) {
        return clearButton && !box.getValue().isEmpty() && mx >= getX() + getWidth() - 16 && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
    }

    // ------------------------------------------------------------------ focus & input

    @Override
    public void setFocused(final boolean focused) {
        super.setFocused(focused);
        box.setFocused(focused);
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        layoutBox();
        if (onClear(mouseX, mouseY)) {
            box.setValue("");
            SlateSounds.tick();
            return;
        }
        if (!box.isFocused()) box.setFocused(true);
        if (mouseX >= box.getX() && mouseX < box.getX() + box.getWidth()) box.mouseClicked(mouseX, Math.max(box.getY(), Math.min(mouseY, box.getY() + box.getHeight() - 1)), 0);
        else box.moveCursorToEnd(false);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible || !box.isFocused()) return false;
        if ((keyCode == 257 || keyCode == 335) && onEnter != null) { onEnter.run(); return true; }
        if (keyCode == 256 && onEscape != null) { onEscape.run(); return true; }
        return box.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(final char c, final int modifiers) {
        return this.active && this.visible && box.isFocused() && box.charTyped(c, modifiers);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        final float foc = box.isFocused() ? 1f : 0f, hov = hover();
        final int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        final int fill = Colors.lerp(Colors.lerp(p.bg2(), p.surface(), hov), p.surface(), foc);
        final int border = invalid ? p.danger() : Colors.lerp(Colors.lerp(p.border(), p.borderStrong(), hov), p.accent(), foc);
        SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(fill, a), t.radius());
        SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(border, a), t.radius());
        box.setTextColor(this.active ? p.text() : p.textDim());
        drawInner(g, mouseX, mouseY, partialTick, p, false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Palette p = Theme.current().palette();
        final int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        SlateDraw.vanillaTextField(g, x, y, w, h, box.isFocused());
        if (invalid) SlateDraw.outline(g, x, y, w, h, p.danger(), 0);
        box.setTextColor(this.active ? 0xFFE0E0E0 : 0xFFA0A0A0);
        drawInner(g, mouseX, mouseY, partialTick, p, true);
    }

    private void drawInner(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final Palette p, final boolean vanilla) {
        layoutBox();
        final int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        if (icon != null) Icons.draw(g, icon, x + PAD_X, y + (h - 12) / 2, 12, box.isFocused() ? p.textMuted() : p.textDim());
        if (clearButton && !box.getValue().isEmpty()) {
            Icons.draw(g, Icon.CLOSE, x + w - 14, y + (h - 8) / 2, 8, onClear(mouseX, mouseY) ? p.text() : p.textDim());
        }
        if (placeholder != null && box.getValue().isEmpty() && !box.isFocused()) {
            g.drawString(SlateDraw.font(), SlateDraw.truncate(placeholder, box.getWidth()), box.getX(), box.getY(), vanilla ? 0xFF808080 : p.textDim(), false);
        }
        box.render(g, mouseX, mouseY, partialTick);
    }
}
