package dev.fallingcloud.slate.multiplayer.hub;

/** A token bucket: {@code perSecond} tokens refill continuously, up to {@code burst}. */
public final class RateBucket {

    private final double perSecond;
    private final double burst;
    private double tokens;
    private long lastMs;

    public RateBucket(final double perSecond, final double burst) {
        this.perSecond = Math.max(0.1, perSecond);
        this.burst = Math.max(1, burst);
        this.tokens = this.burst;
        this.lastMs = System.currentTimeMillis();
    }

    /** Take one token; false when empty. */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    public boolean tryAcquire(final double n) {
        final long now = System.currentTimeMillis();
        tokens = Math.min(burst, tokens + (now - lastMs) / 1000.0 * perSecond);
        lastMs = now;
        if (tokens < n) return false;
        tokens -= n;
        return true;
    }
}
