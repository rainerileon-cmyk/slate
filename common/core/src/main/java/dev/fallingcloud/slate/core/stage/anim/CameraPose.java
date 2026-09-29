package dev.fallingcloud.slate.core.stage.anim;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Where a camera is and what it looks at: eye position, target point, vertical field of view (degrees) and roll
 * (degrees, positive = clockwise). Mutable and reused, so camera paths can sample without allocating.
 */
public final class CameraPose {

    public final Vector3f position = new Vector3f(0, 2, 6);
    public final Vector3f target = new Vector3f(0, 1, 0);
    public float fov = 45f;
    public float roll = 0f;

    public CameraPose() {}

    public CameraPose(final float px, final float py, final float pz, final float tx, final float ty, final float tz, final float fov, final float roll) {
        set(px, py, pz, tx, ty, tz, fov, roll);
    }

    public CameraPose set(final float px, final float py, final float pz, final float tx, final float ty, final float tz, final float fov, final float roll) {
        position.set(px, py, pz);
        target.set(tx, ty, tz);
        this.fov = fov;
        this.roll = roll;
        return this;
    }

    public CameraPose set(final CameraPose other) {
        position.set(other.position);
        target.set(other.target);
        fov = other.fov;
        roll = other.roll;
        return this;
    }

    public CameraPose copy() {
        return new CameraPose().set(this);
    }

    /** {@code this = a + (b - a) * t}. */
    public CameraPose lerp(final CameraPose a, final CameraPose b, final float t) {
        position.set(a.position).lerp(b.position, t);
        target.set(a.target).lerp(b.target, t);
        fov = Mth.lerp(t, a.fov, b.fov);
        roll = Mth.lerp(t, a.roll, b.roll);
        return this;
    }

    @Override
    public String toString() {
        return "CameraPose[" + position.x + "," + position.y + "," + position.z + " -> " + target.x + "," + target.y + "," + target.z
            + " fov=" + fov + " roll=" + roll + "]";
    }
}
