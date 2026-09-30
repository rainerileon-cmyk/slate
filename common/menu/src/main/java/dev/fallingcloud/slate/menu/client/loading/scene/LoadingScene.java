package dev.fallingcloud.slate.menu.client.loading.scene;

import static org.lwjgl.opengl.GL32C.*;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.function.LongSupplier;
import org.lwjgl.system.MemoryUtil;

/**
 * The Overhaul loading screen: a small factory that does the loading. On the far left a tank of lava stands as high
 * as the memory in use; ingots come onto the first belt one after the other, all the while, and sooner for a moment
 * whenever a task is done; the press stamps each into a sheet, which the second belt carries up and into the store
 * at the right. The factory is at work from the first frame to the last. How full the store is, of those very
 * sheets, is how far the whole loading has come. Above it all the game's logo, where and as large as the Overhaul
 * main menu has it, so that the one screen becomes the other without a jump; the version at the top left, and in words
 * what the belt shows: the task, the latest line of the log, the memory to the megabyte.
 *
 * <p>The factory is built of the Create mod's own blocks when the player has Create ({@link CreateFactory}: its
 * models and textures are read from the installed mod, nothing of it is in Slate), and of the scene's own otherwise
 * ({@link OwnFactory}).</p>
 *
 * <p>Drawn in plain OpenGL 3.2, because it has to run where nothing of the game exists yet: in NeoForge's start-up
 * window. The loading overlay in the game draws the very same scene, so this code knows neither the game nor a mod
 * loader: what is loading comes in through a {@link Feed}, the looks through a {@link Look}.</p>
 */
public final class LoadingScene {

    /** What the scene shows. Asked every frame, on the thread that draws. */
    public interface Feed {

        /** How far all of the loading has come, 0 to 1; negative while nobody knows. */
        float overall();

        /** The task at hand, in words. */
        String task();

        /** How far the task at hand has come, 0 to 1; negative while its length is not known. */
        float taskProgress();

        /** The same in the task's own numbers ("13 / 30"), or null. */
        String taskCount();

        /** How many tasks have been finished since the start: every one more sends a sheet to the store. */
        long tasksDone();

        /** A second line under the task (the latest line of the log), or null. */
        String detail();

        long memoryUsed();

        long memoryMax();
    }

    /**
     * How the scene looks.
     *
     * @param accent     Slate's accent colour, ARGB: the bars, the needle, the marks
     * @param vanilla    the Vanilla style: the game's own colours instead of Slate's
     * @param background the colour behind everything in the Vanilla style (the game's loading colour), ARGB
     * @param font       the heading font's key: {@code pixeloid}, {@code monocraft} or {@code pixelify}
     * @param version    the first line at the top left
     * @param loader     the second line at the top left, or empty
     */
    public record Look(int accent, boolean vanilla, int background, String font, String version, String loader) {}

    /** The press: how long it takes down, and up again, in seconds. */
    static final float PRESS_DOWN = 0.11f, PRESS_UP = 0.24f;
    /** Seconds from one ingot to the next, and how far the last has to be on its way before the next may come. */
    private static final float EVERY = 0.95f, APART = 0.9f;
    /** Blocks between the things that are on the belts when the scene opens. */
    private static final float STOCKED = 1.75f;
    private static final int MAX_WAITING = 5, MAX_ITEMS = 16;

    // Slate's dark palette (Core's Palette.dark).
    private static final int BG = 0xFF161615, FLOOR = 0xFF4B4841, TEXT = 0xFFECEAE4, MUTED = 0xFFA19F97, DIM = 0xFF6E6C66, DANGER = 0xFFE5484D;

    private record Face(String file, float text, boolean crisp) {
        static Face of(final String key) {
            return switch (key == null ? "" : key) {
                case "monocraft" -> new Face("monocraft.ttf", 9, true);
                case "pixelify" -> new Face("pixelify.ttf", 10, false);
                default -> new Face("pixeloidsans.ttf", 9, true);
            };
        }
    }

