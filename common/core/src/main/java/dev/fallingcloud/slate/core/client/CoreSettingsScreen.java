package dev.fallingcloud.slate.core.client;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.config.CoreConfig;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.ui.Flow;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateColorField;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateSegmented;
import dev.fallingcloud.slate.core.widget.SlateSeparator;
import dev.fallingcloud.slate.core.widget.SlateSlider;
import dev.fallingcloud.slate.core.widget.SlateToggle;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Core's own settings page (theme, motion, dev mode, restyle scope). The Config module embeds the same
 * options in its Interface page; this screen exists so Core is complete on its own.
 */
public final class CoreSettingsScreen extends SlateScreen {

    public CoreSettingsScreen(@Nullable final Screen parent) {
        super(Component.translatable("slate.settings.title"), parent);
        this.maxContentWidth = 360;
    }

    @Override
    protected void build() {
        final Rect c = contentRect();
        final CoreConfig cfg = Slate.config();
        final SlateScrollPanel panel = add(new SlateScrollPanel(c.x(), c.y() + 4, c.w(), c.h() - 4));
        final int w = c.w() - 10;
        final Flow f = Flow.column(0, 0, 6);

        panel.add(f.place(new SlateSeparator(0, 0, w, Component.translatable("slate.settings.section.look"))));
        panel.add(f.place(new SlateSegmented<>(0, 0, w, List.of("DARK", "VANILLA"), cfg.isVanillaSkin() ? "VANILLA" : "DARK",
            s -> Component.translatable("slate.skin." + s.toLowerCase()), s -> save(x -> x.skin = s))));
        panel.add(f.place(new SlateColorField(0, 0, w, Colors.fromHex(cfg.accent, Palette.DEFAULT_ACCENT), argb -> save(x -> x.accent = Colors.toHex(argb)))));
        final SlateDropdown<Palette.AccentPreset> presets = new SlateDropdown<>(0, 0, w, Palette.ACCENTS, null,
            p -> Component.literal(p.name()), p -> save(x -> x.accent = Colors.toHex(p.color())));
        presets.label(Component.translatable("slate.settings.accent_preset"));
        panel.add(f.place(presets));
        panel.add(f.place(new SlateSlider(0, 0, w, Component.translatable("slate.settings.radius"), 0, 4, 1, cfg.radius, v -> Integer.toString((int) v), v -> save(x -> x.radius = (int) v)).compact(true)));
        panel.add(f.place(new SlateToggle(0, 0, w, Component.translatable("slate.settings.heading_font"), cfg.headingFont, v -> save(x -> x.headingFont = v))));
        panel.add(f.place(new SlateToggle(0, 0, w, Component.translatable("slate.settings.blur_in_game"), cfg.blurInGame, v -> save(x -> x.blurInGame = v))));

        panel.add(f.place(new SlateSeparator(0, 0, w, Component.translatable("slate.settings.section.motion"))));
        panel.add(f.place(new SlateSlider(0, 0, w, Component.translatable("slate.settings.motion"), 0, 2, 0.25, cfg.motion,
            v -> v <= 0 ? "Off" : "%.2fx".formatted(v), v -> save(x -> x.motion = v)).compact(true)));
        panel.add(f.place(new SlateToggle(0, 0, w, Component.translatable("slate.settings.transitions"), cfg.transitions, v -> save(x -> x.transitions = v))));
        panel.add(f.place(new SlateToggle(0, 0, w, Component.translatable("slate.settings.ui_sounds"), cfg.uiSounds, v -> save(x -> x.uiSounds = v))));
        panel.add(f.place(new SlateToggle(0, 0, w, Component.translatable("slate.settings.toasts"), cfg.toasts, v -> save(x -> x.toasts = v))));

        panel.add(f.place(new SlateSeparator(0, 0, w, Component.translatable("slate.settings.section.restyle"))));
        panel.add(f.place(new SlateDropdown<>(0, 0, w, List.of("VANILLA_AND_SLATE", "ALLOWLIST", "ALL_NON_CONTAINER", "NONE"), cfg.reskinScope,
            s -> Component.translatable("slate.reskin." + s.toLowerCase()), s -> { save(x -> x.reskinScope = s); Reskin.invalidate(); })
            .label(Component.translatable("slate.settings.reskin_scope"))));

        panel.add(f.place(new SlateSeparator(0, 0, w, Component.translatable("slate.settings.section.dev"))));
        panel.add(f.place(new SlateToggle(0, 0, w, Component.translatable("slate.settings.dev_mode"), cfg.devMode, v -> save(x -> x.devMode = v))));
        panel.add(f.place(new SlateToggle(0, 0, w, Component.translatable("slate.settings.dev_grid"), cfg.devGrid, v -> save(x -> x.devGrid = v))));
        panel.add(f.place(new SlateSlider(0, 0, w, Component.translatable("slate.settings.dev_snap"), 1, 16, 1, cfg.devSnap, v -> Integer.toString((int) v) + " px", v -> save(x -> x.devSnap = (int) v)).compact(true)));
        panel.add(f.place(new SlateButton(0, 0, w, Component.translatable("slate.settings.open_config_folder"), () ->
            net.minecraft.Util.getPlatform().openPath(dev.fallingcloud.slate.core.config.JsonConfig.dir())).icon(Icon.FOLDER).variant(SlateButton.Variant.GHOST)));
        panel.setContentHeight(f.maxY());
    }

    private void save(final java.util.function.Consumer<CoreConfig> edit) {
        Slate.configFile().update(edit);
        Theme.reload();
    }
}
