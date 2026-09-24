package dev.fallingcloud.slate.building.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Small drawing toolkit shared by the ghost and overlay renderers: an animation clock that honours
 * {@code Theme.motion()}, line and quad mesh builders, uploads into reusable vertex buffers and the GL state our
 * passes set and restore. Everything here runs on the render thread inside {@code SlateRenderEvents.AFTER_TRANSLUCENT}.
 */
final class RenderKit {

    /** Packed full-bright light (block 15, sky 15). */
    static final int FULL_BRIGHT = 0xF000F0;

    private RenderKit() {}

    // ---- time ----

    /** Animation seconds: real time slowed by {@code Theme.motion()} (2 = half speed); frozen at 0 when motion is off. */
    static float seconds() {
        final float motion = Theme.current().motion();
        if (motion <= 0F) return 0F;
        return (float) ((Clock.nowMs() % 3_600_000L) / 1000.0 / motion);
    }

    /** Whether decorative animation should run at all ({@code Theme.motion() > 0}). */
    static boolean animated() {
        return Theme.current().motion() > 0F;
    }

    /** Line width in pixels scaled to the window, so lines look the same at 1080p and 4K. */
    static float px(final float width) {
        final int h = Minecraft.getInstance().getWindow().getHeight();
        return width * Math.max(1F, h / 1080F);
    }

    // ---- meshes ----

    static BufferBuilder begin(final ByteBufferBuilder bytes, final VertexFormat.Mode mode, final VertexFormat format) {
        return new BufferBuilder(bytes, mode, format);
    }

    /** Uploads what {@code buf} holds into {@code vb}; false when it was empty (nothing to draw). */
    static boolean upload(final BufferBuilder buf, final VertexBuffer vb) {
        final MeshData mesh = buf.build();
        if (mesh == null) return false;
        vb.bind();
        vb.upload(mesh);
        VertexBuffer.unbind();
        return true;
    }

    /** One line segment (camera- or origin-relative positions) with a colour per end, for the vanilla lines shader. */
    static void line(final BufferBuilder buf, final float x0, final float y0, final float z0, final float x1, final float y1, final float z1,
                     final int argb0, final int argb1) {
        float nx = x1 - x0, ny = y1 - y0, nz = z1 - z0;
        final float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0E-6F) return;
        nx /= len;
        ny /= len;
        nz /= len;
        buf.addVertex(x0, y0, z0).setColor(argb0).setNormal(nx, ny, nz);
        buf.addVertex(x1, y1, z1).setColor(argb1).setNormal(nx, ny, nz);
    }

    /** A flat quad for the position-colour shader. */
    static void quad(final BufferBuilder buf, final float[] xyz, final int argb) {
        for (int i = 0; i < 4; i++) buf.addVertex(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]).setColor(argb);
    }

    // ---- draws ----

    static VertexFormat linesFormat() {
        return DefaultVertexFormat.POSITION_COLOR_NORMAL;
    }

    /** Draws a line buffer with the vanilla lines shader: {@code width} px, colour scaled by {@code alpha}. */
    static void drawLines(final VertexBuffer vb, final Matrix4f modelView, final Matrix4f projection, final float width,
                          final float alpha, final boolean depthTest) {
        final ShaderInstance shader = GameRenderer.getRendertypeLinesShader();
        if (shader == null) return;
        depth(depthTest);
        RenderSystem.lineWidth(width);
        RenderSystem.setShaderColor(1F, 1F, 1F, alpha);
        vb.bind();
        vb.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /** Draws flat position-colour quads (fills). */
    static void drawFill(final VertexBuffer vb, final Matrix4f modelView, final Matrix4f projection, final float alpha, final boolean depthTest) {
        final ShaderInstance shader = GameRenderer.getPositionColorShader();
        if (shader == null) return;
        depth(depthTest);
        RenderSystem.setShaderColor(1F, 1F, 1F, alpha);
        vb.bind();
        vb.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    private static void depth(final boolean test) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(test ? GL11.GL_LEQUAL : GL11.GL_ALWAYS);
    }

    /** Translucent, no depth writes, both faces: the state overlays and lines draw in. */
    static void translucentState() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    /** Back to the defaults vanilla expects after a world render stage (whatever our passes changed). */
    static void restore() {
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1F);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /** A reusable dynamic vertex buffer (created lazily on the render thread). */
    static final class DynamicBuffer implements AutoCloseable {
        private @Nullable VertexBuffer vb;
        private boolean filled;

        VertexBuffer get() {
            if (vb == null || vb.isInvalid()) vb = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            return vb;
        }

        /** Uploads {@code buf}; returns whether there is something to draw. */
        boolean fill(final BufferBuilder buf) {
            filled = upload(buf, get());
            return filled;
        }

        boolean filled() {
            return filled && vb != null && !vb.isInvalid();
        }

        @Override
        public void close() {
            if (vb != null) vb.close();
            vb = null;
            filled = false;
        }
    }
}
