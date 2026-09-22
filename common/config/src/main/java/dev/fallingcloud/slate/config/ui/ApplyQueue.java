package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.core.gfx.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Debounces expensive applies (GUI rescale, mipmap texture reload, Sodium renderer reloads, file
 * writes from text fields) so a slider drag runs the work once, ~350 ms after the last change. Ticked
 * from the client tick; flushed when the settings screen closes.
 */
public final class ApplyQueue {

    private record Pending(long dueMs, Runnable run) {}

    private static final Map<String, Pending> PENDING = new LinkedHashMap<>();

    public static synchronized void later(final String key, final int delayMs, final Runnable run) {
        PENDING.put(key, new Pending(Clock.nowMs() + delayMs, run));
    }

    public static void later(final String key, final Runnable run) {
        later(key, 350, run);
    }

    public static void tick() {
        final List<Runnable> due = new ArrayList<>();
        synchronized (ApplyQueue.class) {
            if (PENDING.isEmpty()) return;
            final long now = Clock.nowMs();
            PENDING.entrySet().removeIf(e -> { if (e.getValue().dueMs <= now) { due.add(e.getValue().run); return true; } return false; });
        }
        run(due);
    }

    public static void flush() {
        final List<Runnable> all = new ArrayList<>();
        synchronized (ApplyQueue.class) {
            for (final Pending p : PENDING.values()) all.add(p.run);
            PENDING.clear();
        }
        run(all);
    }

    private static void run(final List<Runnable> list) {
        for (final Runnable r : list) {
            try { r.run(); } catch (final Exception e) { SlateConfig.LOGGER.error("[Slate Config] deferred apply failed", e); }
        }
    }

    private ApplyQueue() {}
}
