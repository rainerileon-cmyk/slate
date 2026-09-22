package dev.fallingcloud.slate.config.resolver;

import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.Choice;
import dev.fallingcloud.slate.config.option.NumberRange;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.option.OptionValues;
import dev.fallingcloud.slate.config.ui.ApplyQueue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.OptionEnum;
import org.jetbrains.annotations.Nullable;

/**
 * Every vanilla {@link OptionInstance} as a binding, keyed by its options.txt name ({@code renderDistance},
 * {@code soundCategory_music}, ...). Values go through {@code OptionInstance.set} so vanilla's own
 * side effects run (renderer reload, vsync, fullscreen...), then {@code options.save()}. The few
 * options vanilla applies from its screens rather than the callback (GUI scale, mipmaps) are handled
 * here through the {@link ApplyQueue}.
 */
public final class VanillaOptions {

    private static final Map<String, Supplier<Binding>> DEFS = new LinkedHashMap<>();
    private static final Map<String, OptionBinding> CACHE = new LinkedHashMap<>();

    /** Vanilla options Sodium's own pages already expose; hidden from the vanilla groups when Sodium is loaded. */
    public static final Set<String> COVERED_BY_SODIUM = Set.of("renderDistance", "simulationDistance", "gamma", "guiScale", "fullscreen",
        "enableVsync", "maxFps", "bobView", "attackIndicator", "showAutosaveIndicator", "graphicsMode", "renderClouds", "particles", "ao",
        "biomeBlendRadius", "entityShadows", "entityDistanceScaling", "mipmapLevels");

    private static Options opts() { return Minecraft.getInstance().options; }

    public static Optional<OptionBinding> get(final String key) {
        final OptionBinding c = CACHE.get(key);
        if (c != null) return Optional.of(c);
        final Supplier<Binding> s = DEFS.get(key);
        if (s == null) return Optional.empty();
        final Binding b = s.get();
        CACHE.put(key, b);
        return Optional.of(b);
    }

    public static List<OptionBinding> all(final String... keys) {
        final List<OptionBinding> out = new ArrayList<>();
        for (final String k : keys) get(k).ifPresent(out::add);
        return out;
    }

    public static Set<String> keys() { return DEFS.keySet(); }

    // ------------------------------------------------------------------ builders

    private static <T> void apply(final OptionInstance<T> inst, final T value) {
        inst.set(value);
        opts().save();
    }

    @Nullable
    private static Component tip(final String key, final String caption) {
        if (I18n.exists(caption + ".tooltip")) return Component.translatable(caption + ".tooltip");
        final String own = "slate_config.opt." + key + ".tip";
        return I18n.exists(own) ? Component.translatable(own) : null;
    }

    private static Binding base(final String key, final String caption, final OptionType type) {
        return Binding.of("optionsTxt:" + key, type, Component.translatable(caption)).tooltip(tip(key, caption)).searchWords("vanilla " + key);
    }

    private static void bool(final String key, final String caption, final Function<Options, OptionInstance<Boolean>> f, final boolean def) {
        DEFS.put(key, () -> base(key, caption, OptionType.BOOLEAN)
            .getter(() -> f.apply(opts()).get())
            .setter(v -> apply(f.apply(opts()), OptionValues.asBoolean(v, def)))
            .def(def));
    }

    private static void intRange(final String key, final String caption, final Function<Options, OptionInstance<Integer>> f,
                                 final int min, final int max, final int def, final DoubleFunction<Component> fmt, @Nullable final Runnable after) {
        DEFS.put(key, () -> base(key, caption, OptionType.INT)
            .getter(() -> f.apply(opts()).get())
            .setter(v -> { apply(f.apply(opts()), OptionValues.asInt(v, def)); if (after != null) after.run(); })
            .def(def).range(NumberRange.ints(min, max))
            .format(v -> fmt.apply(OptionValues.asDouble(v, def))));
    }

    private static void dbl(final String key, final String caption, final Function<Options, OptionInstance<Double>> f,
                            final double min, final double max, final double step, final double def, final DoubleFunction<Component> fmt) {
        DEFS.put(key, () -> base(key, caption, OptionType.DOUBLE)
            .getter(() -> f.apply(opts()).get())
            .setter(v -> apply(f.apply(opts()), OptionValues.asDouble(v, def)))
            .def(def).range(NumberRange.of(min, max, step))
            .format(v -> fmt.apply(OptionValues.asDouble(v, def))));
    }

