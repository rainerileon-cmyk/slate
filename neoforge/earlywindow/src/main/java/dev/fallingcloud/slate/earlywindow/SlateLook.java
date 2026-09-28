package dev.fallingcloud.slate.earlywindow;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.neoforged.fml.earlydisplay.ColourScheme;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Slate's settings and colours for FML's early display, which runs before any mod (so no mixins, no Minecraft classes,
 * and Slate's config read as text): the window's choice in fml.toml, FML's colour schemes repainted with Slate's dark
 * palette (its clear colour, and all of FML's own screen should the scene fail) and the running fox taken out of that
 * fallback. Reached by reflection; anything that fails leaves NeoForge's own screen as it is.
 */
final class SlateLook {

    static final Logger LOGGER = LoggerFactory.getLogger("Slate early window");
    /** FML's own early window, the only one this replaces (a pack that picked another, Drippy's, keeps it). */
    static final String FML_DEFAULT = "fmlearlywindow";

    /** Slate's dark skin: bg and text of {@code Palette.dark}. */
    private static final ColourScheme.Colour BACKGROUND = new ColourScheme.Colour(0x16, 0x16, 0x15);
    private static final ColourScheme.Colour FOREGROUND = new ColourScheme.Colour(0xEC, 0xEA, 0xE4);
    private static final Pattern SKIN = Pattern.compile("\"skin\"\\s*:\\s*\"(\\w+)\"");
    private static final Pattern ACCENT = Pattern.compile("\"accent\"\\s*:\\s*\"#?([0-9a-fA-F]{6})\"");
    private static final Pattern RADIUS = Pattern.compile("\"radius\"\\s*:\\s*(\\d+)");
    /** Core's defaults (CoreConfig). */
    private static final int DEFAULT_ACCENT = 0xFFD9805E, DEFAULT_RADIUS = 3;

    private static final Pattern LAYOUT = Pattern.compile("\"customLayout\"\\s*:\\s*(true|false)");
    private static final Pattern LOADING = Pattern.compile("\"loadingScreens\"\\s*:\\s*(true|false)");
    private static final Pattern HEADING_FONT = Pattern.compile("\"headingFont\"\\s*:\\s*(true|false)");
    private static final Pattern PIXEL_FONT = Pattern.compile("\"pixelFont\"\\s*:\\s*\"(\\w+)\"");

    private static String coreJson, menuJson;
    /** The window fml.toml named before {@link #choose} replaced it in memory. */
    private static String replaced;

    /**
     * Slate draws the start-up window: as its other loading screens (Menu's {@code LoadingScreens.active}), with Core's
     * custom layout on and Menu's {@code loadingScreens}, and, having no vanilla-skin version, with the dark skin.
     * Each is on while its file does not exist yet, as the settings' defaults.
     */
    static boolean enabled() {
        return darkSkin() && flag(LAYOUT, core()) && flag(LOADING, menu());
    }

    private static boolean darkSkin() {
        final Matcher m = SKIN.matcher(core());
        return !m.find() || !"VANILLA".equalsIgnoreCase(m.group(1));
    }

    private static boolean flag(final Pattern pattern, final String json) {
        final Matcher m = pattern.matcher(json);
        return !m.find() || Boolean.parseBoolean(m.group(1));
    }

    /** The accent colour chosen in Slate's settings, ARGB. */
    static int accent() {
        final Matcher m = ACCENT.matcher(core());
        return m.find() ? 0xFF000000 | Integer.parseInt(m.group(1), 16) : DEFAULT_ACCENT;
    }

    /**
     * The heading font as Core's {@code PixelFont} key ({@code pixeloid}, {@code monocraft}, {@code pixelify}; missing or
     * unknown reads as pixeloid). With the pixel heading font off the game uses its own font, which does not exist
     * this early: Monocraft, modelled on it, stands in.
     */
    static String pixelFont() {
        if (!flag(HEADING_FONT, core())) return "monocraft";
        final Matcher m = PIXEL_FONT.matcher(core());
        final String key = m.find() ? m.group(1).toLowerCase(Locale.ROOT) : "";
        return key.equals("monocraft") || key.equals("pixelify") ? key : "pixeloid";
    }

    /** The dark skin's corner radius (0-4). */
    static int radius() {
        final Matcher m = RADIUS.matcher(core());
        return m.find() ? Math.min(4, Integer.parseInt(m.group(1))) : DEFAULT_RADIUS;
    }

    private static String core() {
        if (coreJson == null) coreJson = read("core.json");
        return coreJson;
    }

    private static String menu() {
        if (menuJson == null) menuJson = read("menu.json");
        return menuJson;
    }

    /** One of Slate's settings files as text ("" when it does not exist yet): Slate's config classes are not loaded this early. */
    private static String read(final String name) {
        try {
            final Path file = FMLPaths.CONFIGDIR.get().resolve("slate").resolve(name);
            return Files.isRegularFile(file) ? Files.readString(file) : "";
        } catch (final Exception e) {
            return "";
        }
    }

    /**
     * With {@code slate}, makes FML load {@code name} instead of its own window, for this launch only: set in memory,
     * and set back by {@link #restoreSelection} once FML has read it, since FML saves fml.toml later in start-up (the
     * window's size), and a file naming this window would lose the start-up window altogether once Slate is removed.
     */
    static void choose(final String name, final boolean slate) {
        final String current = FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER);
        if (name.equals(current)) {
            // Left in fml.toml by an earlier build: FML finds this window by name. Healed on FML's next save.
            if (slate) replaced = FML_DEFAULT;
            else setProvider(FML_DEFAULT);
            return;
        }
        if (slate && FML_DEFAULT.equals(current) && setProvider(name)) replaced = current;
    }

    /** Puts back the window fml.toml names, in memory; FML has already picked the window by then. */
    static void restoreSelection() {
        if (replaced != null) setProvider(replaced);
        replaced = null;
    }

    private static boolean setProvider(final String name) {
        try {
            final Field instance = FMLConfig.class.getDeclaredField("INSTANCE");
            final Field data = FMLConfig.class.getDeclaredField("configData");
            instance.setAccessible(true);
            data.setAccessible(true);
            Method set = null;
            for (final Method m : FMLConfig.ConfigValue.class.getDeclaredMethods()) {
                if (m.getName().equals("setConfigValue") && m.getParameterCount() == 2) set = m;
            }
            if (set == null) throw new NoSuchMethodException("FMLConfig.ConfigValue.setConfigValue");
            set.setAccessible(true);
            set.invoke(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER, data.get(instance.get(null)), name);
            return true;
        } catch (final ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("[Slate] NeoForge's own loading screen stays: {}", e.toString());
            return false;
        }
    }

    /**
     * Paints both of FML's colour schemes (Mojang red and the monochrome one) with Slate's palette. Called before the
     * window's first frame: the clear colour is read once, when its renderer starts.
     */
    static void recolour() {
        try {
            final Field bg = ColourScheme.class.getDeclaredField("background");
            final Field fg = ColourScheme.class.getDeclaredField("foreground");
            bg.setAccessible(true);
            fg.setAccessible(true);
            for (final ColourScheme s : ColourScheme.values()) {
                bg.set(s, BACKGROUND);
                fg.set(s, FOREGROUND);
            }
        } catch (final ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("[Slate] loading screen keeps NeoForge's colours: {}", e.toString());
        }
    }

    /**
     * Takes the running fox out of the window's elements once its renderer has built them; false while it has not.
     * FML 4 builds them as [fox, log messages, version, memory bar, progress bars] (a squirrel may follow), so the fox
     * is the first; the list is swapped under the window's render lock.
     */
    static boolean dropFox(final DisplayWindow window) {
        try {
            final Field elements = DisplayWindow.class.getDeclaredField("elements");
            final Field lock = DisplayWindow.class.getDeclaredField("renderLock");
            elements.setAccessible(true);
            lock.setAccessible(true);
            if (!(elements.get(window) instanceof List<?> current)) return false;
            final Semaphore renderLock = (Semaphore) lock.get(window);
            renderLock.acquire();
            try {
                final List<?> now = (List<?>) elements.get(window);
                if (now.size() >= 5) {
                    final List<Object> rest = new ArrayList<>(now);
                    rest.remove(0);
                    elements.set(window, rest);
                }
            } finally {
                renderLock.release();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("[Slate] loading screen keeps its fox: {}", e.toString());
        }
        return true;
    }

    private SlateLook() {}
}
