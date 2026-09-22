package dev.fallingcloud.slate.menu.client.title;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The large title-screen navigation button: icon + label, translucent so it reads over the panorama,
 * slides 4 px right and grows an accent bar on hover/focus (dark), or a full stone button (vanilla).
 */
public class NavButton extends SlateButton {

    public static final int HEIGHT = 24;

    private boolean danger;

    public NavButton(final int x, final int y, final int width, final Icon icon, final Component label, final Runnable onPress) {
        super(x, y, width, HEIGHT, label, onPress);
        this.icon = icon;
        this.iconSize = 14;
        leftAligned();
    }

    /** Red label (Quit). */
    public NavButton danger() { this.danger = true; return this; }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        final float hov = hover(), prs = press(), foc = focus();
        final float lift = Math.max(hov, foc);
        final int x = getX() + Math.round(lift * 4), y = getY() + enterOffset() + Math.round(prs), w = getWidth(), h = getHeight();

        int fill = Colors.lerp(Colors.withAlpha(p.surface(), 0xB4), Colors.withAlpha(p.surfaceHover(), 0xF0), lift);
        int border = Colors.lerp(Colors.withAlpha(p.border(), 0xB0), p.borderStrong(), lift);
        if (!this.active) { fill = Colors.withAlpha(p.surface(), 0x70); border = Colors.withAlpha(p.border(), 0x70); }
        fill = Colors.brighten(fill, -0.1f * prs);
        SlateDraw.shadow(g, x, y, w, h, 0.3f * a * (1 - prs));
        SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(fill, a), t.radius());
        SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(border, a), t.radius());
        final int barH = Math.round((h - 10) * lift);
        if (barH > 0) SlateDraw.rect(g, x + 2, y + (h - barH) / 2, 2, barH, Colors.scaleAlpha(danger ? p.danger() : p.accent(), a));
        SlateDraw.focusRing(g, x, y, w, h, foc * a);

        final int ic = this.active ? Colors.lerp(p.textMuted(), danger ? p.danger() : p.accent(), lift) : p.textDim();
        Icons.draw(g, icon, x + 9, y + (h - iconSize) / 2, iconSize, Colors.scaleAlpha(ic, a));
        final int fg = !this.active ? p.textDim() : danger ? Colors.lerp(p.text(), p.danger(), lift) : Colors.lerp(p.text(), 0xFFFFFFFF, lift * 0.3f);
        g.drawString(SlateDraw.font(), SlateDraw.truncate(getMessage(), w - iconSize - 24), x + 9 + iconSize + 7, y + (h - 9) / 2 + 1, Colors.scaleAlpha(fg, a), false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        final int x = getX(), y = getY() + enterOffset() + Math.round(press()), w = getWidth(), h = getHeight();
        SlateDraw.vanillaButton(g, x, y, w, h, Math.max(hover(), focus()), this.active, a);
        final int fg = !this.active ? 0xFFA0A0A0 : danger ? Theme.current().palette().danger() : 0xFFFFFFFF;
        Icons.draw(g, icon, x + 8, y + (h - iconSize) / 2, iconSize, Colors.scaleAlpha(fg, a));
        g.drawString(SlateDraw.font(), SlateDraw.truncate(getMessage(), w - iconSize - 22), x + 8 + iconSize + 6, y + (h - 9) / 2 + 1, Colors.scaleAlpha(fg, a), true);
    }
}
