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
