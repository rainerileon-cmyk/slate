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

/** Static text. Styles pick colour and font; WRAP breaks lines inside the width. Not focusable. */
public class SlateLabel extends SlateWidget {

    public enum Style { HEADING, TITLE, BODY, MUTED, CAPTION }

    public enum Align { LEFT, CENTER, RIGHT }

    private Style style = Style.BODY;
    private Align align = Align.LEFT;
    private boolean wrap;
    private int color = 0;

    public SlateLabel(final int x, final int y, final int width, final Component text) {
        super(x, y, width, 10, text);
        this.active = false;
    }

    public SlateLabel(final int x, final int y, final Component text) {
        this(x, y, SlateDraw.width(text) + 2, text);
    }

    public SlateLabel style(final Style s) {
        this.style = s;
        setHeight(s == Style.HEADING ? 14 : s == Style.TITLE ? 12 : 10);
        return this;
    }

    public SlateLabel align(final Align a) { this.align = a; return this; }

    /** Wrap to the width; height grows to fit. */
    public SlateLabel wrap(final boolean wrap) { this.wrap = wrap; if (wrap) setHeight(lines().size() * 10); return this; }

    /** Explicit colour (ARGB) instead of the style's. */
    public SlateLabel color(final int argb) { this.color = argb; return this; }

    public SlateLabel text(final Component text) { setMessage(text); if (wrap) setHeight(lines().size() * 10); return this; }

    private List<FormattedCharSequence> lines() {
        return SlateDraw.font().split(displayText(), Math.max(1, getWidth()));
    }

    private Component displayText() {
        return style == Style.HEADING || style == Style.TITLE ? Fonts.heading(getMessage()) : getMessage();
    }

    private int fg() {
        if (color != 0) return color;
        final Palette p = Theme.current().palette();
        return switch (style) {
            case MUTED, CAPTION -> p.textMuted();
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
        draw(g, false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, true);
    }

    private void draw(final GuiGraphics g, final boolean shadow) {
        final int fg = Colors.scaleAlpha(fg(), effectiveAlpha());
        final int y = getY() + enterOffset();
        if (wrap) {
            int ly = y;
            for (final FormattedCharSequence s : lines()) {
                drawLine(g, s, ly, fg, shadow);
                ly += 10;
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
