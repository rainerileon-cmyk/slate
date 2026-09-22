package dev.fallingcloud.slate.config.iris;

import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.option.OptionValues;
import dev.fallingcloud.slate.config.ui.Section;
import java.util.List;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Shaders section: current pack, enable toggle (through {@code IrisApi}) and a button to Iris's
 * own pack screen. Only loaded after {@code isModLoaded("iris")}; every call is guarded so an API
 * change degrades to an empty section.
 */
public final class IrisBridge {

    public static Section section() {
        final Section s = Section.of("shaders", Component.translatable("slate_config.video.shaders"));
        try {
            final IrisApi api = IrisApi.getInstance();
            s.add(Binding.of("iris:pack", OptionType.INFO, Component.translatable("slate_config.video.shader_pack"))
                .getter(IrisBridge::currentPackName)
                .format(v -> Component.literal(OptionValues.asString(v)))
                .searchWords("iris shaders shaderpack"));
            s.add(Binding.of("iris:enabled", OptionType.BOOLEAN, Component.translatable("slate_config.video.shaders_enabled"))
                .tooltip(Component.translatable("slate_config.video.shaders_enabled.tip"))
                .getter(() -> api.getConfig().areShadersEnabled())
                .setter(v -> api.getConfig().setShadersEnabledAndApply(OptionValues.asBoolean(v, false)))
                .def(Boolean.TRUE)
                .searchWords("iris shaders toggle"));
            s.add(Binding.of("iris:open", OptionType.ACTION, Component.translatable("slate_config.video.shader_screen"))
                .tooltip(Component.translatable("slate_config.video.shader_screen.tip"))
                .action(Component.translatable("slate_config.row.open"), () -> {
                    final Screen parent = Minecraft.getInstance().screen;
                    try {
                        final Object screen = api.openMainIrisScreenObj(parent);
                        if (screen instanceof Screen sc) Minecraft.getInstance().setScreen(sc);
                    } catch (final Throwable t) {
                        SlateConfig.LOGGER.warn("[Slate Config] cannot open the Iris screen: {}", t.toString());
                    }
                })
                .searchWords("iris shaders shaderpack select"));
        } catch (final Throwable t) {
            SlateConfig.LOGGER.warn("[Slate Config] Iris API unavailable: {}", t.toString());
        }
        return s;
    }

    public static List<OptionBinding> bindings() {
        return section().bindings();
    }

    private static String currentPackName() {
        try {
            final Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            final Object name = iris.getMethod("getCurrentPackName").invoke(null);
            final boolean inUse = IrisApi.getInstance().isShaderPackInUse();
            return name == null ? "-" : name + (inUse ? "" : " (" + Component.translatable("slate_config.video.shaders_off").getString() + ")");
        } catch (final Throwable t) {
            return "-";
        }
    }

    private IrisBridge() {}
}
