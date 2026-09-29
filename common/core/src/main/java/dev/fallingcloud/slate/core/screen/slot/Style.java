package dev.fallingcloud.slate.core.screen.slot;

import dev.fallingcloud.slate.core.theme.Skin;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * How a menu's 2D chrome is painted: Slate's dark modern look ({@link #SLATE}, the {@code skin: "DARK"} of older
 * configs) or vanilla's stone ({@link #VANILLA}). One global value plus per-slot overrides; {@link Skin} stays the
 * type the widgets dispatch on. Pure data: safe on a dedicated server.
 */
public enum Style {
    SLATE, VANILLA;

    /** Parses {@code SLATE}, {@code DARK} (the legacy spelling) or {@code VANILLA}; anything else reads as {@code fallback}. */
    public static Style parse(@Nullable final String s, final Style fallback) {
        if (s == null) return fallback;
        final String v = s.trim().toUpperCase(Locale.ROOT);
        if (v.equals("VANILLA")) return VANILLA;
        if (v.equals("SLATE") || v.equals("DARK")) return SLATE;
        return fallback;
    }

    public static Style of(final Skin skin) { return skin == Skin.VANILLA ? VANILLA : SLATE; }

    public Skin skin() { return this == VANILLA ? Skin.VANILLA : Skin.DARK; }

    /** The lower-case name, for config values and translation keys ({@code slate.style.<key>}). */
    public String key() { return name().toLowerCase(Locale.ROOT); }
}
