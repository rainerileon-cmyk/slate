package dev.fallingcloud.slate.building.client.input;

import dev.fallingcloud.slate.building.SlateBuilding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;

/**
 * The one router for in-world input (no screen open), fed by the {@code mixin.core} client mixins on
 * {@code MouseHandler}, {@code Minecraft} and {@code KeyboardHandler}. Handlers run in {@link Handler#priority()}
 * order (higher first; equal priorities in registration order) and the first one returning {@code true} consumes
 * the event: vanilla (and every lower handler) never sees it. Priorities in use: wheel overlay 100, mode controller
 * 50, pick-block swap 10.
 *
 * <p>Events only fire while the player is in a world with no screen and no overlay (loading) open, and a
 * throwing handler is logged and skipped, never breaking vanilla input.
 *
 * <p>Semantics worth knowing:
 * <ul>
 *   <li>{@link Handler#onMouseButton}: raw GLFW button/action ({@code 1} press, {@code 0} release)/mods, before
 *       vanilla's key mappings see it. Let releases through (return false): a button held down before your handler
 *       took over (use / attack, or a key bound to a mouse button) must see its release, or its KeyMapping stays down;
 *       vanilla's release of a press you consumed is a harmless no-op. The same goes for {@link Handler#onKey}.</li>
 *   <li>{@link Handler#onScroll}: deltas already scaled like vanilla (discrete scrolling + sensitivity), so one
 *       notch is about ±1. Consuming stops hotbar scrolling.</li>
 *   <li>{@link Handler#onMouseLook}: the raw accumulated cursor deltas (screen pixels) for this frame, before
 *       sensitivity. Consuming keeps the camera still (the deltas are dropped), e.g. for a virtual wheel cursor.</li>
 *   <li>{@link Handler#onUse}: vanilla's right-click "use" is about to run (also its 4-tick repeat while held).
 *       Consuming cancels it and keeps vanilla's repeat delay, so it fires every 4 ticks while held, like vanilla.</li>
 *   <li>{@link Handler#onAttack}: a left-click attack / start of mining. Consuming cancels it (no swing).</li>
 *   <li>{@link Handler#suppressContinueAttack}: while true, holding left-click does not keep mining (vanilla also
 *       stops the current break progress).</li>
 *   <li>{@link Handler#onPickBlock}: middle-click pick. Consuming cancels vanilla's pick.</li>
 *   <li>{@link Handler#onKey}: raw GLFW key/scancode/action ({@code 1} press, {@code 2} repeat, {@code 0}
 *       release)/mods before vanilla's key mappings; consuming hides the key from vanilla entirely. Prefer
 *       {@code KeyMapping}s ({@link BuildKeys}) and {@link ExclusiveKeys} for rebindable actions.</li>
 * </ul>
 */
public final class BuildInput {

    /** An in-world input consumer. Every method defaults to "not consumed". */
    public interface Handler {
        /** Higher runs first. */
        default int priority() { return 0; }
        default boolean onMouseButton(final int button, final int action, final int mods) { return false; }
        default boolean onScroll(final double dx, final double dy) { return false; }
        /** {@code true} = the camera does not turn this frame. */
        default boolean onMouseLook(final double dx, final double dy) { return false; }
        /** {@code true} = cancel vanilla's right-click use. */
        default boolean onUse() { return false; }
        /** {@code true} = cancel vanilla's attack / start of mining. */
        default boolean onAttack() { return false; }
        default boolean suppressContinueAttack() { return false; }
        default boolean onPickBlock() { return false; }
        default boolean onKey(final int key, final int scancode, final int action, final int mods) { return false; }
    }

    private static final List<Handler> HANDLERS = new CopyOnWriteArrayList<>();

    /** Adds a handler (idempotent). */
    public static void register(final Handler h) {
        synchronized (HANDLERS) {
            if (HANDLERS.contains(h)) return;
            final List<Handler> sorted = new ArrayList<>(HANDLERS);
            sorted.add(h);
            // Stable sort: equal priorities keep registration order.
            sorted.sort(Comparator.comparingInt(Handler::priority).reversed());
            HANDLERS.clear();
            HANDLERS.addAll(sorted);
        }
    }

    public static void unregister(final Handler h) {
        HANDLERS.remove(h);
    }

    // ---- dispatch (called by the mixins; each returns true when a handler consumed the event) ----

    public static boolean fireMouseButton(final int button, final int action, final int mods) {
        return dispatch("onMouseButton", h -> h.onMouseButton(button, action, mods));
    }

    public static boolean fireScroll(final double dx, final double dy) {
        return dispatch("onScroll", h -> h.onScroll(dx, dy));
    }

    public static boolean fireMouseLook(final double dx, final double dy) {
        return dispatch("onMouseLook", h -> h.onMouseLook(dx, dy));
    }

    public static boolean fireUse() {
        return dispatch("onUse", Handler::onUse);
    }

    public static boolean fireAttack() {
        return dispatch("onAttack", Handler::onAttack);
    }

    public static boolean fireSuppressContinueAttack() {
        return dispatch("suppressContinueAttack", Handler::suppressContinueAttack);
    }

    public static boolean firePickBlock() {
        return dispatch("onPickBlock", Handler::onPickBlock);
    }

    public static boolean fireKey(final int key, final int scancode, final int action, final int mods) {
        return dispatch("onKey", h -> h.onKey(key, scancode, action, mods));
    }

    /** Whether in-world input should be routed at all right now. */
    public static boolean active() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && mc.getOverlay() == null && mc.player != null && mc.level != null;
    }

    private static boolean dispatch(final String what, final Predicate<Handler> call) {
        if (HANDLERS.isEmpty() || !active()) return false;
        for (final Handler h : HANDLERS) {
            try {
                if (call.test(h)) return true;
            } catch (final RuntimeException e) {
                SlateBuilding.LOGGER.error("[Slate Building] input handler {} failed in {}", h.getClass().getName(), what, e);
            }
        }
        return false;
    }

    private BuildInput() {}
}
