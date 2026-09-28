package dev.fallingcloud.slate.earlywindow;

import static org.lwjgl.opengl.GL32C.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.earlydisplay.EarlyFramebuffer;
import net.neoforged.fml.earlydisplay.RenderElement;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

/**
 * NeoForge's start-up window drawn by Slate: FML's own window (everything it does, the window, the handover to the
 * game and the continuation as the game's loading overlay) with its picture replaced by {@link SlateScene}. FML's
 * elements are swapped for one element that paints the scene, before FML draws its first frame. Chosen by
 * {@link SlateEarlyWindowBootstrapper} when fml.toml names FML's default window; if anything here fails, FML's own
 * screen stays, in Slate's colours.
 */
public class SlateEarlyWindow extends DisplayWindow {

    static final String NAME = "slate_early_window";

    private SlateScene scene;
    /** The thread the game runs on: FML's renderer paints on its own thread until the game takes the window over. */
    private Thread gameThread;
    private volatile boolean installed;
    private boolean failed;
    private List<RenderElement> fmlElements;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Runnable initialize(final String[] arguments) {
        SlateLook.restoreSelection();
        SlateLook.recolour();
        gameThread = Thread.currentThread();
        // FML's renderer skips a frame while it cannot take this lock: held until the scene is in, so FML's own
        // picture never shows first.
        Semaphore lock = null;
        try {
            scene = new SlateScene(SlateLook.accent(), SlateLook.radius(), SlateLook.pixelFont(),
                arg(arguments, "--fml.mcVersion", "1.21.1"), arg(arguments, "--fml.neoForgeVersion", "").split("-")[0]);
            lock = (Semaphore) FmlAccess.RENDER_LOCK.get(this);
            lock.acquireUninterruptibly();
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError e) {
            SlateLook.LOGGER.warn("[Slate] NeoForge's own start-up screen stays: {}", e.toString());
            lock = null;
        }
        final Runnable tick;
        try {
            tick = super.initialize(arguments);
        } catch (final RuntimeException | Error e) {
            if (lock != null) lock.release();
            throw e;
        }
        final Semaphore held = lock;
        final Thread installer = new Thread(() -> install(held), "Slate early window");
        installer.setDaemon(true);
        installer.start();
        return tick;
    }

