package dev.fallingcloud.slate.core.theme;

/** The two looks every Slate widget and screen implements. */
public enum Skin {
    /** Dark grey/black modern-pixel look (default). */
    DARK,
    /** Vanilla sprites and colours, polished and animated. */
    VANILLA;

    public static Skin parse(final String s) {
        return "VANILLA".equalsIgnoreCase(s) ? VANILLA : DARK;
    }
}
