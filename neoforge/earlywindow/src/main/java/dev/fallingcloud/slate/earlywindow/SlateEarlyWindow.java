package dev.fallingcloud.slate.earlywindow;

import static org.lwjgl.opengl.GL32C.*;

import dev.fallingcloud.slate.earlywindow.scene.LoadingScene;
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
 * game and the continuation as the game's loading overlay) with its picture replaced by Slate's: {@link SlateScene}
 * for the Custom layout, the factory of {@link LoadingScene} for the Overhaul layout. FML's elements are swapped for
 * one element that paints the scene, before FML draws its first frame. Chosen by
 * {@link SlateEarlyWindowBootstrapper} when fml.toml names FML's default window; if anything here fails, FML's own
 * screen stays, in Slate's colours.
 */
public class SlateEarlyWindow extends DisplayWindow {

    static final String NAME = "slate_early_window";

    private SlateScene scene;
    /** The Overhaul layout's scene: made when the first frame is painted, null with another layout or once it failed. */
    private LoadingScene factory;
    private FmlFeed feed;
    private boolean overhaul, vanillaStyle;
    private String mcVersion = "", loaderVersion = "";
    /** Create's jar and the game's, being looked for while the window comes up; null with another layout. */
    private java.util.concurrent.CompletableFuture<ModJars> jars;
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
        overhaul = "OVERHAUL".equals(SlateLook.layout());
        vanillaStyle = overhaul && SlateLook.vanillaStyle();
        // With the Vanilla style the game's own loading colour stays (red, or black if the player chose that).
        if (!vanillaStyle) SlateLook.recolour();
        gameThread = Thread.currentThread();
        // FML's renderer skips a frame while it cannot take this lock: held until the scene is in, so FML's own
        // picture never shows first.
        Semaphore lock = null;
        try {
            mcVersion = arg(arguments, "--fml.mcVersion", "1.21.1");
            loaderVersion = arg(arguments, "--fml.neoForgeVersion", "").split("-")[0];
            scene = new SlateScene(SlateLook.accent(), SlateLook.radius(), SlateLook.pixelFont(), mcVersion, loaderVersion);
            if (overhaul) {
                final String neoForm = arg(arguments, "--fml.neoFormVersion", "");
                jars = java.util.concurrent.CompletableFuture.supplyAsync(() -> ModJars.find(mcVersion, neoForm));
            }
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
                    // The factory moves: 60 frames a second instead of FML's 20.
                    if (overhaul) FmlAccess.pace(this, 16);
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
            final int w = canvas.scaledWidth(), h = canvas.scaledHeight();
            if (overhaul && factory(canvas)) {
                try {
                    feed.frame();
                    factory.draw(w, h, SlateScene.scaleFor(w, h), true, 1f, feed);
                    if (feed.overall() >= 1f) feed.finished();
                    return;
                } catch (final RuntimeException | LinkageError e) {
                    SlateLook.LOGGER.error("[Slate] the Overhaul start-up screen failed, the Custom one takes over", e);
                    dropFactory();
                    // The Custom screen has no Vanilla style: there NeoForge's own is what is left.
                    if (vanillaStyle) throw e;
                }
            }
            scene.draw(w, h);
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

    /** The Overhaul scene, made at the first frame (FML has chosen its colours by then); false once it has failed. */
    private boolean factory(final RenderElement.DisplayContext canvas) {
        if (factory != null) return true;
        if (feed != null) return false;
        feed = new FmlFeed();
        try {
            final var bg = canvas.colourScheme().background();
            final int background = 0xFF000000 | (bg.red() & 0xFF) << 16 | (bg.green() & 0xFF) << 8 | bg.blue() & 0xFF;
            final String font = vanillaStyle ? "monocraft" : SlateLook.pixelFont();
            // With Create among the mods the factory is built of Create's blocks. The search for it began with the
            // window; if it is not done by now, a moment is waited for it, and no longer.
            ModJars mods = null;
            try {
                if (jars != null) mods = jars.get(600, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
                SlateLook.LOGGER.debug("[Slate] no Create for the start-up screen: {}", e.toString());
            }
            try {
                factory = new LoadingScene(
                    new LoadingScene.Look(SlateLook.accent(), vanillaStyle, background, font, "Minecraft " + mcVersion,
                        loaderVersion.isEmpty() ? "" : "NeoForge " + loaderVersion),
                    SlateScene.bytes("/slate_earlywindow/" + LoadingScene.fontFile(font)), System::nanoTime, mods);
            } finally {
                if (mods != null) mods.close();
            }
            return true;
        } catch (final RuntimeException | LinkageError e) {
            SlateLook.LOGGER.error("[Slate] the Overhaul start-up screen could not be made, the Custom one takes over", e);
            if (vanillaStyle) throw e;
            return false;
        }
    }

    private void dropFactory() {
        final LoadingScene old = factory;
        factory = null;
        if (old == null) return;
        try {
            old.dispose();
        } catch (final RuntimeException | LinkageError e) {
            // It failed while drawing; what it could not free goes with the context.
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
        if (feed != null) feed.finished();
        if (scene != null && installed) {
            try {
                dropFactory();
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
