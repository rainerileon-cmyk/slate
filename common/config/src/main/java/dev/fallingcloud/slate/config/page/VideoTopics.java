package dev.fallingcloud.slate.config.page;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * The Video page's tabs are topics, not mods: every row (vanilla's, Sodium's, Sodium Extra's, Iris', any mod on
 * Sodium's config API) lands on the tab that says what it is about. An option is sorted by the words in its id and
 * label, from the most specific topic to the broadest, and a page whose options say nothing recognisable falls back
 * to what its page is called; the last resort is "HUD &amp; extras".
 */
final class VideoTopics {

    static final String DISPLAY = "display", GRAPHICS = "graphics", PERFORMANCE = "performance", EFFECTS = "effects", EXTRAS = "extras";
    static final List<String> ALL = List.of(DISPLAY, GRAPHICS, PERFORMANCE, EFFECTS, EXTRAS);

    // Checked in this order: a word of an earlier list wins ("show_fps" is HUD, not display; "animate_only_visible" is
    // performance, not an animation; "reduce_resolution" is performance, not display).
    private static final String[] EXTRAS_WORDS = { "overlay", "text_contrast", "show_fps", "coordinates", "debug_hud", "debug", "toast",
        "tooltip", "prevent_shaders", "hud" };
    private static final String[] PERFORMANCE_WORDS = { "chunk_update", "defer", "cull", "occlusion", "animate_only", "no_error", "persistent",
        "render_ahead", "cpu", "gpu", "thread", "memory", "prioritiz", "async", "fast_random", "linear_flat", "light_update",
        "reduce_resolution", "buffer", "translucen", "sorting", "performance", "lag" };
    private static final String[] EFFECTS_WORDS = { "animation", "animate", "particle", "bobbing", "bob_view", "attack_indicator",
        "autosave", "fov", "screen_effect", "damage", "tilt", "glint", "lightning", "darkness", "shake", "hurt", "instant_sneak", "panini" };
    private static final String[] DISPLAY_WORDS = { "fullscreen", "vsync", "v_sync", "fps", "frame", "gui_scale", "guiscale", "brightness",
        "gamma", "resolution", "monitor", "blur", "adaptive", "wayland", "window", "borderless" };
    private static final String[] GRAPHICS_WORDS = { "render_distance", "simulation", "graphics", "cloud", "weather", "leaves", "smooth_light",
        "ambient_occlusion", "biome", "entity_distance", "entity_shadow", "shadow", "vignette", "mipmap", "fog", "sky", "star", "sun", "moon",
        "beacon", "enchanting", "item_frame", "name_tag", "light", "detail", "color", "shader", "texture", "quality", "distance", "render" };

    private VideoTopics() {}

    static Component title(final String topic) {
        return Component.translatable("slate_config.video.topic." + topic);
    }

    /** The topic of one option: {@code idPath} is its id's path (e.g. {@code render_distance}), the label and the page name are the fallbacks. */
    static String topicOf(final String idPath, final String label, final String pageName) {
        final String hay = idPath.toLowerCase(Locale.ROOT) + " " + label.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (any(hay, EXTRAS_WORDS)) return EXTRAS;
        if (any(hay, PERFORMANCE_WORDS)) return PERFORMANCE;
        if (any(hay, EFFECTS_WORDS)) return EFFECTS;
        if (any(hay, DISPLAY_WORDS)) return DISPLAY;
        if (any(hay, GRAPHICS_WORDS)) return GRAPHICS;
        return topicOfPage(pageName);
    }

    /** What a whole page is about, from its name (Sodium: General, Quality, Performance, Advanced; Sodium Extra: Animations, ...). */
    static String topicOfPage(final String pageName) {
        final String p = pageName.toLowerCase(Locale.ROOT);
        if (p.contains("general") || p.contains("display") || p.contains("video")) return DISPLAY;
        if (p.contains("quality") || p.contains("detail") || p.contains("render") || p.contains("fog") || p.contains("shader") || p.contains("graphic")) return GRAPHICS;
        if (p.contains("performance") || p.contains("advanced")) return PERFORMANCE;
        if (p.contains("animation") || p.contains("particle") || p.contains("effect")) return EFFECTS;
        return EXTRAS;
    }

    private static boolean any(final String hay, final String[] words) {
        for (final String w : words) if (hay.contains(w)) return true;
        return false;
    }
}
