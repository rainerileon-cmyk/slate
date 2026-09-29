package dev.fallingcloud.slate.config.resolver;

import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.Choice;
import dev.fallingcloud.slate.config.option.NumberRange;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.option.OptionValues;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.config.CoreConfig;
import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.slot.Layout;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.PixelFont;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/** Core's {@link CoreConfig} fields as live bindings ({@code slate:core:<field>}); every set reloads the theme. */
public final class CoreBindings {

    private static final Map<String, Supplier<Binding>> DEFS = new LinkedHashMap<>();
    private static final Map<String, OptionBinding> CACHE = new LinkedHashMap<>();
    private static final CoreConfig DEFAULTS = new CoreConfig();

    private static void save(final Consumer<CoreConfig> edit) {
        Slate.configFile().update(edit);
        Theme.reload();
        Reskin.invalidate();
    }

    private static Binding base(final String key, final OptionType type) {
        return Binding.of("slate:core:" + key, type, Component.translatable("slate_config.core." + key))
            .tooltip(Component.translatable("slate_config.core." + key + ".tip")).searchWords("slate core theme");
    }

    private static void bool(final String key, final Function<CoreConfig, Boolean> get, final java.util.function.BiConsumer<CoreConfig, Boolean> set) {
        DEFS.put(key, () -> base(key, OptionType.BOOLEAN).getter(() -> get.apply(Slate.config()))
            .setter(v -> save(c -> set.accept(c, OptionValues.asBoolean(v, false)))).def(get.apply(DEFAULTS)));
    }

    static {
        // The legacy two-state switch (curated pages may still bind slate:core:customLayout) and the three-way layout.
        bool("customLayout", CoreConfig::hasCustomLayout, CoreConfig::setCustomLayout);
        DEFS.put("layout", () -> base("layout", OptionType.CHOICE)
            .choices(layouts())
            .getter(() -> Slate.config().layout().name())
            .setter(v -> save(c -> c.setLayout(Layout.parse(OptionValues.asString(v), Layout.CUSTOM))))
            .def(DEFAULTS.layout));
        DEFS.put("skin", () -> base("skin", OptionType.CHOICE)
            .choices(List.of(Choice.of("DARK", Component.translatable("slate.skin.dark").getString()), Choice.of("VANILLA", Component.translatable("slate.skin.vanilla").getString())))
            .getter(() -> Slate.config().isVanillaSkin() ? "VANILLA" : "DARK")
            .setter(v -> save(c -> c.skin = "VANILLA".equalsIgnoreCase(OptionValues.asString(v)) ? "VANILLA" : "DARK"))
            .def("DARK"));
        DEFS.put("accent", () -> base("accent", OptionType.COLOR)
            .getter(() -> Colors.fromHex(Slate.config().accent, Palette.DEFAULT_ACCENT))
            .setter(v -> save(c -> c.accent = Colors.toHex(OptionValues.asColor(v, Palette.DEFAULT_ACCENT))))
            .def(Palette.DEFAULT_ACCENT));
        DEFS.put("radius", () -> base("radius", OptionType.INT).range(NumberRange.ints(0, 4))
            .getter(() -> Slate.config().radius).setter(v -> save(c -> c.radius = Math.max(0, Math.min(4, OptionValues.asInt(v, 3))))).def(DEFAULTS.radius));
        bool("headingFont", c -> c.headingFont, (c, v) -> c.headingFont = v);
        DEFS.put("pixelFont", () -> base("pixelFont", OptionType.CHOICE)
            .choices(pixelFonts())
            .getter(() -> PixelFont.parse(Slate.config().pixelFont).key())
            .setter(v -> save(c -> c.pixelFont = PixelFont.parse(OptionValues.asString(v)).key()))
            .def(PixelFont.DEFAULT.key())
            .enabledIf(() -> Slate.config().headingFont));
        bool("blurInGame", c -> c.blurInGame, (c, v) -> c.blurInGame = v);
        DEFS.put("motion", () -> base("motion", OptionType.DOUBLE).range(NumberRange.of(0, 2, 0.25))
            .format(v -> { final double d = OptionValues.asDouble(v, 1); return d <= 0 ? Component.translatable("options.off") : Component.literal(String.format(java.util.Locale.ROOT, "%.2fx", d)); })
            .getter(() -> Slate.config().motion).setter(v -> save(c -> c.motion = OptionValues.asDouble(v, 1))).def(DEFAULTS.motion));
        bool("transitions", c -> c.transitions, (c, v) -> c.transitions = v);
        bool("uiSounds", c -> c.uiSounds, (c, v) -> c.uiSounds = v);
        bool("toasts", c -> c.toasts, (c, v) -> c.toasts = v);
        DEFS.put("reskinScope", () -> base("reskinScope", OptionType.CHOICE)
            .choices(scopes())
            .getter(() -> Slate.config().reskinScope)
            .setter(v -> save(c -> c.reskinScope = OptionValues.asString(v)))
            .def(DEFAULTS.reskinScope));
        DEFS.put("reskinAllowlist", () -> base("reskinAllowlist", OptionType.LIST)
            .getter(() -> new ArrayList<>(Slate.config().reskinAllowlist))
            .setter(v -> save(c -> c.reskinAllowlist = new ArrayList<>(OptionValues.asList(v))))
            .def(new ArrayList<>(DEFAULTS.reskinAllowlist)));
        bool("reskinContainers", c -> c.reskinContainers, (c, v) -> c.reskinContainers = v);
        DEFS.put("reskinDenylist", () -> base("reskinDenylist", OptionType.LIST)
            .getter(() -> new ArrayList<>(Slate.config().reskinDenylist))
            .setter(v -> save(c -> c.reskinDenylist = new ArrayList<>(OptionValues.asList(v))))
            .def(new ArrayList<>(DEFAULTS.reskinDenylist)));
        bool("devMode", c -> c.devMode, (c, v) -> c.devMode = v);
        bool("devGrid", c -> c.devGrid, (c, v) -> c.devGrid = v);
        DEFS.put("devSnap", () -> base("devSnap", OptionType.INT).range(NumberRange.ints(1, 16))
            .format(v -> Component.literal(OptionValues.asInt(v, 4) + " px"))
            .getter(() -> Slate.config().devSnap).setter(v -> save(c -> c.devSnap = Math.max(1, OptionValues.asInt(v, 4)))).def(DEFAULTS.devSnap));
    }

    /** One choice per pixel font, each name written in its own font so the dropdown previews them. */
    private static List<Choice> pixelFonts() {
        final List<Choice> out = new ArrayList<>();
        for (final PixelFont f : PixelFont.values()) out.add(new Choice(f.key(), f.label()));
        return out;
    }

    /** The three layouts; the ones the installed modules cannot show are still listed (the Interface page disables them). */
    private static List<Choice> layouts() {
        final List<Choice> out = new ArrayList<>();
        for (final Layout l : Layout.values()) out.add(new Choice(l.name(), Component.translatable("slate.layout." + l.key())));
        return out;
    }

    private static List<Choice> scopes() {
        final List<Choice> out = new ArrayList<>();
        for (final String s : List.of("VANILLA_AND_SLATE", "ALLOWLIST", "ALL_NON_CONTAINER", "NONE")) out.add(new Choice(s, Component.translatable("slate.reskin." + s.toLowerCase(java.util.Locale.ROOT))));
        return out;
    }

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

    private CoreBindings() {}
}
