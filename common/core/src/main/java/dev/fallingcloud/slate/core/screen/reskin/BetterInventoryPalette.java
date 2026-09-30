package dev.fallingcloud.slate.core.screen.reskin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.config.CoreConfig;
import dev.fallingcloud.slate.core.mixin.SpriteContentsAccessor;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jetbrains.annotations.Nullable;

/**
 * Better Inventory in the colours containers are painted with ({@link Theme#containerPalette()}: the dark palette
 * with the accent in use), while the container style is on ({@code reskinContainers}) and the mod is not deny-listed.
 * That mod draws nearly everything from sheets of pixel art in a palette of its own, so the work is a palette swap:
 * <ul>
 *   <li>its sheets ({@code betterinventory:textures/gui/...}) are read from whatever pack provides them, every colour
 *       of its palette is replaced by the tone that has its role here, and the result takes the sheet's place in the
 *       texture manager. Both of its looks are known: the dark one, and the grey one of its Vanilla Style pack. Colours
 *       that belong to neither palette (the shading of an icon) stay as drawn;</li>
 *   <li>the hotbar it replaces vanilla's with lives on the GUI atlas: those sprites are repainted where they sit;</li>
 *   <li>the few colours it paints from code, and its labels, are swapped on their way into {@code GuiGraphics}
 *       ({@link #fill}, {@link #text}) while one of its screens is open.</li>
 * </ul>
 * Its screens then show their own art, so {@link ContainerReskin} leaves their background alone ({@link #paints}).
 * Nothing of that mod is compiled against, and nothing of it is changed on disk; with the container style off, or
 * without the mod, this does nothing at all.
 */
public final class BetterInventoryPalette implements ResourceManagerReloadListener {

    public static final String MOD = "betterinventory";
    private static final String CLASSES = "dev.fallingcloud.betterinventory.";
    private static final String ART = "textures/gui";
    private static final String[] HOTBAR = {"hud/hotbar", "hud/hotbar_selection", "hud/hotbar_offhand_left", "hud/hotbar_offhand_right"};

    /** Which of the mod's screens is open: its containers, or the block chooser (a light panel painted from code). */
    private enum Open { NONE, CONTAINER, CHOOSER }

    /** The tones of the palette by the role they play in that mod's art. */
    private record Tones(int bg, int rim, int deep, int sunk, int well, int line, int faint, int glyph, int border, int strong, int mid,
                         int dim, int muted, int text, int accent, int surface, int key, int keyHover, int danger, int success,
                         int hover, int hoverEdge, int hoverShade) {

        static Tones of(final Palette p) {
            final int well = Colors.brighten(p.bg(), -0.32f);
            return new Tones(p.bg(), Colors.brighten(p.bg(), -0.5f), Colors.brighten(p.bg(), -0.2f), Colors.brighten(p.bg(), -0.14f), well,
                Colors.mix(p.bg(), p.border(), 0.8f), Colors.mix(p.bg(), p.border(), 0.55f), Colors.mix(p.border(), p.textDim(), 0.35f),
                p.border(), p.borderStrong(), Colors.mix(p.borderStrong(), p.textDim(), 0.5f), p.textDim(), p.textMuted(), p.text(),
                p.accent(), p.surface(), p.surfaceActive(), p.borderStrong(), p.danger(), p.success(),
                Colors.mix(well, p.accent(), 0.45f), p.accent(), Colors.mix(p.bg(), p.accent(), 0.3f));
        }
    }

    /** A sprite of the GUI atlas as it was stitched: the image it lives in, and its pixels before they were repainted. */
    private record Stitched(NativeImage image, int[] pixels) {}

    private static boolean looked, installed, registered;
    /** The art could not be repainted: not tried again before the resources are loaded anew. */
    private static boolean failed;
    /** The hotbar could not be repainted (another mod keeps the atlas its own way): the sheets are, the hotbar is left. */
    private static boolean noHotbar;
    /** What is in the texture manager now: 0 = that mod's own art, otherwise the accent it was repainted with. */
    private static int applied;
    @Nullable private static Tones tones;
    private static final Map<ResourceLocation, DynamicTexture> SHEETS = new HashMap<>();
    private static final Map<ResourceLocation, Stitched> SPRITES = new HashMap<>();
    @Nullable private static Screen seen;
    private static Open open = Open.NONE;

    private BetterInventoryPalette() {}

