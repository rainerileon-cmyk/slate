package dev.fallingcloud.slate.building.client.gfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fallingcloud.slate.core.theme.Colors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Batched 2D drawing for the building UI: axis-aligned spans (the pixel rasters of {@link RingRaster} /
 * {@link ArcRaster}), arbitrary (rotated) quads and thick lines, all in ONE vertex batch through the
 * {@link GuiGraphics} buffer source ({@code RenderType.gui()}, same path as {@code GuiGraphics.fill}), so a whole
 * wheel is a single draw call. Coordinates are GUI pixels in the current pose. Always pair {@link #begin} with
 * {@link #end} (which flushes, keeping the layering with items and text drawn afterwards correct).
 *
 * <p>Core's {@code SlateDraw} has no arcs, sectors or rotated shapes; these helpers live here so Core stays
 * untouched.</p>
 */
public final class PixelCanvas {

    private final GuiGraphics g;
    private final VertexConsumer vc;
    private final Matrix4f m;
    private float alpha = 1f;

    private PixelCanvas(final GuiGraphics g) {
        this.g = g;
        this.vc = g.bufferSource().getBuffer(RenderType.gui());
        this.m = g.pose().last().pose();
    }

    public static PixelCanvas begin(final GuiGraphics g) {
        return new PixelCanvas(g);
    }

    /** Multiplies the alpha of everything drawn from now on (0..1). */
    public PixelCanvas alpha(final float a) {
        this.alpha = Math.max(0f, Math.min(1f, a));
        return this;
    }

    public float alpha() { return alpha; }

    /** Axis-aligned rectangle {@code [x0,x1) x [y0,y1)}. */
    public void rect(final float x0, final float y0, final float x1, final float y1, final int argb) {
        if (x1 <= x0 || y1 <= y0) return;
        final int c = alpha >= 1f ? argb : Colors.scaleAlpha(argb, alpha);
        if (Colors.alpha(c) == 0) return;
        // GuiGraphics.fill's winding: TL, BL, BR, TR.
        vc.addVertex(m, x0, y0, 0).setColor(c);
        vc.addVertex(m, x0, y1, 0).setColor(c);
        vc.addVertex(m, x1, y1, 0).setColor(c);
        vc.addVertex(m, x1, y0, 0).setColor(c);
    }

    /** One horizontal pixel run on row {@code y}, columns {@code x0..x1} inclusive. */
    public void span(final int y, final int x0, final int x1, final int argb) {
        rect(x0, y, x1 + 1, y + 1, argb);
    }

    /** Any convex quad; the winding is fixed up so back-face culling never hides it. */
    public void quad(final float x0, final float y0, final float x1, final float y1,
                     final float x2, final float y2, final float x3, final float y3, final int argb) {
        final int c = alpha >= 1f ? argb : Colors.scaleAlpha(argb, alpha);
        if (Colors.alpha(c) == 0) return;
        final float area = (x0 * y1 - x1 * y0) + (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y0 - x0 * y3);
        if (area <= 0) {
            vc.addVertex(m, x0, y0, 0).setColor(c);
            vc.addVertex(m, x1, y1, 0).setColor(c);
            vc.addVertex(m, x2, y2, 0).setColor(c);
            vc.addVertex(m, x3, y3, 0).setColor(c);
        } else {
            vc.addVertex(m, x3, y3, 0).setColor(c);
            vc.addVertex(m, x2, y2, 0).setColor(c);
            vc.addVertex(m, x1, y1, 0).setColor(c);
            vc.addVertex(m, x0, y0, 0).setColor(c);
        }
    }

    /** A triangle (a degenerate quad). */
    public void triangle(final float x0, final float y0, final float x1, final float y1, final float x2, final float y2, final int argb) {
        quad(x0, y0, x1, y1, x2, y2, x2, y2, argb);
    }

    /** A straight line of {@code thickness} px between two points (a rotated quad; smooth at any angle). */
    public void line(final float x0, final float y0, final float x1, final float y1, final float thickness, final int argb) {
        final float dx = x1 - x0, dy = y1 - y0;
        final float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) return;
        final float nx = -dy / len * thickness / 2f, ny = dx / len * thickness / 2f;
        quad(x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny, argb);
    }

    /** A rectangle of {@code w x h} centred on (cx, cy) and rotated by {@code angle} radians. */
    public void rotatedRect(final float cx, final float cy, final float w, final float h, final float angle, final int argb) {
        final float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        final float hx = w / 2f, hy = h / 2f;
        quad(cx + (-hx * c - -hy * s), cy + (-hx * s + -hy * c),
            cx + (hx * c - -hy * s), cy + (hx * s + -hy * c),
            cx + (hx * c - hy * s), cy + (hx * s + hy * c),
            cx + (-hx * c - hy * s), cy + (-hx * s + hy * c), argb);
    }

    /** A crisp 1 px line on the pixel grid (Bresenham), for pointer ticks and guides. */
    public void pixelLine(int x0, int y0, final int x1, final int y1, final int argb) {
        final int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        final int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        for (int guard = 0; guard < 4096; guard++) {
            rect(x0, y0, x0 + 1, y0 + 1, argb);
            if (x0 == x1 && y0 == y1) return;
            final int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    /** Flushes the batch (call once when done). */
    public void end() {
        g.flush();
    }

    // ------------------------------------------------------------------ textured spans

    /**
     * A batch of textured spans: every rectangle samples {@code texture} at its own screen position (the texture
     * repeats), so arbitrary pixel shapes get a seamless tiled fill (the vanilla skin's stone look). One draw call.
     */
    public static final class Textured {

        private final ResourceLocation texture;
        private final float texW, texH;
        private final BufferBuilder buf;
        private final Matrix4f m;
        private boolean any;

        private Textured(final GuiGraphics g, final ResourceLocation texture, final int texW, final int texH) {
            this.texture = texture;
            this.texW = texW;
            this.texH = texH;
            this.m = g.pose().last().pose();
            this.buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        }

        public static Textured begin(final GuiGraphics g, final ResourceLocation texture, final int texW, final int texH) {
            g.flush();
            return new Textured(g, texture, texW, texH);
        }

        public void rect(final float x0, final float y0, final float x1, final float y1, final float u0, final float v0, final int argb) {
            if (x1 <= x0 || y1 <= y0 || Colors.alpha(argb) == 0) return;
            final float u1 = u0 + (x1 - x0), v1 = v0 + (y1 - y0);
            buf.addVertex(m, x0, y0, 0).setUv(u0 / texW, v0 / texH).setColor(argb);
            buf.addVertex(m, x0, y1, 0).setUv(u0 / texW, v1 / texH).setColor(argb);
            buf.addVertex(m, x1, y1, 0).setUv(u1 / texW, v1 / texH).setColor(argb);
            buf.addVertex(m, x1, y0, 0).setUv(u1 / texW, v0 / texH).setColor(argb);
            any = true;
        }

        public void end() {
            final var mesh = buf.build();
            if (mesh == null || !any) {
                if (mesh != null) mesh.close();
                return;
            }
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            BufferUploader.drawWithShader(mesh);
            RenderSystem.disableBlend();
        }
    }
}
