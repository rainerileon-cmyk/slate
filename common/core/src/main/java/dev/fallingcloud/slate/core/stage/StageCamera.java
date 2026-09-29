package dev.fallingcloud.slate.core.stage;

import dev.fallingcloud.slate.core.stage.anim.CameraPose;
import dev.fallingcloud.slate.core.stage.anim.IdleMotion;
import dev.fallingcloud.slate.core.stage.anim.Timeline;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * A perspective camera: eye, target, vertical fov, roll. Its {@link #pose() rest pose} is what a scene sets and what
 * a {@link Timeline} animates (the intro); an {@link IdleMotion} is layered on top every frame without touching the
 * rest pose. The stage calls {@link #update} once per frame with its pause-aware delta, which recomputes the view and
 * projection matrices used for rendering and for {@link #ray picking}.
 */
public final class StageCamera {

    private final CameraPose pose = new CameraPose();
    private final CameraPose rendered = new CameraPose();
    private final Timeline timeline = new Timeline();
    private IdleMotion idle = IdleMotion.NONE;
    private float near = 0.05f, far = 256f;
    private float aspect = 1f;
    private float timeMs;

    private final Matrix4f view = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f viewProjection = new Matrix4f();
    private final Matrix4f invViewProjection = new Matrix4f();
    private final Vector3f up = new Vector3f(0, 1, 0);
    private final Vector3f dir = new Vector3f();
    private final Vector4f a = new Vector4f();
    private final Vector4f b = new Vector4f();

    // ------------------------------------------------------------------ rest pose

    /** The rest pose (mutable; changes apply next frame). */
    public CameraPose pose() { return pose; }

    public StageCamera at(final float x, final float y, final float z) { pose.position.set(x, y, z); return this; }

    public StageCamera lookAt(final float x, final float y, final float z) { pose.target.set(x, y, z); return this; }

    public StageCamera fov(final float degrees) { pose.fov = Mth.clamp(degrees, 5f, 150f); return this; }

    public StageCamera roll(final float degrees) { pose.roll = degrees; return this; }

    public StageCamera clip(final float near, final float far) { this.near = Math.max(0.001f, near); this.far = Math.max(this.near + 0.01f, far); return this; }

    public StageCamera idle(final IdleMotion motion) { this.idle = motion == null ? IdleMotion.NONE : motion; return this; }

    public IdleMotion idle() { return idle; }

    /** The intro/move chain; {@code timeline().play()} after adding segments. */
    public Timeline timeline() { return timeline; }

    // ------------------------------------------------------------------ per frame

    /** Advances the timeline and idle motion by {@code deltaMs} and rebuilds the matrices for {@code aspect}. */
    public void update(final float deltaMs, final float aspect) {
        timeMs += deltaMs;
        this.aspect = aspect <= 0f ? 1f : aspect;
        timeline.advance(deltaMs, pose);
        if (timeline.finished()) idle.apply(pose, timeMs, rendered);
        else rendered.set(pose);
        computeMatrices();
    }

    private void computeMatrices() {
        projection.setPerspective((float) Math.toRadians(rendered.fov), aspect, near, far);
        dir.set(rendered.target).sub(rendered.position);
        if (dir.lengthSquared() < 1e-8f) dir.set(0, 0, -1);
        dir.normalize();
        // A look direction parallel to +Y has no well-defined "up": pick one.
        if (Math.abs(dir.y) > 0.999f) up.set(0, 0, -1);
        else up.set(0, 1, 0);
        view.identity();
        if (rendered.roll != 0f) view.rotateZ((float) Math.toRadians(rendered.roll));
        view.lookAt(rendered.position.x, rendered.position.y, rendered.position.z,
            rendered.position.x + dir.x, rendered.position.y + dir.y, rendered.position.z + dir.z, up.x, up.y, up.z);
        viewProjection.set(projection).mul(view);
        viewProjection.invert(invViewProjection);
    }

    /** The pose actually rendered this frame (rest pose plus idle motion). */
    public CameraPose rendered() { return rendered; }

    public Matrix4f view() { return view; }

    public Matrix4f projection() { return projection; }

    public float aspect() { return aspect; }

    public float near() { return near; }

    public float far() { return far; }

    /** Unprojects normalised device coordinates (-1..1, y up) into a world-space ray. */
    public void ray(final float ndcX, final float ndcY, final Vector3f originOut, final Vector3f dirOut) {
        a.set(ndcX, ndcY, -1f, 1f);
        invViewProjection.transform(a);
        a.div(a.w);
        b.set(ndcX, ndcY, 1f, 1f);
        invViewProjection.transform(b);
        b.div(b.w);
        originOut.set(a.x, a.y, a.z);
        dirOut.set(b.x - a.x, b.y - a.y, b.z - a.z);
        if (dirOut.lengthSquared() < 1e-12f) dirOut.set(0, 0, -1);
        else dirOut.normalize();
    }

    /** Projects a world point to normalised device coordinates; returns false when behind the camera. */
    public boolean project(final float x, final float y, final float z, final Vector3f ndcOut) {
        a.set(x, y, z, 1f);
        viewProjection.transform(a);
        if (a.w <= 1e-6f) return false;
        ndcOut.set(a.x / a.w, a.y / a.w, a.z / a.w);
        return true;
    }
}
