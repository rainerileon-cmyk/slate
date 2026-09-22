package dev.fallingcloud.slate.core.gfx;

import dev.fallingcloud.slate.core.theme.Theme;

/**
 * A float that eases toward a target in real time. {@code set(target)} starts a tween from the current
 * value; {@code get()} samples it. Honours {@link Theme#motion()}: 0 snaps instantly, 2 doubles durations.
 * Cheap enough to give every widget three of them (hover, press, focus).
 */
public final class Anim {

    private float from, to, value;
    private long startMs;
    private int durationMs;
    private Ease ease;

    public Anim(final float initial) {
        this(initial, 150, Ease.OUT_CUBIC);
    }

    public Anim(final float initial, final int durationMs, final Ease ease) {
        this.from = this.to = this.value = initial;
        this.durationMs = durationMs;
        this.ease = ease;
        this.startMs = 0;
    }

    public Anim duration(final int ms) { this.durationMs = ms; return this; }

    public Anim ease(final Ease e) { this.ease = e; return this; }

    /** Tween toward {@code target} (no-op when already heading there). */
    public void set(final float target) {
        if (target == to) return;
        from = get();
        to = target;
        startMs = Clock.nowMs();
    }

    /** Tween toward {@code target} with a one-off duration. */
    public void set(final float target, final int ms) {
        if (target == to) return;
        from = get();
        to = target;
        durationMs = ms;
        startMs = Clock.nowMs();
    }

    /** Jump without animating. */
    public void snap(final float v) {
        from = to = value = v;
        startMs = 0;
    }

    public float target() { return to; }

    public boolean isAnimating() { return get() != to; }

    public float get() {
        if (startMs == 0 || from == to) return value = to;
        final float motion = Theme.current().motion();
        if (motion <= 0) return value = to;
        final float dur = Math.max(1, durationMs * motion);
        final float t = (Clock.nowMs() - startMs) / dur;
        if (t >= 1) { startMs = 0; return value = to; }
        return value = from + (to - from) * ease.apply(t);
    }

    /** Convenience for boolean states. */
    public void set(final boolean on) { set(on ? 1f : 0f); }
}
