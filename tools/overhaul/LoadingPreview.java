import dev.fallingcloud.slate.menu.client.loading.scene.Assets;
import dev.fallingcloud.slate.menu.client.loading.scene.LoadingScene;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.MemoryUtil;

/**
 * Renders the Overhaul loading scene without starting the game: a hidden window, a made-up loading of 24 seconds
 * played at 60 frames a second, and a PNG of the frame at each of the times asked for. For looking at the scene while
 * working on it; the game is not needed and nothing of it is loaded.
 *
 * <pre>
 *   javac -d out -cp LWJGL common/menu/src/main/java/dev/fallingcloud/slate/menu/client/loading/scene/*.java
 *   java -cp out;LWJGL tools/overhaul/LoadingPreview.java OUT_DIR [WIDTHxHEIGHT] [slate|vanilla|black] [seconds,seconds,...]
 * </pre>
 * LWJGL is lwjgl, lwjgl-glfw, lwjgl-opengl and lwjgl-stb 3.3.3 with their natives (Gradle's cache has them once the
 * game has been built). With the environment variable CREATE_JAR naming the jar of the Create mod, the factory is
 * built of Create's blocks, as it is in a game that has Create; VANILLA_JAR names a jar with the game's own assets,
 * for the few textures of the game that Create's models use.
 */
public final class LoadingPreview {

    /** A task of the made-up loading: its name, how long it takes, and in how many steps (0: nobody knows). */
    private record Task(String name, float seconds, int steps) {}

    private static final List<Task> TASKS = List.of(
        new Task("Launching Minecraft", 1.2f, 0),
        new Task("Discovering mod files", 1.6f, 6),
        new Task("Mod Construction", 2.4f, 48),
        new Task("Registry initialization", 1.8f, 30),
        new Task("Config loading", 0.35f, 48),
        new Task("Common setup", 1.4f, 48),
        new Task("Sided setup", 0.3f, 48),
        new Task("Enqueue IMC", 0.25f, 48),
        new Task("Process IMC", 0.25f, 48),
        new Task("Complete loading of 48 mods", 0.4f, 48),
        new Task("Loading resources", 3.0f, 0),
        new Task("Stitching textures", 4.0f, 200),
        new Task("Baking models", 3.4f, 1000),
        new Task("Loading sounds", 1.3f, 12),
        new Task("Compiling shaders", 1.2f, 80),
        new Task("Finishing up", 1.0f, 4));

    private static final String[] LOG = {
        "Loading model minecraft:block/andesite", "Scanning slate_menu", "Registering minecraft:block", "Found 48 mods",
        "Reloading ResourceManager: vanilla, mod_resources", "Creating atlas minecraft:textures/atlas/blocks.png-atlas",
        "OpenAL initialized on device OpenAL Soft", "Sound engine started", "Created: 1024x512x4 minecraft:textures/atlas/blocks.png-atlas"};

    /** The assets of the mods named by the environment, read from their jars; null when none is named. */
    private static Assets assets() throws Exception {
        final Map<String, ZipFile> jars = new HashMap<>();
        final String create = System.getenv("CREATE_JAR"), game = System.getenv("VANILLA_JAR");
        if (create != null && !create.isBlank()) jars.put("create", new ZipFile(create));
        if (game != null && !game.isBlank()) jars.put("minecraft", new ZipFile(game));
        if (jars.isEmpty()) return null;
        return (namespace, path) -> {
            final ZipFile jar = jars.get(namespace);
            if (jar == null) return null;
            final ZipEntry entry = jar.getEntry("assets/" + namespace + "/" + path);
            if (entry == null) return null;
            try (var in = jar.getInputStream(entry)) {
                return in.readAllBytes();
            } catch (final java.io.IOException e) {
                return null;
            }
        };
    }

