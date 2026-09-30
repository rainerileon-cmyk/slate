package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * The button. Variants: PRIMARY (accent fill), SECONDARY (surface), GHOST (no fill until hover), DANGER.
 * Optional leading icon. Keyboard: Enter/Space activate. Labels that do not fit are truncated with an
 * ellipsis, or scroll like vanilla's when {@link #scrollLongLabels()} is set.
 */
public class SlateButton extends SlateWidget {

    public enum Variant { PRIMARY, SECONDARY, GHOST, DANGER }

    public static final int HEIGHT = 20;
    public static final int HEIGHT_SMALL = 16;
    public static final int HEIGHT_LARGE = 26;
    /** Horizontal padding between the face edge and the content. */
    public static final int PAD = 6;

    @Nullable protected Icon icon;
    protected Variant variant = Variant.SECONDARY;
    protected Runnable onPress;
    protected boolean centered = true;
    protected int iconSize = 12;
    protected boolean scrolling;

    public SlateButton(final int x, final int y, final int width, final int height, final Component label, final Runnable onPress) {
        super(x, y, width, height, label);
        this.onPress = onPress;
    }

    public SlateButton(final int x, final int y, final int width, final Component label, final Runnable onPress) {
        this(x, y, width, HEIGHT, label, onPress);
    }

    public SlateButton variant(final Variant v) { this.variant = v; return this; }

    public SlateButton icon(@Nullable final Icon icon) { this.icon = icon; return this; }

    public SlateButton iconSize(final int size) { this.iconSize = size; return this; }

    /** Left-align the content instead of centring (menus, sidebars). */
    public SlateButton leftAligned() { this.centered = false; return this; }

    public SlateButton onPress(final Runnable r) { this.onPress = r; return this; }

    /** Long labels slide back and forth (vanilla behaviour) instead of being cut with an ellipsis. */
    public SlateButton scrollLongLabels() { this.scrolling = true; return this; }

    @Override
    public SlateButton tip(final Component tooltip) { super.tip(tooltip); return this; }

    @Override
    public SlateButton tip(final List<Component> lines) { super.tip(lines); return this; }

    public Variant variant() { return variant; }

    @Nullable public Icon icon() { return icon; }

    /** The preferred width for the current label + icon. */
    public int preferredWidth() {
        final int text = getMessage().getString().isEmpty() ? 0 : SlateDraw.width(getMessage());
        final int ic = icon == null ? 0 : iconSize + (text > 0 ? 4 : 0);
        return text + ic + PAD * 2;
    }

    /** Programmatic activation with the same feedback as a click. */
    public void activate() {
        if (!this.active || !this.visible || isLocked()) return;
        this.playDownSound(Minecraft.getInstance().getSoundManager());
        flashPress();
        if (onPress != null) onPress.run();
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        if (isLocked()) return;
        super.onClick(mouseX, mouseY);
        if (onPress != null) onPress.run();
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible) return false;
        if (keyCode == 257 || keyCode == 32 || keyCode == 335) {        // enter, space, keypad enter
            activate();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ dark

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final float hov = hover(), prs = press(), foc = focus();
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();

        int fill, border, fg;
        switch (variant) {
            case PRIMARY -> {
                fill = Colors.lerp(p.accent(), p.accentHover(), hov);
                border = Colors.brighten(fill, 0.15f);
                fg = p.accentText();
            }
            case DANGER -> {
                fill = Colors.lerp(Colors.withAlpha(p.danger(), 0x30), p.danger(), hov);
                border = Colors.lerp(Colors.withAlpha(p.danger(), 0xA0), Colors.brighten(p.danger(), 0.2f), hov);
                fg = Colors.lerp(p.danger(), 0xFFFFFFFF, hov);
            }
            case GHOST -> {
                fill = Colors.lerp(0x00000000, p.surfaceHover(), hov);
                border = Colors.lerp(0x00000000, p.border(), hov);
                fg = Colors.lerp(p.textMuted(), p.text(), hov);
            }
            default -> {
                fill = Colors.lerp(p.surface(), p.surfaceHover(), hov);
                border = Colors.lerp(p.border(), p.borderStrong(), hov);
                fg = Colors.lerp(p.text(), 0xFFFFFFFF, hov * 0.3f);
            }
        }
        if (!this.active) {
            fill = variant == Variant.GHOST ? 0 : Colors.withAlpha(p.surface(), 0x80);
            border = variant == Variant.GHOST ? 0 : Colors.withAlpha(p.border(), 0x80);
            fg = p.textDim();
        } else if (isLocked()) {
            // Locked: the face stays (it is hoverable, its tooltip explains), the content greys out, a lock marks it.
            fill = Colors.lerp(Colors.withAlpha(p.surface(), 0x80), Colors.withAlpha(p.surfaceHover(), 0xA0), hov);
            border = Colors.lerp(Colors.withAlpha(p.border(), 0x80), p.border(), hov);
            fg = Colors.lerp(p.textDim(), p.textMuted(), hov);
        }
        // Press: darken and nudge 1 px down (pixel feel, no scaling).
        final int py = y + Math.round(prs);
        fill = Colors.brighten(fill, -0.12f * prs);

        if (this.active && (variant != Variant.GHOST || hov > 0.01f)) SlateDraw.shadow(g, x, py, w, h, 0.35f * a * (1 - prs) * (variant == Variant.GHOST ? hov : 1f));
        SlateDraw.pixelRound(g, x, py, w, h, Colors.scaleAlpha(fill, a), t.radius());
        SlateDraw.outline(g, x, py, w, h, Colors.scaleAlpha(border, a), t.radius());
        SlateDraw.focusRing(g, x, py, w, h, foc * a);

        drawContent(g, x, py, w, h, Colors.scaleAlpha(fg, a), false);
        if (isLocked()) drawLock(g, x, py, w, h, Colors.scaleAlpha(fg, a));
    }

