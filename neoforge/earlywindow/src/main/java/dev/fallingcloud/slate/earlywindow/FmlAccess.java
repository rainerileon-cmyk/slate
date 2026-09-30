package dev.fallingcloud.slate.earlywindow;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.earlydisplay.EarlyFramebuffer;
import net.neoforged.fml.earlydisplay.RenderElement;

/**
 * The private parts of FML's {@link DisplayWindow} the start-up screen needs (FML's early display is an automatic
 * module, so they are open to reflection). A missing one fails the class's initialisation, which callers catch as a
 * {@link LinkageError} and leave NeoForge's own screen in place.
 */
final class FmlAccess {

    static final Field ELEMENTS = field("elements");
    static final Field RENDER_LOCK = field("renderLock");
    /** Set last by FML's renderer when it starts, after its elements are built. */
    static final Field WINDOW_TICK = field("windowTick");
    static final Field CONTEXT = field("context");
    static final Field FRAMEBUFFER = field("framebuffer");
    static final Field FB_WIDTH = field("fbWidth");
    static final Field FB_HEIGHT = field("fbHeight");
    static final Field WINDOW = field("window");
    static final Constructor<EarlyFramebuffer> NEW_FRAMEBUFFER;
    static final Method ACTIVATE;

    static {
        try {
            NEW_FRAMEBUFFER = EarlyFramebuffer.class.getDeclaredConstructor(RenderElement.DisplayContext.class);
            NEW_FRAMEBUFFER.setAccessible(true);
            ACTIVATE = EarlyFramebuffer.class.getDeclaredMethod("activate");
            ACTIVATE.setAccessible(true);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * FML's renderer draws 20 frames a second; a scene that moves wants three times that. Runs {@code window}'s render
     * function every {@code millis} instead, on FML's own scheduler, and leaves FML the handle it cancels when the game
     * takes the window over. Call with the window's render lock held. False (and nothing changed) when FML is not built
     * the way this expects.
     */
    static boolean pace(final DisplayWindow window, final long millis) {
        try {
            final Field scheduler = DisplayWindow.class.getDeclaredField("renderScheduler");
            scheduler.setAccessible(true);
            final Method render = DisplayWindow.class.getDeclaredMethod("renderThreadFunc");
            render.setAccessible(true);
            final java.util.concurrent.ScheduledExecutorService service = (java.util.concurrent.ScheduledExecutorService) scheduler.get(window);
            final java.util.concurrent.ScheduledFuture<?> old = (java.util.concurrent.ScheduledFuture<?>) WINDOW_TICK.get(window);
            if (service == null || old == null) return false;
            final Runnable tick = () -> {
                try {
                    render.invoke(window);
                } catch (final ReflectiveOperationException | RuntimeException e) {
                    // FML's function catches what its painting throws; what is left is not worth a log line a frame.
                }
            };
            final java.util.concurrent.ScheduledFuture<?> faster = service.scheduleAtFixedRate(tick, millis, millis, java.util.concurrent.TimeUnit.MILLISECONDS);
            WINDOW_TICK.set(window, faster);
            old.cancel(false);
            return true;
        } catch (final ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static Field field(final String name) {
        try {
            final Field f = DisplayWindow.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (final NoSuchFieldException e) {
            throw new IllegalStateException("DisplayWindow." + name, e);
        }
    }

    private FmlAccess() {}
}