    private final Look look;
    private final Face face;
    private final LongSupplier clock;
    private final ByteBuffer ttf;
    private final Sheet sheet = new Sheet();
    private final Factory factory;
    private final Gfx gfx = new Gfx();
    private final SceneGl gl = new SceneGl();
    private final GlSave saved = new GlSave();
    private final Frame frame = new Frame();
    private final Mesh title = new Mesh(8000);
    private boolean titleBuilt;
    /**
     * The game's logo and the line under it, as the game's assets have them; null where they could not be read (then
     * the title is built of blocks of stone). Their textures are made when the first frame is drawn.
     */
    private Picture logo, edition;
    private int logoTexture, editionTexture;
    private final float[] mirrored = new float[16];
    private final SceneGl.Light light = new SceneGl.Light(), titleLight = new SceneGl.Light();
    private PixelFont text;
    private float bakedScale = -1;

    private final long start;
    private long last;
    private final Motion motion = new Motion();
    /** Seconds the task at hand has been going, for one whose length nobody knows. */
    private float taskAge;
    private long seenDone = -1;
    /** Tasks that are done and have not yet sent an ingot on its way sooner. */
    private int waiting;
    /** Seconds until the next ingot comes. */
    private float next;
    private boolean stocked;
    private boolean struck;
    private int seed = 12345, made;

    private final Camera camera = new Camera(), titleCamera = new Camera();
    private final float[] lightViewProj = new float[16];
    private final float[] tmp = new float[4];

    private static final float TITLE_W = Letters.WIDTH, TITLE_H = Letters.HEIGHT;
    private static final float[] TITLE = Camera.box(-TITLE_W / 2f, 0f, -0.6f, TITLE_W / 2f, TITLE_H, 0.6f);

    /**
     * @param look   how it looks
     * @param font   the bytes of the heading font's TTF file named by {@code look.font()}
     * @param clock  nanoseconds, as {@link System#nanoTime}
     * @param assets where the models and textures of the mods that are installed are read from, or null: with
     *               Create among them the factory is built of Create's blocks
     */
    public LoadingScene(final Look look, final byte[] font, final LongSupplier clock, final Assets assets) {
        this.look = look;
        this.face = Face.of(look.font());
        this.clock = clock;
        this.ttf = MemoryUtil.memAlloc(font.length).put(font).flip();
        this.start = clock.getAsLong();
        this.last = start;
        if (assets != null) {
            logo = Picture.read(assets.read("minecraft", "textures/gui/title/minecraft.png"));
            edition = logo == null ? null : Picture.read(assets.read("minecraft", "textures/gui/title/edition.png"));
        }
        final Factory create = CreateFactory.make(assets, sheet);
        this.factory = create != null ? create : new OwnFactory();
    }

    /** Whether the title is the game's own logo (and not the word built of blocks, which stands in for it). */
    public boolean logo() {
        return logo != null;
    }

    /** Whether the factory is the one of Create's blocks. */
    public boolean create() {
        return factory instanceof CreateFactory;
    }

    /** The file of the heading font with this key, as it is named in Core's assets. */
    public static String fontFile(final String key) {
        return Face.of(key).file();
    }

    /** The scene's textures as ARGB, a square: for a tool that wants to look at them. */
    public int[] textures() {
        return sheet.pixels().clone();
    }

    /** Frees the GL objects and the memory; call with the context current. */
    public void dispose() {
        if (text != null) text.delete();
        text = null;
        if (logoTexture != 0) glDeleteTextures(logoTexture);
        if (editionTexture != 0) glDeleteTextures(editionTexture);
        logoTexture = editionTexture = 0;
        gfx.delete();
        gl.delete();
        frame.free();
        title.free();
        factory.free();
        MemoryUtil.memFree(ttf);
    }

    // ------------------------------------------------------------------ a frame

