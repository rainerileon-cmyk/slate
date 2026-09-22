package dev.fallingcloud.slate.core.theme;

import net.minecraft.util.Mth;

/** ARGB int helpers. All colours in Slate are ARGB ints ({@code 0xAARRGGBB}). */
public final class Colors {

    public static int argb(final int a, final int r, final int g, final int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    public static int alpha(final int c) { return c >>> 24; }
    public static int red(final int c) { return (c >> 16) & 0xFF; }
    public static int green(final int c) { return (c >> 8) & 0xFF; }
    public static int blue(final int c) { return c & 0xFF; }

    /** Same colour with alpha {@code a} (0-255). */
    public static int withAlpha(final int c, final int a) {
        return (Mth.clamp(a, 0, 255) << 24) | (c & 0xFFFFFF);
    }

    /** Same colour with its alpha scaled by {@code f} (0-1). */
    public static int scaleAlpha(final int c, final float f) {
        return withAlpha(c, Math.round(alpha(c) * Mth.clamp(f, 0f, 1f)));
    }

    /** Linear interpolation per channel, alpha included. */
    public static int lerp(final int a, final int b, final float t) {
        final float k = Mth.clamp(t, 0f, 1f);
        return argb(
            Math.round(alpha(a) + (alpha(b) - alpha(a)) * k),
            Math.round(red(a) + (red(b) - red(a)) * k),
            Math.round(green(a) + (green(b) - green(a)) * k),
            Math.round(blue(a) + (blue(b) - blue(a)) * k));
    }

    /** Mix RGB only, keeping {@code a}'s alpha. */
    public static int mix(final int a, final int b, final float t) {
        return withAlpha(lerp(a, b, t), alpha(a));
    }

    /** Lightens (f&gt;0) or darkens (f&lt;0) toward white/black by fraction |f|. */
    public static int brighten(final int c, final float f) {
        return f >= 0 ? mix(c, 0xFFFFFFFF, f) : mix(c, 0xFF000000, -f);
    }

    /** Multiplies RGB by {@code f} (used for vanilla hover lifts). */
    public static int scale(final int c, final float f) {
        return argb(alpha(c), Math.round(Mth.clamp(red(c) * f, 0, 255)),
            Math.round(Mth.clamp(green(c) * f, 0, 255)), Math.round(Mth.clamp(blue(c) * f, 0, 255)));
    }

    /** Parses {@code #RRGGBB} / {@code #AARRGGBB} / {@code RRGGBB}; returns {@code fallback} on garbage. */
    public static int fromHex(final String hex, final int fallback) {
        if (hex == null) return fallback;
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        try {
            if (h.length() == 6) return 0xFF000000 | Integer.parseInt(h, 16);
            if (h.length() == 8) return (int) Long.parseLong(h, 16);
        } catch (final NumberFormatException ignored) {}
        return fallback;
    }

    /** {@code #RRGGBB}. */
    public static String toHex(final int c) {
        return "#%02X%02X%02X".formatted(red(c), green(c), blue(c));
    }

    /** Relative luminance 0-1 (sRGB approximation). */
    public static float luminance(final int c) {
        return (0.2126f * red(c) + 0.7152f * green(c) + 0.0722f * blue(c)) / 255f;
    }

    /** Black or white, whichever reads better on {@code bg}. */
    public static int readableOn(final int bg) {
        return luminance(bg) > 0.55f ? 0xFF141413 : 0xFFF5F4EF;
    }

    /** Hue-preserving hover variant of an accent: a little brighter, a little more saturated. */
    public static int hover(final int c) {
        return brighten(c, 0.12f);
    }

    public static int hsvToRgb(final float h, final float s, final float v) {
        final int rgb = Mth.hsvToRgb(((h % 1f) + 1f) % 1f, Mth.clamp(s, 0f, 1f), Mth.clamp(v, 0f, 1f));
        return 0xFF000000 | rgb;
    }

    /** {h, s, v} each 0-1. */
    public static float[] rgbToHsv(final int c) {
        final float r = red(c) / 255f, g = green(c) / 255f, b = blue(c) / 255f;
        final float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        final float d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h /= 6f;
            if (h < 0) h += 1f;
        }
        return new float[] { h, max == 0 ? 0 : d / max, max };
    }

    private Colors() {}
}