    // ------------------------------------------------------------------ when

    /** Whether that mod's art should be in Slate's palette now. */
    private static boolean wanted() {
        if (!looked) {
            looked = true;
            installed = SlatePlatform.get().isModLoaded(MOD);
        }
        if (!installed) return false;
        final CoreConfig cfg = Slate.config();
        if (!cfg.reskinContainers) return false;
        for (final String deny : cfg.reskinDenylist) if (!deny.isEmpty() && CLASSES.startsWith(deny)) return false;
        return true;
    }

    /** Every client tick: follows the container style, the deny-list and the accent. */
    public static void tick() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;                                // resources are being loaded: the listener follows
        final int want = wanted() ? Theme.current().containerPalette().accent() | 0xFF000000 : 0;
        if (want == 0 && applied == 0 || failed) return;
        if (!registered) {
            registered = true;
            if (mc.getResourceManager() instanceof ReloadableResourceManager reloadable) reloadable.registerReloadListener(new BetterInventoryPalette());
        }
        if (want != applied) apply(mc.getResourceManager(), want);
    }

    @Override
    public void onResourceManagerReload(final ResourceManager manager) {
        // The sheets may come from another pack now, and the atlas has been stitched again.
        SPRITES.clear();
        failed = false;
        noHotbar = false;
        final int want = wanted() ? Theme.current().containerPalette().accent() | 0xFF000000 : 0;
        if (want != 0 || applied != 0) apply(manager, want);
    }

    /** True while that mod's screens show their own art in Slate's palette: their background is not replaced. */
    public static boolean paints(@Nullable final Screen screen) {
        return applied != 0 && screen != null && screen.getClass().getName().startsWith(CLASSES);
    }

    // ------------------------------------------------------------------ the art

    private static void apply(final ResourceManager manager, final int want) {
        final Minecraft mc = Minecraft.getInstance();
        final TextureManager textures = mc.getTextureManager();
        tones = want == 0 ? null : Tones.of(Theme.current().containerPalette());
        seen = null;
        try {
            final Map<ResourceLocation, DynamicTexture> gone = new HashMap<>(SHEETS);
            if (tones != null) {
                final Map<ResourceLocation, Resource> found = manager.listResources(ART, id -> id.getNamespace().equals(MOD) && id.getPath().endsWith(".png"));
                for (final Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
                    final NativeImage image = repainted(e.getValue(), tones);
                    if (image == null) continue;
                    gone.remove(e.getKey());
                    final DynamicTexture texture = new DynamicTexture(image);
                    textures.register(e.getKey(), texture);                   // closes the one it replaces
                    SHEETS.put(e.getKey(), texture);
                }
            }
            // What is not repainted any more goes back to what the packs provide: the next draw loads it.
            for (final ResourceLocation id : gone.keySet()) {
                textures.release(id);
                SHEETS.remove(id);
            }
            applied = want;
        } catch (final Exception e) {
            Slate.LOGGER.warn("[Slate] Better Inventory could not be given Slate's palette: {}", e.toString());
            for (final ResourceLocation id : SHEETS.keySet()) textures.release(id);
            SHEETS.clear();
            tones = null;
            applied = 0;
            failed = true;
            return;
        }
        if (noHotbar) return;
        try {
            for (final String name : HOTBAR) sprite(mc, ResourceLocation.withDefaultNamespace(name), tones);
        } catch (final Exception e) {
            noHotbar = true;
            Slate.LOGGER.warn("[Slate] Better Inventory's hotbar keeps its own colours: {}", e.toString());
        }
    }

    /** One sheet in Slate's palette, or null when it cannot be read or none of it is in a palette that is known. */
    private static @Nullable NativeImage repainted(final Resource resource, final Tones t) {
        final NativeImage image;
        try (InputStream in = resource.open()) {
            image = NativeImage.read(in);
        } catch (final Exception e) {
            return null;
        }
        final int w = image.getWidth(), h = image.getHeight();
        final int[] from = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) from[y * w + x] = argb(image.getPixelRGBA(x, y));
        final int[] to = repaint(from, w, h, t);
        if (to == null) {
            image.close();
            return null;
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setPixelRGBA(x, y, argb(to[y * w + x]));
        return image;
    }

    /** The pixels of a sheet or a sprite in Slate's palette; null when nothing of them changed. */
    private static int @Nullable [] repaint(final int[] from, final int w, final int h, final Tones t) {
        final boolean light = light(from);
        final int[] to = new int[from.length];
        boolean changed = false;
        for (int i = 0; i < from.length; i++) {
            final int c = from[i];
            final int alpha = c >>> 24, rgb = c & 0xFFFFFF;
            if (alpha == 0) { to[i] = c; continue; }
            int tone = light ? ofLight(rgb, t) : ofDark(rgb, t);
            // The dark art's ground is also what parts its slots: there it is the line of a well.
            if (!light && rgb == 0x000000 && alpha == 0xFF && besideSlot(from, w, h, i)) tone = t.line;
            if (tone == 0) { to[i] = c; continue; }
            to[i] = alpha << 24 | tone & 0xFFFFFF;
            changed |= to[i] != c;
        }
        return changed ? to : null;
    }

    /** Whether the art is the grey one: what most of it is painted with is light. */
    private static boolean light(final int[] pixels) {
        int dark = 0, bright = 0;
        for (final int c : pixels) {
            if (c >>> 24 < 0x80) continue;
            if (Colors.luminance(c) > 0.4f) bright++; else dark++;
        }
        return bright > dark;
    }

    private static boolean besideSlot(final int[] pixels, final int w, final int h, final int at) {
        final int x = at % w, y = at / w;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                final int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < w && ny < h && pixels[ny * w + nx] == 0xFF1A1A1A) return true;
            }
        }
        return false;
    }

    /** The dark art's palette. 0 = not one of its colours. */
    private static int ofDark(final int rgb, final Tones t) {
        return switch (rgb) {
            case 0x000000 -> t.bg;                       // the ground of a panel
            case 0x141414 -> t.deep;                     // the shade beside a frame
            case 0x1A1A1A -> t.well;                     // the inside of a slot
            case 0x242424 -> t.faint;                    // the grid that is not there yet
            case 0x333333 -> t.glyph;                    // icons
            case 0x404040, 0x575757 -> t.strong;         // frames that are off, the hotbar's lines
            case 0x5C5C5C -> t.mid;
            case 0x7F7F7F, 0x808080 -> t.dim;            // frames
            case 0xD7D7D7 -> t.text;
            case 0xFFFFFF -> t.accent;                   // the ornaments, and what is pointed at
            case 0x26262C -> t.bg;                       // the padlock
            case 0x5C5C66 -> t.dim;
            case 0x83838F -> t.muted;
            default -> 0;
        };
    }

    /** The palette of the grey art (the Vanilla Style pack, and the settings in either look). 0 = not one of its colours. */
    private static int ofLight(final int rgb, final Tones t) {
        return switch (rgb) {
            case 0x000000, 0x141414 -> t.rim;            // the outline of a panel
            case 0x373737, 0xFFFFFF -> t.line;           // the two bevels, of a panel and of a slot
            case 0x555555 -> t.border;
            case 0x6E6E6E -> t.deep;                     // a tab that is off
            case 0x8B8B8B -> t.well;                     // the inside of a slot
            case 0xAEAEAE -> t.sunk;
            case 0xC6C6C6 -> t.bg;                       // the body of a panel
            case 0xD2D2D2, 0xD6D6D6 -> t.surface;
            case 0x7778A0 -> t.hover;                    // the blue of what is pointed at
            case 0xCFD0F7 -> t.hoverEdge;
            case 0x373860 -> t.hoverShade;
            default -> 0;
        };
    }

    /** One sprite of the GUI atlas: repainted where it sits, or put back as it was stitched ({@code t} null). */
    private static void sprite(final Minecraft mc, final ResourceLocation id, @Nullable final Tones t) {
        final TextureAtlasSprite sprite = mc.getGuiSprites().getSprite(id);
        if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) return;
        final NativeImage image = ((SpriteContentsAccessor) sprite.contents()).slate$originalImage();
        final int w = image.getWidth(), h = image.getHeight();
        Stitched stitched = SPRITES.get(id);
        if (stitched == null || stitched.image != image) {
            final int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) pixels[y * w + x] = argb(image.getPixelRGBA(x, y));
            stitched = new Stitched(image, pixels);
            SPRITES.put(id, stitched);
        }
        int[] to = stitched.pixels;
        if (t != null) {
            if (!darkHotbar(stitched.pixels)) return;                        // vanilla's, or another pack's
            final int[] painted = repaint(stitched.pixels, w, h, t);
            if (painted != null) to = painted;
        }
        boolean same = true;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final int c = argb(to[y * w + x]);
                if (image.getPixelRGBA(x, y) != c) {
                    image.setPixelRGBA(x, y, c);
                    same = false;
                }
            }
        }
        if (same) return;
        mc.getTextureManager().getTexture(sprite.atlasLocation()).bind();
        sprite.uploadFirstFrame();
    }

    /** That mod's dark hotbar is painted with four colours; anything else on a sprite means the art is someone else's. */
    private static boolean darkHotbar(final int[] pixels) {
        for (final int c : pixels) {
            if (c >>> 24 == 0) continue;
            final int rgb = c & 0xFFFFFF;
            if (rgb != 0x000000 && rgb != 0x575757 && rgb != 0x808080 && rgb != 0xFFFFFF) return false;
        }
        return true;
    }

    /** {@code NativeImage} keeps a pixel as ABGR; the swap is its own inverse. */
    private static int argb(final int abgr) {
        return abgr & 0xFF00FF00 | (abgr >> 16 & 0xFF) | (abgr & 0xFF) << 16;
    }

    // ------------------------------------------------------------------ what that mod paints from code

    private static Open open() {
        if (tones == null) return Open.NONE;
        final Screen screen = Minecraft.getInstance().screen;
        if (screen != seen) {
            seen = screen;
            final String name = screen == null ? "" : screen.getClass().getName();
            open = !name.startsWith(CLASSES) ? Open.NONE : name.endsWith("BlockChooserScreen") ? Open.CHOOSER : Open.CONTAINER;
        }
        return open;
    }

    /** The colour of a {@code fill}: that mod's own colours become the tones that have their role. */
    public static int fill(final int color) {
        final Tones t = tones;
        if (t == null) return color;
        final Open on = open();
        if (on == Open.NONE) return color;
        final int rgb = color & 0xFFFFFF;
        final int tone = on == Open.CHOOSER ? switch (rgb) {
            case 0x000000 -> t.border;                   // the panel's outline
            case 0xC6C6C6 -> t.bg;                       // the panel
            case 0x909090 -> t.surface;                  // a row
            case 0xA0A0A0 -> t.key;                      // the row pointed at
            case 0x555555 -> t.strong;                   // a checkbox
            case 0xB0B0B0 -> t.well;
            case 0x3D8E3D -> t.accent;
            default -> 0;
        } : switch (rgb) {
            case 0x17171B -> t.well;                     // the board over what is locked
            case 0x2A2A31 -> t.line;
            case 0x1A1A1A, 0x8B8B8B -> color >>> 24 == 0x9E ? t.well : 0;   // the wash over a locked slot's ghost item
            case 0x26262C, 0x1A1A1E -> t.rim;            // the padlock's keyhole, the ring of a pip
            case 0x5C5C66 -> t.dim;                      // the padlock
            case 0x83838F -> t.muted;
            case 0x1A1D23 -> t.border;                   // the swap keys: edge, face, light, arrows
            case 0x6E7686 -> t.key;
            case 0x8E97A8 -> t.keyHover;
            case 0x9AA3B4 -> t.strong;
            case 0xB6BFCE -> t.accent;
            case 0xF2F5F9 -> t.text;
            case 0xE6E6EA, 0xB8B8B8 -> t.muted;          // the mark of a locked slot, the pager's arrows
            case 0x4A4A4A -> t.strong;
            case 0xC0392B, 0xB4443F -> t.danger;
            case 0x56C25A -> t.success;
            default -> 0;
        };
        return tone == 0 ? color : color & 0xFF000000 | tone & 0xFFFFFF;
    }

    /** The colour of a string: that mod's labels are dark ones made for a light panel, or the greys of its dark art. */
    public static int text(final int color) {
        final Tones t = tones;
        if (t == null) return color;
        final Open on = open();
        if (on == Open.NONE) return color;
        final int tone = switch (color & 0xFFFFFF) {
            case 0x22222A, 0x303030 -> t.text;           // a heading, the name in a row
            case 0x3F3F46, 0x404040, 0xB9B9C2 -> t.muted;
            case 0x7A7A82, 0x333333 -> t.dim;
            case 0x226622 -> t.accent;                   // a row that is chosen
            default -> 0;
        };
        return tone == 0 ? color : color & 0xFF000000 | tone & 0xFFFFFF;
    }
}
