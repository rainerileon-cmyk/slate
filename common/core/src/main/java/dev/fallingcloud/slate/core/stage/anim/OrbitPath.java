package dev.fallingcloud.slate.core.stage.anim;

import dev.fallingcloud.slate.core.gfx.Ease;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * A circular move around a centre point at a fixed radius and height, looking at the centre (plus
 * {@code targetHeight}). Angles are degrees around +Y; 0 is on +Z (in front), 90 on +X.
 */
public final class OrbitPath implements CameraPath {

    private final Vector3f center = new Vector3f();
    private final float radius, height, fromDeg, toDeg, fov, targetHeight;
    private final float durationMs;
    private final Ease ease;

    public OrbitPath(final Vector3f center, final float radius, final float height, final float fromDeg, final float toDeg,
                     final float fov, final float targetHeight, final float durationMs, final Ease ease) {
        this.center.set(center);
        this.radius = radius;
        this.height = height;
        this.fromDeg = fromDeg;
        this.toDeg = toDeg;
        this.fov = fov;
        this.targetHeight = targetHeight;
        this.durationMs = Math.max(0f, durationMs);
        this.ease = ease == null ? Ease.LINEAR : ease;
    }

    @Override
    public void sample(final float t, final CameraPose out) {
        final float a = (float) Math.toRadians(Mth.lerp(t, fromDeg, toDeg));
        out.position.set(center.x + Mth.sin(a) * radius, center.y + height, center.z + Mth.cos(a) * radius);
        out.target.set(center.x, center.y + targetHeight, center.z);
        out.fov = fov;
        out.roll = 0f;
    }

    @Override
    public float durationMs() { return durationMs; }

    @Override
    public Ease ease() { return ease; }
}
