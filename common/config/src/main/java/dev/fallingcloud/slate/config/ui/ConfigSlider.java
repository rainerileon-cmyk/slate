package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.widget.SlateSlider;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Core's slider with the label owned by the row: on the vanilla skin the handle text is just the value
 * ("120 fps") instead of ": 120 fps", and the dark skin is unchanged.
 */
public class ConfigSlider extends SlateSlider {

    private final double min, max;
    private final DoubleFunction<String> format;

    public ConfigSlider(final int x, final int y, final int width, final double min, final double max, final double step,
                        final double value, final DoubleFunction<String> format, final DoubleConsumer onChange) {
        super(x, y, width, Component.empty(), min, max, step, value, format, onChange);
        this.min = min;
        this.max = max;
        this.format = format;
        compact(true);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = 20;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.setColor(1, 1, 1, a);
        final boolean hl = hover() > 0.5f || focus() > 0.5f;
        g.blitSprite(hl ? SlateDraw.SLIDER_HIGHLIGHTED : SlateDraw.SLIDER, x, y, w, h);
        final float fraction = max <= min ? 0 : (float) ((value() - min) / (max - min));
        final int kx = x + Math.round((w - 8) * Math.max(0, Math.min(1, fraction)));
        g.blitSprite(hl ? SlateDraw.SLIDER_HANDLE_HIGHLIGHTED : SlateDraw.SLIDER_HANDLE, kx, y, 8, h);
        g.setColor(1, 1, 1, 1);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        final int fg = Colors.scaleAlpha(this.active ? 0xFFFFFFFF : 0xFFA0A0A0, a);
        SlateDraw.textCentered(g, Component.literal(format.apply(value())), x + w / 2, y + 6, fg);
    }
}
