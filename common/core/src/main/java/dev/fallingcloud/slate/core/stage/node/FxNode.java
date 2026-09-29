package dev.fallingcloud.slate.core.stage.node;

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
 * Lightweight effects made of plain coloured quads, drawn in the translucent pass without depth writes:
 * <ul>
 *   <li>{@link #clouds}: blocky, slowly drifting vanilla-style clouds (clusters of translucent white cells with the
 *       vanilla top/bottom/side shading);</li>
 *   <li>{@link #sparkles}: small camera-facing diamonds that twinkle.</li>
 * </ul>
 * Everything is generated once from a seed; per frame only positions advance. No textures, one draw call each.
 */
public class FxNode extends StageNode {

    public enum Kind { CLOUDS, SPARKLES }

    private final Kind kind;
    private final float[] px, py, pz, sx, sy, sz, phase;
    private final float spreadX, spreadZ;
    private int color = 0xFFFFFFFF;
    private float opacity;
    private float drift;
    private final Vector3f right = new Vector3f(), up = new Vector3f(), a = new Vector3f(), b = new Vector3f();

    private FxNode(final Kind kind, final int count, final float spreadX, final float spreadZ) {
        this.kind = kind;
        this.spreadX = spreadX;
        this.spreadZ = spreadZ;
        px = new float[count]; py = new float[count]; pz = new float[count];
        sx = new float[count]; sy = new float[count]; sz = new float[count];
        phase = new float[count];
        pickable(false);
    }

    /**
     * Blocky clouds: {@code clusters} clouds of 2-5 cells each, scattered over {@code spreadX × spreadZ} blocks around
     * the node at height {@code height} (relative), drifting along +X at {@code driftBlocksPerSec}.
     */
    public static FxNode clouds(final int clusters, final float spreadX, final float spreadZ, final float height,
                                final float cellSize, final long seed, final float driftBlocksPerSec) {
        final Random rnd = new Random(seed);
        final int cells = clusters * 5;
        final FxNode fx = new FxNode(Kind.CLOUDS, cells, spreadX, spreadZ);
        int i = 0;
        for (int c = 0; c < clusters; c++) {
            final float cx = (rnd.nextFloat() - 0.5f) * spreadX, cz = (rnd.nextFloat() - 0.5f) * spreadZ;
            final float cy = height + (rnd.nextFloat() - 0.5f) * cellSize;
            final int n = 2 + rnd.nextInt(4);
            float ox = 0, oz = 0;
            for (int k = 0; k < 5; k++) {
                if (k < n) {
                    fx.px[i] = cx + ox; fx.py[i] = cy; fx.pz[i] = cz + oz;
                    fx.sx[i] = cellSize * (0.8f + rnd.nextFloat() * 0.6f);
                    fx.sy[i] = cellSize * 0.3f;
                    fx.sz[i] = cellSize * (0.8f + rnd.nextFloat() * 0.6f);
                    ox += rnd.nextBoolean() ? fx.sx[i] * 0.8f : 0f;
                    oz += ox == 0 || rnd.nextBoolean() ? fx.sz[i] * 0.7f : 0f;
                } else {
                    fx.sx[i] = 0f;   // unused slot
                }
                fx.phase[i] = rnd.nextFloat();
                i++;
            }
        }
        fx.opacity = 0.8f;
        fx.drift = driftBlocksPerSec;
        fx.bounds(-spreadX / 2f, height - cellSize, -spreadZ / 2f, spreadX / 2f, height + cellSize, spreadZ / 2f);
        fx.named("clouds");
        return fx;
    }

    /** Twinkling diamonds scattered in a {@code spreadX × spreadY × spreadZ} box around the node. */
    public static FxNode sparkles(final int count, final float spreadX, final float spreadY, final float spreadZ, final float size,
                                  final int argb, final long seed) {
        final Random rnd = new Random(seed);
        final FxNode fx = new FxNode(Kind.SPARKLES, count, spreadX, spreadZ);
        for (int i = 0; i < count; i++) {
            fx.px[i] = (rnd.nextFloat() - 0.5f) * spreadX;
            fx.py[i] = (rnd.nextFloat() - 0.5f) * spreadY;
            fx.pz[i] = (rnd.nextFloat() - 0.5f) * spreadZ;
            fx.sx[i] = size * (0.6f + rnd.nextFloat() * 0.8f);
            fx.phase[i] = rnd.nextFloat() * Mth.TWO_PI;
            fx.sy[i] = 1.5f + rnd.nextFloat() * 2f;   // twinkle speed
        }
        fx.color = argb;
        fx.opacity = 1f;
        fx.bounds(-spreadX / 2f, -spreadY / 2f, -spreadZ / 2f, spreadX / 2f, spreadY / 2f, spreadZ / 2f);
        fx.named("sparkles");
        return fx;
    }

    public FxNode color(final int argb) { this.color = argb; return this; }

    public FxNode opacity(final float o) { this.opacity = Mth.clamp(o, 0f, 1f); return this; }

    @Override
    public void update(final StageRenderContext ctx) {
        if (kind == Kind.CLOUDS && drift != 0f && Theme.current().motion() > 0f) {
            final float dx = drift * ctx.deltaMs / 1000f;
            for (int i = 0; i < px.length; i++) {
                if (sx[i] <= 0f) continue;
                px[i] += dx;
                if (px[i] > spreadX / 2f + sx[i]) px[i] -= spreadX + 2f * sx[i];
                if (px[i] < -spreadX / 2f - sx[i]) px[i] += spreadX + 2f * sx[i];
            }
        }
    }

    @Override
    protected void draw(final StageRenderContext ctx) {
        // Everything is translucent: drawn after the opaque batch (see renderTranslucent).
    }

    @Override
    public boolean hasTranslucentPass() { return true; }

    @Override
    protected void drawTranslucent(final StageRenderContext ctx) {
        final Matrix4f m = ctx.pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        final float alpha = opacity * this.alpha;
        final int cr = (color >> 16) & 0xFF, cg = (color >> 8) & 0xFF, cb = color & 0xFF;
        final BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        if (kind == Kind.CLOUDS) {
            RenderSystem.enableCull();
            final int ca = Math.round(alpha * 255f);
            for (int i = 0; i < px.length; i++) {
                if (sx[i] <= 0f) continue;
                final float x0 = px[i] - sx[i] / 2f, x1 = px[i] + sx[i] / 2f;
                final float y0 = py[i] - sy[i] / 2f, y1 = py[i] + sy[i] / 2f;
                final float z0 = pz[i] - sz[i] / 2f, z1 = pz[i] + sz[i] / 2f;
                cube(bb, m, x0, y0, z0, x1, y1, z1, cr, cg, cb, ca);
            }
        } else {
            RenderSystem.disableCull();
            // Camera right/up in local space so the diamonds face the viewer whatever the node's transform.
            final Matrix4f view = ctx.camera.view();
            right.set(view.m00(), view.m10(), view.m20());
            up.set(view.m01(), view.m11(), view.m21());
            modelInverse.transformDirection(right).normalize();
            modelInverse.transformDirection(up).normalize();
            final float t = ctx.seconds();
            final boolean anim = Theme.current().motion() > 0f;
            for (int i = 0; i < px.length; i++) {
                final float tw = anim ? 0.55f + 0.45f * Mth.sin(t * sy[i] + phase[i]) : 0.8f;
                final float s = sx[i] * (0.7f + 0.3f * tw);
                final int sa = Math.round(alpha * tw * 255f);
                // A diamond: the quad's axes are (right+up) and (up-right).
                a.set(right).add(up).mul(s * 0.7071f);
                b.set(up).sub(right).mul(s * 0.7071f);
                final float cx = px[i], cy = py[i], cz = pz[i];
                bb.addVertex(m, cx - a.x, cy - a.y, cz - a.z).setColor(cr, cg, cb, sa);
                bb.addVertex(m, cx - b.x, cy - b.y, cz - b.z).setColor(cr, cg, cb, sa);
                bb.addVertex(m, cx + a.x, cy + a.y, cz + a.z).setColor(255, 255, 255, sa);
                bb.addVertex(m, cx + b.x, cy + b.y, cz + b.z).setColor(cr, cg, cb, sa);
            }
        }
        final var mesh = bb.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /** One cloud cell with vanilla's face shading; faces wound counter-clockwise seen from outside. */
    private static void cube(final BufferBuilder bb, final Matrix4f m, final float x0, final float y0, final float z0,
                             final float x1, final float y1, final float z1, final int r, final int g, final int b, final int a) {
        // top (1.0)
        quad(bb, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a, 1f);
        // bottom (0.7)
        quad(bb, m, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, r, g, b, a, 0.7f);
        // north / south (0.8)
        quad(bb, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a, 0.8f);
        quad(bb, m, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, r, g, b, a, 0.8f);
        // west / east (0.9)
        quad(bb, m, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, r, g, b, a, 0.9f);
        quad(bb, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a, 0.9f);
    }

    private static void quad(final BufferBuilder bb, final Matrix4f m,
                             final float ax, final float ay, final float az, final float bx, final float by, final float bz,
                             final float cx, final float cy, final float cz, final float dx, final float dy, final float dz,
                             final int r, final int g, final int b, final int a, final float shade) {
        final int sr = Math.round(r * shade), sg = Math.round(g * shade), sb = Math.round(b * shade);
        bb.addVertex(m, ax, ay, az).setColor(sr, sg, sb, a);
        bb.addVertex(m, bx, by, bz).setColor(sr, sg, sb, a);
        bb.addVertex(m, cx, cy, cz).setColor(sr, sg, sb, a);
        bb.addVertex(m, dx, dy, dz).setColor(sr, sg, sb, a);
    }
}
