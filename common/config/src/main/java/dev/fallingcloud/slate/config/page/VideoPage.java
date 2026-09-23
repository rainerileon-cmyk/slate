package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.ConfigSettings;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.resolver.VanillaOptions;
import dev.fallingcloud.slate.config.sodium.SodiumBridge;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Video: one top tab per mod in Sodium's option model (Sodium, Sodium Extra, Reese's, Iris...) with that
 * mod's pages as secondary tabs, and a "Vanilla" tab with Display / World / Effects under headers (minus the
 * rows Sodium already shows, unless "show duplicates" is on). Without Sodium, Display / World / Effects are
 * the tabs. Shaders live under Customization.
 */
public final class VideoPage extends OptionPageBase {

    public static final String VANILLA_TAB = "vanilla";

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
    protected boolean pills(final String tabKey) {
        // Sodium's mods: one page at a time. Vanilla next to them: a short list under headers.
        return !VANILLA_TAB.equals(tabKey) || !sodium();
    }

    @Override
    protected List<Section> sections() {
        final List<Section> out = new ArrayList<>();
        final boolean sodium = sodium();
        if (sodium) out.addAll(SodiumBridge.sections());
        final Component vanillaTitle = Component.translatable("slate_config.video.vanilla");
        if (sodium) {
            final Section dupes = Section.of("duplicates", Component.empty()).fixed().tab(VANILLA_TAB, vanillaTitle);
            dupes.add(Binding.of("video:show_duplicates", OptionType.BOOLEAN, Component.translatable("slate_config.video.show_duplicates"))
                .tooltip(Component.translatable("slate_config.video.show_duplicates.tip"))
                .getter(() -> ConfigSettings.get().showDuplicateRows)
                .setter(v -> { ConfigSettings.file().update(c -> c.showDuplicateRows = Boolean.TRUE.equals(v)); rebuild(); })
                .def(Boolean.FALSE)
                .searchWords("sodium duplicate vanilla rows"));
            out.add(dupes);
        }
        final boolean hideDupes = sodium && !ConfigSettings.get().showDuplicateRows;
        out.add(vanilla("display", "slate_config.video.display", DISPLAY, hideDupes).tab(VANILLA_TAB, vanillaTitle));
        out.add(vanilla("world", "slate_config.video.world", WORLD, hideDupes).tab(VANILLA_TAB, vanillaTitle));
        out.add(vanilla("effects", "slate_config.video.effects", EFFECTS, hideDupes).tab(VANILLA_TAB, vanillaTitle));
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
