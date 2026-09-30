package dev.fallingcloud.slate.core.stage.anim;

import dev.fallingcloud.slate.core.gfx.Ease;
import org.jetbrains.annotations.Nullable;

/** A straight move from one pose to another (position, target, fov and roll all lerp). */
public final class LinePath implements CameraPath {

    @Nullable private final CameraPose from;
    private final CameraPose to;
    private final CameraPose start = new CameraPose();
    private final float durationMs;
    private final Ease ease;

    /** {@code from == null} starts from wherever the camera is when the segment begins. */
    public LinePath(@Nullable final CameraPose from, final CameraPose to, final float durationMs, final Ease ease) {
        this.from = from == null ? null : from.copy();
        this.to = to.copy();
        this.durationMs = Math.max(0f, durationMs);
        this.ease = ease == null ? Ease.OUT_CUBIC : ease;
        if (this.from != null) start.set(this.from);
    }

    @Override
    public void begin(final CameraPose current) {
        start.set(from != null ? from : current);
    }

    @Override
    public void sample(final float t, final CameraPose out) {
        out.lerp(start, to, t);
    }

    @Override
    public float durationMs() { return durationMs; }

    @Override
    public Ease ease() { return ease; }
}
