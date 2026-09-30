package dev.fallingcloud.slate.core.stage.node;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.Random;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A puff of sparks: {@link #fire} throws them up and out from the node, they slow, sink a little and go out within a
 * second. The flourish on something appearing (a chest opening, a world being picked). Drawn additively, facing the
 * camera. Nothing shows before the first {@code fire}, after the sparks have died, or with animations off.
 */
public class BurstNode extends StageNode {

    private final float[] vx, vy, vz, life, size, twinkle;
    private int color;
    private float firedAtMs = -1f;
    private boolean pending;
    private final Vector3f right = new Vector3f(), up = new Vector3f();

    /**
     * @param count how many sparks
     * @param reach how far they get, roughly, in blocks
     * @param size  the side of a spark in blocks
     */
    public BurstNode(final int count, final float reach, final float size, final int argb, final long seed) {
        final Random rnd = new Random(seed);
        this.color = argb;
        vx = new float[count]; vy = new float[count]; vz = new float[count];
        life = new float[count]; this.size = new float[count]; twinkle = new float[count];
        for (int i = 0; i < count; i++) {
            final float angle = rnd.nextFloat() * Mth.TWO_PI;
            final float out = (0.25f + rnd.nextFloat() * 0.75f) * reach;
            vx[i] = Mth.sin(angle) * out;
            vz[i] = Mth.cos(angle) * out * 0.6f;
            vy[i] = (0.7f + rnd.nextFloat() * 1.1f) * reach;
            life[i] = 520f + rnd.nextFloat() * 520f;
            this.size[i] = size * (0.6f + rnd.nextFloat() * 0.8f);
            twinkle[i] = rnd.nextFloat() * Mth.TWO_PI;
        }
        bounds(-reach, 0f, -reach, reach, reach * 2f, reach);
        pickable(false);
        hoverFeel(0f, 1f);
        named("sparks");
    }

    public BurstNode color(final int argb) { this.color = argb; return this; }

    /** Throws the sparks, from now. */
    public void fire() {
        pending = true;
    }

    @Override
    public void update(final StageRenderContext ctx) {
        if (pending) {
            pending = false;
            firedAtMs = ctx.timeMs;
        }
    }

    @Override
    protected void draw(final StageRenderContext ctx) {
        // All light: drawn after the opaque batch.
    }

    @Override
    public boolean hasTranslucentPass() { return true; }

    @Override
    protected void drawTranslucent(final StageRenderContext ctx) {
        final float motion = Theme.current().motion();
        if (firedAtMs < 0f || motion <= 0f) return;
        final float age = (ctx.timeMs - firedAtMs) / motion;
        if (age > 1100f) { firedAtMs = -1f; return; }
        final float base = ((color >>> 24) & 0xFF) / 255f * alpha();
        if (base <= 0.004f) return;
        final Matrix4f m = ctx.pose.last().pose();
        final Matrix4f view = ctx.camera.view();
        right.set(view.m00(), view.m10(), view.m20());
        up.set(view.m01(), view.m11(), view.m21());
        modelInverse.transformDirection(right).normalize();
        modelInverse.transformDirection(up).normalize();
        final int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        dev.fallingcloud.slate.core.stage.StageBlend.add();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        final BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < vx.length; i++) {
            final float t = age / life[i];
            if (t >= 1f) continue;
            // Thrown hard, slowing at once: the distance covered eases out; a little gravity bends the path down.
            final float travel = 1f - (1f - t) * (1f - t) * (1f - t);
            final float seconds = age / 1000f;
            final float px = vx[i] * travel * 0.5f, pz = vz[i] * travel * 0.5f;
            final float py = vy[i] * travel * 0.5f - 0.55f * seconds * seconds;
            final float fade = (1f - t) * (0.65f + 0.35f * Mth.sin(age * 0.03f + twinkle[i]));
            final int pa = Math.round(255f * base * Math.max(0f, fade));
            if (pa <= 0) continue;
            final float s = size[i] * (1f - 0.5f * t) / 2f;
            final float rx = right.x * s, ry = right.y * s, rz = right.z * s;
            final float ux = up.x * s, uy = up.y * s, uz = up.z * s;
            // A diamond: the corners of the square turned onto the axes.
            bb.addVertex(m, px - rx, py - ry, pz - rz).setColor(r, g, b, pa);
            bb.addVertex(m, px - ux, py - uy, pz - uz).setColor(r, g, b, pa);
            bb.addVertex(m, px + rx, py + ry, pz + rz).setColor(255, 255, 255, pa);
            bb.addVertex(m, px + ux, py + uy, pz + uz).setColor(r, g, b, pa);
        }
        final var mesh = bb.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        dev.fallingcloud.slate.core.stage.StageBlend.over();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }
}
