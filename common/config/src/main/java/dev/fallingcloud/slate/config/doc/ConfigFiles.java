package dev.fallingcloud.slate.config.doc;

import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Finds a mod's config files by name: {@code config/<modid>*.{json,json5,toml,properties,cfg}} plus
 * anything supported inside {@code config/<modid>/}. Hyphen/underscore spelling differences are ignored
 * ({@code where_winds_blow} matches {@code where-winds-blow-client.toml}).
 *
 * <p>The Mods page asks this for every installed mod at once. Listing {@code config/} and testing every entry for being
 * a folder, once per mod, came to about 90 000 file-system calls in a 300-mod pack and froze the page, so the top level
 * is listed once and that listing reused for {@link #SNAPSHOT_TTL_MS}.
 */
public final class ConfigFiles {

    /** How long one listing of {@code config/} answers (long enough for one Mods page build, short enough to see new files). */
    private static final long SNAPSHOT_TTL_MS = 2_000;

    /** A top-level entry of {@code config/}: its name stem normalized as {@link #normalize} does, and whether it is a folder. */
    private record Entry(Path path, String stem, boolean directory, boolean supported) {}

    private record Snapshot(Path dir, List<Entry> entries, long takenAtMs) {}

    private static @Nullable Snapshot snapshot;

    public static List<Path> forMod(final String modId) {
        final List<Path> out = new ArrayList<>();
        final String norm = normalize(modId);
        if (norm.isEmpty()) return out;
        for (final Entry e : entries()) {
            if (e.directory()) {
                if (e.stem().equals(norm)) out.addAll(listDir(e.path(), 2));
            } else if (e.supported() && (e.stem().equals(norm) || e.stem().startsWith(norm + "client") || e.stem().startsWith(norm + "common")
                || e.stem().startsWith(norm + "server") || e.stem().startsWith(norm + "startup")
                || e.stem().startsWith(norm) && e.stem().length() - norm.length() <= 8)) {
                out.add(e.path());
            }
        }
        return out;
    }

    /** The top level of {@code config/}, sorted, listed at most once per {@link #SNAPSHOT_TTL_MS}. */
    private static synchronized List<Entry> entries() {
        final Path cfg = SlatePlatform.get().configDir();
        final long now = Util.getMillis();
        final Snapshot s = snapshot;
        if (s != null && s.dir().equals(cfg) && now - s.takenAtMs() < SNAPSHOT_TTL_MS) return s.entries();
        final List<Entry> entries = new ArrayList<>();
        if (Files.isDirectory(cfg)) {
            try (Stream<Path> list = Files.list(cfg)) {
                for (final Path p : list.sorted().toList()) {
                    entries.add(new Entry(p, normalize(stripExtension(p.getFileName().toString())), Files.isDirectory(p), Documents.isSupported(p)));
                }
            } catch (final IOException ignored) {}
        }
        snapshot = new Snapshot(cfg, List.copyOf(entries), now);
        return snapshot.entries();
    }

    private static List<Path> listDir(final Path dir, final int depth) {
        final List<Path> out = new ArrayList<>();
        if (depth <= 0) return out;
        try (Stream<Path> s = Files.list(dir)) {
            for (final Path p : s.sorted().toList()) {
                if (Files.isDirectory(p)) out.addAll(listDir(p, depth - 1));
                else if (Documents.isSupported(p) && Files.size(p) < 4_000_000L) out.add(p);
            }
        } catch (final IOException ignored) {}
        return out;
    }

    private static String stripExtension(final String name) {
        final int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String normalize(final String s) {
        return s.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
    }

    private ConfigFiles() {}
}
