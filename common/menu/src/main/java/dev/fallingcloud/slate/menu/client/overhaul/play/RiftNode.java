package dev.fallingcloud.slate.menu.client.overhaul.play;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fallingcloud.slate.core.stage.StageBlend;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.node.StageNode;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.Random;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Where a ring goes on: a slow whirl of light at the back of it. A ring shows only so many planets at once; the
 * others are in here. Turn the ring and the planet that leaves it at the back grows small and is drawn into the
 * whirl, while the next one comes out of it on the other side: the whirl is what says that there is more.
 *
 * <p>It is a small spiral of stars: specks of light that ride three arms inwards, each with the way it has come
 * behind it, and go out in the heart, where squares of light turn against each other round a white middle. All of
 * it faces the viewer, is drawn additively and costs one draw call. With animations off it stands still.</p>
 */
final class RiftNode extends StageNode {

    private static final int SPECKS = 150, ARMS = 3;
    /** How far a speck is carried round on its way in, in turns. */
    private static final float TWIST = 1.15f;
    /** A speck is drawn so many times, each a little further back on its way and fainter: its tail. */
    private static final int TAIL = 3;
    private static final float TAIL_STEP = 0.022f;
    /** The squares of light in the heart, one in the other. */
    private static final int HEART = 6;

    private final float radius;
    private final float[] arm = new float[SPECKS], phase = new float[SPECKS], speed = new float[SPECKS], size = new float[SPECKS],
        tone = new float[SPECKS], stray = new float[SPECKS];
    private final int warm, accent;
    private float strength;
    private final Vector3f right = new Vector3f(), up = new Vector3f();

    /**
     * @param radius of the whirl, in blocks
     * @param warm   the colour of most of its light
     * @param accent the colour some of it takes
     */
    RiftNode(final float radius, final int warm, final int accent, final long seed) {
        this.radius = radius;
        this.warm = warm;
        this.accent = accent;
        final Random rnd = new Random(seed);
        for (int i = 0; i < SPECKS; i++) {
            arm[i] = (i % ARMS) * Mth.TWO_PI / ARMS + (rnd.nextFloat() - 0.5f) * 0.34f;
            phase[i] = rnd.nextFloat();
            speed[i] = 0.10f + rnd.nextFloat() * 0.09f;
            // Most are small, a few are not.
            size[i] = radius * (0.022f + rnd.nextFloat() * rnd.nextFloat() * 0.06f);
            tone[i] = rnd.nextFloat();
            stray[i] = (rnd.nextFloat() - 0.5f) * 0.12f;
        }
        bounds(-radius, -radius, -radius, radius, radius, radius);
        pickable(false);
        hoverFeel(0f, 1f);
        named("rift");
    }

    /** How much of it shows: 0 = there is nothing more than what is on the ring, 1 = there is. */
    RiftNode strength(final float s) {
        this.strength = Mth.clamp(s, 0f, 1f);
        return this;
    }

    @Override
    protected void draw(final StageRenderContext ctx) {
        // All light: drawn after what is solid.
    }

    @Override
    public boolean hasTranslucentPass() { return true; }

    @Override
    protected void drawTranslucent(final StageRenderContext ctx) {
        final float a = strength * alpha();
        if (a <= 0.004f) return;
        final Matrix4f m = ctx.pose.last().pose();
        final Matrix4f view = ctx.camera.view();
        right.set(view.m00(), view.m10(), view.m20());
        up.set(view.m01(), view.m11(), view.m21());
        modelInverse.transformDirection(right).normalize();
        modelInverse.transformDirection(up).normalize();
        final float t = Theme.current().motion() > 0f ? ctx.seconds() : 0f;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        StageBlend.add();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        final BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // The heart: squares of light one in the other, each turning against the next, brighter towards the middle.
        for (int k = 0; k < HEART; k++) {
            final float f = k / (HEART - 1f);
            square(bb, m, 0f, 0f, radius * (0.74f - 0.56f * f), t * (0.18f + 0.1f * k) * ((k & 1) == 0 ? 1f : -1f) + k * 0.52f,
                Colors.lerp(accent, warm, f), (0.035f + 0.05f * f) * a);
        }
        square(bb, m, 0f, 0f, radius * 0.1f, t * 0.9f, 0xFFFFFFFF, 0.85f * a);

        for (int i = 0; i < SPECKS; i++) {
            final float head = (phase[i] + t * speed[i]) % 1f;
            final float twinkle = 0.7f + 0.3f * Mth.sin(t * 3.1f + i * 1.7f);
            final int colour = tone[i] > 0.62f ? accent : Colors.lerp(warm, accent, tone[i] * 0.5f);
            for (int k = 0; k < TAIL; k++) {
                // 0 at the rim, 1 in the heart; and again from the rim.
                final float life = head - k * TAIL_STEP;
                if (life <= 0f) break;
                final float in = life * life * (3f - 2f * life);
                final float r = radius * (1f - in) * (1f + stray[i]);
                final float angle = arm[i] + in * TWIST * Mth.TWO_PI + t * 0.22f;
                // Comes up at the rim, is brightest on its way, goes out in the heart.
                final float light = Math.min(1f, life / 0.18f) * Math.min(1f, (1f - life) / 0.22f);
                final float pa = a * light * twinkle * (0.35f + 0.5f * tone[i]) * (k == 0 ? 1f : k == 1 ? 0.45f : 0.2f);
                if (pa <= 0.004f) continue;
                square(bb, m, Mth.cos(angle) * r, Mth.sin(angle) * r * 0.9f, size[i] * (0.55f + 0.45f * (1f - in)) * (1f - 0.18f * k), 0f, colour, pa);
            }
        }
        final var mesh = bb.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        StageBlend.over();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /** A square of light facing the viewer, its middle at {@code (x, y)} on the whirl's plane, turned by {@code turn} radians. */
    private void square(final BufferBuilder bb, final Matrix4f m, final float x, final float y, final float half, final float turn,
                        final int rgb, final float alpha) {
        final int pa = Math.round(255f * Mth.clamp(alpha, 0f, 1f));
        if (pa <= 0) return;
        final int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        final float c = Mth.cos(turn) * half, s = Mth.sin(turn) * half;
        // The corners, in the plane: (c - s, s + c) turned by quarters.
        corner(bb, m, x + (-c + s), y + (-s - c), r, g, b, pa);
        corner(bb, m, x + (c + s), y + (s - c), r, g, b, pa);
        corner(bb, m, x + (c - s), y + (s + c), r, g, b, pa);
        corner(bb, m, x + (-c - s), y + (-s + c), r, g, b, pa);
    }

    private void corner(final BufferBuilder bb, final Matrix4f m, final float x, final float y, final int r, final int g, final int b, final int a) {
        bb.addVertex(m, right.x * x + up.x * y, right.y * x + up.y * y, right.z * x + up.z * y).setColor(r, g, b, a);
    }
}
