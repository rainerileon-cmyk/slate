package dev.fallingcloud.slate.config.file;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;

/** UTF-8 text file helpers with atomic writes (temp + move), the same discipline as Core's JsonConfig. */
public final class TextFile {

    public static String read(final Path path) throws IOException {
        String s = Files.readString(path, StandardCharsets.UTF_8);
        if (!s.isEmpty() && s.charAt(0) == '﻿') s = s.substring(1);     // BOM: NeoForge's TOML reader chokes on it, so never write one back
        return s;
    }

    public static void writeAtomic(final Path path, final String content) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        final Path tmp = path.resolveSibling(path.getFileName() + ".slate-tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Last-modified millis, 0 when the file is missing. */
    public static long mtime(final Path path) {
        try {
            final FileTime t = Files.getLastModifiedTime(path);
            return t.toMillis();
        } catch (final IOException e) {
            return 0;
        }
    }

    /** Line separator used by the text ("\r\n" when the first line ends with it). */
    public static String lineSeparator(final String text) {
        final int i = text.indexOf('\n');
        return i > 0 && text.charAt(i - 1) == '\r' ? "\r\n" : "\n";
    }

    private TextFile() {}
}