    /**
     * One frame onto the framebuffer that is bound, which is {@code pixelW} x {@code pixelH}.
     *
     * @param scale   pixels to a GUI unit, as the game's GUI has it; the words are set a size finer than that, so
     *                the factory is what the eye goes to
     * @param flipped whether the framebuffer is shown upside down (FML's canvas is)
     * @param alpha   1 for all of it; less lets what is under it through (the overlay fading out)
     */
    public void draw(final int pixelW, final int pixelH, final float scale, final boolean flipped, final float alpha, final Feed feed) {
        final long now = clock.getAsLong();
        final float dt = Math.max(0f, Math.min(0.1f, (now - last) / 1e9f));
        last = now;
        motion.time = (now - start) / 1e9f;
        advance(feed, dt);

        saved.save();
        try {
            cameras(pixelW, pixelH);
            lights();
            frame.clear();
            frame.camera = camera;
            frame.ground.kind(Mesh.FLOOR);
            frame.ground.rawQuad(-60, 0, -40, -60, 0, 40, 60, 0, 40, 60, 0, -40, 0, 1, 0, 0, 0, 0, 1, 1, 1, 1, 0);
            factory.build(motion, frame, look.accent());
            if (!titleBuilt) {
                titleBuilt = true;
                title.clear();
                Letters.build(title);
            }

            gl.begin(pixelW, pixelH, sheet);
            gl.load(frame.ground, frame.stage, frame.polish, frame.solid, frame.glass, frame.glow, title);
            gl.shadows(frame.solid, lightViewProj);
            gl.picture(light.background);
            gl.view(camera.viewProj, camera.eye, light, lightViewProj, true);
            gl.draw(frame.ground, SceneGl.OPAQUE);
            // The plinth is polished: what stands on it shows in it. The same things once more, upside down under its
            // top, and the top over them, letting a little of them through.
            gl.camera(mirrored);
            gl.draw(frame.solid, SceneGl.MIRRORED);
            gl.camera(camera.viewProj);
            gl.draw(frame.stage, SceneGl.OPAQUE);
            gl.draw(frame.polish, SceneGl.BLEND);
            gl.draw(frame.solid, SceneGl.OPAQUE);
            gl.draw(frame.glass, SceneGl.BLEND);
            gl.draw(frame.glow, SceneGl.ADD);
            if (logo == null) {
                gl.inFront();
                gl.view(titleCamera.viewProj, titleCamera.eye, titleLight, lightViewProj, false);
                gl.draw(title, SceneGl.OPAQUE);
            }
            final int picture = gl.finish();
            if (logo != null && logoTexture == 0) {
                logoTexture = logo.texture();
                if (edition != null) editionTexture = edition.texture();
            }

            glBindFramebuffer(GL_FRAMEBUFFER, saved.canvas());
            glViewport(0, 0, pixelW, pixelH);
            final float fine = Math.max(1, Math.round(scale * 0.7f));
            gfx.begin(pixelW, pixelH, fine, flipped, false);
            try {
                fonts(fine);
                final float w = pixelW / fine, h = pixelH / fine;
                final float shown = alpha * ease(Math.min(1f, motion.time / 0.45f));
                if (alpha >= 1f) gfx.rect(0, 0, w, h, background());
                gfx.alpha(shown);
                gfx.image(picture, 0, 0, w, h, 0, 1, 1, 0, 0xFFFFFFFF);
                gfx.vignette(w, h, look.vanilla() ? 0x30000000 : 0x66000000);
                if (logoTexture != 0) logo(pixelW / scale, pixelH / scale, scale / fine);
                words(feed, w, h);
            } finally {
                gfx.end();
            }
        } finally {
            saved.restore();
        }
    }

    private int background() {
        return look.vanilla() ? look.background() | 0xFF000000 : BG;
    }

    private void fonts(final float scale) {
        if (scale == bakedScale && text != null) return;
        if (text != null) text.delete();
        text = PixelFont.bake(ttf, face.text(), scale, PixelFont.LATIN_1, face.crisp());
        bakedScale = scale;
    }

    // ------------------------------------------------------------------ what moves

