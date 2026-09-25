package dev.fallingcloud.slate.core.gfx;

import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Text helpers that apply the theme's fonts. */
public final class Fonts {

    /** {@code text} in the heading font (the configured pixel font) when enabled. */
    public static MutableComponent heading(final Component text) {
        return text.copy().withStyle(Theme.current().headingStyle());
    }

    public static MutableComponent heading(final String text) {
        return heading(Component.literal(text));
    }

    private Fonts() {}
}
