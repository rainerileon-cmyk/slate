package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.ConfigSettings;
import dev.fallingcloud.slate.config.iris.IrisBridge;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.resolver.VanillaOptions;
import dev.fallingcloud.slate.config.sodium.SodiumBridge;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.screen.ScreenSwaps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;

/**
 * Video: Sodium's full option model (its pages plus every mod page registered through Sodium's API)
 * when present, vanilla's video options (minus the ones Sodium already shows), Iris shaders, and escape
 * hatches to the native screens.
 */
public final class VideoPage extends OptionPageBase {

    private static final String[] DISPLAY = { "fullscreen", "enableVsync", "maxFps", "guiScale", "gamma", "menuBackgroundBlurriness" };
    private static final String[] WORLD = { "renderDistance", "simulationDistance", "graphicsMode", "renderClouds", "particles", "ao",
        "biomeBlendRadius", "entityDistanceScaling", "entityShadows", "mipmapLevels", "prioritizeChunkUpdates" };
    private static final String[] EFFECTS = { "fov", "screenEffectScale", "fovEffectScale", "darknessEffectScale", "damageTiltStrength",
        "glintSpeed", "glintStrength", "hideLightningFlashes", "bobView", "attackIndicator", "showAutosaveIndicator" };

    public VideoPage() {
        super("video", Component.translatable("slate_config.page.video"), Icon.MONITOR);
    }

    public static boolean sodium() {
        return SlatePlatform.get().isModLoaded("sodium") && SodiumBridge.available();
    }

    @Override
    protected List<Section> sections() {
        final List<Section> out = new ArrayList<>();
        final boolean sodium = sodium();
        if (sodium) out.addAll(SodiumBridge.sections());
        final boolean hideDupes = sodium && !ConfigSettings.get().showDuplicateRows;
        out.add(vanilla("display", "slate_config.video.display", DISPLAY, hideDupes));
        out.add(vanilla("world", "slate_config.video.world", WORLD, hideDupes));
        out.add(vanilla("effects", "slate_config.video.effects", EFFECTS, hideDupes));
        if (SlatePlatform.get().isModLoaded("iris")) out.add(IrisBridge.section());
        final Section more = Section.of("more", Component.translatable("slate_config.video.more"));
        if (sodium) {
            more.add(Binding.of("video:open_sodium", OptionType.ACTION, Component.translatable("slate_config.video.open_sodium"))
                .tooltip(Component.translatable("slate_config.video.open_sodium.tip"))
                .action(Component.translatable("slate_config.row.open"), () -> SodiumBridge.openNativeScreen(Minecraft.getInstance().screen))
                .searchWords("sodium native screen"));
        }
        more.add(Binding.of("video:open_vanilla", OptionType.ACTION, Component.translatable("slate_config.video.open_vanilla"))
            .tooltip(Component.translatable("slate_config.video.open_vanilla.tip"))
            .action(Component.translatable("slate_config.row.open"), () -> {
                final Minecraft mc = Minecraft.getInstance();
                final Screen parent = mc.screen;
                ScreenSwaps.runUnswapped(() -> mc.setScreen(new VideoSettingsScreen(parent, mc, mc.options)));
            })
            .searchWords("vanilla video settings screen"));
        more.add(Binding.of("video:show_duplicates", OptionType.BOOLEAN, Component.translatable("slate_config.video.show_duplicates"))
            .tooltip(Component.translatable("slate_config.video.show_duplicates.tip"))
            .getter(() -> ConfigSettings.get().showDuplicateRows)
            .setter(v -> { ConfigSettings.file().update(c -> c.showDuplicateRows = Boolean.TRUE.equals(v)); rebuild(); })
            .def(Boolean.FALSE)
            .enabledIf(VideoPage::sodium));
        out.add(more);
        return out;
    }

    private static Section vanilla(final String id, final String titleKey, final String[] keys, final boolean hideDupes) {
        final Section s = Section.of(id, Component.translatable(titleKey));
        for (final String k : keys) {
            if (hideDupes && VanillaOptions.COVERED_BY_SODIUM.contains(k)) continue;
            VanillaOptions.get(k).ifPresent(s::add);
        }
        return s;
    }

    /** The bindings the shipped presets snapshot. */
    public static List<OptionBinding> presetBindings() {
        final List<OptionBinding> out = new ArrayList<>();
        out.addAll(VanillaOptions.all(DISPLAY));
        out.addAll(VanillaOptions.all(WORLD));
        out.addAll(VanillaOptions.all(EFFECTS));
        if (sodium()) for (final Section s : SodiumBridge.sections()) out.addAll(s.bindings());
        return out;
    }
}
