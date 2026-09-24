package dev.fallingcloud.slate.building.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Crops one material quad to a {@link ShapeFaces.Region} and moves it onto the region's plane (the idea of Create's
 * {@code BakedModelHelper.cropAndMove}). The texture keeps its world alignment: a stair's lower half shows the
 * lower half of the material's side texture, exactly like vanilla's uv-locked stairs, and the texture coordinates
 * never leave the quad's own sprite, so there is no bleeding from neighbouring atlas sprites.
 *
 * <p>Positions are clamped on the two in-plane axes; the new texture coordinates (and, for a tilted quad, the new
 * depth) come from the quad's affine map from position to UV, solved from three of its corners. Colour, light,
 * normal, tint index, sprite and shade are kept.
 */
final class QuadCropper {

    /** Vertex layout of {@code DefaultVertexFormat.BLOCK} in ints: x y z colour u v light normal. */
    private static final int X = 0, U = 4, V = 5;

    /**
     * {@code quad} cropped to {@code region} and moved by {@code region.move()} along the region's axis; the quad
     * itself when it lies fully inside and needs no move; null when nothing of it is inside.
     */
    static @Nullable BakedQuad crop(final BakedQuad quad, final ShapeFaces.Region region) {
        final int[] src = quad.getVertices();
        if (src.length < 16 || src.length % 4 != 0) return null;
        final int stride = src.length / 4;
        final Direction.Axis n = region.dir().getAxis();
        final int ni = n.ordinal(), ai = ShapeFaces.axisA(n).ordinal(), bi = ShapeFaces.axisB(n).ordinal();

        final float[][] p = new float[4][3];
        double minA = Double.MAX_VALUE, maxA = -Double.MAX_VALUE, minB = Double.MAX_VALUE, maxB = -Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            for (int k = 0; k < 3; k++) p[i][k] = Float.intBitsToFloat(src[i * stride + X + k]);
            minA = Math.min(minA, p[i][ai]);
            maxA = Math.max(maxA, p[i][ai]);
            minB = Math.min(minB, p[i][bi]);
            maxB = Math.max(maxB, p[i][bi]);
        }
        final double loA = Math.max(minA, region.a0()), hiA = Math.min(maxA, region.a1());
        final double loB = Math.max(minB, region.b0()), hiB = Math.min(maxB, region.b1());
        if (hiA - loA < ShapeFaces.EPS || hiB - loB < ShapeFaces.EPS) return null;

        final double move = region.move();
        final boolean inside = loA <= minA + ShapeFaces.EPS && hiA >= maxA - ShapeFaces.EPS
            && loB <= minB + ShapeFaces.EPS && hiB >= maxB - ShapeFaces.EPS;
        if (inside && Math.abs(move) < ShapeFaces.EPS) return quad;

        // Affine map from (a, b) to (u, v, n), anchored at vertex 0 along the edges 0→1 and 0→3.
        final double e1a = p[1][ai] - p[0][ai], e1b = p[1][bi] - p[0][bi];
        final double e2a = p[3][ai] - p[0][ai], e2b = p[3][bi] - p[0][bi];
        final double det = e1a * e2b - e1b * e2a;
        if (Math.abs(det) < 1.0E-9) return null;   // edge-on to this plane: not a face of this side
        final float u0 = Float.intBitsToFloat(src[U]), v0 = Float.intBitsToFloat(src[V]);
        final float du1 = Float.intBitsToFloat(src[stride + U]) - u0, dv1 = Float.intBitsToFloat(src[stride + V]) - v0;
        final float du2 = Float.intBitsToFloat(src[3 * stride + U]) - u0, dv2 = Float.intBitsToFloat(src[3 * stride + V]) - v0;
        final double dn1 = p[1][ni] - p[0][ni], dn2 = p[3][ni] - p[0][ni];

        final int[] out = src.clone();
        for (int i = 0; i < 4; i++) {
            final double a = clamp(p[i][ai], loA, hiA), b = clamp(p[i][bi], loB, hiB);
            final double da = a - p[0][ai], db = b - p[0][bi];
            final double s = (da * e2b - db * e2a) / det;
            final double t = (e1a * db - e1b * da) / det;
            final int base = i * stride;
            final float[] pos = new float[3];
            pos[ai] = (float) a;
            pos[bi] = (float) b;
            pos[ni] = (float) (p[0][ni] + s * dn1 + t * dn2 + move);
            for (int k = 0; k < 3; k++) out[base + X + k] = Float.floatToRawIntBits(pos[k]);
            out[base + U] = Float.floatToRawIntBits((float) (u0 + s * du1 + t * du2));
            out[base + V] = Float.floatToRawIntBits((float) (v0 + s * dv1 + t * dv2));
        }
        return new BakedQuad(out, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade());
    }

    private static double clamp(final double v, final double lo, final double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private QuadCropper() {}
}
