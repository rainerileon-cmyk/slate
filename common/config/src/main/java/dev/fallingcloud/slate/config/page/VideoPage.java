package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.option.AdvancedBinding;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.resolver.VanillaOptions;
import dev.fallingcloud.slate.config.sodium.SodiumBridge;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * Video: tabs by topic (Display, Graphics, Performance, Animations &amp; effects, HUD &amp; extras), never by mod.
 * Each tab lists vanilla's rows first and then, under a header per mod page ("Sodium › Quality"), the rows of
 * every mod on Sodium's config API that {@link VideoTopics} sorts there. Vanilla rows Sodium lists itself
 * (render distance, graphics, clouds, ...) are left to Sodium's row. Without Sodium the tabs hold vanilla alone.
 * Shaders live under Customization.
 */
public final class VideoPage extends OptionPageBase {

    /** The tab the old {@code video/vanilla} path meant: vanilla's rows now sit on every topic, starting here. */
    public static final String VANILLA_TAB = VideoTopics.DISPLAY;

    private static final String[] DISPLAY = { "fullscreen", "enableVsync", "maxFps", "guiScale", "gamma", "menuBackgroundBlurriness" };
    private static final String[] GRAPHICS = { "renderDistance", "simulationDistance", "graphicsMode", "renderClouds", "particles", "ao",
        "biomeBlendRadius", "entityDistanceScaling", "entityShadows", "mipmapLevels" };
    private static final String[] PERFORMANCE = { "prioritizeChunkUpdates" };
    private static final String[] EFFECTS = { "fov", "screenEffectScale", "fovEffectScale", "darknessEffectScale", "damageTiltStrength",
        "glintSpeed", "glintStrength", "hideLightningFlashes", "bobView", "attackIndicator", "showAutosaveIndicator" };

    public VideoPage() {
        super("video", Component.translatable("slate_config.page.video"), Icon.MONITOR);
    }

    public static boolean sodium() {
        return SlatePlatform.get().isModLoaded("sodium") && SodiumBridge.available();
    }

    /** Every topic is a scrolling list under collapsible headers; there is nothing to page through. */
    @Override
    protected boolean pills(final String tabKey) { return false; }

    @Override
    protected List<Section> sections() {
        final List<Section> out = new ArrayList<>();
        final boolean sodium = sodium();
        // Vanilla first on every topic, in topic order (which also fixes the order of the tabs).
        out.add(vanilla("display", DISPLAY, sodium).tab(VideoTopics.DISPLAY, VideoTopics.title(VideoTopics.DISPLAY)));
        out.add(vanilla("world", GRAPHICS, sodium).tab(VideoTopics.GRAPHICS, VideoTopics.title(VideoTopics.GRAPHICS)));
        out.add(vanilla("performance", PERFORMANCE, sodium).tab(VideoTopics.PERFORMANCE, VideoTopics.title(VideoTopics.PERFORMANCE)));
        out.add(vanilla("effects", EFFECTS, sodium).tab(VideoTopics.EFFECTS, VideoTopics.title(VideoTopics.EFFECTS)));
        if (!sodium) return out;
        // Then each mod page, split by what its options are about; a header names the mod and its page.
        for (final SodiumBridge.ModPage page : SodiumBridge.pages()) {
            final Component header = Component.literal(page.mod() + " › " + page.title().getString());
            final String base = "sodium." + page.configId() + "." + page.index();
            if (page.external() != null) {
                final String topic = VideoTopics.topicOf("", page.title().getString(), page.title().getString());
                out.add(Section.of(base, header).add(page.external()).tab(topic, VideoTopics.title(topic)));
                continue;
            }
            final Map<String, Section> byTopic = new LinkedHashMap<>();
            final boolean pageAdvanced = advancedPage(page.title().getString());
            for (final OptionBinding raw : page.options()) {
                final OptionBinding b = pageAdvanced || advancedOption(idPath(raw.id()), raw.label().getString()) ? AdvancedBinding.of(raw) : raw;
                final String topic = VideoTopics.topicOf(idPath(b.id()), b.label().getString(), page.title().getString());
                byTopic.computeIfAbsent(topic, t -> Section.of(base + "." + t, header).tab(t, VideoTopics.title(t))).add(b);
            }
            // Topic order, so a page split in two lists its parts where the tabs are.
            for (final String topic : VideoTopics.ALL) {
                final Section s = byTopic.get(topic);
                if (s != null) out.add(s);
            }
        }
        return out;
    }

    // Sodium's "Advanced" and "Performance" pages are advanced wholesale; elsewhere the option's own words decide
    // (chunk builder threads, culling, translucency sorting, buffers... are tuning knobs, not everyday settings).
    private static final String[] ADVANCED_WORDS = { "chunk_update", "chunk_build", "thread", "cull", "occlusion", "defer", "no_error", "buffer",
        "sorting", "translucen", "entity_distance", "biome_blend", "mipmap", "persistent", "async", "memory", "cpu", "gpu", "light_update",
        "render_ahead", "prevent_shaders", "linear_flat", "fast_random", "steady_debug", "use_fog_occlusion", "block_face", "compact_vertex",
        "always_defer", "allocator", "leaves_quality" };

    static boolean advancedPage(final String pageTitle) {
        final String t = pageTitle.toLowerCase(java.util.Locale.ROOT);
        return t.contains("advanced") || t.contains("performance");
    }

    static boolean advancedOption(final String idPath, final String label) {
        final String hay = idPath.toLowerCase(java.util.Locale.ROOT) + " " + label.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        for (final String w : ADVANCED_WORDS) if (hay.contains(w)) return true;
        return false;
    }

    /** {@code sodium:sodium:render_distance} → {@code render_distance}. */
    private static String idPath(final String bindingId) {
        final int i = bindingId.lastIndexOf(':');
        return i < 0 ? bindingId : bindingId.substring(i + 1);
    }

    private static Section vanilla(final String id, final String[] keys, final boolean sodium) {
        final Section s = Section.of(id, Component.translatable("slate_config.video.vanilla"));
        for (final String k : keys) {
            if (sodium && VanillaOptions.COVERED_BY_SODIUM.contains(k)) continue;
            VanillaOptions.get(k).ifPresent(s::add);
        }
        return s;
    }

    /** The bindings the shipped presets snapshot. */
    public static List<OptionBinding> presetBindings() {
        final List<OptionBinding> out = new ArrayList<>();
        out.addAll(VanillaOptions.all(DISPLAY));
        out.addAll(VanillaOptions.all(GRAPHICS));
        out.addAll(VanillaOptions.all(PERFORMANCE));
        out.addAll(VanillaOptions.all(EFFECTS));
        if (sodium()) for (final Section s : SodiumBridge.sections()) out.addAll(s.bindings());
        return out;
    }
}
