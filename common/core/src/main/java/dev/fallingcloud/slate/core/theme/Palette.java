package dev.fallingcloud.slate.core.theme;

import java.util.List;

/**
 * The colour tokens of a theme, ARGB. Widgets never hard-code colours: they read these. The dark set is
 * a warm near-black (a shade darker than the Claude desktop app); the vanilla set only supplies text and
 * accent tones because surfaces are drawn from vanilla sprites.
 */
public record Palette(
    int bg, int bg2, int surface, int surfaceHover, int surfaceActive,
    int border, int borderStrong,
    int text, int textMuted, int textDim,
    int accent, int accentHover, int accentText,
    int danger, int success, int warning,
    int overlay, int shadow) {

    public static final int DEFAULT_ACCENT = 0xFFD9805E;

    public static Palette dark(final int accent) {
        return new Palette(
            0xFF161615, 0xFF1B1B1A, 0xFF222221, 0xFF2A2A28, 0xFF323230,
            0xFF33332F, 0xFF45443F,
            0xFFECEAE4, 0xFFA19F97, 0xFF6E6C66,
            accent, Colors.hover(accent), Colors.readableOn(accent),
            0xFFE5484D, 0xFF5CB176, 0xFFE0A458,
            0xA0000000, 0x66000000);
    }

    public static Palette vanilla(final int accent) {
        return new Palette(
            0xFF1E1E1E, 0xFF242424, 0xFF3A3A3A, 0xFF474747, 0xFF555555,
            0xFF000000, 0xFF8B8B8B,
            0xFFFFFFFF, 0xFFA0A0A0, 0xFF707070,
            accent, Colors.hover(accent), Colors.readableOn(accent),
            0xFFFF5555, 0xFF55FF55, 0xFFFFAA00,
            0xB0000000, 0x66000000);
    }

    public Palette withAccent(final int accent) {
        return new Palette(bg, bg2, surface, surfaceHover, surfaceActive, border, borderStrong, text, textMuted,
            textDim, accent, Colors.hover(accent), Colors.readableOn(accent), danger, success, warning, overlay, shadow);
    }

    /** Named accents offered by the pickers. */
    public record AccentPreset(String name, int color) {}

    public static final List<AccentPreset> ACCENTS = List.of(
        new AccentPreset("Terracotta", 0xFFD9805E),
        new AccentPreset("Ember", 0xFFE06C4B),
        new AccentPreset("Amber", 0xFFE0A458),
        new AccentPreset("Moss", 0xFF7BB36A),
        new AccentPreset("Mint", 0xFF5CC8A8),
        new AccentPreset("Sky", 0xFF5BA8E0),
        new AccentPreset("Cobalt", 0xFF5C7CFA),
        new AccentPreset("Lavender", 0xFF9B86E0),
        new AccentPreset("Orchid", 0xFFD076C9),
        new AccentPreset("Rose", 0xFFE5698A),
        new AccentPreset("Slate", 0xFF9AA5B4),
        new AccentPreset("Bone", 0xFFD8D2C4));
}
