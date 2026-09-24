package dev.fallingcloud.slate.config.sodium;

import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.Choice;
import dev.fallingcloud.slate.config.option.NumberRange;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.option.OptionValues;
import dev.fallingcloud.slate.config.ui.ApplyQueue;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.screen.ScreenSwaps;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.SteppedValidator;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.BooleanOption;
import net.caffeinemc.mods.sodium.client.config.structure.Config;
import net.caffeinemc.mods.sodium.client.config.structure.EnumOption;
import net.caffeinemc.mods.sodium.client.config.structure.ExternalButtonOption;
import net.caffeinemc.mods.sodium.client.config.structure.ExternalPage;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.config.structure.OptionGroup;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.config.structure.Page;
import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Everything that touches Sodium 0.8's config model ({@code ConfigManager.CONFIG}): renders every
 * registered {@link ModOptions} (Sodium's own pages plus Iris / Sodium Extra / ... pages built through
 * Sodium's API) as Slate sections, applies edits through {@code Config.applyOption} (which runs Sodium's
 * own renderer/asset reload flags), and swaps Sodium's video settings screen for the hub. Only loaded
 * after {@code isModLoaded("sodium")}; a missing or changed API degrades to "no Sodium sections".
 *
 * <p>Compiled against the vendored 0.8 jar; Sodium is not in the dev environment, so this path is
 * verified by compilation and code review, not by a runtime smoke test.</p>
 */
public final class SodiumBridge {

    private static final Map<ResourceLocation, OptionBinding> CACHE = new LinkedHashMap<>();
    @Nullable private static Field idField;
    private static boolean idFieldTried;

    public static boolean available() {
        try {
            return ConfigManager.CONFIG != null;
        } catch (final Throwable t) {
            return false;
        }
    }

    private static Config config() { return ConfigManager.CONFIG; }

    /** Replace Sodium's own screen with the hub's Video page (registered from the module's initClient). */
    public static void installScreenSwap(final java.util.function.BiFunction<Screen, String, Screen> hubFactory) {
        try {
            // By NAME: loading Sodium's GUI classes at mod init would make Slate the first to trigger every
            // other mod's mixins on them (Controlify's Sodium compat, for one) - only touch them when opened.
            ScreenSwaps.registerByName("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen", original -> {
                // setScreen has not switched yet, so the current screen is exactly the parent Sodium was given.
                Screen parent = Minecraft.getInstance().screen;
                try {
                    final Field f = original.getClass().getDeclaredField("prevScreen");
                    f.setAccessible(true);
                    final Object p = f.get(original);
                    if (p instanceof Screen s) parent = s;
                } catch (final Throwable ignored) {}
                return hubFactory.apply(parent, "video");
            });
            SlateConfig.LOGGER.info("[Slate Config] Sodium video settings screen routed to the Slate hub");
        } catch (final Throwable t) {
            SlateConfig.LOGGER.warn("[Slate Config] could not hook Sodium's video settings screen: {}", t.toString());
        }
    }

    // ------------------------------------------------------------------ sections

    /**
     * One option page of a mod on Sodium's config API, every option wrapped as a binding. {@code external} is the
     * "open its own screen" action of an {@link ExternalPage} (then {@code options} is empty).
     */
    public record ModPage(String mod, String configId, int index, Component title, List<OptionBinding> options, @Nullable OptionBinding external) {}

