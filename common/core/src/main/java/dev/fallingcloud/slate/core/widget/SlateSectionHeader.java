package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A collapsible section title inside a list or scroll panel: chevron, heading, an optional row count and a rule
 * running to the right edge. Enter/Space toggles; a null {@code onToggle} makes it a plain caption. The one header
 * every module uses (the Config module's option pages and file editor, Menu's server groups). Height 20.
 */
public class SlateSectionHeader extends SlateWidget {

    public static final int HEIGHT = 20;

    private boolean collapsed;
    private final int count;
    @Nullable private final Runnable onToggle;
    private final Anim chevron = new Anim(0, 160, Ease.OUT_CUBIC);
    private final int depth;

    /**
     * @param count   rows under the header, drawn dim after the title ({@code <= 0}: none)
     * @param depth   nesting level; each level indents by 8 px
     */
    public SlateSectionHeader(final int x, final int y, final int width, final Component title, final int count, final boolean collapsed,
                              @Nullable final Runnable onToggle, final int depth) {
        super(x, y, width, HEIGHT, title);
        this.count = count;
        this.collapsed = collapsed;
        this.onToggle = onToggle;
        this.depth = depth;
        this.chevron.snap(collapsed ? 1 : 0);
        if (onToggle == null) this.active = false;
    }

    public SlateSectionHeader(final int x, final int y, final int width, final Component title, final int count, final boolean collapsed, @Nullable final Runnable onToggle) {
        this(x, y, width, title, count, collapsed, onToggle, 0);
    }

    public boolean collapsed() { return collapsed; }

    private void toggle() {
        if (onToggle == null) return;
        collapsed = !collapsed;
        chevron.set(collapsed ? 1 : 0);
        SlateSounds.tick();
        onToggle.run();
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        toggle();
    }

    @Override
    public void playDownSound(final net.minecraft.client.sounds.SoundManager handler) {}

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (this.active && this.visible && (keyCode == 257 || keyCode == 32 || keyCode == 335)) { toggle(); return true; }
        return false;
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        final int x = getX() + depth * 8, y = getY() + enterOffset(), w = getWidth() - depth * 8;
        if (hover() > 0.01f) SlateDraw.pixelRound(g, x, y, w, HEIGHT, Colors.scaleAlpha(Colors.withAlpha(p.surfaceHover(), 0x90), a * hover()), t.radius());
        int tx = x + 4;
        if (onToggle != null) {
            final Icon ic = chevron.get() > 0.5f ? Icon.CHEVRON_RIGHT : Icon.CHEVRON_DOWN;
            Icons.draw(g, ic, tx, y + (HEIGHT - 10) / 2, 10, Colors.scaleAlpha(p.textMuted(), a));
            tx += 14;
        }
        final Component title = Fonts.heading(getMessage());
        g.drawString(SlateDraw.font(), title, tx, y + (HEIGHT - 9) / 2 + 1, Colors.scaleAlpha(p.text(), a), false);
        tx += SlateDraw.width(title) + 6;
        if (count > 0) {
            final String c = Integer.toString(count);
            g.drawString(SlateDraw.font(), c, tx, y + (HEIGHT - 9) / 2 + 1, Colors.scaleAlpha(p.textDim(), a), false);
            tx += SlateDraw.width(c) + 8;
        }
        SlateDraw.hline(g, tx, y + HEIGHT / 2, Math.max(0, x + w - 4 - tx), Colors.scaleAlpha(p.border(), a));
        SlateDraw.focusRing(g, x, y, w, HEIGHT, focus() * a);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        final int x = getX() + depth * 8, y = getY() + enterOffset(), w = getWidth() - depth * 8;
        int tx = x + 4;
        final int fg = Colors.scaleAlpha(hover() > 0.5f || focus() > 0.5f ? 0xFFFFFFA0 : 0xFFFFFFFF, a);
        if (onToggle != null) {
            Icons.draw(g, chevron.get() > 0.5f ? Icon.CHEVRON_RIGHT : Icon.CHEVRON_DOWN, tx, y + (HEIGHT - 10) / 2, 10, fg);
            tx += 14;
        }
        final Component title = Fonts.heading(getMessage());
        g.drawString(SlateDraw.font(), title, tx, y + (HEIGHT - 9) / 2 + 1, fg, true);
        tx += SlateDraw.width(title) + 6;
        if (count > 0) {
            final String c = Integer.toString(count);
            g.drawString(SlateDraw.font(), c, tx, y + (HEIGHT - 9) / 2 + 1, Colors.scaleAlpha(0xFFA0A0A0, a), true);
            tx += SlateDraw.width(c) + 8;
        }
        SlateDraw.hline(g, tx, y + HEIGHT / 2, Math.max(0, x + w - 4 - tx), Colors.scaleAlpha(0xFF6F6F6F, a));
        if (focus() > 0.5f) SlateDraw.outline(g, x, y, w, HEIGHT, Colors.scaleAlpha(0xFFFFFFFF, a), 0);
    }
}
