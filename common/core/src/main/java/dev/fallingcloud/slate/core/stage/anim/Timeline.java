package dev.fallingcloud.slate.core.stage.anim;

import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * A chain of {@link CameraPath} segments played back to back. Time is fed in by the stage (so a hidden stage pauses),
 * durations follow {@code Theme.motion()} (2 = half speed), and {@code motion == 0} jumps straight to the end pose of
 * the last segment on the first update, as the accessibility rule requires.
 */
public final class Timeline {

    private final List<CameraPath> segments = new ArrayList<>();
    private int index;
    private float elapsedMs;
    private boolean finished = true;
    private boolean started;
    @Nullable private Runnable onFinished;

    public Timeline add(final CameraPath segment) {
        segments.add(segment);
        return this;
    }

    public Timeline clear() {
        segments.clear();
        finished = true;
        started = false;
        return this;
    }

    public Timeline onFinished(@Nullable final Runnable r) { this.onFinished = r; return this; }

    public boolean isEmpty() { return segments.isEmpty(); }

    public boolean finished() { return finished; }

    public boolean playing() { return !finished; }

    /** 0..1 over the whole chain (by segment count and time). */
    public float progress() {
        if (finished || segments.isEmpty()) return 1f;
        final float dur = segments.get(index).durationMs();
        final float inSegment = dur <= 0f ? 1f : Math.min(1f, elapsedMs / dur);
        return (index + inSegment) / segments.size();
    }

    /** Restarts from the first segment. */
    public void play() {
        index = 0;
        elapsedMs = 0f;
        started = false;
        finished = segments.isEmpty();
    }

    /** Jumps to the end pose immediately. */
    public void skip(final CameraPose out) {
        if (segments.isEmpty()) { finished = true; return; }
        final CameraPath last = segments.get(segments.size() - 1);
        if (!started) { last.begin(out); started = true; }
        last.sample(1f, out);
        finish();
    }

    /**
     * Advances by {@code deltaMs} of stage time and writes the current pose into {@code out} (which also serves as
     * the "current pose" a segment may start from). Returns false when nothing is playing.
     */
    public boolean advance(final float deltaMs, final CameraPose out) {
        if (finished) return false;
        final float motion = Theme.current().motion();
        if (motion <= 0f) { skip(out); return true; }
        if (!started) { segments.get(0).begin(out); started = true; }
        elapsedMs += deltaMs / motion;
        while (true) {
            final CameraPath seg = segments.get(index);
            final float dur = seg.durationMs();
            if (elapsedMs >= dur && index < segments.size() - 1) {
                seg.sample(1f, out);
                elapsedMs -= dur;
                index++;
                segments.get(index).begin(out);
                continue;
            }
            final float t = dur <= 0f ? 1f : Math.min(1f, elapsedMs / dur);
            seg.sample(seg.ease().apply(t), out);
            if (t >= 1f && index == segments.size() - 1) finish();
            return true;
        }
    }

    private void finish() {
        finished = true;
        if (onFinished != null) onFinished.run();
    }
}
