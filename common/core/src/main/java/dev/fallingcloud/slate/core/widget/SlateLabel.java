package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Static text. Styles pick colour, font and line height; WRAP breaks lines inside the width and the
 * widget's height follows the number of lines ({@link #heightFor} measures without a widget).
 * Not focusable.
 */
public class SlateLabel extends SlateWidget {

    public enum Style {
        HEADING(14), TITLE(12), BODY(10), MUTED(10), CAPTION(10);

        public final int lineHeight;

        Style(final int lineHeight) { this.lineHeight = lineHeight; }

        public boolean heading() { return this == HEADING || this == TITLE; }
    }

    public enum Align { LEFT, CENTER, RIGHT }

    private Style style = Style.BODY;
    private Align align = Align.LEFT;
    private boolean wrap;
    private int color = 0;
    private boolean shadowOverride;
    private boolean shadow;

    public SlateLabel(final int x, final int y, final int width, final Component text) {
        super(x, y, width, Style.BODY.lineHeight, text);
        this.active = false;
    }

    public SlateLabel(final int x, final int y, final Component text) {
        this(x, y, SlateDraw.width(text) + 2, text);
    }

    public SlateLabel style(final Style s) {
        this.style = s;
        autoHeight();
        return this;
    }

    public Style style() { return style; }

    public SlateLabel align(final Align a) { this.align = a; return this; }

    /** Wrap to the width; height grows to fit. */
    public SlateLabel wrap(final boolean wrap) { this.wrap = wrap; autoHeight(); return this; }

    /** Explicit colour (ARGB) instead of the style's. */
    public SlateLabel color(final int argb) { this.color = argb; return this; }

    /** Force the text shadow on or off (default: off on the dark skin, on on vanilla). */
    public SlateLabel shadow(final boolean on) { this.shadowOverride = true; this.shadow = on; return this; }

    public SlateLabel text(final Component text) { setMessage(text); autoHeight(); return this; }

    @Override
    public void setWidth(final int width) {
        super.setWidth(width);
        if (wrap) autoHeight();
    }

    /** Line height for the current style. */
    public int lineHeight() { return style.lineHeight; }

    /** Number of lines the current text takes (1 unless wrapping). */
    public int lineCount() { return wrap ? Math.max(1, lines().size()) : 1; }

    /** Recomputes the height from the style and (if wrapping) the line count. */
    public SlateLabel autoHeight() {
        setHeight(lineCount() * style.lineHeight);
        return this;
    }

    /** Height a wrapped label of {@code text} needs at {@code width} in {@code style}. */
    public static int heightFor(final Component text, final int width, final Style style) {
        final Component t = style.heading() ? Fonts.heading(text) : text;
        return Math.max(1, SlateDraw.font().split(t, Math.max(1, width)).size()) * style.lineHeight;
    }

    private List<FormattedCharSequence> lines() {
        return SlateDraw.font().split(displayText(), Math.max(1, getWidth()));
    }

    private Component displayText() {
        return style.heading() ? Fonts.heading(getMessage()) : getMessage();
    }

    private int fg() {
        if (color != 0) return color;
        final Palette p = Theme.current().palette();
        return switch (style) {
            case MUTED -> p.textMuted();
            case CAPTION -> p.textDim();
            default -> p.text();
        };
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) { return false; }

    @Override
    public net.minecraft.client.gui.ComponentPath nextFocusPath(final net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
        return null;
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, shadowOverride ? shadow : false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, shadowOverride ? shadow : true);
    }

    private void draw(final GuiGraphics g, final boolean shadow) {
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final int fg = Colors.scaleAlpha(fg(), a);
        // Centre the 9 px glyph line inside the style's line height (headings get their extra space).
        final int lh = style.lineHeight;
        final int y = getY() + enterOffset() + Math.max(0, (lh - 9) / 2);
        if (wrap) {
            int ly = y;
            for (final FormattedCharSequence s : lines()) {
                drawLine(g, s, ly, fg, shadow);
                ly += lh;
            }
        } else {
            drawLine(g, SlateDraw.truncate(displayText(), getWidth()), y, fg, shadow);
        }
    }

    private void drawLine(final GuiGraphics g, final FormattedCharSequence s, final int y, final int fg, final boolean shadow) {
        final int w = SlateDraw.font().width(s);
        final int x = switch (align) {
            case CENTER -> getX() + (getWidth() - w) / 2;
            case RIGHT -> getX() + getWidth() - w;
            default -> getX();
        };
        g.drawString(SlateDraw.font(), s, x, y, fg, shadow);
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {
        out.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, getMessage());
    }
}
