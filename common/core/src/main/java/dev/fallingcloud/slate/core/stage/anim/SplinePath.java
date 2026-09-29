package dev.fallingcloud.slate.core.stage.anim;

import dev.fallingcloud.slate.core.gfx.Ease;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * A smooth move through key poses: Catmull-Rom splines for the eye and the target, linear fov/roll between keys.
 * The end points are duplicated so the curve starts and ends exactly on the first and last key.
 */
public final class SplinePath implements CameraPath {

    private final List<CameraPose> keys = new ArrayList<>();
    private final float durationMs;
    private final Ease ease;

    public SplinePath(final List<CameraPose> points, final float durationMs, final Ease ease) {
        for (final CameraPose p : points) keys.add(p.copy());
        if (keys.isEmpty()) keys.add(new CameraPose());
        this.durationMs = Math.max(0f, durationMs);
        this.ease = ease == null ? Ease.OUT_CUBIC : ease;
    }

    @Override
    public void sample(final float t, final CameraPose out) {
        final int n = keys.size();
        if (n == 1) { out.set(keys.get(0)); return; }
        final float f = Mth.clamp(t, 0f, 1f) * (n - 1);
        final int i = Math.min(n - 2, (int) f);
        final float u = f - i;
        final CameraPose p0 = keys.get(Math.max(0, i - 1)), p1 = keys.get(i), p2 = keys.get(i + 1), p3 = keys.get(Math.min(n - 1, i + 2));
        catmull(p0.position, p1.position, p2.position, p3.position, u, out.position);
        catmull(p0.target, p1.target, p2.target, p3.target, u, out.target);
        out.fov = Mth.lerp(u, p1.fov, p2.fov);
        out.roll = Mth.lerp(u, p1.roll, p2.roll);
    }

    private static void catmull(final Vector3f p0, final Vector3f p1, final Vector3f p2, final Vector3f p3, final float t, final Vector3f out) {
        final float t2 = t * t, t3 = t2 * t;
        out.x = 0.5f * (2f * p1.x + (-p0.x + p2.x) * t + (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 + (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3);
        out.y = 0.5f * (2f * p1.y + (-p0.y + p2.y) * t + (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 + (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3);
        out.z = 0.5f * (2f * p1.z + (-p0.z + p2.z) * t + (2f * p0.z - 5f * p1.z + 4f * p2.z - p3.z) * t2 + (-p0.z + 3f * p1.z - 3f * p2.z + p3.z) * t3);
    }

    @Override
    public float durationMs() { return durationMs; }

    @Override
    public Ease ease() { return ease; }
}
