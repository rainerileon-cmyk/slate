package dev.fallingcloud.slate.core.theme;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.config.CoreConfig;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * The current theme, derived from {@link CoreConfig}. Rebuilt by {@link #reload()} whenever the config
 * changes (the Config module and Core's settings page call it); widgets read {@link #current()} every
 * frame, so a change applies live.
 */
public final class Theme {

    public static final ResourceLocation HEADING_FONT = Slate.id("heading");

    private static volatile Theme current = new Theme(new CoreConfig());
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private final Skin skin;
    private final Palette palette;
    /** The dark palette with the current accent: what container screens are painted with in either skin. */
    private final Palette containerPalette;
    private final int radius;
    private final int containerRadius;
    private final float motion;
    private final boolean headingFont;
    private final boolean transitions;
    private final boolean uiSounds;
    private final boolean toasts;
    private final boolean blurInGame;

    private Theme(final CoreConfig cfg) {
        this.skin = Skin.parse(cfg.skin);
        final int accent = Colors.fromHex(cfg.accent, Palette.DEFAULT_ACCENT);
        this.palette = skin == Skin.VANILLA ? Palette.vanilla(accent) : Palette.dark(accent);
        this.containerPalette = Palette.dark(accent);
        this.containerRadius = Math.max(0, Math.min(4, cfg.radius));
        this.radius = skin == Skin.VANILLA ? 0 : containerRadius;
        this.motion = (float) Math.max(0, Math.min(2, cfg.motion));
        this.headingFont = cfg.headingFont;
        this.transitions = cfg.transitions;
        this.uiSounds = cfg.uiSounds;
        this.toasts = cfg.toasts;
        this.blurInGame = cfg.blurInGame;
    }

    public static Theme current() { return current; }

    /** Rebuild from the saved core config and notify listeners. */
    public static void reload() {
        current = new Theme(Slate.config());
        for (final Runnable r : LISTENERS) {
            try { r.run(); } catch (final Exception e) { Slate.LOGGER.error("[Slate] theme listener threw", e); }
        }
    }

    public static void onChange(final Runnable listener) { LISTENERS.add(listener); }

    public Skin skin() { return skin; }
    public boolean isVanilla() { return skin == Skin.VANILLA; }
    public Palette palette() { return palette; }
    /**
     * The palette container screens are restyled with: the dark one, in either skin, so the container-style switch
     * works on its own ({@code reskinContainers}).
     */
    public Palette containerPalette() { return containerPalette; }
    /** Pixel-stepped corner radius; 0 on the vanilla skin. */
    public int radius() { return radius; }
    /** The configured corner radius, used by the container restyle in either skin. */
    public int containerRadius() { return containerRadius; }
    /**
     * The layout switch: Slate Menu's rebuilt screens replace vanilla's (true) or vanilla's own screens show (false).
     * Read live from the config, so a change applies at the next screen open.
     */
    public static boolean customLayout() { return Slate.config().customLayout; }
    /** 0 = no motion (everything snaps), 1 = default speeds, 2 = half speed. */
    public float motion() { return motion; }
    public boolean transitions() { return transitions; }
    public boolean uiSounds() { return uiSounds; }
    public boolean toasts() { return toasts; }
    public boolean blurInGame() { return blurInGame; }

    /** Style for headings: Pixelify Sans when enabled, the default font otherwise. */
    public Style headingStyle() {
        return headingFont ? Style.EMPTY.withFont(HEADING_FONT) : Style.EMPTY;
    }

    /** Duration in ms after the motion multiplier (0 when motion is off). */
    public int ms(final int base) {
        return Math.round(base * motion);
    }

    // Shorthands so widget code reads well.
    public int bg() { return palette.bg(); }
    public int surface() { return palette.surface(); }
    public int border() { return palette.border(); }
    public int text() { return palette.text(); }
    public int muted() { return palette.textMuted(); }
    public int accent() { return palette.accent(); }
}
