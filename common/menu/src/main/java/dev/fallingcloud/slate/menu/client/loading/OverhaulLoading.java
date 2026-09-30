package dev.fallingcloud.slate.menu.client.loading;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.client.DevHarness;
import dev.fallingcloud.slate.core.config.CoreConfig;
import dev.fallingcloud.slate.core.platform.Loader;
import dev.fallingcloud.slate.core.platform.ModInfo;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.Layout;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.screen.slot.Style;
import dev.fallingcloud.slate.core.theme.PixelFont;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.loading.scene.LoadingScene;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.jetbrains.annotations.Nullable;

/**
 * The Overhaul layout of the game's loading overlay (the screen with the Mojang logo, shown while resources load): the
 * factory of {@link LoadingScene}, the very scene NeoForge's start-up window shows before the game exists. On Fabric,
 * which has no start-up window, this is where the scene is first seen; on NeoForge the start-up window carries on as
 * the first loading overlay by itself, and this one is what every later reload shows.
 *
 * <p>The scene is plain OpenGL beside the game's own drawing. It puts back everything it touches, so the game's state
 * manager, which remembers what it has set, is none the wiser.</p>
 */
public final class OverhaulLoading {

    /** The game's loading colours (LoadingOverlay's). */
    private static final int RED = 0xFFEF323D, BLACK = 0xFF000000;
    private static final int PARTS = 10;

    private static @Nullable LoadingScene scene;
    /** What the scene was made for; another accent, style or font makes a new one. */
    private static @Nullable String made;
    private static boolean failed;
    private static final Feed FEED = new Feed();

    /** Whether the loading overlay is the Overhaul one: the slot says so, and the scene has not failed before. */
    public static boolean active() {
        if (failed) return false;
        final String asked = DevHarness.autoLayout();
        if (asked != null) return "OVERHAUL".equalsIgnoreCase(asked) && SlateMenu.config().loadingScreens;
        return MenuSlots.effective(CoreSlots.LOADING) == Layout.OVERHAUL;
    }

    /**
     * One frame over all of the window.
     *
     * @param alpha 1 for all of it; less lets the screen under it through
     * @return false when the scene could not be drawn: the game's own overlay has to
     */
    public static boolean draw(final ReloadInstance reload, final float alpha) {
        RenderSystem.assertOnRenderThread();
        final Minecraft mc = Minecraft.getInstance();
        try {
            final LoadingScene s = scene(mc);
            FEED.frame(reload);
            s.draw(mc.getWindow().getWidth(), mc.getWindow().getHeight(), (float) mc.getWindow().getGuiScale(), false, alpha, FEED);
            return true;
        } catch (final RuntimeException | LinkageError e) {
            failed = true;
            SlateMenu.LOGGER.error("[Slate Menu] the Overhaul loading screen failed, the game's own takes over", e);
            drop();
            return false;
        }
    }

    private static LoadingScene scene(final Minecraft mc) {
        final CoreConfig cfg = Slate.config();
        final String skin = DevHarness.autoSkin();
        final boolean vanilla = skin != null ? "VANILLA".equalsIgnoreCase(skin) : MenuSlots.style(CoreSlots.LOADING) == Style.VANILLA;
        final int accent = Theme.current().accent();
        final int background = mc.options.darkMojangStudiosBackground().get() ? BLACK : RED;
        // The Vanilla style has the game's font, which the scene cannot draw with: Monocraft is modelled on it.
        final String font = vanilla || !cfg.headingFont ? "monocraft" : PixelFont.parse(cfg.pixelFont).key();
        final String key = accent + "/" + vanilla + "/" + background + "/" + font;
        if (scene != null && key.equals(made)) return scene;
        drop();
        final Loader loader = SlatePlatform.get().loader();
        final String version = SlatePlatform.get().modInfo(loader == Loader.FABRIC ? "fabricloader" : "neoforge").map(ModInfo::version).orElse("");
        scene = new LoadingScene(
            new LoadingScene.Look(accent, vanilla, background, font, "Minecraft " + SharedConstants.getCurrentVersion().getName(),
                (loader.displayName + " " + version).trim()),
            font(LoadingScene.fontFile(font)), System::nanoTime, OverhaulLoading::asset);
        made = key;
        return scene;
    }

    /**
     * A file of the assets of a mod that is installed, read from the mod's own jar, or of the game's. The resource
     * manager is what is being loaded while this screen shows, so it is not asked; what a resource pack changes of
     * another mod's blocks does not show here.
     */
    private static byte @Nullable [] asset(final String namespace, final String path) {
        try {
            if (namespace.equals("minecraft")) {
                final IoSupplier<InputStream> file = Minecraft.getInstance().getVanillaPackResources()
                    .getResource(PackType.CLIENT_RESOURCES, ResourceLocation.withDefaultNamespace(path));
                if (file == null) return null;
                try (InputStream in = file.get()) {
                    return in.readAllBytes();
                }
            }
            final Optional<Path> file = SlatePlatform.get().modFile(namespace, "assets/" + namespace + "/" + path);
            return file.isPresent() ? Files.readAllBytes(file.get()) : null;
        } catch (final IOException | RuntimeException e) {
            return null;
        }
    }

    /** A heading font from Core's assets, read from its jar: the resource manager is what is being loaded. */
    private static byte[] font(final String file) {
        try (InputStream in = Slate.class.getResourceAsStream("/assets/slate/font/" + file)) {
            if (in == null) throw new IllegalStateException("missing font " + file);
            return in.readAllBytes();
        } catch (final IOException e) {
            throw new IllegalStateException("could not read font " + file, e);
        }
    }

    private static void drop() {
        final LoadingScene old = scene;
        scene = null;
        made = null;
        if (old == null) return;
        try {
            old.dispose();
        } catch (final RuntimeException | LinkageError e) {
            // It failed while drawing; what it could not free goes with the context.
        }
    }

    /**
     * A reload as the scene asks for it. The game only knows how far all of a reload is, so a tenth of it is the task
     * at hand: every tenth sends a bar to the store.
     */
    private static final class Feed implements LoadingScene.Feed {

        private @Nullable ReloadInstance reload;
        private float overall, part;
        private int at;
        private long done;
        private long used, max;

        void frame(final ReloadInstance now) {
            if (now != reload) {
                reload = now;
                overall = 0f;
                at = 0;
            }
            overall = Math.max(overall, Math.max(0f, Math.min(1f, now.getActualProgress())));
            final float parts = overall * PARTS;
            final int n = Math.min(PARTS - 1, (int) parts);
            if (n > at) done += n - at;
            at = n;
            part = Math.min(1f, parts - n);
            final Runtime rt = Runtime.getRuntime();
            max = rt.maxMemory();
            used = rt.totalMemory() - rt.freeMemory();
        }

        @Override public float overall() { return overall; }

        @Override public String task() {
            // The language is one of the things being loaded: until it is there, plain English.
            return I18n.exists("slate_menu.loading.resources") ? I18n.get("slate_menu.loading.resources") : "Loading resources";
        }

        @Override public float taskProgress() { return part; }

        @Override public String taskCount() { return (at + 1) + " / " + PARTS; }

        @Override public long tasksDone() { return done; }

        @Override public @Nullable String detail() { return null; }

        @Override public long memoryUsed() { return used; }

        @Override public long memoryMax() { return max; }
    }

    private OverhaulLoading() {}
}
