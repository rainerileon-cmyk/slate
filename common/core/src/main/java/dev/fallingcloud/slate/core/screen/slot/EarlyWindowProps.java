package dev.fallingcloud.slate.core.screen.slot;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.config.CoreConfig;
import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.theme.PixelFont;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * {@code config/slate/earlywindow.properties}: the few settings the NeoForge start-up window needs, written as a flat
 * file whenever they change, because that window runs before any mod (no Slate classes, no Gson) and used to parse
 * {@code core.json} and {@code menu.json} with regexes that broke whenever their structure moved. It holds the
 * effective layout and style of the {@link CoreSlots#LOADING} slot, the accent, radius and fonts, and whether Slate's
 * loading screens are on at all. The window falls back to the regexes when the file is missing.
 */
public final class EarlyWindowProps {

    public static final String FILE = "earlywindow.properties";
    private static String last;

    /** Writes the file if its content changed. Cheap; called from theme and slot listeners. */
    public static synchronized void write() {
        final CoreConfig cfg = Slate.config();
        final Layout layout = MenuSlots.effective(CoreSlots.LOADING);
        final Style style = MenuSlots.style(CoreSlots.LOADING);
        final String text = "# Written by Slate whenever its settings change; read by the NeoForge start-up window (slate-earlywindow).\n"
            + "# Do not edit: change the settings in the game instead.\n"
            + "layout=" + layout.name() + "\n"
            + "style=" + style.name() + "\n"
            + "loadingScreens=" + (layout != Layout.VANILLA) + "\n"
            + "accent=" + (cfg.accent == null ? "" : cfg.accent.trim()) + "\n"
            + "radius=" + cfg.radius + "\n"
            + "headingFont=" + cfg.headingFont + "\n"
            + "pixelFont=" + PixelFont.parse(cfg.pixelFont).key() + "\n";
        if (text.equals(last)) return;
        final Path file = JsonConfig.dir().resolve(FILE);
        try {
            Files.createDirectories(file.getParent());
            final Path tmp = file.resolveSibling(FILE + ".tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            last = text;
        } catch (final IOException e) {
            Slate.LOGGER.warn("[Slate] could not write {}: {}", file, e.toString());
        }
    }

    private EarlyWindowProps() {}
}
