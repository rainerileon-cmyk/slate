package dev.fallingcloud.slate.core.layout;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.theme.Colors;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a layout's {@link ScreenLayout.Background} in place of the screen's own background. Called from
 * {@code ScreenLayoutMixin}, which wraps the {@code renderBackground} call inside {@code Screen.render}
 * (so overrides such as {@code SlateScreen}'s are covered too) and the head of {@code renderBackground}
 * itself (for screens that draw their background by hand). The wrapper is the right place because it
 * runs UNDER the widgets and can suppress vanilla's panorama/blur/dirt, which a widget or a
 * render-post hook never could.
 *
 * <p>Kinds: {@code default} (untouched), {@code color} ({@code #RRGGBB} / {@code #AARRGGBB}),
 * {@code image} (a file under the game dir, cover-fit, or a {@code namespace:path} texture, stretched),
 * {@code panorama} (vanilla's rotating cube map), {@code none} (nothing at all: the world shows through
 * in-game). {@code dim} darkens images and lays the menu gradient over the panorama.</p>
 */
public final class LayoutBackground {

    private static final Map<String, Textures.Loaded> IMAGES = new ConcurrentHashMap<>();
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();
    @Nullable private static Screen frameScreen;
    private static boolean drawnThisFrame;
    private static long lastErrorMs;

    /** Start of a screen frame (renderWithTooltip HEAD): allows one background draw per frame. */
    public static void beginFrame(final Screen screen) {
        frameScreen = screen;
        drawnThisFrame = false;
    }

    /** Whether the layout of this screen replaces the vanilla background. */
    public static boolean overrides(@Nullable final Screen screen) {
        return background(screen) != null;
    }

    @Nullable
    private static ScreenLayout.Background background(@Nullable final Screen screen) {
        if (screen == null || ScreenIds.isContainer(screen)) return null;
        final ScreenLayout.Background bg;
        try {
            bg = LayoutStore.get(LayoutApplier.layoutId(screen)).background;
        } catch (final Exception e) {
            return null;
        }
        if (bg == null || bg.kind == null) return null;
        final String kind = bg.kind.trim().toLowerCase(Locale.ROOT);
        return kind.isEmpty() || kind.equals("default") ? null : bg;
    }

    /**
     * Draws the layout background. Returns true when it did (the caller must then skip the vanilla
     * background), false when the layout leaves the background alone.
     */
    public static boolean render(final Screen screen, final GuiGraphics g, final float partialTick, @Nullable final PanoramaRenderer panorama) {
        final ScreenLayout.Background bg = background(screen);
        if (bg == null) return false;
        if (screen == frameScreen && drawnThisFrame) return true;     // second call in the same frame
        frameScreen = screen;
        drawnThisFrame = true;
        final int w = screen.width, h = screen.height;
        try {
            switch (bg.kind.trim().toLowerCase(Locale.ROOT)) {
                case "none" -> {}
                case "color", "colour" -> g.fill(0, 0, w, h, Colors.fromHex(bg.value, 0xFF000000));
                case "image" -> drawImage(g, bg, w, h);
                case "panorama" -> {
                    if (panorama != null) panorama.render(g, w, h, 1f, partialTick);
                    if (bg.dim) Screen.renderMenuBackgroundTexture(g, Screen.MENU_BACKGROUND, 0, 0, 0, 0, w, h);
                }
                default -> { return false; }
            }
        } catch (final Exception e) {
            final long now = Clock.nowMs();
            if (now - lastErrorMs > 5000) {
                lastErrorMs = now;
                Slate.LOGGER.warn("[Slate] layout background of {} failed: {}", ScreenIds.of(screen), e.toString());
            }
        }
        return true;
    }

    private static void drawImage(final GuiGraphics g, final ScreenLayout.Background bg, final int w, final int h) {
        final String src = bg.value == null ? "" : bg.value.trim();
        if (src.isEmpty()) { g.fill(0, 0, w, h, 0xFF000000); return; }
        if (isResource(src)) {
            final ResourceLocation rl = ResourceLocation.tryParse(src);
            if (rl != null) {
                RenderSystem.enableBlend();
                g.blit(rl, 0, 0, w, h, 0f, 0f, 1, 1, 1, 1);
                RenderSystem.disableBlend();
            }
        } else {
            final Textures.Loaded img = IMAGES.get(src);
            if (img == null) {
                if (REQUESTED.add(src)) {
                    final Path p = SlatePlatform.get().gameDir().resolve(src);
                    Textures.load(p, l -> IMAGES.put(src, l));
                }
                g.fill(0, 0, w, h, 0xFF000000);
                return;
            }
            final float s = Math.max((float) w / Math.max(1, img.width()), (float) h / Math.max(1, img.height()));
            final int dw = Math.max(1, Math.round(img.width() * s)), dh = Math.max(1, Math.round(img.height() * s));
            final int dx = (w - dw) / 2, dy = (h - dh) / 2;
            g.fill(0, 0, w, h, 0xFF000000);
            RenderSystem.enableBlend();
            g.blit(img.id(), dx, dy, dw, dh, 0f, 0f, img.width(), img.height(), img.width(), img.height());
            RenderSystem.disableBlend();
        }
        if (bg.dim) g.fill(0, 0, w, h, 0x66000000);
    }

    /** {@code namespace:path} (a resource) rather than a file path; a drive letter ({@code c:/...}) is a file. */
    private static boolean isResource(final String src) {
        return src.matches("^[a-z0-9_.-]{2,}:[a-z0-9_.-][a-z0-9_./-]*$");
    }

    /** Forget cached images so an edited path (or file) is loaded again. */
    public static void forgetImages() {
        IMAGES.clear();
        REQUESTED.clear();
    }

    private LayoutBackground() {}
}
