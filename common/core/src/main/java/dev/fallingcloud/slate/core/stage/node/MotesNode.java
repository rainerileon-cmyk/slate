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
 * Dust in the light: small square motes that drift slowly upwards through a box around the node, swaying a little,
 * each fading in and out at its own pace. Drawn additively (they are lit dust, not soot), facing the camera. What
 * makes the air of a scene visible; a few dozen are enough.
 *
 * <p>With animations off the motes hang still. They cost one draw call.</p>
 */
public class MotesNode extends StageNode {

    private final float[] x, y, z, rise, sway, swaySpeed, phase, size;
    private final float spreadY;
    private int color;
    private float strength = 1f;
    private final Vector3f right = new Vector3f(), up = new Vector3f();

    /**
     * @param count  how many motes
     * @param size   the side of a mote in blocks (0.02 is a speck)
     * @param argb   their colour; the alpha is how bright a mote gets at its brightest
     */
    public MotesNode(final int count, final float spreadX, final float spreadY, final float spreadZ, final float size, final int argb, final long seed) {
        final Random rnd = new Random(seed);
        this.spreadY = spreadY;
        this.color = argb;
        x = new float[count]; y = new float[count]; z = new float[count];
        rise = new float[count]; sway = new float[count]; swaySpeed = new float[count]; phase = new float[count];
        this.size = new float[count];
        for (int i = 0; i < count; i++) {
            x[i] = (rnd.nextFloat() - 0.5f) * spreadX;
            y[i] = rnd.nextFloat() * spreadY;
            z[i] = (rnd.nextFloat() - 0.5f) * spreadZ;
            rise[i] = 0.018f + rnd.nextFloat() * 0.05f;
            sway[i] = 0.03f + rnd.nextFloat() * 0.09f;
            swaySpeed[i] = 0.25f + rnd.nextFloat() * 0.6f;
            phase[i] = rnd.nextFloat() * Mth.TWO_PI;
            this.size[i] = size * (0.55f + rnd.nextFloat() * 0.9f);
        }
        bounds(-spreadX / 2f, 0f, -spreadZ / 2f, spreadX / 2f, spreadY, spreadZ / 2f);
        pickable(false);
        hoverFeel(0f, 1f);
        named("motes");
    }

    public MotesNode color(final int argb) { this.color = argb; return this; }

    /** Scales every mote's brightness (0 = none show): for fading the dust in with the light that shows it. */
    public MotesNode strength(final float s) { this.strength = Mth.clamp(s, 0f, 1f); return this; }

    @Override
    protected void draw(final StageRenderContext ctx) {
        // All light: drawn after the opaque batch.
    }

    @Override
    public boolean hasTranslucentPass() { return true; }

    @Override
    protected void drawTranslucent(final StageRenderContext ctx) {
        final float a = ((color >>> 24) & 0xFF) / 255f * strength * alpha();
        if (a <= 0.004f || x.length == 0) return;
        final Matrix4f m = ctx.pose.last().pose();
        final Matrix4f view = ctx.camera.view();
        right.set(view.m00(), view.m10(), view.m20());
        up.set(view.m01(), view.m11(), view.m21());
        modelInverse.transformDirection(right).normalize();
        modelInverse.transformDirection(up).normalize();
        final float t = Theme.current().motion() > 0f ? ctx.seconds() : 0f;
        final int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        dev.fallingcloud.slate.core.stage.StageBlend.add();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        final BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < x.length; i++) {
            final float py = (y[i] + rise[i] * t) % spreadY;
            final float px = x[i] + Mth.sin(t * swaySpeed[i] + phase[i]) * sway[i];
            final float pz = z[i] + Mth.cos(t * swaySpeed[i] * 0.8f + phase[i] * 1.7f) * sway[i];
            // Fades in near the floor and out near the top, and breathes in between.
            final float edge = Math.min(1f, Math.min(py, spreadY - py) / (spreadY * 0.18f));
            final float breath = 0.45f + 0.55f * Mth.sin(t * 0.7f * swaySpeed[i] + phase[i] * 2.3f);
            final int pa = Math.round(255f * a * Math.max(0f, edge) * Math.max(0f, breath));
            if (pa <= 0) continue;
            final float s = size[i] / 2f;
            final float rx = right.x * s, ry = right.y * s, rz = right.z * s;
            final float ux = up.x * s, uy = up.y * s, uz = up.z * s;
            bb.addVertex(m, px - rx - ux, py - ry - uy, pz - rz - uz).setColor(r, g, b, pa);
            bb.addVertex(m, px + rx - ux, py + ry - uy, pz + rz - uz).setColor(r, g, b, pa);
            bb.addVertex(m, px + rx + ux, py + ry + uy, pz + rz + uz).setColor(r, g, b, pa);
            bb.addVertex(m, px - rx + ux, py - ry + uy, pz - rz + uz).setColor(r, g, b, pa);
        }
        final var mesh = bb.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        dev.fallingcloud.slate.core.stage.StageBlend.over();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }
}
