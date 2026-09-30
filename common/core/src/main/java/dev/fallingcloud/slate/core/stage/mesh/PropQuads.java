package dev.fallingcloud.slate.core.stage.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/**
 * Writes the faces of a prop built by hand (a chest, a sign board) into a vertex consumer of the entity format. A
 * face is given the way one would draw it on paper: its lower left corner, the edge running right, the edge running
 * up, and the rectangle of the texture it shows. Coordinates are in pixels of a block (sixteen to a block), which is
 * what such props are designed in.
 *
 * <p>The two shades of a face (along its lower and its upper edge) are the cheap ambient occlusion of these props:
 * the light falls off towards the floor, or into a hollow, and the shader adds the rest.</p>
 */
public final class PropQuads {

    private static final float PX = 1f / 16f;

    private final VertexConsumer out;
    private final PoseStack.Pose pose;
    private final float texW, texH;
    private final int alpha;
    private final Vector3f normal = new Vector3f();

    /** @param alpha 0..1, written into every vertex */
    public PropQuads(final VertexConsumer out, final PoseStack.Pose pose, final int textureWidth, final int textureHeight, final float alpha) {
        this.out = out;
        this.pose = pose;
        this.texW = textureWidth;
        this.texH = textureHeight;
        this.alpha = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
    }

    /**
     * One face. {@code (px, py, pz)} is its lower left corner seen from outside, {@code (ux, uy, uz)} the edge to the
     * right, {@code (vx, vy, vz)} the edge upwards; the face looks along their cross product. {@code (u0, v0)} is the
     * texture rectangle's upper left texel corner and {@code (u1, v1)} its lower right.
     */
    public PropQuads face(final float px, final float py, final float pz, final float ux, final float uy, final float uz,
                          final float vx, final float vy, final float vz,
                          final float u0, final float v0, final float u1, final float v1, final float shadeLow, final float shadeHigh) {
        normal.set(ux, uy, uz).cross(vx, vy, vz);
        if (normal.lengthSquared() < 1e-9f) return this;
        normal.normalize();
        vertex(px, py, pz, u0, v1, shadeLow);
        vertex(px + ux, py + uy, pz + uz, u1, v1, shadeLow);
        vertex(px + ux + vx, py + uy + vy, pz + uz + vz, u1, v0, shadeHigh);
        vertex(px + vx, py + vy, pz + vz, u0, v0, shadeHigh);
        return this;
    }

    /** {@link #face} evenly lit. */
    public PropQuads face(final float px, final float py, final float pz, final float ux, final float uy, final float uz,
                          final float vx, final float vy, final float vz, final float u0, final float v0, final float u1, final float v1) {
        return face(px, py, pz, ux, uy, uz, vx, vy, vz, u0, v0, u1, v1, 1f, 1f);
    }

    private void vertex(final float x, final float y, final float z, final float u, final float v, final float shade) {
        final int c = Math.round(Math.max(0f, Math.min(1f, shade)) * 255f);
        out.addVertex(pose, x * PX, y * PX, z * PX)
            .setColor(c, c, c, alpha)
            .setUv(u / texW, v / texH)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, normal.x, normal.y, normal.z);
    }
}
