package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * The button. Variants: PRIMARY (accent fill), SECONDARY (surface), GHOST (no fill until hover), DANGER.
 * Optional leading icon. Keyboard: Enter/Space activate.
 */
public class SlateButton extends SlateWidget {

    public enum Variant { PRIMARY, SECONDARY, GHOST, DANGER }

    public static final int HEIGHT = 20;
    public static final int HEIGHT_SMALL = 16;
    public static final int HEIGHT_LARGE = 26;

    @Nullable protected Icon icon;
    protected Variant variant = Variant.SECONDARY;
    protected Runnable onPress;
    protected boolean centered = true;
    protected int iconSize = 12;

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

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        if (onPress != null) onPress.run();
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible) return false;
        if (keyCode == 257 || keyCode == 32 || keyCode == 335) {        // enter, space, keypad enter
            this.playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            pressAnim.snap(1);
            pressAnim.set(0);
            if (onPress != null) onPress.run();
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
                border = Colors.withAlpha(p.danger(), 0xA0);
                fg = hov > 0.5f ? 0xFFFFFFFF : p.danger();
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
            fill = Colors.withAlpha(p.surface(), 0x80);
            border = Colors.withAlpha(p.border(), 0x80);
            fg = p.textDim();
        }
        // Press: darken and nudge 1 px down (pixel feel, no scaling).
        final int py = y + Math.round(prs);
        fill = Colors.brighten(fill, -0.12f * prs);

        if (variant != Variant.GHOST || hov > 0.01f) SlateDraw.shadow(g, x, py, w, h, 0.35f * a * (1 - prs));
        SlateDraw.pixelRound(g, x, py, w, h, Colors.scaleAlpha(fill, a), t.radius());
        SlateDraw.outline(g, x, py, w, h, Colors.scaleAlpha(border, a), t.radius());
        SlateDraw.focusRing(g, x, py, w, h, foc * a);

        drawContent(g, x, py, w, h, Colors.scaleAlpha(fg, a), false);
    }

    // ------------------------------------------------------------------ vanilla

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        final int py = y + Math.round(press());
        if (variant == Variant.GHOST && hover() < 0.01f && focus() < 0.01f) {
            drawContent(g, x, py, w, h, Colors.scaleAlpha(Theme.current().palette().textMuted(), a), true);
            return;
        }
        SlateDraw.vanillaButton(g, x, py, w, h, Math.max(hover(), focus()), this.active, a);
        int fg = this.active ? 0xFFFFFFFF : 0xFFA0A0A0;
        if (variant == Variant.PRIMARY && this.active) fg = Colors.lerp(0xFFFFFFFF, Theme.current().accent(), 0.35f);
        if (variant == Variant.DANGER && this.active) fg = Theme.current().palette().danger();
        drawContent(g, x, py, w, h, Colors.scaleAlpha(fg, a), true);
    }

    // ------------------------------------------------------------------ content

    protected void drawContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final int fg, final boolean shadow) {
        final int pad = 6;
        final int iconW = icon == null ? 0 : iconSize + (getMessage().getString().isEmpty() ? 0 : 4);
        final FormattedCharSequence text = SlateDraw.truncate(getMessage(), Math.max(0, w - pad * 2 - iconW));
        final int textW = getMessage().getString().isEmpty() ? 0 : SlateDraw.font().width(text);
        final int contentW = iconW + textW;
        int cx = centered ? x + (w - contentW) / 2 : x + pad;
        final int ty = y + (h - SlateDraw.lineHeight()) / 2 + 1;
        if (icon != null) {
            Icons.draw(g, icon, cx, y + (h - iconSize) / 2, iconSize, fg);
            cx += iconW;
        }
        if (textW > 0) g.drawString(SlateDraw.font(), text, cx, ty, fg, shadow);
    }
}
