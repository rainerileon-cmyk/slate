package dev.fallingcloud.slate.menu.client;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A collapsible section title inside a scroll panel: chevron, caption, count, rule. Height 18. */
public class SectionHeader extends SlateButton {

    public static final int HEIGHT = 18;

    private final int count;
    private final boolean collapsed;

    public SectionHeader(final int x, final int y, final int w, final Component label, final int count, final boolean collapsed, final Runnable onToggle) {
        super(x, y, w, HEIGHT, label, onToggle);
        this.count = count;
        this.collapsed = collapsed;
        this.variant = Variant.GHOST;
        silent();
    }

    private void draw(final GuiGraphics g, final boolean van) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        final int fg = Colors.scaleAlpha(van ? Colors.lerp(0xFFC0C0C0, 0xFFFFFFFF, hover()) : Colors.lerp(p.textMuted(), p.text(), hover()), a);
        Icons.draw(g, collapsed ? Icon.CHEVRON_RIGHT : Icon.CHEVRON_DOWN, x + 2, y + (h - 8) / 2, 8, fg);
        final Component text = count >= 0 ? Component.empty().append(getMessage()).append(" · " + count) : getMessage();
        g.drawString(SlateDraw.font(), text, x + 14, y + (h - 9) / 2 + 1, fg, van);
        final int tw = SlateDraw.width(text) + 20;
        SlateDraw.hline(g, x + tw, y + h / 2, Math.max(0, w - tw), Colors.scaleAlpha(van ? 0xFF6F6F6F : p.border(), a));
        SlateDraw.focusRing(g, x, y, w, h, focus() * a);
    }

    @Override protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) { draw(g, false); }

    @Override protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) { draw(g, true); }
}
