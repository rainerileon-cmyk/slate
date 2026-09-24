package dev.fallingcloud.slate.building.client.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Immediate-mode submissions of the renderers, split by when they were made (see {@link BuildingRender}):
 * <ul>
 *   <li>made during a client tick → kept and drawn in every frame until the next tick starts (a tick-driven
 *       submitter gets a steady picture at any frame rate);</li>
 *   <li>made anywhere else (a render listener, {@link BuildingRender#FRAME}, a HUD callback) → drawn by the next
 *       world render pass, then dropped.</li>
 * </ul>
 * Render thread only.
 */
final class Buckets<T> {

    private final List<T> tick = new ArrayList<>();
    private final List<T> frame = new ArrayList<>();

    void add(final T item) {
        (BuildingRender.inTick() ? tick : frame).add(item);
    }

    /** A client tick starts: last tick's submissions are replaced by this tick's. */
    void beginTick() {
        tick.clear();
    }

    /** A world render pass has drawn: frame submissions are consumed. */
    void endPass() {
        frame.clear();
    }

    boolean isEmpty() {
        return tick.isEmpty() && frame.isEmpty();
    }

    int size() {
        return tick.size() + frame.size();
    }

    /** Tick submissions first, then frame submissions (later ones draw on top of earlier ones). */
    List<T> snapshot() {
        final List<T> out = new ArrayList<>(tick.size() + frame.size());
        out.addAll(tick);
        out.addAll(frame);
        return out;
    }

    void clear() {
        tick.clear();
        frame.clear();
    }
}
