package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A 1 px rule, optionally with a small muted caption in the line (section headers). */
public class SlateSeparator extends SlateWidget {

    private final boolean vertical;

    public SlateSeparator(final int x, final int y, final int length, final boolean vertical) {
        super(x, y, vertical ? 1 : length, vertical ? length : 1, Component.empty());
        this.vertical = vertical;
        this.active = false;
    }

    /** Horizontal rule with a caption. Height 10. */
    public SlateSeparator(final int x, final int y, final int width, final Component caption) {
        super(x, y, width, 10, caption);
        this.vertical = false;
        this.active = false;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) { return false; }

    @Override
    public net.minecraft.client.gui.ComponentPath nextFocusPath(final net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
        return null;
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, Theme.current().palette().border(), Theme.current().palette().textMuted(), false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, 0xFF6F6F6F, 0xFFA0A0A0, true);
    }

    private void draw(final GuiGraphics g, final int line, final int textColor, final boolean shadow) {
        final int lc = Colors.scaleAlpha(line, effectiveAlpha());
        if (vertical) { SlateDraw.vline(g, getX(), getY(), getHeight(), lc); return; }
        final String cap = getMessage().getString();
        if (cap.isEmpty()) { SlateDraw.hline(g, getX(), getY(), getWidth(), lc); return; }
        final int y = getY() + 4;
        g.drawString(SlateDraw.font(), getMessage(), getX(), getY(), Colors.scaleAlpha(textColor, effectiveAlpha()), shadow);
        final int tw = SlateDraw.width(getMessage()) + 6;
        SlateDraw.hline(g, getX() + tw, y, getWidth() - tw, lc);
    }
}