    private void advance(final Feed feed, final float dt) {
        final Motion m = motion;
        // The store and the tank follow what is, softly.
        final float overall = feed.overall();
        if (overall >= 0f) m.overall += (Math.min(1f, overall) - m.overall) * rate(dt, 5f);
        final long max = feed.memoryMax();
        final float memory = max > 0 ? Math.max(0f, Math.min(1f, feed.memoryUsed() / (float) max)) : 0f;
        m.memory = m.memory < 0 ? memory : m.memory + (memory - m.memory) * rate(dt, 1.6f);

        // Tasks that were finished since the last frame.
        final long done = feed.tasksDone();
        if (seenDone < 0) seenDone = done;
        if (done > seenDone) {
            waiting = (int) Math.min(MAX_WAITING, waiting + (done - seenDone));
            seenDone = done;
            taskAge = 0f;
            m.lump = 0f;
        }
        taskAge += dt;

        // The task at hand, for the dial: never back until the next one begins.
        final float progress = feed.taskProgress();
        final float target = progress >= 0f ? Math.min(1f, progress) : 0.9f * (1f - (float) Math.exp(-taskAge / 5f));
        m.lump += (Math.max(m.lump, target) - m.lump) * rate(dt, 6f);
        m.needle += (m.lump - m.needle) * rate(dt, 10f);

        // The belts never stop, and neither does what feeds them: an ingot every so often, and sooner for a moment
        // whenever a task is done. When the scene opens the factory is at work already.
        m.beltA += Factory.BELT_SPEED * dt;
        m.beltB += Factory.BELT_SPEED * dt;
        final float first = factory.lumpX1 - factory.lumpX0;
        if (!stocked) {
            stocked = true;
            for (float s = factory.way.length() - 0.6f; s > -first + 0.4f; s -= STOCKED) {
                final Motion.Item item = new Motion.Item();
                item.s = s;
                item.pressed = s > factory.pressAt;
                item.id = ++made;
                m.items.add(item);
            }
            next = EVERY * 0.4f;
        }
        next -= dt * (1f + 1.2f * waiting);
        final Motion.Item newest = m.items.isEmpty() ? null : m.items.get(m.items.size() - 1);
        if (next <= 0f && m.items.size() < MAX_ITEMS && (newest == null || newest.s >= -first + APART)) {
            final Motion.Item item = new Motion.Item();
            item.s = -first;
            item.id = ++made;
            m.items.add(item);
            next = EVERY;
            if (waiting > 0) waiting--;
        }

        // What is on the belts goes with them, waits its turn at the press, and leaves at the end.
        final float end = factory.way.length();
        Motion.Item ahead = null;
        for (int i = 0; i < m.items.size(); i++) {
            final Motion.Item it = m.items.get(i);
            if (it.leaving >= 0f) {
                it.leaving += dt;
                continue;
            }
            float to = it.s + Factory.BELT_SPEED * dt;
            if (!it.pressed && to >= factory.pressAt) {
                to = factory.pressAt;
                if (m.stroke < 0f) {
                    m.stroke = 0f;
                    struck = false;
                }
            }
            if (ahead != null && ahead.leaving < 0f) to = Math.min(to, ahead.s - 0.62f);
            it.s = Math.max(it.s, to);
            if (it.s >= end) it.leaving = 0f;
            ahead = it;
        }
        final float gone = factory.leaving(m);
        m.items.removeIf(it -> {
            if (it.leaving < gone) return false;
            m.landed = 0f;
            return true;
        });
        m.landed = Math.min(1f, m.landed + dt * 4f);

        // The press: down fast, up slower. At the bottom of its stroke what is under it is a sheet.
        if (m.stroke >= 0f) {
            m.stroke += dt;
            if (m.stroke < PRESS_DOWN) {
                final float t = m.stroke / PRESS_DOWN;
                m.pressDown = t * t;
            } else {
                if (!struck) {
                    struck = true;
                    strike();
                }
                final float t = Math.min(1f, (m.stroke - PRESS_DOWN) / PRESS_UP);
                m.pressDown = 1f - ease(t);
                if (t >= 1f) m.stroke = -1f;
            }
        } else {
            m.pressDown = 0f;
        }

        for (final Motion.Spark s : m.sparks) {
            s.life += dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.z += s.vz * dt;
            if (s.smoke) {
                s.vy += 0.5f * dt;
                s.vx *= 1f - 1.5f * dt;
                s.vz *= 1f - 1.5f * dt;
            } else {
                s.vy -= 9f * dt;
                if (s.y < factory.beltY + 0.02f && s.vy < 0) {
                    s.y = factory.beltY + 0.02f;
                    s.vy *= -0.35f;
                    s.vx *= 0.6f;
                }
            }
        }
        m.sparks.removeIf(s -> s.life >= s.span);
    }

