package dev.fallingcloud.slate.core.client.media;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.media.MediaCache;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateSpinner;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Drawing helpers for {@link MediaCache} entries: fitted frames (GIFs animate) and loading/failed states. */
public final class MediaDraw {

    /** Where an image was actually drawn. */
    public record Drawn(int x, int y, int w, int h) {
        public boolean contains(final double mx, final double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    /** The fitted size of {@code (w,h)} inside {@code (maxW,maxH)}, never upscaled past {@code allowUpscale}. */
    public static int[] fit(final int w, final int h, final int maxW, final int maxH, final boolean allowUpscale) {
        if (w <= 0 || h <= 0) return new int[] { 1, 1 };
        float f = Math.min((float) maxW / w, (float) maxH / h);
        if (!allowUpscale) f = Math.min(1f, f);
        return new int[] { Math.max(1, Math.round(w * f)), Math.max(1, Math.round(h * f)) };
    }

    /** Draws the current frame stretched to exactly (x,y,w,h). */
    public static void drawFrame(final GuiGraphics g, final MediaCache.Entry e, final int x, final int y, final int w, final int h, final float alpha) {
        if (!e.ready() || e.frames.isEmpty() || e.width <= 0) return;
        final MediaCache.Frame f = MediaCache.currentFrame(e.frames);
        RenderSystem.enableBlend();
        g.setColor(1, 1, 1, alpha);
        g.blit(f.texture(), x, y, w, h, 0, 0, e.width, e.height, e.width, e.height);
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /**
     * Draws the entry fitted into the box (centred when {@code center}); draws a loading/failed placeholder
     * otherwise. Returns the image rect when an image was drawn.
     */
    @Nullable
    public static Drawn drawFit(final GuiGraphics g, final MediaCache.Entry e, final int x, final int y, final int maxW, final int maxH,
                                final float alpha, final boolean center, final boolean allowUpscale) {
        if (!e.ready() || e.frames.isEmpty() || e.width <= 0) {
            drawPlaceholder(g, e, x, y, maxW, maxH, alpha);
            return null;
        }
        final int[] s = fit(e.width, e.height, maxW, maxH, allowUpscale);
        final int dx = center ? x + (maxW - s[0]) / 2 : x;
        final int dy = center ? y + (maxH - s[1]) / 2 : y;
        drawFrame(g, e, dx, dy, s[0], s[1], alpha);
        return new Drawn(dx, dy, s[0], s[1]);
    }

    /** Spinner while loading, a muted "couldn't load" when failed. */
    public static void drawPlaceholder(final GuiGraphics g, final MediaCache.Entry e, final int x, final int y, final int w, final int h, final float alpha) {
        final Theme t = Theme.current();
        if (e.status == MediaCache.Status.FAILED) {
            final Component text = Component.translatable("slate.media.failed");
            final int tw = SlateDraw.width(text);
            if (tw <= w - 4 && h >= 10) {
                g.drawString(SlateDraw.font(), text, x + (w - tw) / 2, y + (h - 8) / 2, Colors.scaleAlpha(t.palette().textDim(), alpha), t.isVanilla());
            }
            return;
        }
        final int size = Math.max(6, Math.min(12, Math.min(w, h) - 2));
        SlateSpinner.draw(g, x + (w - size) / 2, y + (h - size) / 2, size, Colors.scaleAlpha(t.isVanilla() ? 0xFFFFFFFF : t.palette().textMuted(), alpha));
    }

    /** Short status line for a card ("loading...", "couldn't load", the failure reason). */
    public static Component statusText(final MediaCache.Entry e) {
        return switch (e.status) {
            case READY -> Component.empty();
            case FAILED -> e.error.isEmpty() ? Component.translatable("slate.media.failed") : Component.translatable("slate.media.failed_reason", e.error);
            default -> Component.translatable("slate.media.loading");
        };
    }

    private MediaDraw() {}
}
