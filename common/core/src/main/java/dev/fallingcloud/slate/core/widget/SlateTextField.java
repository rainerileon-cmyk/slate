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
 *
 * <p>The widget rectangle IS the frame: {@code getX/getY/getWidth/getHeight} describe the drawn box, so
 * layout containers (Flow, scroll panels, cards, modals) move and size it like any other widget. The
 * EditBox runs unbordered and its text is shifted into the padded slot by translating the pose while it
 * renders (and un-shifting mouse coordinates), which keeps vanilla's cursor, selection and scrolling
 * logic untouched. Inner width = frame width minus padding, icon and clear button.</p>
 */
public class SlateTextField extends EditBox {

    public static final int HEIGHT = 20;
    private static final int PAD_X = 6;
    private static final int ICON_SLOT = 16, CLEAR_SLOT = 12;

    private final Anim focusAnim = new Anim(0, 160, Ease.OUT_CUBIC);
    private final Anim hoverAnim = new Anim(0, 140, Ease.OUT_CUBIC);
    @Nullable private Icon icon;
    private boolean clearButton;
    @Nullable private Component placeholder;
    private boolean invalid;
    private Runnable onEnter;
    private Runnable onEscape;

    /**
     * @param x,y,width,height the FRAME rectangle (the text sits inside it)
     */
    public SlateTextField(final int x, final int y, final int width, final int height, final Component narration) {
        super(SlateDraw.font(), x, y, Math.max(1, width), Math.max(1, height), narration);
        setBordered(false);
        setMaxLength(256);
        setTextColor(Theme.current().text());
    }

    public SlateTextField(final int x, final int y, final int width, final Component narration) {
        this(x, y, width, HEIGHT, narration);
    }

    public SlateTextField placeholder(final Component text) { this.placeholder = text; return this; }

    public SlateTextField icon(@Nullable final Icon icon) { this.icon = icon; return this; }

    public SlateTextField clearButton(final boolean on) { this.clearButton = on; return this; }

    public SlateTextField onChange(final Consumer<String> responder) { setResponder(responder); return this; }

    public SlateTextField onEnter(final Runnable r) { this.onEnter = r; return this; }

    public SlateTextField onEscape(final Runnable r) { this.onEscape = r; return this; }

    public SlateTextField maxLength(final int n) { setMaxLength(n); return this; }

    public SlateTextField text(final String value) { setValue(value == null ? "" : value); return this; }

    /** Red frame while true (validation). */
    public void setInvalid(final boolean invalid) { this.invalid = invalid; }

    public boolean isInvalid() { return invalid; }

    // The frame is the widget rect; these stay for callers written against the old API.
    public int frameX() { return getX(); }
    public int frameY() { return getY(); }
    public int frameWidth() { return getWidth(); }
    public int frameHeight() { return getHeight(); }

    private int leftInset() { return PAD_X + (icon == null ? 0 : ICON_SLOT); }

    private int rightInset() { return PAD_X + (clearButton ? CLEAR_SLOT : 0); }

    /** Y shift that centres the 9 px text line in the frame. */
    private int textDy() { return (getHeight() - 9) / 2 + 1; }

    /** Width available to the text: vanilla reads this for display scrolling and click-to-cursor. */
    @Override
    public int getInnerWidth() {
        return Math.max(1, getWidth() - leftInset() - rightInset());
    }

    @Override
    public int getScreenX(final int charNum) {
        return super.getScreenX(charNum) + leftInset();
    }

    private boolean onClear(final double mx, final double my) {
        return clearButton && !getValue().isEmpty()
            && mx >= getX() + getWidth() - PAD_X - CLEAR_SLOT && mx < getX() + getWidth()
            && my >= getY() && my < getY() + getHeight();
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        // Vanilla maps mouseX - getX() to a character; shift it by the text slot's left edge.
        super.onClick(mouseX - leftInset(), mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.visible || !this.active) return false;
        if (button == 0 && onClear(mouseX, mouseY)) {
            setValue("");
            SlateSounds.tick();
            return true;                       // consumed: the screen focuses this field
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (isFocused() && this.active) {
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
        final int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        final boolean hovered = this.active && this.isHovered() && g.containsPointInScissor(mouseX, mouseY);
        hoverAnim.set(hovered);
        focusAnim.set(isFocused());
        final float foc = focusAnim.get(), hov = hoverAnim.get();

        if (t.isVanilla()) {
            SlateDraw.vanillaTextField(g, x, y, w, h, isFocused());
            if (invalid) SlateDraw.outline(g, x, y, w, h, p.danger(), 0);
            setTextColor(this.active ? 0xFFE0E0E0 : 0xFFA0A0A0);
        } else {
            int fill = Colors.lerp(Colors.lerp(p.bg2(), p.surface(), hov), p.surface(), foc);
            int border = invalid ? p.danger() : Colors.lerp(Colors.lerp(p.border(), p.borderStrong(), hov), p.accent(), foc);
            if (!this.active) { fill = Colors.withAlpha(p.bg2(), 0x80); border = Colors.withAlpha(p.border(), 0x80); }
            SlateDraw.pixelRound(g, x, y, w, h, fill, t.radius());
            SlateDraw.outline(g, x, y, w, h, border, t.radius());
            setTextColor(this.active ? p.text() : p.textDim());
        }
        if (icon != null) Icons.draw(g, icon, x + PAD_X, y + (h - 12) / 2, 12, Colors.lerp(p.textDim(), p.textMuted(), Math.max(foc, hov)));
        if (clearButton && !getValue().isEmpty()) {
            final boolean over = onClear(mouseX, mouseY);
            Icons.draw(g, Icon.CLOSE, x + w - PAD_X - 10, y + (h - 8) / 2, 8, over ? p.text() : p.textDim());
        }
        // Vanilla draws the text at (getX(), getY()) when unbordered: shift it into the padded slot.
        final int dx = leftInset(), dy = textDy();
        g.pose().pushPose();
        g.pose().translate(dx, dy, 0);
        if (placeholder != null && getValue().isEmpty() && !isFocused()) {
            g.drawString(SlateDraw.font(), SlateDraw.truncate(placeholder, getInnerWidth()), x, y, p.textDim(), false);
        }
        super.renderWidget(g, mouseX - dx, mouseY - dy, partialTick);
        g.pose().popPose();
    }
}