    /** The press has come down: what is under it is a sheet, and sparks fly. */
    private void strike() {
        final Motion m = motion;
        for (final Motion.Item it : m.items) {
            if (!it.pressed && it.leaving < 0f && Math.abs(it.s - factory.pressAt) < 0.05f) {
                it.pressed = true;
                break;
            }
        }
        final int hot = Atlas.mix(look.accent(), 0xFFFFE9B0, 0.55f);
        for (int i = 0; i < 12; i++) {
            final Motion.Spark s = new Motion.Spark();
            final double a = random() * Math.PI * 2;
            final float out = 1.4f + random() * 2.2f;
            s.x = factory.pressX + (float) Math.cos(a) * 0.3f;
            s.z = (float) Math.sin(a) * 0.3f;
            s.y = factory.beltY + 0.19f;
            s.vx = (float) Math.cos(a) * out;
            s.vz = (float) Math.sin(a) * out;
            s.vy = 1.2f + random() * 2.2f;
            s.span = 0.35f + random() * 0.3f;
            s.size = 0.05f + random() * 0.05f;
            s.colour = hot;
            m.sparks.add(s);
        }
        for (int i = 0; i < 3; i++) {
            final Motion.Spark s = new Motion.Spark();
            s.smoke = true;
            s.x = factory.pressX + (random() - 0.5f) * 0.5f;
            s.z = 0.2f + random() * 0.3f;
            s.y = factory.beltY + 0.25f;
            s.vx = (random() - 0.5f) * 0.6f;
            s.vz = random() * 0.3f;
            s.vy = 0.5f + random() * 0.4f;
            s.span = 0.9f + random() * 0.5f;
            s.size = 0.32f + random() * 0.2f;
            s.colour = 0xFFE8E4DA;
            m.sparks.add(s);
        }
    }

