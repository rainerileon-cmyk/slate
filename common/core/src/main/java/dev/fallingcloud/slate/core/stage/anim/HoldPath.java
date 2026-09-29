package dev.fallingcloud.slate.core.stage.anim;

import dev.fallingcloud.slate.core.gfx.Ease;
import org.jetbrains.annotations.Nullable;

/** Stays at a pose (the current one when none is given) for a while: a beat between moves. */
public final class HoldPath implements CameraPath {

    @Nullable private final CameraPose pose;
    private final CameraPose current = new CameraPose();
    private final float durationMs;

    public HoldPath(@Nullable final CameraPose pose, final float durationMs) {
        this.pose = pose == null ? null : pose.copy();
        this.durationMs = Math.max(0f, durationMs);
    }

    @Override
    public void begin(final CameraPose currentPose) {
        current.set(currentPose);
    }

    @Override
    public void sample(final float t, final CameraPose out) {
        out.set(pose != null ? pose : current);
    }

    @Override
    public float durationMs() { return durationMs; }

    @Override
    public Ease ease() { return Ease.LINEAR; }
}
