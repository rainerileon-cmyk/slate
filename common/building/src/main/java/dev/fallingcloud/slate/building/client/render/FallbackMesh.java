package dev.fallingcloud.slate.building.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.fallingcloud.slate.core.theme.Colors;
import java.util.Arrays;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Ghost vertices recorded once, relative to an origin block, for the shader-pack fallback path (the vanilla
 * translucent block sheet, see {@link GhostRenderer}). Meshing a ghost means model lookups, face-culling checks
 * against the world, light and tint lookups; with a shader pack active that used to happen for every ghost on every
 * frame. A recorded mesh is built when its plan changes and afterwards only replayed: per vertex one colour multiply
 * (the style tint and the frame's opacity and pulse) and one vertex write.
 *
 * <p>Layout per vertex, in ints: x, y, z, u, v (float bits), colour (ARGB, alpha = the ghost's own fade), packed
 * light, style, packed normal.
 */
final class FallbackMesh implements GhostMesher.Sink {

    private static final int STRIDE = 9;

    private int[] data = new int[STRIDE * 64];
    private int vertices;

    void clear() {
        vertices = 0;
    }

    int vertices() {
        return vertices;
    }

    /** Frees a large backing array (a scratch mesh after a big frame). */
    void trim(final int maxVertices) {
        if (data.length > maxVertices * STRIDE) data = new int[STRIDE * 64];
        vertices = 0;
    }

    @Override
    public void vertex(final float x, final float y, final float z, final float r, final float g, final float b, final float a, final float u,
                       final float v, final int blockLight, final int skyLight, final int style, final float nx, final float ny, final float nz) {
        final int i = vertices * STRIDE;
        if (i + STRIDE > data.length) data = Arrays.copyOf(data, Math.max(data.length * 2, i + STRIDE));
        data[i] = Float.floatToRawIntBits(x);
        data[i + 1] = Float.floatToRawIntBits(y);
        data[i + 2] = Float.floatToRawIntBits(z);
        data[i + 3] = Float.floatToRawIntBits(u);
        data[i + 4] = Float.floatToRawIntBits(v);
        data[i + 5] = Colors.argb(unit(a), unit(r), unit(g), unit(b));
        data[i + 6] = (blockLight & 0xFFFF) | (skyLight << 16);
        data[i + 7] = style;
        data[i + 8] = (Math.round(nx * 127F) & 0xFF) | (Math.round(ny * 127F) & 0xFF) << 8 | (Math.round(nz * 127F) & 0xFF) << 16;
        vertices++;
    }

    /**
     * Writes the mesh to {@code consumer} at {@code (ox, oy, oz)} (the origin's camera-relative offset): replacements
     * tinted amber and removals red (45%, as the ghost shader's tint), alpha scaled by {@code opacity}.
     */
    void replay(final VertexConsumer consumer, final float ox, final float oy, final float oz, final float opacity, final int dangerRgb) {
        replay(consumer, ox, oy, oz, null, opacity, dangerRgb);
    }

    /**
     * The same, for a mesh that is not only moved: {@code placed} (when not null) takes a vertex from the mesh's own
     * space to where it is seen from the camera, turned and scaled (a ghost on a Sable sub-level), and the offset is
     * not used.
     */
    void replay(final VertexConsumer consumer, final float ox, final float oy, final float oz, final @Nullable Matrix4f placed, final float opacity,
                final int dangerRgb) {
        final int[] d = data;
        final Vector3f p = new Vector3f(), nrm = new Vector3f();
        for (int k = 0, n = vertices; k < n; k++) {
            final int i = k * STRIDE;
            final int argb = d[i + 5];
            final int style = d[i + 7];
            int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            if (style != 0) {
                final int tint = style == 1 ? GhostMesher.REPLACE_AMBER : dangerRgb;
                r = Math.round(r * 0.55F + ((tint >> 16) & 0xFF) * 0.45F);
                g = Math.round(g * 0.55F + ((tint >> 8) & 0xFF) * 0.45F);
                b = Math.round(b * 0.55F + (tint & 0xFF) * 0.45F);
            }
            final int a = Math.min(255, Math.round(((argb >>> 24) & 0xFF) * opacity));
            final int normal = d[i + 8];
            p.set(Float.intBitsToFloat(d[i]), Float.intBitsToFloat(d[i + 1]), Float.intBitsToFloat(d[i + 2]));
            nrm.set((byte) normal / 127F, (byte) (normal >> 8) / 127F, (byte) (normal >> 16) / 127F);
            if (placed == null) {
                p.add(ox, oy, oz);
            } else {
                placed.transformPosition(p);
                placed.transformDirection(nrm).normalize();
            }
            consumer.addVertex(p.x, p.y, p.z,
                Colors.argb(a, Math.min(255, r), Math.min(255, g), Math.min(255, b)),
                Float.intBitsToFloat(d[i + 3]), Float.intBitsToFloat(d[i + 4]), OverlayTexture.NO_OVERLAY, d[i + 6],
                nrm.x, nrm.y, nrm.z);
        }
    }

    private static int unit(final float f) {
        return Math.round(Math.max(0F, Math.min(1F, f)) * 255F);
    }
}