    // ------------------------------------------------------------------ vanilla

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final Palette p = Theme.current().palette();
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        final int py = y + Math.round(press());
        final float lift = Math.max(hover(), focus());
        if (variant == Variant.GHOST) {
            // Text-only until hovered/focused, then the stone face fades in underneath.
            if (lift > 0.01f) SlateDraw.vanillaButton(g, x, py, w, h, lift, this.active, a * lift);
            final int fg = this.active ? Colors.lerp(0xFFE0E0E0, 0xFFFFFFFF, lift) : 0xFFA0A0A0;
            drawContent(g, x, py, w, h, Colors.scaleAlpha(fg, a), true);
            return;
        }
        SlateDraw.vanillaButton(g, x, py, w, h, this.active ? lift : 0f, this.active && !isLocked(), a);
        int fg = this.active && !isLocked() ? 0xFFFFFFFF : 0xFFA0A0A0;
        if (variant == Variant.PRIMARY && this.active) fg = Colors.lerp(0xFFFFFFFF, p.accent(), 0.35f);
        if (variant == Variant.DANGER && this.active) fg = Colors.lerp(0xFFFF6A6A, 0xFFFFFFFF, lift * 0.5f);
        drawContent(g, x, py, w, h, Colors.scaleAlpha(fg, a), true);
        if (isLocked()) drawLock(g, x, py, w, h, Colors.scaleAlpha(fg, a));
    }

    /** The size of the lock glyph for a button of height {@code h}. */
    protected static int lockSize(final int h) { return h >= 20 ? 8 : 6; }

    /** The lock glyph of a locked button: at the right edge, vertically centred, in the content colour. */
    protected void drawLock(final GuiGraphics g, final int x, final int y, final int w, final int h, final int color) {
        final int s = lockSize(h);
        Icons.draw(g, Icon.LOCK, x + w - s - PAD + 1, y + (h - s) / 2, s, color);
    }

    // ------------------------------------------------------------------ content

    protected void drawContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final int fg, final boolean shadow) {
        final boolean hasText = !getMessage().getString().isEmpty();
        final int iconW = icon == null ? 0 : iconSize + (hasText ? 4 : 0);
        final int lockW = isLocked() ? lockSize(h) + 4 : 0;                 // the lock glyph keeps its own room
        final int avail = Math.max(0, w - PAD * 2 - iconW - lockW);
        final int fullW = hasText ? SlateDraw.width(getMessage()) : 0;
        final boolean overflow = fullW > avail;
        final FormattedCharSequence text = hasText ? (scrolling ? getMessage().getVisualOrderText() : SlateDraw.truncate(getMessage(), avail)) : null;
        final int textW = text == null ? 0 : Math.min(avail, SlateDraw.width(text));
        final int contentW = iconW + textW;
        int cx = centered ? x + (w - lockW - contentW) / 2 : x + PAD;
        final int ty = SlateDraw.textY(y, h);
        if (icon != null) {
            Icons.draw(g, icon, cx, y + (h - iconSize) / 2, iconSize, fg);
            cx += iconW;
        }
        if (text == null) return;
        if (scrolling && overflow) SlateDraw.drawScrollingText(g, text, cx, ty, avail, fg, shadow);
        else g.drawString(SlateDraw.font(), text, cx, ty, fg, shadow);
    }
}
