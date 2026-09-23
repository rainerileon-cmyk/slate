package dev.fallingcloud.slate.core.screen;

import dev.fallingcloud.slate.core.Slate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Replaces vanilla screens with Slate ones at {@code Minecraft.setScreen}. Matching is by exact class,
 * so a subclass another mod made is left alone. The replacement function receives the original (so it
 * can copy its parent/state) and returns the screen to show, or {@code null} to keep the original.
 */
public final class ScreenSwaps {

    private static final Map<Class<? extends Screen>, Function<Screen, Screen>> SWAPS = new LinkedHashMap<>();
    /** Swaps keyed by class NAME, for soft-dependency screens that must not be class-loaded at registration. */
    private static final Map<String, Function<Screen, Screen>> SWAPS_BY_NAME = new LinkedHashMap<>();
    private static boolean suspended;

    public static void register(final Class<? extends Screen> vanilla, final Function<Screen, Screen> replacement) {
        SWAPS.put(vanilla, replacement);
    }

    public static void unregister(final Class<? extends Screen> vanilla) {
        SWAPS.remove(vanilla);
    }

    /**
     * Like {@link #register} but by fully qualified class name: nothing is loaded now, so a soft
     * dependency's screen (Sodium's video settings, ...) is only touched when it is actually opened.
     */
    public static void registerByName(final String className, final Function<Screen, Screen> replacement) {
        SWAPS_BY_NAME.put(className, replacement);
    }

    public static void unregisterByName(final String className) {
        SWAPS_BY_NAME.remove(className);
    }

    public static boolean has(final Class<? extends Screen> cls) { return SWAPS.containsKey(cls); }

    /** Temporarily disable swapping (e.g. "open the vanilla screen" buttons). */
    public static void runUnswapped(final Runnable r) {
        suspended = true;
        try { r.run(); } finally { suspended = false; }
    }

    /** Called from the setScreen hook. Returns the screen to actually show. */
    @Nullable
    public static Screen apply(@Nullable final Screen screen) {
        if (screen == null || suspended || (SWAPS.isEmpty() && SWAPS_BY_NAME.isEmpty())) return screen;
        Function<Screen, Screen> f = SWAPS.get(screen.getClass());
        if (f == null) f = SWAPS_BY_NAME.get(screen.getClass().getName());
        if (f == null) return screen;
        try {
            final Screen s = f.apply(screen);
            return s == null ? screen : s;
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] screen swap for {} failed; showing vanilla", screen.getClass().getName(), e);
            return screen;
        }
    }

    private ScreenSwaps() {}
}
