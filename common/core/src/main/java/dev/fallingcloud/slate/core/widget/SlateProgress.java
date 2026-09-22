package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A progress bar (0..1) that animates toward its value; {@code indeterminate()} shows a sweeping segment. */
public class SlateProgress extends SlateWidget {

    private final Anim value = new Anim(0, 300, Ease.OUT_CUBIC);
    private boolean indeterminate;
    private int color = 0;

    public SlateProgress(final int x, final int y, final int width, final int height) {
        super(x, y, width, height, Component.empty());
        this.active = false;
    }

    public SlateProgress set(final float v) { value.set(Math.max(0, Math.min(1, v))); return this; }

    public SlateProgress snap(final float v) { value.snap(Math.max(0, Math.min(1, v))); return this; }

    public SlateProgress indeterminate(final boolean on) { this.indeterminate = on; return this; }

    public SlateProgress color(final int argb) { this.color = argb; return this; }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) { return false; }

    @Override
    public net.minecraft.client.gui.ComponentPath nextFocusPath(final net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
        return null;
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, Theme.current().palette().surfaceActive());
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, 0xFF000000);
    }

    private void draw(final GuiGraphics g, final int track) {
        final Palette p = Theme.current().palette();
        final float a = effectiveAlpha();
        final int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        final int fill = color != 0 ? color : p.accent();
        SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(track, a), 1);
        if (indeterminate) {
            final float t = (Clock.nowMs() % 1400) / 1400f;
            final int segW = Math.max(8, w / 3);
            final int sx = x + Math.round((w + segW) * t) - segW;
            SlateDraw.scissor(g, x, y, w, h);
            SlateDraw.pixelRound(g, sx, y, segW, h, Colors.scaleAlpha(fill, a), 1);
            SlateDraw.unscissor(g);
        } else {
            final int fw = Math.round(w * value.get());
            if (fw > 0) SlateDraw.pixelRound(g, x, y, fw, h, Colors.scaleAlpha(fill, a), 1);
        }
    }
}
