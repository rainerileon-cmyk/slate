package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A text field on top of vanilla's {@link EditBox} (cursor, selection, clipboard, scrolling all inherited).
 * The EditBox runs unbordered and this class draws the frame, placeholder, leading icon and clear button
 * around it. The frame is {@code PAD} pixels larger than the EditBox on every side; hit-testing is expanded
 * to match so clicks in the padding still focus the field.
 */
public class SlateTextField extends EditBox {

    public static final int HEIGHT = 20;
    private static final int PAD_X = 6, PAD_Y = 5;

    private final Anim focusAnim = new Anim(0, 160, Ease.OUT_CUBIC);
    private final Anim hoverAnim = new Anim(0, 140, Ease.OUT_CUBIC);
    @Nullable private Icon icon;
    private boolean clearButton;
    @Nullable private Component placeholder;
    private boolean invalid;
    private Runnable onEnter;
    private Runnable onEscape;
    private final int frameX, frameY, frameW, frameH;

    /**
     * @param x,y,width,height the FRAME rectangle (the EditBox sits inside it)
     */
    public SlateTextField(final int x, final int y, final int width, final int height, final Component narration) {
        super(SlateDraw.font(), x + PAD_X, y + PAD_Y, Math.max(1, width - PAD_X * 2), Math.max(1, height - PAD_Y * 2), narration);
        this.frameX = x; this.frameY = y; this.frameW = width; this.frameH = height;
        setBordered(false);
        setMaxLength(256);
        setTextColor(Theme.current().text());
    }

    public SlateTextField(final int x, final int y, final int width, final Component narration) {
        this(x, y, width, HEIGHT, narration);
    }

    public SlateTextField placeholder(final Component text) { this.placeholder = text; return this; }

    public SlateTextField icon(@Nullable final Icon icon) {
        this.icon = icon;
        relayout();
        return this;
    }

    public SlateTextField clearButton(final boolean on) { this.clearButton = on; relayout(); return this; }

    public SlateTextField onChange(final Consumer<String> responder) { setResponder(responder); return this; }

    public SlateTextField onEnter(final Runnable r) { this.onEnter = r; return this; }

    public SlateTextField onEscape(final Runnable r) { this.onEscape = r; return this; }

    public SlateTextField maxLength(final int n) { setMaxLength(n); return this; }

    public SlateTextField text(final String value) { setValue(value == null ? "" : value); return this; }

    /** Red frame while true (validation). */
    public void setInvalid(final boolean invalid) { this.invalid = invalid; }

    private void relayout() {
        final int left = icon == null ? 0 : 16;
        final int right = clearButton ? 14 : 0;
        setX(frameX + PAD_X + left);
        setWidth(Math.max(1, frameW - PAD_X * 2 - left - right));
    }

    public int frameX() { return frameX; }
    public int frameY() { return frameY; }
    public int frameWidth() { return frameW; }
    public int frameHeight() { return frameH; }

    private boolean inFrame(final double mx, final double my) {
        return mx >= frameX && mx < frameX + frameW && my >= frameY && my < frameY + frameH;
    }

    private boolean onClear(final double mx, final double my) {
        return clearButton && !getValue().isEmpty() && mx >= frameX + frameW - 16 && mx < frameX + frameW && my >= frameY && my < frameY + frameH;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.visible || !this.active) return false;
        if (button == 0 && onClear(mouseX, mouseY)) {
            setValue("");
            setFocused(true);
            SlateSounds.tick();
            return true;
        }
        if (button == 0 && inFrame(mouseX, mouseY) && !super.isMouseOver(mouseX, mouseY)) {
            // Padding click: focus and put the cursor at the end.
            setFocused(true);
            moveCursorToEnd(false);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isMouseOver(final double mouseX, final double mouseY) {
        return this.active && this.visible && inFrame(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (isFocused()) {
            if ((keyCode == 257 || keyCode == 335) && onEnter != null) { onEnter.run(); return true; }
            if (keyCode == 256 && onEscape != null) { onEscape.run(); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        if (!this.visible) return;
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean hovered = inFrame(mouseX, mouseY);
        hoverAnim.set(hovered);
        focusAnim.set(isFocused());
        final float foc = focusAnim.get(), hov = hoverAnim.get();

        if (t.isVanilla()) {
            SlateDraw.vanillaTextField(g, frameX, frameY, frameW, frameH, isFocused());
            if (invalid) SlateDraw.outline(g, frameX, frameY, frameW, frameH, p.danger(), 0);
            setTextColor(0xFFE0E0E0);
        } else {
            final int fill = Colors.lerp(Colors.lerp(p.bg2(), p.surface(), hov), p.surface(), foc);
            final int border = invalid ? p.danger() : Colors.lerp(Colors.lerp(p.border(), p.borderStrong(), hov), p.accent(), foc);
            SlateDraw.pixelRound(g, frameX, frameY, frameW, frameH, fill, t.radius());
            SlateDraw.outline(g, frameX, frameY, frameW, frameH, border, t.radius());
            setTextColor(p.text());
        }
        if (icon != null) Icons.draw(g, icon, frameX + PAD_X, frameY + (frameH - 12) / 2, 12, Colors.lerp(p.textDim(), p.textMuted(), foc));
        if (clearButton && !getValue().isEmpty()) {
            final boolean over = onClear(mouseX, mouseY);
            Icons.draw(g, Icon.CLOSE, frameX + frameW - 14, frameY + (frameH - 8) / 2, 8, over ? p.text() : p.textDim());
        }
        if (placeholder != null && getValue().isEmpty() && !isFocused()) {
            g.drawString(SlateDraw.font(), SlateDraw.truncate(placeholder, getWidth()), getX(), getY(), p.textDim(), false);
        }
        super.renderWidget(g, mouseX, mouseY, partialTick);
    }
}
