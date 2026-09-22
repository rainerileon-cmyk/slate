package dev.fallingcloud.slate.core.event;

import dev.fallingcloud.slate.core.Slate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A minimal typed event: a list of listeners of type {@code T} plus helpers to invoke them.
 * Listener exceptions are logged and swallowed so one broken handler cannot take a screen down.
 */
public final class Event<T> {

    private final String name;
    private final List<T> listeners = new CopyOnWriteArrayList<>();

    public Event(final String name) { this.name = name; }

    public void register(final T listener) { listeners.add(listener); }

    public void unregister(final T listener) { listeners.remove(listener); }

    public List<T> listeners() { return listeners; }

    /** Calls every listener. */
    public void invoke(final Consumer<T> call) {
        for (final T l : listeners) {
            try {
                call.accept(l);
            } catch (final Exception e) {
                Slate.LOGGER.error("[Slate] listener of {} threw", name, e);
            }
        }
    }

    /** Calls listeners in order until one returns true (consumed). */
    public boolean invokeUntilConsumed(final Predicate<T> call) {
        for (final T l : listeners) {
            try {
                if (call.test(l)) return true;
            } catch (final Exception e) {
                Slate.LOGGER.error("[Slate] listener of {} threw", name, e);
            }
        }
        return false;
    }
}