    /** Every page of every mod registered with Sodium's config API, in registration order. */
    public static List<ModPage> pages() {
        final List<ModPage> out = new ArrayList<>();
        if (!available()) return out;
        try {
            for (final ModOptions mod : config().getModOptions()) {
                int pageIdx = 0;
                for (final Page page : mod.pages()) {
                    final int idx = pageIdx++;
                    if (page instanceof ExternalPage ext) {
                        final OptionBinding open = Binding.of("sodium:" + mod.configId() + ":page." + idx, OptionType.ACTION, page.name())
                            .tooltip(Component.translatable("slate_config.video.external_page", mod.name()))
                            .action(Component.translatable("slate_config.row.open"), () -> ext.currentScreenConsumer().accept(Minecraft.getInstance().screen))
                            .searchWords("sodium " + mod.name());
                        out.add(new ModPage(mod.name(), mod.configId(), idx, page.name(), List.of(), open));
                        continue;
                    }
                    final List<OptionBinding> options = new ArrayList<>();
                    for (final OptionGroup group : page.groups()) {
                        for (final Option opt : group.options()) {
                            final OptionBinding b = wrap(opt, mod);
                            if (b != null) options.add(b);
                        }
                    }
                    if (!options.isEmpty()) out.add(new ModPage(mod.name(), mod.configId(), idx, page.name(), options, null));
                }
            }
        } catch (final Throwable t) {
            SlateConfig.LOGGER.warn("[Slate Config] Sodium config model changed; its pages are unavailable: {}", t.toString());
        }
        return out;
    }

    /** One section per option page of every mod, grouped into one tab per mod (presets and the option index). */
    public static List<Section> sections() {
        final List<Section> out = new ArrayList<>();
        for (final ModPage p : pages()) {
            final Section s = Section.of("sodium." + p.configId() + "." + p.index(), p.title()).tab("sodium." + p.configId(), Component.literal(p.mod()));
            if (p.external() != null) s.add(p.external());
            else s.addAll(p.options());
            out.add(s);
        }
        return out;
    }

