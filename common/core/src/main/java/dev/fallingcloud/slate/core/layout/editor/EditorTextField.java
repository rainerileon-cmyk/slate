package dev.fallingcloud.slate.core.layout.editor;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
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
 * A Slate-styled single-line field whose frame follows the widget position, so it works inside a
 * scrolling panel ({@code SlateTextField} pins its frame where it was constructed). The EditBox is the
 * text area; the frame is {@code PAD} larger on every side and is what hit-testing uses.
 */
final class EditorTextField extends EditBox {

    static final int PAD_X = 6, PAD_Y = 5, H = 20;

    private final Anim focusAnim = new Anim(0, 160, Ease.OUT_CUBIC);
    private final Anim hoverAnim = new Anim(0, 140, Ease.OUT_CUBIC);
    @Nullable private Component placeholder;
    private boolean invalid;
    @Nullable private Runnable onEnter;

    /** {@code x, y, width} describe the FRAME (height {@link #H}); the box sits inside it. */
    EditorTextField(final int x, final int y, final int width, final Component narration) {
        super(SlateDraw.font(), x + PAD_X, y + PAD_Y, Math.max(1, width - PAD_X * 2), H - PAD_Y * 2, narration);
        setBordered(false);
        setMaxLength(2048);
        setTextColor(Theme.current().text());
    }

    EditorTextField text(final String v) { setValue(v == null ? "" : v); return this; }

    EditorTextField onChange(final Consumer<String> c) { setResponder(c); return this; }

    EditorTextField onEnter(final Runnable r) { this.onEnter = r; return this; }

    EditorTextField placeholder(final Component c) { this.placeholder = c; return this; }

    /** Integers only (optional sign). */
    EditorTextField integer() { setFilter(s -> s.matches("-?\\d{0,6}")); return this; }

    /** Decimal numbers (optional sign and fraction). */
    EditorTextField decimal() { setFilter(s -> s.matches("-?\\d{0,7}(\\.\\d{0,4})?")); return this; }

    void setInvalid(final boolean invalid) { this.invalid = invalid; }

    int frameX() { return getX() - PAD_X; }
    int frameY() { return getY() - PAD_Y; }
    int frameW() { return getWidth() + PAD_X * 2; }
    int frameH() { return H; }

    private boolean inFrame(final double mx, final double my) {
        return mx >= frameX() && mx < frameX() + frameW() && my >= frameY() && my < frameY() + frameH();
    }

    @Override
    public boolean isMouseOver(final double mouseX, final double mouseY) {
        return this.active && this.visible && inFrame(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.visible || !this.active || button != 0 || !inFrame(mouseX, mouseY)) return false;
        if (!super.isMouseOver(mouseX, mouseY)) {                 // padding click: cursor to the end
            moveCursorToEnd(false);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (isFocused() && EditorKeys.isEnter(keyCode) && onEnter != null) { onEnter.run(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        if (!this.visible) return;
        final Theme t = Theme.current();
        final Palette p = t.palette();
        hoverAnim.set(inFrame(mouseX, mouseY));
        focusAnim.set(isFocused());
        final float foc = focusAnim.get(), hov = hoverAnim.get();
        final int fx = frameX(), fy = frameY(), fw = frameW(), fh = frameH();
        if (t.isVanilla()) {
            SlateDraw.vanillaTextField(g, fx, fy, fw, fh, isFocused());
            if (invalid) SlateDraw.outline(g, fx, fy, fw, fh, p.danger(), 0);
            setTextColor(0xFFE0E0E0);
        } else {
            final int fill = Colors.lerp(Colors.lerp(p.bg2(), p.surface(), hov), p.surface(), foc);
            final int border = invalid ? p.danger() : Colors.lerp(Colors.lerp(p.border(), p.borderStrong(), hov), p.accent(), foc);
            SlateDraw.pixelRound(g, fx, fy, fw, fh, fill, t.radius());
            SlateDraw.outline(g, fx, fy, fw, fh, border, t.radius());
            setTextColor(p.text());
        }
        if (placeholder != null && getValue().isEmpty() && !isFocused()) {
            g.drawString(SlateDraw.font(), SlateDraw.truncate(placeholder, getWidth()), getX(), getY(), p.textDim(), false);
        }
        super.renderWidget(g, mouseX, mouseY, partialTick);
    }
}
