package dev.fallingcloud.slate.config.iris;

import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.option.OptionValues;
import dev.fallingcloud.slate.config.ui.Section;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Everything that touches Iris: the enable toggle (through {@code IrisApi}), the pack list and pack switching
 * (Iris' internal {@code Iris}/{@code IrisConfig} through reflection, the way Iris' own pack screen applies a
 * pack: set name, enable, save, reload) and Iris' own screen as the fallback. Only loaded after
 * {@code isModLoaded("iris")}; every call is guarded so an API change degrades instead of crashing.
 */
public final class IrisBridge {

    private static final String IRIS = "net.irisshaders.iris.Iris";

    /** The {@code iris:*} bindings favourites, presets and curated pages resolve. */
    public static Section section() {
        final Section s = Section.of("shaders", Component.translatable("slate_config.page.shaders"));
        try {
            s.add(Binding.of("iris:pack", OptionType.INFO, Component.translatable("slate_config.shaders.current"))
                .getter(IrisBridge::currentPackLabel)
                .format(v -> Component.literal(OptionValues.asString(v)))
                .searchWords("iris shaders shaderpack"));
            s.add(enabledBinding());
            s.add(Binding.of("iris:open", OptionType.ACTION, Component.translatable("slate_config.shaders.iris_screen"))
                .tooltip(Component.translatable("slate_config.shaders.iris_screen.tip"))
                .action(Component.translatable("slate_config.row.open"), () -> openIrisScreen(Minecraft.getInstance().screen))
                .searchWords("iris shaders shaderpack settings options"));
        } catch (final Throwable t) {
            SlateConfig.LOGGER.warn("[Slate Config] Iris API unavailable: {}", t.toString());
        }
        return s;
    }

    public static List<OptionBinding> bindings() {
        return section().bindings();
    }

    public static OptionBinding enabledBinding() {
        return Binding.of("iris:enabled", OptionType.BOOLEAN, Component.translatable("slate_config.shaders.enabled"))
            .tooltip(Component.translatable("slate_config.shaders.enabled.tip"))
            .getter(IrisBridge::enabled)
            .setter(v -> setEnabled(OptionValues.asBoolean(v, false)))
            .def(Boolean.TRUE)
            .searchWords("iris shaders toggle on off");
    }

    public static boolean enabled() {
        try {
            return IrisApi.getInstance().getConfig().areShadersEnabled();
        } catch (final Throwable t) {
            return false;
        }
    }

    public static void setEnabled(final boolean on) {
        try {
            IrisApi.getInstance().getConfig().setShadersEnabledAndApply(on);
        } catch (final Throwable t) {
            SlateConfig.LOGGER.warn("[Slate Config] Iris refused to toggle shaders: {}", t.toString());
        }
    }

    /** The pack Iris' config points at (whether or not shaders are on), or null for none. */
    @Nullable
    public static String selectedPack() {
        try {
            final Object cfg = Class.forName(IRIS).getMethod("getIrisConfig").invoke(null);
            final Object name = cfg.getClass().getMethod("getShaderPackName").invoke(cfg);
            if (name instanceof Optional<?> o) return o.map(String::valueOf).orElse(null);
            return name == null ? null : String.valueOf(name);
        } catch (final Throwable t) {
            return null;
        }
    }

    private static String currentPackLabel() {
        final String name = selectedPack();
        if (name == null) return "-";
        return name + (enabled() ? "" : " (" + Component.translatable("slate_config.shaders.off").getString() + ")");
    }

    /** Iris' shader pack folder ({@code shaderpacks/}). */
    public static Path packsDir() {
        try {
            return (Path) Class.forName(IRIS).getMethod("getShaderpacksDirectory").invoke(null);
        } catch (final Throwable t) {
            return Minecraft.getInstance().gameDirectory.toPath().resolve("shaderpacks");
        }
    }

    /** Packs Iris would list (folders with a {@code shaders/} dir, and zips), by file name. */
    @SuppressWarnings("unchecked")
    public static List<String> packs() {
        final Path dir = packsDir();
        try {
            final Class<?> iris = Class.forName(IRIS);
            final Object mgr = iris.getMethod("getShaderpacksDirectoryManager").invoke(null);
            final List<String> names = new ArrayList<>((List<String>) mgr.getClass().getMethod("enumerate").invoke(mgr));
            final Method valid = iris.getMethod("isValidToShowPack", Path.class);
            names.removeIf(n -> {
                try { return !Boolean.TRUE.equals(valid.invoke(null, dir.resolve(n))); } catch (final Throwable t) { return false; }
            });
            return names;
        } catch (final Throwable t) {
            SlateConfig.LOGGER.debug("[Slate Config] Iris pack listing unavailable ({}); scanning the folder", t.toString());
        }
        final List<String> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> Files.isDirectory(p) ? Files.isDirectory(p.resolve("shaders")) : p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                .map(p -> p.getFileName().toString())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(out::add);
        } catch (final IOException ignored) {}
        return out;
    }

    /**
     * Select {@code name}, switch shaders on and reload, like Iris' own "Apply". Returns false when Iris'
     * internals are not reachable (the caller then opens Iris' screen instead).
     */
    public static boolean applyPack(final String name) {
        try {
            final Class<?> iris = Class.forName(IRIS);
            final Object cfg = iris.getMethod("getIrisConfig").invoke(null);
            cfg.getClass().getMethod("setShaderPackName", String.class).invoke(cfg, name);
            cfg.getClass().getMethod("setShadersEnabled", boolean.class).invoke(cfg, true);
            cfg.getClass().getMethod("save").invoke(cfg);
            // The old pack's pending option edits must not leak into the new one.
            try { iris.getMethod("clearShaderPackOptionQueue").invoke(null); } catch (final Throwable ignored) {}
            iris.getMethod("reload").invoke(null);
            return true;
        } catch (final Throwable t) {
            SlateConfig.LOGGER.warn("[Slate Config] cannot switch shader pack to {} directly: {}", name, t.toString());
            return false;
        }
    }

    /** Iris' own shader pack screen, returning to {@code parent}. */
    public static void openIrisScreen(@Nullable final Screen parent) {
        try {
            final Object screen = IrisApi.getInstance().openMainIrisScreenObj(parent);
            if (screen instanceof Screen sc) Minecraft.getInstance().setScreen(sc);
        } catch (final Throwable t) {
            SlateConfig.LOGGER.warn("[Slate Config] cannot open the Iris screen: {}", t.toString());
        }
    }

    private IrisBridge() {}
}
