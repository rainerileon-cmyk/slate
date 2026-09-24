package dev.fallingcloud.slate.building.client.settings;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.BuildingIcons;
import dev.fallingcloud.slate.building.client.wheel.WheelConfig;
import dev.fallingcloud.slate.config.SlateConfigApi;
import dev.fallingcloud.slate.config.api.SettingsTabs;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.Choice;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.ui.Section;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * The "Building" tab of Slate Config's Gameplay category (design §10): one section per {@link BuildingSettings}
 * group (General, Placement preview, Swap wheel, Build menu &amp; HUD, Server rules) as typed {@link Binding}s with
 * labels, tooltips, ranges and defaults, plus the "Edit wheels…" row. Registers the {@code building} reload hook so
 * rows apply live (no restart badge) and a hand-edited {@code building.json} is picked up.
 *
 * <p>Only loaded after the {@code slate_config} presence check.</p>
 */
public final class BuildingSettingsTab {

    public static final String TAB_ID = "building";

    public static void register() {
        SettingsTabs.register(SettingsTabs.GAMEPLAY, new SettingsTabs.Tab(TAB_ID, Component.translatable("slate_building.settings.tab"),
            BuildingIcons.WHEEL, BuildingSettingsTab::sections, 100));
        SlateConfigApi.registerReloadHook("building", () -> {
            SlateBuilding.configFile().load();
            WheelConfig.changed();
        });
    }

    private static List<Section> sections() {
        final List<Section> out = new ArrayList<>();
        for (final BuildingSettings.Group g : BuildingSettings.groups()) {
            final Section s = new Section(g.id(), g.title(), g.description());
            for (final BuildingSettings.Setting set : g.settings()) s.add(binding(set));
            if (g.wheelEditor()) s.custom(WheelsRow::new);
            out.add(s);
        }
        return out;
    }

    private static Binding binding(final BuildingSettings.Setting s) {
        final OptionType type = switch (s.type()) {
            case BOOL -> OptionType.BOOLEAN;
            case INT -> OptionType.INT;
            case DOUBLE -> OptionType.DOUBLE;
            case CHOICE -> OptionType.CHOICE;
            case INFO -> OptionType.INFO;
            case TIERS -> OptionType.STRING;
        };
        final Binding b = Binding.of("slate_building:" + s.id(), type, s.label())
            .tooltip(s.tooltip())
            .getter(s.get())
            .setter(s.set())
            .enabledIf(s.enabled())
            .restart(s.restart())
            .searchWords("building slate_building " + s.id().replace('.', ' ').replace('_', ' ')
                + (s.type() == BuildingSettings.Type.TIERS ? " tiers per tier" : ""));
        if (s.def() != null) b.def(s.def());
        if (s.type() == BuildingSettings.Type.INT || s.type() == BuildingSettings.Type.DOUBLE) b.range(s.min(), s.max(), s.step());
        if (s.type() == BuildingSettings.Type.CHOICE) {
            final List<Choice> choices = new ArrayList<>();
            for (final String id : s.choices()) choices.add(new Choice(id, s.choiceLabel().apply(id)));
            b.choices(choices);
        }
        if (s.format() != null) b.format(s.format());
        if (s.type() == BuildingSettings.Type.INFO) b.format(v -> v instanceof Component c ? c : Component.literal(String.valueOf(v)));
        return b;
    }

    private BuildingSettingsTab() {}
}
