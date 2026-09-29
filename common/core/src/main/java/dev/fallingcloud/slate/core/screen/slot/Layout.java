package dev.fallingcloud.slate.core.screen.slot;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Which screen shows for a menu slot: Minecraft's own ({@link #VANILLA}), Slate's rebuilt 2D screens
 * ({@link #CUSTOM}) or the scene-based screens ({@link #OVERHAUL}). Ordered from plainest to richest, so a slot
 * that lacks the requested layout falls back down the order ({@link MenuSlots#effective}). Pure data: safe on a
 * dedicated server.
 */
public enum Layout {
    VANILLA, CUSTOM, OVERHAUL;

    /** Parses a config value ({@code "overhaul"}, {@code "CUSTOM"}, ...); anything unknown reads as {@code fallback}. */
    public static Layout parse(@Nullable final String s, final Layout fallback) {
        if (s == null) return fallback;
        try { return valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (final IllegalArgumentException e) { return fallback; }
    }

    /** The lower-case name, for config values and translation keys ({@code slate.layout.<key>}). */
    public String key() { return name().toLowerCase(Locale.ROOT); }

    /** The next plainer layout, or null below {@link #VANILLA}. */
    @Nullable
    public Layout lower() { return ordinal() == 0 ? null : values()[ordinal() - 1]; }
}
