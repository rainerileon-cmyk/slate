package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A pixel loading spinner: 8 dots around a ring, one lit at a time. Static {@link #draw} for rows. */
public class SlateSpinner extends SlateWidget {

    public SlateSpinner(final int x, final int y, final int size) {
        super(x, y, size, size, Component.translatable("slate.loading"));
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
        draw(g, getX(), getY(), getWidth(), Colors.scaleAlpha(Theme.current().accent(), effectiveAlpha()));
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, getX(), getY(), getWidth(), Colors.scaleAlpha(0xFFFFFFFF, effectiveAlpha()));
    }

    public static void draw(final GuiGraphics g, final int x, final int y, final int size, final int color) {
        final int dots = 8;
        final int lit = (int) ((Clock.nowMs() / 90) % dots);
        final float r = (size - 2) / 2f;
        final float cx = x + size / 2f, cy = y + size / 2f;
        final int dot = Math.max(1, size / 8);
        for (int i = 0; i < dots; i++) {
            final double ang = i * Math.PI * 2 / dots;
            final int px = Math.round(cx + (float) Math.cos(ang) * r - dot / 2f);
            final int py = Math.round(cy + (float) Math.sin(ang) * r - dot / 2f);
            final int d = (i - lit + dots) % dots;
            final float a = d == 0 ? 1f : d == 1 ? 0.6f : d == 2 ? 0.35f : 0.15f;
            g.fill(px, py, px + dot, py + dot, Colors.scaleAlpha(color, a));
        }
    }
}