    public static void main(final String[] args) throws Exception {
        final Path out = Path.of(args.length > 0 ? args[0] : "loading-preview");
        final String[] size = (args.length > 1 ? args[1] : "1280x720").split("x");
        final int w = Integer.parseInt(size[0]), h = Integer.parseInt(size[1]);
        final String style = args.length > 2 ? args[2] : "slate";
        final List<Float> shots = new ArrayList<>();
        for (final String s : (args.length > 3 ? args[3] : "0.2,2,5,8,11,14,17,20,23.5").split(",")) shots.add(Float.parseFloat(s));
        final String name = args.length > 4 ? args[4] : "loading";
        Files.createDirectories(out);

        float total = 0;
        for (final Task t : TASKS) total += t.seconds();
        final float all = total;

        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        final long window = GLFW.glfwCreateWindow(64, 64, "Slate loading preview", 0, 0);
        if (window == 0) throw new IllegalStateException("window");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();

        // The canvas the scene is drawn onto, as the start-up window and the game have theirs.
        final int texture = GL32C.glGenTextures();
        GL32C.glBindTexture(GL32C.GL_TEXTURE_2D, texture);
        GL32C.glTexImage2D(GL32C.GL_TEXTURE_2D, 0, GL32C.GL_RGBA8, w, h, 0, GL32C.GL_RGBA, GL32C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL32C.glTexParameteri(GL32C.GL_TEXTURE_2D, GL32C.GL_TEXTURE_MIN_FILTER, GL32C.GL_NEAREST);
        GL32C.glTexParameteri(GL32C.GL_TEXTURE_2D, GL32C.GL_TEXTURE_MAG_FILTER, GL32C.GL_NEAREST);
        final int canvas = GL32C.glGenFramebuffers();
        GL32C.glBindFramebuffer(GL32C.GL_FRAMEBUFFER, canvas);
        GL32C.glFramebufferTexture2D(GL32C.GL_FRAMEBUFFER, GL32C.GL_COLOR_ATTACHMENT0, GL32C.GL_TEXTURE_2D, texture, 0);

        final boolean vanilla = !style.equals("slate");
        final int background = style.equals("black") ? 0xFF000000 : 0xFFEF323D;
        final String font = vanilla ? "monocraft" : "pixeloid";
        final byte[] ttf = Files.readAllBytes(Path.of("common/core/src/main/resources/assets/slate/font", LoadingScene.fontFile(font)));
        final long[] clock = {1_000_000_000L};
        final float[] at = {0f};
        final LoadingScene scene = new LoadingScene(
            new LoadingScene.Look(0xFFD9805E, vanilla, background, font, "Minecraft 1.21.1", "NeoForge 21.1.247"), ttf, () -> clock[0],
            assets());
        System.out.println((scene.create() ? "built of Create's blocks" : "built of the scene's own") + (scene.logo() ? ", the game's logo" : ", the title in blocks"));

        final LoadingScene.Feed feed = new LoadingScene.Feed() {
            private int index(final float[] into) {
                float t = at[0];
                for (int i = 0; i < TASKS.size(); i++) {
                    final float s = TASKS.get(i).seconds();
                    if (t < s) {
                        into[0] = t / s;
                        return i;
                    }
                    t -= s;
                }
                into[0] = 1f;
                return TASKS.size() - 1;
            }

            @Override public float overall() { return Math.min(1f, at[0] / all); }

            @Override public String task() {
                final float[] f = new float[1];
                final Task t = TASKS.get(index(f));
                return t.name();
            }

            @Override public String taskCount() {
                final float[] f = new float[1];
                final Task t = TASKS.get(index(f));
                return t.steps() > 0 ? Math.round(f[0] * t.steps()) + " / " + t.steps() : null;
            }

            @Override public float taskProgress() {
                final float[] f = new float[1];
                final Task t = TASKS.get(index(f));
                return t.steps() > 0 ? Math.round(f[0] * t.steps()) / (float) t.steps() : -1f;
            }

            @Override public long tasksDone() { return at[0] >= all ? TASKS.size() : index(new float[1]); }

            @Override public String detail() { return LOG[(int) (at[0] * 1.7f) % LOG.length]; }

            @Override public long memoryUsed() {
                // Climbs, and drops when the garbage is taken out.
                final float saw = (at[0] % 3.1f) / 3.1f;
                return (long) ((0.18f + 0.30f * Math.min(1f, at[0] / all) + 0.16f * saw) * memoryMax());
            }

            @Override public long memoryMax() { return 4096L << 20; }
        };

        final int scale = Math.max(1, Math.min(w / 320, h / 240));
        final ByteBuffer pixels = MemoryUtil.memAlloc(w * h * 4);
        final float step = 1f / 60f;
        final float end = shots.stream().max(Float::compare).orElse(1f) + step;
        int shot = 0;
        long spent = 0;
        int frames = 0;
        for (float t = 0; t <= end; t += step) {
            at[0] = t;
            clock[0] = 1_000_000_000L + (long) (t * 1e9);
            GL32C.glBindFramebuffer(GL32C.GL_FRAMEBUFFER, canvas);
            GL32C.glViewport(0, 0, w, h);
            final long before = System.nanoTime();
            scene.draw(w, h, scale, false, 1f, feed);
            GL32C.glFinish();
            spent += System.nanoTime() - before;
            frames++;
            final int error = GL32C.glGetError();
            if (error != 0) System.out.println("GL error 0x" + Integer.toHexString(error) + " at " + t);
            boolean take = false;
            for (final float s : shots) if (Math.abs(s - t) < step / 2f) take = true;
            if (!take) continue;
            GL32C.glBindFramebuffer(GL32C.GL_FRAMEBUFFER, canvas);
            GL32C.glReadPixels(0, 0, w, h, GL32C.GL_RGBA, GL32C.GL_UNSIGNED_BYTE, pixels);
            final BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    final int i = ((h - 1 - y) * w + x) * 4;
                    image.setRGB(x, y, (pixels.get(i) & 0xFF) << 16 | (pixels.get(i + 1) & 0xFF) << 8 | pixels.get(i + 2) & 0xFF);
                }
            }
            final File file = out.resolve(String.format("%s-%02d-%05.1fs.png", name, shot++, t).replace(',', '.')).toFile();
            ImageIO.write(image, "png", file);
            System.out.println(file);
        }
        System.out.printf("%d frames, %.2f ms each%n", frames, spent / 1e6 / frames);

        // The textures, eight times their size.
        final int[] atlas = scene.textures();
        final int side = (int) Math.sqrt(atlas.length), rows = scene.create() ? 640 : 32, across = scene.create() ? side : 256, zoom = scene.create() ? 2 : 8;
        final BufferedImage sheet = new BufferedImage(across * zoom, rows * zoom, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < rows * zoom; y++) for (int x = 0; x < across * zoom; x++) sheet.setRGB(x, y, atlas[(y / zoom) * side + x / zoom]);
        ImageIO.write(sheet, "png", out.resolve("atlas.png").toFile());

        scene.dispose();
        MemoryUtil.memFree(pixels);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }
}
