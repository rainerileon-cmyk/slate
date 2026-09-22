package dev.fallingcloud.slate.core.gfx;

/** Wall clock for UI animation. Real time, independent of game ticks and pause. */
public final class Clock {

    private static long lastFrameNanos = System.nanoTime();
    private static float frameDeltaMs = 16f;

    public static long nowMs() { return System.nanoTime() / 1_000_000L; }

    public static long nowNanos() { return System.nanoTime(); }

    /** Called once per rendered frame by Core's render hook. */
    public static void onFrame() {
        final long now = System.nanoTime();
        frameDeltaMs = Math.min(100f, (now - lastFrameNanos) / 1_000_000f);
        lastFrameNanos = now;
    }

    /** Milliseconds since the previous frame, clamped to 100. */
    public static float frameDelta() { return frameDeltaMs; }

    private Clock() {}
}
