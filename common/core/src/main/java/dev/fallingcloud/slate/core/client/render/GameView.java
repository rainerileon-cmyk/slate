package dev.fallingcloud.slate.core.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * A copy of the world as it was rendered this frame, before the GUI (and the menu blur) went over it, for screens that
 * show "the game" inside a rectangle: the Overhaul settings hub draws it above General and Video so a changed FOV,
 * brightness or render distance is seen at once. The copy is taken by a Core hook right after the level pass, only
 * while something asked for it in the last half second ({@link #request()}), into a texture no wider than
 * {@link #MAX_WIDTH} (a 5120 px window costs a 1280 px copy). Nothing is allocated per frame after the first.
 */
public final class GameView {

    public static final int MAX_WIDTH = 1280;
    private static final long KEEP_MS = 500;

    @Nullable private static TextureTarget copy;
    private static long wantedUntil;
    private static boolean hasFrame;

    /** Ask for a frame: call every frame while the view is on screen. */
    public static void request() {
        wantedUntil = Util.getMillis() + KEEP_MS;
    }

    /** True once a copy exists (after the first frame that followed a {@link #request()} in a world). */
    public static boolean hasFrame() { return hasFrame && copy != null; }

    public static int width() { return copy == null ? 0 : copy.width; }

    public static int height() { return copy == null ? 0 : copy.height; }

    /**
     * Core's hook, on the render thread, with the main target bound and holding the finished level pass (post effects
     * included) and no GUI yet. Copies it when wanted, frees the copy when nobody asked for a while.
     */
    public static void afterLevelRender() {
        if (Util.getMillis() > wantedUntil) {
            if (copy != null) release();
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final RenderTarget main = mc.getMainRenderTarget();
        final int mw = main.width, mh = main.height;
        if (mw <= 0 || mh <= 0) return;
        final int scale = Math.max(1, (int) Math.ceil(mw / (double) MAX_WIDTH));
        final int cw = Math.max(1, mw / scale), ch = Math.max(1, mh / scale);
        if (copy == null || copy.width != cw || copy.height != ch) {
            if (copy != null) copy.destroyBuffers();
            copy = new TextureTarget(cw, ch, false, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, mw, mh, 0, 0, cw, ch, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        main.bindWrite(true);
        hasFrame = true;
    }

    /**
     * Draws the last copy into {@code x, y, w, h} (GUI pixels), stretched to the rectangle: callers keep the aspect
     * ratio themselves ({@link #fit}, {@link #drawCover}). Returns false when there is no frame to draw.
     */
    public static boolean draw(final GuiGraphics g, final int x, final int y, final int w, final int h, final float alpha) {
        return drawRegion(g, x, y, w, h, 0f, 1f, 0f, 1f, alpha);
    }

    /**
     * Draws the copy scaled to fill {@code w x h} at its own aspect ratio, cropping the sides or the top and bottom
     * evenly (the centre of the view stays in the middle): a wide plate shows a wide band of the world.
     */
    public static boolean drawCover(final GuiGraphics g, final int x, final int y, final int w, final int h, final float alpha) {
        final int fw = width(), fh = height();
        if (fw <= 0 || fh <= 0 || w <= 0 || h <= 0) return false;
        final float frame = fw / (float) fh, target = w / (float) h;
        float u0 = 0f, u1 = 1f, v0 = 0f, v1 = 1f;
        if (frame > target) {
            final float keep = target / frame;
            u0 = (1f - keep) / 2f;
            u1 = 1f - u0;
        } else if (frame < target) {
            final float keep = frame / target;
            v0 = (1f - keep) / 2f;
            v1 = 1f - v0;
        }
        return drawRegion(g, x, y, w, h, u0, u1, v0, v1, alpha);
    }

    private static boolean drawRegion(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                      final float u0, final float u1, final float v0, final float v1, final float alpha) {
        if (!hasFrame() || w <= 0 || h <= 0 || alpha <= 0.004f) return false;
        g.flush();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        final Matrix4f m = g.pose().last().pose();
        // The texture's origin is bottom-left, so the top edge of the rectangle takes the larger v.
        final BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(m, x, y, 0).setUv(u0, v1);
        b.addVertex(m, x, y + h, 0).setUv(u0, v0);
        b.addVertex(m, x + w, y + h, 0).setUv(u1, v0);
        b.addVertex(m, x + w, y, 0).setUv(u1, v1);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        return true;
    }

    /** The largest rectangle of the frame's aspect ratio inside {@code w x h}, centred: {x, y, w, h} offsets. */
    public static int[] fit(final int w, final int h) {
        final int fw = width(), fh = height();
        if (fw <= 0 || fh <= 0) return new int[] { 0, 0, w, h };
        int rw = w, rh = Math.round(w * (fh / (float) fw));
        if (rh > h) { rh = h; rw = Math.round(h * (fw / (float) fh)); }
        return new int[] { (w - rw) / 2, (h - rh) / 2, rw, rh };
    }

    public static void release() {
        if (copy != null) {
            copy.destroyBuffers();
            copy = null;
        }
        hasFrame = false;
    }

    private GameView() {}
}