    private static void unit(final String key, final String caption, final Function<Options, OptionInstance<Double>> f, final double def, final DoubleFunction<Component> fmt) {
        dbl(key, caption, f, 0, 1, 0.01, def, fmt);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void choice(final String key, final String caption, final Function<Options, OptionInstance<?>> f, final String def) {
        DEFS.put(key, () -> {
            final OptionInstance inst = f.apply(opts());
            final List<Object> values = enumValues(inst);
            final List<Choice> choices = new ArrayList<>();
            for (final Object v : values) choices.add(new Choice(choiceId(v), choiceLabel(v)));
            return base(key, caption, OptionType.CHOICE)
                .choices(choices)
                .getter(() -> choiceId(f.apply(opts()).get()))
                .setter(v -> {
                    final String id = OptionValues.asString(v);
                    final OptionInstance live = f.apply(opts());
                    for (final Object o : enumValues(live)) if (choiceId(o).equals(id)) { apply(live, o); return; }
                })
                .def(def);
        });
    }

    private static List<Object> enumValues(final OptionInstance<?> inst) {
        final Object vs = inst.values();
        final List<Object> out = new ArrayList<>();
        if (vs instanceof OptionInstance.Enum<?> e) out.addAll(e.values());
        else if (vs instanceof OptionInstance.AltEnum<?> e) out.addAll(e.values());
        else if (vs instanceof OptionInstance.LazyEnum<?> e) out.addAll(e.values().get());
        else if (vs instanceof OptionInstance.ClampingLazyMaxIntRange r) { for (int i = r.minInclusive(); i <= Math.min(r.maxInclusive(), 64); i++) out.add(i); }
        return out;
    }

    private static String choiceId(final Object v) {
        if (v instanceof Enum<?> e) return e.name();
        return String.valueOf(v);
    }

    private static Component choiceLabel(final Object v) {
        if (v instanceof OptionEnum oe) return oe.getCaption();
        if (v instanceof NarratorStatus n) return n.getName();
        if (v instanceof Boolean b) return Component.translatable(b ? "options.on" : "options.off");
        if (v instanceof String s) return s.isEmpty() ? Component.translatable("options.audioDevice.default") : Component.literal(s);
        return Component.literal(String.valueOf(v));
    }

    private static Component percent(final double v) { return Component.literal(Math.round(v * 100) + "%"); }

    private static Component chunks(final double v) { return Component.translatable("options.chunks", (int) Math.round(v)); }

    private static Component num(final double v) { return Component.literal(Long.toString(Math.round(v))); }

    private static Component off(final double v, final Component on) { return v <= 0 ? Component.translatable("options.off") : on; }

    // ------------------------------------------------------------------ the table

    static {
        final Minecraft mc = Minecraft.getInstance();
        // Video
        intRange("renderDistance", "options.renderDistance", Options::renderDistance, 2, 32, 12, VanillaOptions::chunks, null);
        intRange("simulationDistance", "options.simulationDistance", Options::simulationDistance, 5, 32, 12, VanillaOptions::chunks, null);
        choice("graphicsMode", "options.graphics", Options::graphicsMode, "FANCY");
        intRange("fov", "options.fov", Options::fov, 30, 110, 70, v -> v == 70 ? Component.translatable("options.fov.min") : v == 110 ? Component.translatable("options.fov.max") : num(v), null);
        intRange("guiScale", "options.guiScale", Options::guiScale, 0, 8, 0, v -> v <= 0 ? Component.translatable("options.guiScale.auto") : num(v),
            () -> ApplyQueue.later("guiScale", 300, () -> mc.resizeDisplay()));
        bool("fullscreen", "options.fullscreen", Options::fullscreen, false);
        bool("enableVsync", "options.vsync", Options::enableVsync, true);
        intRange("maxFps", "options.framerateLimit", Options::framerateLimit, 10, 260, 120, v -> v >= 260 ? Component.translatable("options.framerateLimit.max") : Component.translatable("options.framerate", (int) Math.round(v)), null);
        intRange("mipmapLevels", "options.mipmapLevels", Options::mipmapLevels, 0, 4, 4, v -> off(v, num(v)),
            () -> ApplyQueue.later("mipmap", 600, () -> { mc.updateMaxMipLevel(opts().mipmapLevels().get()); mc.delayTextureReload(); }));
        choice("particles", "options.particles", Options::particles, "ALL");
        dbl("entityDistanceScaling", "options.entityDistanceScaling", Options::entityDistanceScaling, 0.5, 5.0, 0.25, 1.0, VanillaOptions::percent);
        bool("entityShadows", "options.entityShadows", Options::entityShadows, true);
        intRange("biomeBlendRadius", "options.biomeBlendRadius", Options::biomeBlendRadius, 0, 7, 2, v -> off(v, Component.literal((int) (v * 2 + 1) + "x" + (int) (v * 2 + 1))), null);
        choice("prioritizeChunkUpdates", "options.prioritizeChunkUpdates", Options::prioritizeChunkUpdates, "NONE");
        unit("gamma", "options.gamma", Options::gamma, 0.5, v -> v <= 0 ? Component.translatable("options.gamma.min") : v >= 1 ? Component.translatable("options.gamma.max") : percent(v));
        choice("renderClouds", "options.renderClouds", Options::cloudStatus, "FANCY");
        bool("ao", "options.ao", Options::ambientOcclusion, true);
        unit("glintSpeed", "options.glintSpeed", Options::glintSpeed, 0.5, VanillaOptions::percent);
        unit("glintStrength", "options.glintStrength", Options::glintStrength, 0.75, VanillaOptions::percent);
        unit("screenEffectScale", "options.screenEffectScale", Options::screenEffectScale, 1.0, VanillaOptions::percent);
        unit("fovEffectScale", "options.fovEffectScale", Options::fovEffectScale, 1.0, VanillaOptions::percent);
        unit("darknessEffectScale", "options.darknessEffectScale", Options::darknessEffectScale, 1.0, VanillaOptions::percent);
        unit("damageTiltStrength", "options.damageTiltStrength", Options::damageTiltStrength, 1.0, VanillaOptions::percent);
        bool("hideLightningFlashes", "options.hideLightningFlashes", Options::hideLightningFlash, false);
        intRange("menuBackgroundBlurriness", "options.accessibility.menu_background_blurriness", Options::menuBackgroundBlurriness, 0, 10, 5, v -> off(v, num(v)), null);
        bool("bobView", "options.viewBobbing", Options::bobView, true);
        choice("attackIndicator", "options.attackIndicator", Options::attackIndicator, "CROSSHAIR");
        bool("showAutosaveIndicator", "options.autosaveIndicator", Options::showAutosaveIndicator, true);

        // Controls
        unit("mouseSensitivity", "options.sensitivity", Options::sensitivity, 0.5, v -> v <= 0 ? Component.translatable("options.sensitivity.min") : v >= 1 ? Component.translatable("options.sensitivity.max") : percent(v * 2));
        bool("invertYMouse", "options.invertMouse", Options::invertYMouse, false);
        bool("rawMouseInput", "options.rawMouseInput", Options::rawMouseInput, true);
        bool("discrete_mouse_scroll", "options.discrete_mouse_scroll", Options::discreteMouseScroll, false);
        dbl("mouseWheelSensitivity", "options.mouseWheelSensitivity", Options::mouseWheelSensitivity, 0.01, 10.0, 0.01, 1.0, v -> Component.literal(String.format(Locale.ROOT, "%.2f", v)));
        bool("touchscreen", "options.touchscreen", Options::touchscreen, false);
        bool("toggleSprint", "key.sprint", Options::toggleSprint, false);
        bool("toggleCrouch", "key.sneak", Options::toggleCrouch, false);
        bool("autoJump", "options.autoJump", Options::autoJump, false);
        bool("operatorItemsTab", "options.operatorItemsTab", Options::operatorItemsTab, false);

        // Audio
        for (final SoundSource src : SoundSource.values()) {
            final String key = "soundCategory_" + src.getName();
            unit(key, "soundCategory." + src.getName(), o -> o.getSoundSourceOptionInstance(src), 1.0, v -> v <= 0 ? Component.translatable("options.off") : percent(v));
        }
        bool("showSubtitles", "options.showSubtitles", Options::showSubtitles, false);
        bool("directionalAudio", "options.directionalAudio", Options::directionalAudio, false);
        choice("soundDevice", "options.audioDevice", Options::soundDevice, "");

        // Chat
        choice("chatVisibility", "options.chat.visibility", Options::chatVisibility, "FULL");
        bool("chatColors", "options.chat.color", Options::chatColors, true);
        bool("chatLinks", "options.chat.links", Options::chatLinks, true);
        bool("chatLinksPrompt", "options.chat.links.prompt", Options::chatLinksPrompt, true);
        unit("chatOpacity", "options.chat.opacity", Options::chatOpacity, 1.0, v -> percent(v * 0.9 + 0.1));
        unit("textBackgroundOpacity", "options.accessibility.text_background_opacity", Options::textBackgroundOpacity, 0.5, VanillaOptions::percent);
        bool("backgroundForChatOnly", "options.accessibility.text_background", Options::backgroundForChatOnly, true);
        unit("chatScale", "options.chat.scale", Options::chatScale, 1.0, VanillaOptions::percent);
        unit("chatLineSpacing", "options.chat.line_spacing", Options::chatLineSpacing, 0.0, VanillaOptions::percent);
        dbl("chatDelay", "options.chat.delay_instant", Options::chatDelay, 0.0, 6.0, 0.1, 0.0, v -> v <= 0 ? Component.translatable("options.chat.delay_none") : Component.translatable("options.chat.delay", String.format(Locale.ROOT, "%.1f", v)));
        unit("chatWidth", "options.chat.width", Options::chatWidth, 1.0, v -> Component.literal(Math.round(v * 280 + 40) + "px"));
        unit("chatHeightFocused", "options.chat.height.focused", Options::chatHeightFocused, 1.0, v -> Component.literal(Math.round(v * 160 + 20) + "px"));
        unit("chatHeightUnfocused", "options.chat.height.unfocused", Options::chatHeightUnfocused, 0.44366196, v -> Component.literal(Math.round(v * 160 + 20) + "px"));
        bool("autoSuggestions", "options.autoSuggestCommands", Options::autoSuggestions, true);
        bool("hideMatchedNames", "options.hideMatchedNames", Options::hideMatchedNames, true);
        bool("onlyShowSecureChat", "options.onlyShowSecureChat", Options::onlyShowSecureChat, false);
        bool("reducedDebugInfo", "options.reducedDebugInfo", Options::reducedDebugInfo, false);
        dbl("notificationDisplayTime", "options.notifications.display_time", Options::notificationDisplayTime, 0.5, 10.0, 0.1, 1.0, v -> Component.translatable("options.multiplier", String.format(Locale.ROOT, "%.1f", v)));

        // Accessibility / interface
        choice("narrator", "options.narrator", Options::narrator, "OFF");
        bool("highContrast", "options.accessibility.high_contrast", Options::highContrast, false);
        bool("forceUnicodeFont", "options.forceUnicodeFont", Options::forceUnicodeFont, false);
        bool("japaneseGlyphVariants", "options.japaneseGlyphVariants", Options::japaneseGlyphVariants, false);
        choice("mainHand", "options.mainHand", Options::mainHand, "RIGHT");
        bool("darkMojangStudiosBackground", "options.darkMojangStudiosBackgroundColor", Options::darkMojangStudiosBackground, false);
        bool("hideSplashTexts", "options.hideSplashTexts", Options::hideSplashTexts, false);
        unit("panoramaScrollSpeed", "options.accessibility.panorama_speed", Options::panoramaSpeed, 1.0, VanillaOptions::percent);
        bool("narratorHotkey", "options.accessibility.narrator_hotkey", Options::narratorHotkey, true);

        // Online
        bool("realmsNotifications", "options.realmsNotifications", Options::realmsNotifications, true);
        bool("allowServerListing", "options.allowServerListing", Options::allowServerListing, true);
        bool("telemetryOptInExtra", "options.telemetry.button", Options::telemetryOptInExtra, false);
    }

    private VanillaOptions() {}
}