    private float random() {
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 8) & 0xFFFF) / 65536f;
    }

    private static float rate(final float dt, final float speed) {
        return 1f - (float) Math.exp(-dt * speed);
    }

    private static float ease(final float t) {
        final float u = 1 - t;
        return 1 - u * u * u;
    }

    // ------------------------------------------------------------------ cameras and light

    private void cameras(final int w, final int h) {
        final float aspect = w / (float) h;
        // The factory along the bottom of the screen, seen from a little to the left and above, so the belt runs away
        // to the right; the title over it, in the middle.
        camera.fit(factory.target, 13f, factory.pitch, 24f, aspect, factory.bounds, new float[] {-0.96f, 0.96f, -0.94f, 0.16f}, 0f);
        titleCamera.fit(new float[] {0f, TITLE_H / 2f, 0f}, 0f, -13f, 18f, aspect, TITLE, new float[] {-0.56f, 0.56f, 0.34f, 0.8f}, 0.5f);

        // The key light looks at the factory from where it shines; what it sees is what throws shadows.
        final float kx = light.keyDir[0], ky = light.keyDir[1], kz = light.keyDir[2];
        final float cx = factory.target[0];
        final float[] v = Mat4.lookAt(cx - kx * 30f, 1.5f - ky * 30f, -kz * 30f, cx, 1.5f, 0f, 0, 1, 0);
        float l = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = Float.MAX_VALUE, t = -Float.MAX_VALUE, n = Float.MAX_VALUE, f = -Float.MAX_VALUE;
        final float[] bounds = factory.bounds;
        for (int i = 0; i + 2 < bounds.length; i += 3) {
            Mat4.transform(v, bounds[i], bounds[i + 1], bounds[i + 2], tmp);
            l = Math.min(l, tmp[0]);
            r = Math.max(r, tmp[0]);
            b = Math.min(b, tmp[1]);
            t = Math.max(t, tmp[1]);
            n = Math.min(n, -tmp[2]);
            f = Math.max(f, -tmp[2]);
        }
        Mat4.mul(lightViewProj, Mat4.ortho(l - 0.5f, r + 0.5f, b - 0.5f, t + 0.5f, n - 2f, f + 6f), v);

        // The camera of the mirror: everything turned over about the top of the plinth.
        final float[] flip = Mat4.identity();
        Mat4.translate(flip, 0f, Factory.BASE, 0f);
        Mat4.scale(flip, 1f, -1f, 1f);
        Mat4.translate(flip, 0f, -Factory.BASE, 0f);
        Mat4.mul(mirrored, camera.viewProj, flip);
    }

    private void lights() {
        final int bg = background();
        rgb(bg, light.background);
        rgb(look.vanilla() ? bg : FLOOR, light.floorLit);
        if (look.vanilla()) {
            // On the game's own colour the floor is that colour, a little lighter where the light falls.
            light.floorLit[0] = Math.min(1f, light.floorLit[0] * 1.18f + 0.04f);
            light.floorLit[1] = Math.min(1f, light.floorLit[1] * 1.18f + 0.04f);
            light.floorLit[2] = Math.min(1f, light.floorLit[2] * 1.18f + 0.04f);
        }
        light.pool[0] = factory.stage[0];
        light.pool[1] = factory.stage[1] + 0.2f;
        light.pool[2] = factory.stage[2] + 5.25f;
        light.pool[3] = factory.stage[3] + 4.75f;
        System.arraycopy(factory.stage, 0, light.stage, 0, 4);
        light.time = motion.time;
        normalize(light.keyDir);
        normalize(light.fillDir);
        factory.lamps(motion, light, look.accent());

        // The title has the same sun. A lamp before its middle lifts it out of the dark, and now and then a light
        // runs along it from end to end.
        final float along = (motion.time % 7f) / 1.6f;
        final float run = along < 1f ? (float) Math.sin(along * Math.PI) : 0f;
        Factory.lamp(titleLight, 0, 0f, TITLE_H / 2f, 9f, 30f, 0.16f, 0.15f, 0.13f);
        Factory.lamp(titleLight, 1, (along - 0.5f) * (TITLE_W + 12f), TITLE_H / 2f, 3.5f, 9f, 0.5f * run, 0.48f * run, 0.42f * run);
        System.arraycopy(light.keyDir, 0, titleLight.keyDir, 0, 3);
        System.arraycopy(light.background, 0, titleLight.background, 0, 3);
        titleLight.keyColour[0] = 0.98f;
        titleLight.keyColour[1] = 0.93f;
        titleLight.keyColour[2] = 0.84f;
        titleLight.ambient[0] = titleLight.ambient[1] = 0.36f;
        titleLight.ambient[2] = 0.39f;
        titleLight.wrap = 0.5f;
        titleLight.time = motion.time;
    }

    private static void rgb(final int argb, final float[] out) {
        out[0] = ((argb >>> 16) & 0xFF) / 255f;
        out[1] = ((argb >>> 8) & 0xFF) / 255f;
        out[2] = (argb & 0xFF) / 255f;
    }

    private static void normalize(final float[] v) {
        final float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        v[0] /= l;
        v[1] /= l;
        v[2] /= l;
    }

    // ------------------------------------------------------------------ the logo

    /**
     * The game's logo with "Java Edition" under it, as the game's own title screen draws the two, where and as large
     * as the Overhaul main menu has them ({@code OverhaulTitleScreen.logoScale}): half of the screen wide, its middle
     * a sixth down.
     *
     * @param w    the screen in GUI units, as the game's GUI has them
     * @param unit how many of the units this frame is drawn in make one of those
     */
    private void logo(final float w, final float h, final float unit) {
        final float s = Math.max(0.7f, Math.min(4f, Math.min(w * 0.51f / 256f, h * 0.22f / 51f)));
        final int high = Math.round(51 * s);
        final float middle = Math.max(4, Math.round(h * 0.16f - high / 2f)) + high / 2f;
        final float top = middle - 25.5f * s;
        // The logo is the upper 44 of its picture's 64 rows, the line under it the upper 14 of 16.
        gfx.image(logoTexture, (w / 2f - 128f * s) * unit, top * unit, 256f * s * unit, 44f * s * unit, 0f, 0f, 1f, 44f / 64f, 0xFFFFFFFF);
        if (editionTexture != 0) {
            gfx.image(editionTexture, (w / 2f - 64f * s) * unit, (top + 37f * s) * unit, 128f * s * unit, 14f * s * unit, 0f, 0f, 1f, 14f / 16f, 0xFFFFFFFF);
        }
    }

    // ------------------------------------------------------------------ the words

    private void words(final Feed feed, final float w, final float h) {
        final int strong = look.vanilla() ? 0xFFFFFFFF : TEXT, soft = look.vanilla() ? 0xD9FFFFFF : MUTED, faint = look.vanilla() ? 0x99FFFFFF : DIM;
        final float[] at = new float[2];

        // The version, top left.
        text.draw(gfx, look.version(), 10, 10, soft);
        if (look.loader() != null && !look.loader().isEmpty()) text.draw(gfx, look.loader(), 10, 21, faint);

        // The memory, over the tank: never off the screen.
        final long max = feed.memoryMax(), used = feed.memoryUsed();
        final String memory = String.format(Locale.ROOT, "%,d / %,d MB", used >> 20, max >> 20);
        project(factory.memoryAt, w, h, at);
        final float mw = text.width(memory);
        final float mx = Math.max(8f, Math.round(at[0] - mw / 2f));
        final float my = Math.round(at[1] - 26f);
        text.draw(gfx, "RAM", mx, my, faint);
        text.draw(gfx, memory, mx, my + 10f, max > 0 && used / (double) max > 0.88 ? (look.vanilla() ? 0xFFFFFFFF : DANGER) : strong);

        // What is being done, over the first belt: from where the belt starts to where the press stands, clear of
        // what rises over the belt.
        project(factory.taskFrom, w, h, at);
        final float tx = Math.max(mx + mw + 14f, Math.round(at[0]));
        project(factory.taskTo, w, h, at);
        final float room = Math.max(60f, at[0] - 8f - tx);
        float clear = h;
        for (final float[] point : factory.over) {
            project(point, w, h, at);
            clear = Math.min(clear, at[1]);
        }
        final float ty = Math.round(clear - 8f - 25f);
        final String task = feed.task() == null || feed.task().isBlank() ? "Loading" : feed.task();
        final String count = feed.taskCount() == null ? "" : feed.taskCount();
        final float cw = text.width(count);
        final String shown = text.fit(task, room - (cw > 0 ? cw + 8f : 0f));
        text.draw(gfx, shown, tx, ty, strong);
        if (cw > 0) text.draw(gfx, count, tx + room - cw, ty, soft);
        final float capW = Math.max(16f, Math.min(text.width(shown), 48f));
        gfx.hgradient(tx, ty + 10f, capW, 2f, look.accent(), look.accent() & 0x00FFFFFF | 0x30000000);
        final String detail = feed.detail();
        if (detail != null && !detail.isBlank() && !detail.equals(task)) text.draw(gfx, text.fit(detail, room), tx, ty + 17f, soft);

        // How far it all is, over the store.
        if (feed.overall() >= 0f) {
            final String all = Math.round(Math.min(1f, motion.overall) * 100f) + "%";
            project(factory.totalAt, w, h, at);
            final float ax = Math.min(w - 8f - text.width(all), Math.round(at[0] - text.width(all) / 2f));
            text.draw(gfx, all, ax, Math.round(at[1] - 9f), strong);
        }
    }

    private void project(final float[] point, final float w, final float h, final float[] out) {
        camera.project(point[0], point[1], point[2], w, h, out);
    }
}
