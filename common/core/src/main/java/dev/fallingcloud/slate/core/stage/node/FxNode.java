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
 * Lightweight effects made of plain coloured quads, drawn in the translucent pass without depth writes:
 * <ul>
 *   <li>{@link #clouds}: blocky, slowly drifting vanilla-style clouds (clusters of translucent white cells with the
 *       vanilla top/bottom/side shading);</li>
 *   <li>{@link #sparkles}: small camera-facing diamonds that twinkle;</li>
 *   <li>{@link #cloudRing}: the same clouds set on a ring around the node (spin the node and they circle it);</li>
 *   <li>{@link #glow}: a pool of light lying on the floor, fading out towards its edge;</li>
 *   <li>{@link #cone}: the beam of a spotlight, from a small opening above down to a wide foot.</li>
 * </ul>
 * Everything is generated once from a seed; per frame only positions advance. No textures, one draw call each.
 */
public class FxNode extends StageNode {

    public enum Kind { CLOUDS, SPARKLES, GLOW, CONE }

    /** Slices of the round shapes ({@link #glow}, {@link #cone}). */
    private static final int SEGMENTS = 32;
    /** Rings a {@link #glow} is drawn in. */
    private static final int RINGS = 12;

    /** How much light is left {@code t} of the way out (0 = the middle, 1 = the rim): full, then a smooth fall to nothing. */
    private float falloff(final float t) {
        final float u = 1f - Mth.clamp(t, 0f, 1f);
        if (fade > 0f) return (float) Math.pow(u, fade);
        return u * u * (3f - 2f * u);
    }

    private final Kind kind;
    private final float[] px, py, pz, sx, sy, sz, phase;
    private final float spreadX, spreadZ;
    private int color = 0xFFFFFFFF;
    private float opacity;
    private float drift;
    /** A glow's curve: 0 = the smooth step, else the power its light falls off by. */
    private float fade;
    /** A glow cut to a rectangle (half sizes), 0 = round. */
    private float clipX, clipZ;
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

    /**
     * Blocky clouds set on a ring of {@code radius} blocks around the node, {@code band} blocks deep, at height
     * {@code height}. They do not drift: spin the node and they circle whatever stands in the middle.
     */
    public static FxNode cloudRing(final int clusters, final float radius, final float band, final float height,
                                   final float cellSize, final long seed) {
        final Random rnd = new Random(seed);
        final FxNode fx = new FxNode(Kind.CLOUDS, clusters * 5, (radius + band) * 2f, (radius + band) * 2f);
        int i = 0;
        for (int c = 0; c < clusters; c++) {
            final float angle = (c + (rnd.nextFloat() - 0.5f) * 0.6f) / clusters * Mth.TWO_PI;
            final float r = radius + (rnd.nextFloat() - 0.5f) * band;
            final float cx = Mth.sin(angle) * r, cz = Mth.cos(angle) * r;
            final float cy = height + (rnd.nextFloat() - 0.5f) * cellSize * 1.6f;
            // Cells line up along the ring, so a cloud follows the curve it sits on.
            final float tx = Mth.cos(angle), tz = -Mth.sin(angle);
            final int n = 2 + rnd.nextInt(3);
            float along = -(n - 1) * cellSize * 0.35f;
            for (int k = 0; k < 5; k++) {
                if (k < n) {
                    final float out = (rnd.nextFloat() - 0.5f) * cellSize * 0.5f;
                    fx.px[i] = cx + tx * along + Mth.sin(angle) * out;
                    fx.py[i] = cy + (k % 2 == 0 ? 0f : cellSize * 0.12f);
                    fx.pz[i] = cz + tz * along + Mth.cos(angle) * out;
                    fx.sx[i] = cellSize * (0.85f + rnd.nextFloat() * 0.5f);
                    fx.sy[i] = cellSize * 0.32f;
                    fx.sz[i] = cellSize * (0.85f + rnd.nextFloat() * 0.5f);
                    along += cellSize * 0.7f;
                } else {
                    fx.sx[i] = 0f;   // unused slot
                }
                fx.phase[i] = rnd.nextFloat();
                i++;
            }
        }
        fx.opacity = 0.85f;
        fx.bounds(-radius - band, height - cellSize, -radius - band, radius + band, height + cellSize, radius + band);
        fx.named("clouds");
        return fx;
    }

    /** A round pool of light on the floor: {@code argb} in the middle, nothing at {@code radius}. */
    public static FxNode glow(final float radius, final int argb) {
        final FxNode fx = new FxNode(Kind.GLOW, 1, radius * 2f, radius * 2f);
        fx.sx[0] = radius;
        fx.color = argb;
        fx.opacity = ((argb >>> 24) & 0xFF) / 255f;
        fx.bounds(-radius, -0.01f, -radius, radius, 0.01f, radius);
        fx.named("glow");
        return fx;
    }

    /**
     * The beam of a spotlight: a cone from an opening of {@code topRadius} at {@code height} down to a foot of
     * {@code bottomRadius} on the floor, brightest at the top. Drawn additively, so it lights what is behind it.
     */
    public static FxNode cone(final float topRadius, final float bottomRadius, final float height, final int argb) {
        final FxNode fx = new FxNode(Kind.CONE, 1, bottomRadius * 2f, bottomRadius * 2f);
        fx.sx[0] = topRadius;
        fx.sz[0] = bottomRadius;
        fx.sy[0] = height;
        fx.color = argb;
        fx.opacity = ((argb >>> 24) & 0xFF) / 255f;
        fx.bounds(-bottomRadius, 0f, -bottomRadius, bottomRadius, height, bottomRadius);
        fx.named("spotlight");
        return fx;
    }

    public FxNode color(final int argb) { this.color = argb; return this; }

    /**
     * How a {@link #glow} loses its light on the way out: by this power of the way left to its rim. 2 and more give a
     * bright middle with a long, faint skirt, which is what a wide floor wants: seen at a flat angle its far half is
     * squeezed into a few rows of pixels, and anything still bright there reads as an edge.
     */
    public FxNode fade(final float power) { this.fade = Math.max(0f, power); return this; }

    /** Cuts a {@link #glow} to a rectangle of these half sizes around its middle: light on a panel, not past it. */
    public FxNode clip(final float halfX, final float halfZ) { this.clipX = Math.max(0f, halfX); this.clipZ = Math.max(0f, halfZ); return this; }

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
        dev.fallingcloud.slate.core.stage.StageBlend.over();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        final float alpha = opacity * alpha();
        final int cr = (color >> 16) & 0xFF, cg = (color >> 8) & 0xFF, cb = color & 0xFF;
        if (kind == Kind.CONE) dev.fallingcloud.slate.core.stage.StageBlend.add();
        final BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        if (kind == Kind.GLOW) {
            RenderSystem.disableCull();
            // Rings from the middle outwards, their alpha on a smooth curve: a straight fade shows its rim.
            final float r = sx[0];
            for (int ring = 0; ring < RINGS; ring++) {
                final float t0 = ring / (float) RINGS, t1 = (ring + 1) / (float) RINGS;
                final int c0 = Math.round(alpha * 255f * falloff(t0)), c1 = Math.round(alpha * 255f * falloff(t1));
                final float r0 = r * t0, r1 = r * t1;
                for (int i = 0; i < SEGMENTS; i++) {
                    final float a0 = i / (float) SEGMENTS * Mth.TWO_PI, a1 = (i + 1) / (float) SEGMENTS * Mth.TWO_PI;
                    bb.addVertex(m, cutX(Mth.sin(a0) * r0), 0f, cutZ(Mth.cos(a0) * r0)).setColor(cr, cg, cb, c0);
                    bb.addVertex(m, cutX(Mth.sin(a0) * r1), 0f, cutZ(Mth.cos(a0) * r1)).setColor(cr, cg, cb, c1);
                    bb.addVertex(m, cutX(Mth.sin(a1) * r1), 0f, cutZ(Mth.cos(a1) * r1)).setColor(cr, cg, cb, c1);
                    bb.addVertex(m, cutX(Mth.sin(a1) * r0), 0f, cutZ(Mth.cos(a1) * r0)).setColor(cr, cg, cb, c0);
                }
            }
        } else if (kind == Kind.CONE) {
            RenderSystem.disableCull();
            final int top = Math.round(alpha * 255f), foot = Math.round(alpha * 255f * 0.12f);
            final float r0 = sx[0], r1 = sz[0], h = sy[0];
            for (int i = 0; i < SEGMENTS; i++) {
                final float a0 = i / (float) SEGMENTS * Mth.TWO_PI, a1 = (i + 1) / (float) SEGMENTS * Mth.TWO_PI;
                bb.addVertex(m, Mth.sin(a0) * r0, h, Mth.cos(a0) * r0).setColor(cr, cg, cb, top);
                bb.addVertex(m, Mth.sin(a0) * r1, 0f, Mth.cos(a0) * r1).setColor(cr, cg, cb, foot);
                bb.addVertex(m, Mth.sin(a1) * r1, 0f, Mth.cos(a1) * r1).setColor(cr, cg, cb, foot);
                bb.addVertex(m, Mth.sin(a1) * r0, h, Mth.cos(a1) * r0).setColor(cr, cg, cb, top);
            }
        } else if (kind == Kind.CLOUDS) {
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
        dev.fallingcloud.slate.core.stage.StageBlend.over();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private float cutX(final float x) { return clipX > 0f ? Mth.clamp(x, -clipX, clipX) : x; }

    private float cutZ(final float z) { return clipZ > 0f ? Mth.clamp(z, -clipZ, clipZ) : z; }

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
