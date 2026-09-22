package dev.fallingcloud.slate.core.gfx;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.theme.Colors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Draws {@link Icon}s from the atlas, tinted. Glyphs are white on transparent so tinting works. */
public final class Icons {

    public static final ResourceLocation ATLAS = Slate.id("textures/gui/icons.png");

    /** Draw at native 16 px. */
    public static void draw(final GuiGraphics g, final Icon icon, final int x, final int y, final int color) {
        draw(g, icon, x, y, Icon.CELL, color);
    }

    /** Draw scaled to {@code size} px (8, 12, 16, 24, 32 look right; others blur). */
    public static void draw(final GuiGraphics g, final Icon icon, final int x, final int y, final int size, final int color) {
        final float a = Colors.alpha(color) / 255f;
        if (a <= 0.004f) return;
        RenderSystem.enableBlend();
        g.setColor(Colors.red(color) / 255f, Colors.green(color) / 255f, Colors.blue(color) / 255f, a);
        g.blit(ATLAS, x, y, size, size, icon.u(), icon.v(), Icon.CELL, Icon.CELL,
            Icon.COLUMNS * Icon.CELL, Icon.rows() * Icon.CELL);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private Icons() {}
}
