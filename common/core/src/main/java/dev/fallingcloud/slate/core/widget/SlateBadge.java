package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Small pill: unread counts, tags, status chips. Static {@link #draw} for use inside list rows. */
public class SlateBadge extends SlateWidget {

    private int color;

    public SlateBadge(final int x, final int y, final Component text, final int color) {
        super(x, y, SlateDraw.width(text) + 8, 10, text);
        this.color = color;
        this.active = false;
    }

    public SlateBadge color(final int argb) { this.color = argb; return this; }

    public SlateBadge text(final Component t) { setMessage(t); setWidth(SlateDraw.width(t) + 8); return this; }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) { return false; }

    @Override
    public net.minecraft.client.gui.ComponentPath nextFocusPath(final net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
        return null;
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, getMessage(), getX(), getY(), Colors.scaleAlpha(color, effectiveAlpha()));
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, getMessage(), getX(), getY(), Colors.scaleAlpha(color, effectiveAlpha()));
    }

    /** Draws a pill at (x,y) with height 10; returns its width. */
    public static int draw(final GuiGraphics g, final Component text, final int x, final int y, final int color) {
        final int w = SlateDraw.width(text) + 8;
        final int fill = Theme.current().isVanilla() ? Colors.withAlpha(color, 0xC0) : Colors.withAlpha(color, 0x40);
        SlateDraw.pixelRound(g, x, y, w, 10, fill, 2);
        if (!Theme.current().isVanilla()) SlateDraw.outline(g, x, y, w, 10, Colors.withAlpha(color, 0x90), 2);
        final int fg = Theme.current().isVanilla() ? Colors.readableOn(color) : Colors.brighten(color, 0.35f);
        g.drawString(SlateDraw.font(), text, x + 4, y + 1, Colors.withAlpha(fg, Colors.alpha(color)), false);
        return w;
    }

    /** A solid count bubble (unread). */
    public static int drawCount(final GuiGraphics g, final int count, final int x, final int y) {
        final String s = count > 99 ? "99+" : Integer.toString(count);
        final int w = Math.max(10, SlateDraw.width(s) + 6);
        final int c = Theme.current().accent();
        SlateDraw.pixelRound(g, x, y, w, 10, c, 3);
        g.drawString(SlateDraw.font(), s, x + (w - SlateDraw.width(s)) / 2, y + 1, Theme.current().palette().accentText(), false);
        return w;
    }
}
