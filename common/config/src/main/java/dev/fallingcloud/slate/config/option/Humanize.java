package dev.fallingcloud.slate.config.option;

import java.util.Locale;

/** Turns config keys into readable labels: {@code leafSpawnRate} / {@code leaf_spawn_rate} -> "Leaf spawn rate". */
public final class Humanize {

    public static String key(final String key) {
        if (key == null || key.isEmpty()) return "";
        final StringBuilder sb = new StringBuilder(key.length() + 8);
        char prev = 0;
        for (int i = 0; i < key.length(); i++) {
            final char c = key.charAt(i);
            if (c == '_' || c == '-' || c == '.') {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') sb.append(' ');
            } else if (Character.isUpperCase(c) && i > 0 && (Character.isLowerCase(prev) || Character.isDigit(prev))) {
                sb.append(' ').append(Character.toLowerCase(c));
            } else if (Character.isUpperCase(c) && i > 0 && i + 1 < key.length() && Character.isUpperCase(prev) && Character.isLowerCase(key.charAt(i + 1))) {
                // "HUDScale" -> "HUD scale"
                sb.append(' ').append(Character.toLowerCase(c));
            } else {
                sb.append(sb.length() == 0 ? Character.toUpperCase(c) : c);
            }
            prev = c;
        }
        return sb.toString().trim();
    }

    /** {@code SHARP_8X} -> "Sharp 8x", {@code DEFAULT} -> "Default". */
    public static String enumName(final String name) {
        if (name == null || name.isEmpty()) return "";
        if (name.chars().anyMatch(Character::isLowerCase) && !name.contains("_")) return key(name);
        final String[] parts = name.toLowerCase(Locale.ROOT).split("[_\\s]+");
        final StringBuilder sb = new StringBuilder();
        for (final String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(sb.length() == 0 ? Character.toUpperCase(p.charAt(0)) + p.substring(1) : p);
        }
        return sb.toString();
    }

    /** A file name without extension, humanised: {@code betterfog-client.toml} -> "Betterfog client". */
    public static String fileName(final String file) {
        String s = file;
        final int dot = s.lastIndexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        return key(s);
    }

    private Humanize() {}
}
