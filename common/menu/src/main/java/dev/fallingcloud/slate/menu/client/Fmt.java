package dev.fallingcloud.slate.menu.client;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.minecraft.network.chat.Component;

/** Small formatting helpers shared by the menu screens ("2h ago", "1.2 GB", "12m 30s"). */
public final class Fmt {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_SHORT = DateTimeFormatter.ofPattern("d MMM yyyy");

    /** "just now", "5m ago", "2h ago", "3d ago", "2w ago", then the date. */
    public static Component ago(final long epochMs) {
        if (epochMs <= 0) return Component.translatable("slate_menu.time.never");
        final long d = Math.max(0, System.currentTimeMillis() - epochMs);
        final long s = d / 1000, m = s / 60, h = m / 60, days = h / 24;
        if (s < 45) return Component.translatable("slate_menu.time.just_now");
        if (m < 60) return Component.translatable("slate_menu.time.minutes_ago", m);
        if (h < 24) return Component.translatable("slate_menu.time.hours_ago", h);
        if (days < 7) return Component.translatable("slate_menu.time.days_ago", days);
        if (days < 30) return Component.translatable("slate_menu.time.weeks_ago", days / 7);
        return Component.literal(DATE_SHORT.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())));
    }

    public static String date(final long epochMs) {
        if (epochMs <= 0) return "-";
        return DATE.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
    }

    /** "12m 30s" / "1h 05m" style session duration. */
    public static String duration(final long ms) {
        final long s = Math.max(0, ms / 1000), m = s / 60, h = m / 60;
        if (h > 0) return "%dh %02dm".formatted(h, m % 60);
        if (m > 0) return "%dm %02ds".formatted(m, s % 60);
        return s + "s";
    }

    /** "512 KB", "1.2 GB". */
    public static String bytes(final long bytes) {
        if (bytes < 0) return "?";
        if (bytes < 1024) return bytes + " B";
        final double kb = bytes / 1024.0;
        if (kb < 1024) return "%.0f KB".formatted(kb);
        final double mb = kb / 1024.0;
        if (mb < 1024) return "%.1f MB".formatted(mb);
        return "%.2f GB".formatted(mb / 1024.0);
    }

    /** Latency in ms to a 0..5 bar count like vanilla's ping icon. */
    public static int pingBars(final long ping) {
        if (ping < 0) return 0;
        if (ping < 150) return 5;
        if (ping < 300) return 4;
        if (ping < 600) return 3;
        if (ping < 1000) return 2;
        return 1;
    }

    private Fmt() {}
}
