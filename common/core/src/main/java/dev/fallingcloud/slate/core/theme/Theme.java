package dev.fallingcloud.slate.core.theme;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.config.CoreConfig;
import dev.fallingcloud.slate.core.screen.slot.Layout;
import dev.fallingcloud.slate.core.screen.slot.Style;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The current theme, derived from {@link CoreConfig}. Rebuilt by {@link #reload()} whenever the config
 * changes (the Config module and Core's settings page call it); widgets read {@link #current()} every
 * frame, so a change applies live.
 *
 * <p>Both styles are kept built ({@link #variant}): the global style is the config's {@code skin}, and a menu slot
 * may override it, so the client switches the active variant with {@link #activate} right before a screen is
 * shown (before its {@code init}, so nothing flickers). Screens outside any slot, and the in-world HUD, use the
 * global style.</p>
 */
public final class Theme {

    /**
     * The default pixel font's id. Headings follow the configured font: use {@link #headingStyle()}.
     * @deprecated public API kept for older callers; the configured font is {@link #pixelFont()}.
     */
    @Deprecated
    public static final ResourceLocation HEADING_FONT = PixelFont.DEFAULT.id();

    private static volatile Theme dark = new Theme(new CoreConfig(), Skin.DARK);
    private static volatile Theme vanilla = new Theme(new CoreConfig(), Skin.VANILLA);
    private static volatile Theme current = dark;
    private static volatile Style globalStyle = Style.SLATE;
    private static volatile Style activeStyle = Style.SLATE;
    /** Client-side: the style of the open screen's slot (null before the client is up: the global style). */
    @Nullable private static volatile Supplier<Style> activeResolver;
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private final Skin skin;
    private final Palette palette;
    /** The dark palette with the current accent: what container screens are painted with in either skin. */
    private final Palette containerPalette;
    private final int radius;
    private final int containerRadius;
    private final float motion;
    private final boolean headingFont;
    private final PixelFont pixelFont;
    private final boolean transitions;
    private final boolean uiSounds;
    private final boolean toasts;
    private final boolean blurInGame;

    private Theme(final CoreConfig cfg, final Skin skin) {
        this.skin = skin;
        final int accent = Colors.fromHex(cfg.accent, Palette.DEFAULT_ACCENT);
        this.palette = skin == Skin.VANILLA ? Palette.vanilla(accent) : Palette.dark(accent);
        this.containerPalette = Palette.dark(accent);
        this.containerRadius = Math.max(0, Math.min(4, cfg.radius));
        this.radius = skin == Skin.VANILLA ? 0 : containerRadius;
        this.motion = (float) Math.max(0, Math.min(2, cfg.motion));
        this.headingFont = cfg.headingFont;
        this.pixelFont = PixelFont.parse(cfg.pixelFont);
        this.transitions = cfg.transitions;
        this.uiSounds = cfg.uiSounds;
        this.toasts = cfg.toasts;
        this.blurInGame = cfg.blurInGame;
    }

    /** The active variant: the open screen's style, or the global one. */
    public static Theme current() { return current; }

    /** The built theme of one style (both exist at all times). */
    public static Theme variant(final Style style) { return style == Style.VANILLA ? vanilla : dark; }

    /** Rebuild both variants from the saved core config, re-resolve the active style and notify listeners. */
    public static void reload() {
        final CoreConfig cfg = Slate.config();
        dark = new Theme(cfg, Skin.DARK);
        vanilla = new Theme(cfg, Skin.VANILLA);
        globalStyle = cfg.style();
        final Supplier<Style> resolver = activeResolver;
        Style style = null;
        if (resolver != null) {
            try { style = resolver.get(); } catch (final Exception e) { Slate.LOGGER.warn("[Slate] style resolver threw: {}", e.toString()); }
        }
        activate(style);
        for (final Runnable r : LISTENERS) {
            try { r.run(); } catch (final Exception e) { Slate.LOGGER.error("[Slate] theme listener threw", e); }
        }
    }

    public static void onChange(final Runnable listener) { LISTENERS.add(listener); }

    /** Client bootstrap installs the resolver that maps the open screen to its slot's style. */
    public static void setActiveStyleResolver(@Nullable final Supplier<Style> resolver) { activeResolver = resolver; }

    /** Switches the active variant (null = the global style). Called before a screen is shown; no listeners fire. */
    public static void activate(@Nullable final Style style) {
        activeStyle = style == null ? globalStyle : style;
        current = variant(activeStyle);
    }

    /** The style of the config ({@code skin}), whatever the open screen overrides. */
    public static Style globalStyle() { return globalStyle; }

    /** The style the open screen is painted in. */
    public static Style activeStyle() { return activeStyle; }

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
     * The legacy layout switch: any Slate layout (true) or vanilla's own screens (false), read live from the config.
     * @deprecated the layout is three-valued and per menu now: ask {@code MenuSlots.effective(slot)} or
     *             {@code Slate.config().layout()}.
     */
    @Deprecated
    public static boolean customLayout() { return Slate.config().layout() != Layout.VANILLA; }
    /** 0 = no motion (everything snaps), 1 = default speeds, 2 = half speed. */
    public float motion() { return motion; }
    public boolean transitions() { return transitions; }
    public boolean uiSounds() { return uiSounds; }
    public boolean toasts() { return toasts; }
    public boolean blurInGame() { return blurInGame; }

    /** The chosen pixel font; headings use it while the heading font is on. */
    public PixelFont pixelFont() { return pixelFont; }

    /** Style for headings: the chosen pixel font when enabled, the default font otherwise. */
    public net.minecraft.network.chat.Style headingStyle() {
        return headingFont ? net.minecraft.network.chat.Style.EMPTY.withFont(pixelFont.id()) : net.minecraft.network.chat.Style.EMPTY;
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
