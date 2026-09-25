package dev.fallingcloud.slate.core.theme;

import dev.fallingcloud.slate.core.Slate;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The pixel fonts headings can use ({@code CoreConfig.pixelFont}), each a font definition in
 * {@code assets/slate/font/heading_<key>.json}. Pixeloid Sans and Monocraft are drawn on a 9-pixel em with
 * Minecraft's own cap height, so at size 9 one font pixel is one GUI pixel and they stay crisp at every GUI
 * scale; Pixelify Sans is the softer face Slate started with.
 */
public enum PixelFont {
    /** Pixeloid Sans (default). */
    PIXELOID("pixeloid"),
    /** Monocraft, a monospaced face modelled on Minecraft's own. */
    MONOCRAFT("monocraft"),
    /** Pixelify Sans. */
    PIXELIFY("pixelify");

    public static final PixelFont DEFAULT = PIXELOID;

    private final String key;
    private final ResourceLocation id;

    PixelFont(final String key) {
        this.key = key;
        this.id = Slate.id("heading_" + key);
    }

    /** The value stored in {@code core.json}. */
    public String key() { return key; }

    /** The font id, for {@code Style.withFont}. */
    public ResourceLocation id() { return id; }

    /** The font's name written in the font itself, so a chooser previews each option. */
    public MutableComponent label() {
        return Component.translatable("slate.pixel_font." + key).withStyle(s -> s.withFont(id));
    }

    /** The font a stored value names; missing or unknown values get {@link #DEFAULT}. */
    public static PixelFont parse(@Nullable final String s) {
        if (s != null) {
            final String k = s.trim().toLowerCase(Locale.ROOT);
            for (final PixelFont f : values()) if (f.key.equals(k)) return f;
        }
        return DEFAULT;
    }
}