    public static Optional<OptionBinding> binding(final String id) {
        if (!available()) return Optional.empty();
        try {
            final ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) return Optional.empty();
            final OptionBinding cached = CACHE.get(rl);
            if (cached != null) return Optional.of(cached);
            final Option opt = config().getOption(rl);
            if (opt == null) return Optional.empty();
            return Optional.ofNullable(wrap(opt, ownerOf(opt), rl));
        } catch (final Throwable t) {
            return Optional.empty();
        }
    }

    @Nullable
    private static ModOptions ownerOf(final Option opt) {
        for (final ModOptions mod : config().getModOptions()) {
            for (final Page page : mod.pages()) for (final OptionGroup g : page.groups()) if (g.options().contains(opt)) return mod;
        }
        return null;
    }

    @Nullable
    private static ResourceLocation idOf(final Option opt) {
        if (!idFieldTried) {
            idFieldTried = true;
            try {
                idField = Option.class.getDeclaredField("id");
                idField.setAccessible(true);
            } catch (final Throwable t) {
                SlateConfig.LOGGER.warn("[Slate Config] cannot read Sodium option ids ({}); using positional ids", t.toString());
                idField = null;
            }
        }
        if (idField == null) return null;
        try {
            return (ResourceLocation) idField.get(opt);
        } catch (final Throwable t) {
            return null;
        }
    }

    @Nullable
    private static OptionBinding wrap(final Option opt, @Nullable final ModOptions owner) {
        ResourceLocation id = idOf(opt);
        if (id == null) id = ResourceLocation.fromNamespaceAndPath("slate_config", "anon." + System.identityHashCode(opt));
        return wrap(opt, owner, id);
    }

    @Nullable
    private static OptionBinding wrap(final Option opt, @Nullable final ModOptions owner, final ResourceLocation id) {
        final OptionBinding cached = CACHE.get(id);
        if (cached != null) return cached;
        final String bid = "sodium:" + id;
        final String words = "sodium " + (owner == null ? "" : owner.name()) + " " + id.getPath().replace('.', ' ').replace('_', ' ');
        Binding b;
        if (opt instanceof BooleanOption bo) {
            b = Binding.of(bid, OptionType.BOOLEAN, opt.getName())
                .getter(bo::getValidatedValue)
                .setter(v -> { bo.modifyValue(OptionValues.asBoolean(v, false)); apply(bo, id); })
                .def(bo.getDefaultValue().get(config()));
        } else if (opt instanceof IntegerOption io) {
            final SteppedValidator sv = io.getSteppedValidator();
            b = Binding.of(bid, OptionType.INT, opt.getName())
                .getter(io::getValidatedValue)
                .setter(v -> { io.modifyValue(OptionValues.asInt(v, 0)); apply(io, id); })
                .def(io.getDefaultValue().get(config()))
                .format(v -> io.formatValue(OptionValues.asInt(v, 0)));
            if (sv != null) b.range(NumberRange.of(sv.min(), sv.max(), Math.max(1, sv.step())));
        } else if (opt instanceof EnumOption<?> eo) {
            b = enumBinding(bid, eo, id);
        } else if (opt instanceof ExternalButtonOption ext) {
            b = Binding.of(bid, OptionType.ACTION, opt.getName())
                .action(Component.translatable("slate_config.row.open"), () -> ext.getCurrentScreenConsumer().accept(Minecraft.getInstance().screen));
        } else if (opt instanceof StatefulOption<?>) {
            return null;                                              // unknown stateful kind from a newer API: skip rather than guess
        } else {
            b = Binding.of(bid, OptionType.INFO, opt.getName()).getter(() -> "");
        }
        b.tooltip(safeTooltip(opt)).enabledIf(() -> safeEnabled(opt)).searchWords(words);
        if (opt instanceof StatefulOption<?> so) {
            final Set<ResourceLocation> flags = so.getFlags();
            b.restart(flags != null && flags.contains(OptionFlag.REQUIRES_GAME_RESTART.getId()));
        }
        CACHE.put(id, b);
        return b;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Binding enumBinding(final String bid, final EnumOption eo, final ResourceLocation id) {
        final Class<? extends Enum> cls = eo.getEnumClass();
        final List<Choice> choices = new ArrayList<>();
        for (final Object c : cls.getEnumConstants()) {
            final Enum e = (Enum) c;
            if (!eo.isValueAllowed(e)) continue;
            choices.add(new Choice(e.name(), eo.getElementName(e)));
        }
        return Binding.of(bid, OptionType.CHOICE, eo.getName())
            .choices(choices)
            .getter(() -> { final Object v = eo.getValidatedValue(); return v instanceof Enum e ? e.name() : String.valueOf(v); })
            .setter(v -> {
                final String name = OptionValues.asString(v);
                for (final Object c : cls.getEnumConstants()) if (((Enum) c).name().equals(name)) { eo.modifyValue((Enum) c); apply(eo, id); return; }
            })
            .def(((Enum) eo.getDefaultValue().get(config())).name());
    }

    /** Apply now for cheap options; debounce the ones that reload the renderer so slider drags do not thrash. */
    private static void apply(final StatefulOption<?> opt, final ResourceLocation id) {
        final Set<ResourceLocation> flags = opt.getFlags();
        final boolean heavy = flags != null && (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD.getId())
            || flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD.getId()) || flags.contains(OptionFlag.REQUIRES_VIDEOMODE_RELOAD.getId()));
        final Runnable run = () -> {
            try {
                config().applyOption(id);
            } catch (final Throwable t) {
                SlateConfig.LOGGER.warn("[Slate Config] Sodium refused to apply {}: {}", id, t.toString());
            }
        };
        if (heavy) ApplyQueue.later("sodium:" + id, 450, run);
        else run.run();
    }

    @Nullable
    private static Component safeTooltip(final Option opt) {
        try { return opt.getTooltip(); } catch (final Throwable t) { return null; }
    }

    private static boolean safeEnabled(final Option opt) {
        try { return opt.isEnabled(); } catch (final Throwable t) { return true; }
    }

    /** Sodium's search index text sources are not needed: our own index sees every wrapped option. */
    public static int optionCount() {
        int n = 0;
        if (!available()) return 0;
        for (final ModOptions mod : config().getModOptions()) for (final Page page : mod.pages()) if (page instanceof OptionPage op) for (final OptionGroup g : op.groups()) n += g.options().size();
        return n;
    }

    private SodiumBridge() {}
}
