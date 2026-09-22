package dev.fallingcloud.slate.config.doc;

import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Finds a mod's config files by name: {@code config/<modid>*.{json,json5,toml,properties,cfg}} plus
 * anything supported inside {@code config/<modid>/}. Hyphen/underscore spelling differences are ignored
 * ({@code where_winds_blow} matches {@code where-winds-blow-client.toml}).
 */
public final class ConfigFiles {

    public static List<Path> forMod(final String modId) {
        final List<Path> out = new ArrayList<>();
        final Path cfg = SlatePlatform.get().configDir();
        if (!Files.isDirectory(cfg)) return out;
        final String norm = normalize(modId);
        if (norm.isEmpty()) return out;
        try (Stream<Path> s = Files.list(cfg)) {
            for (final Path p : s.sorted().toList()) {
                final String name = p.getFileName().toString();
                final String n = normalize(stripExtension(name));
                if (Files.isDirectory(p)) {
                    if (n.equals(norm)) out.addAll(listDir(p, 2));
                } else if (Documents.isSupported(p) && (n.equals(norm) || n.startsWith(norm + "client") || n.startsWith(norm + "common")
                    || n.startsWith(norm + "server") || n.startsWith(norm + "startup") || n.startsWith(norm) && n.length() - norm.length() <= 8)) {
                    out.add(p);
                }
            }
        } catch (final IOException ignored) {}
        return out;
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