    /** Swaps FML's elements for the scene once FML's renderer has built them, then lets it draw. */
    @SuppressWarnings("unchecked")
    private void install(final Semaphore lock) {
        if (lock != null) {
            try {
                final long until = System.nanoTime() + 20_000_000_000L;
                while (FmlAccess.WINDOW_TICK.get(this) == null) {
                    if (System.nanoTime() > until) break;
                    Thread.sleep(2);
                }
                if (FmlAccess.WINDOW_TICK.get(this) != null) {
                    fmlElements = (List<RenderElement>) FmlAccess.ELEMENTS.get(this);
                    FmlAccess.ELEMENTS.set(this, new ArrayList<>(List.of(SceneElement.create(this::paint))));
                    installed = true;
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final ReflectiveOperationException | RuntimeException | LinkageError e) {
                SlateLook.LOGGER.warn("[Slate] NeoForge's own start-up screen stays: {}", e.toString());
            } finally {
                lock.release();
            }
        }
        if (installed) return;
        // Not drawn by Slate: FML's screen in Slate's colours, without the running fox.
        final long until = System.nanoTime() + 30_000_000_000L;
        while (System.nanoTime() < until && !SlateLook.dropFox(this)) {
            try {
                Thread.sleep(5);
            } catch (final InterruptedException e) {
                return;
            }
        }
    }

    private void paint(final RenderElement.DisplayContext context) {
        if (failed) return;
        try {
            final RenderElement.DisplayContext canvas = fitCanvas(context);
            scene.draw(canvas.scaledWidth(), canvas.scaledHeight());
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError e) {
            // FML's own elements take over from the next frame.
            failed = true;
            SlateLook.LOGGER.error("[Slate] start-up screen failed, NeoForge's own takes over", e);
            try {
                FmlAccess.ELEMENTS.set(this, new ArrayList<>(fmlElements));
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                // Nothing left to draw with: the window stays blank until the game takes over.
            }
        }
    }

    /**
     * FML paints on a fixed 854 x 480 canvas and stretches it over the window, nearest-neighbour. The scene gets a
     * canvas the size of the window instead, so it is drawn 1:1: FML's context and framebuffer are replaced with ones of
     * the window's size, and both ways FML shows the canvas (its window blit and the game's loading overlay) size
     * themselves from those. Called while FML's framebuffer is bound for painting; binds the new one in its place.
     */
    private RenderElement.DisplayContext fitCanvas(final RenderElement.DisplayContext context) throws ReflectiveOperationException {
        final int w, h;
        if (Thread.currentThread() == gameThread) {
            // In game FML's resize callbacks are gone with the window; this is the main thread, so GLFW can be asked.
            try (MemoryStack stack = MemoryStack.stackPush()) {
                final IntBuffer pw = stack.mallocInt(1), ph = stack.mallocInt(1);
                GLFW.glfwGetFramebufferSize(FmlAccess.WINDOW.getLong(this), pw, ph);
                w = pw.get(0);
                h = ph.get(0);
            }
        } else {
            w = FmlAccess.FB_WIDTH.getInt(this);
            h = FmlAccess.FB_HEIGHT.getInt(this);
        }
        if (w < 320 || h < 240 || w > 16384 || h > 16384) return context;          // minimised, or not known yet
        if (context.width() == w && context.height() == h && context.scale() == 1) return context;
        final RenderElement.DisplayContext next = new RenderElement.DisplayContext(w, h, 1,
            context.elementShader(), context.colourScheme(), context.performance());
        final EarlyFramebuffer old = (EarlyFramebuffer) FmlAccess.FRAMEBUFFER.get(this);
        final int active = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        final int bound = glGetInteger(GL_TEXTURE_BINDING_2D);
        final EarlyFramebuffer framebuffer = FmlAccess.NEW_FRAMEBUFFER.newInstance(next);
        glBindTexture(GL_TEXTURE_2D, bound);
        glActiveTexture(active);
        FmlAccess.ACTIVATE.invoke(framebuffer);
        glViewport(0, 0, w, h);
        FmlAccess.CONTEXT.set(this, next);
        FmlAccess.FRAMEBUFFER.set(this, framebuffer);
        old.close();
        return next;
    }

    /** The game's loading overlay hands over the Mojang logo for FML's element; the scene has no place for it. */
    @Override
    public void addMojangTexture(final int textureId) {
        if (!installed || failed) super.addMojangTexture(textureId);
    }

    @Override
    public void close() {
        if (scene != null && installed) {
            try {
                scene.dispose();
            } catch (final RuntimeException | LinkageError e) {
                SlateLook.LOGGER.warn("[Slate] start-up screen cleanup: {}", e.toString());
            }
        }
        super.close();
    }

    /**
     * FML's version adds the read edge to NeoForge on {@code getClass().getModule()}, which for this subclass is Slate's
     * module, and only a module's own code may add reads to it: so the same steps here. Read NeoForge, then keep its
     * in-game loading overlay's factory in the window's (private) {@code loadingOverlay} for the handover.
     */
    @Override
    public void updateModuleReads(final ModuleLayer layer) {
        final Module neoforge = layer.findModule("neoforge").orElseThrow();
        SlateEarlyWindow.class.getModule().addReads(neoforge);
        try {
            final Class<?> overlay = Class.forName(neoforge, "net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay");
            Method factory = null;
            for (final Method m : overlay.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getName().equals("newInstance")) factory = m;
            }
            final Field field = DisplayWindow.class.getDeclaredField("loadingOverlay");
            field.setAccessible(true);
            field.set(this, factory);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Slate early window: NeoForge's loading overlay not found", e);
        }
    }

    private static String arg(final String[] args, final String name, final String fallback) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (name.equals(args[i])) return args[i + 1];
        }
        return fallback;
    }
}
