package dev.fallingcloud.slate.menu.client.overhaul.play;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.node.StageNode;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The path the planets of a ring stand on: a circle of short dashes of light lying flat, brightest where the
 * selected planet stands (towards the viewer) and fading round the back. It is what makes a handful of planets
 * read as one ring, and says where the ring goes on when only a few worlds sit on it.
 */
final class OrbitNode extends StageNode {

    private static final int DASHES = 72;

    private final float radius, width;
    private int color;

    /** @param radius of the circle, in blocks; {@code width} of the line */
    OrbitNode(final float radius, final float width, final int argb) {
        this.radius = radius;
        this.width = width;
        this.color = argb;
        bounds(-radius, -0.01f, -radius, radius, 0.01f, radius);
        pickable(false);
    }

    OrbitNode color(final int argb) { this.color = argb; return this; }

    @Override
    protected void draw(final StageRenderContext ctx) {
        // Light: drawn after everything solid.
    }

    @Override
    public boolean hasTranslucentPass() { return true; }

    @Override
    protected void drawTranslucent(final StageRenderContext ctx) {
        final float a = alpha() * ((color >>> 24) & 0xFF) / 255f;
        if (a <= 0.004f) return;
        final Matrix4f m = ctx.pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        dev.fallingcloud.slate.core.stage.StageBlend.add();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        final int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        final float r0 = radius - width / 2f, r1 = radius + width / 2f;
        final BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < DASHES; i++) {
            final float a0 = (i + 0.22f) / DASHES * Mth.TWO_PI, a1 = (i + 0.78f) / DASHES * Mth.TWO_PI;
            // 1 towards the viewer (+Z), 0 at the back.
            final float front = (1f + Mth.cos((a0 + a1) / 2f)) / 2f;
            final int alpha = Math.round(255f * a * (0.22f + 0.78f * front * front));
            bb.addVertex(m, Mth.sin(a0) * r0, 0f, Mth.cos(a0) * r0).setColor(r, g, b, alpha);
            bb.addVertex(m, Mth.sin(a0) * r1, 0f, Mth.cos(a0) * r1).setColor(r, g, b, alpha);
            bb.addVertex(m, Mth.sin(a1) * r1, 0f, Mth.cos(a1) * r1).setColor(r, g, b, alpha);
            bb.addVertex(m, Mth.sin(a1) * r0, 0f, Mth.cos(a1) * r0).setColor(r, g, b, alpha);
        }
        final var mesh = bb.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        dev.fallingcloud.slate.core.stage.StageBlend.over();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }
}
