package dev.fallingcloud.slate.core.client;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.config.CoreConfig;
import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.client.settings.LayoutStyleRows;
import dev.fallingcloud.slate.core.client.settings.MenuSlotRow;
import dev.fallingcloud.slate.core.client.settings.SettingRow;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.screen.slot.MenuSlot;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.layout.ui.Flow;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.PixelFont;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateCheckbox;
import dev.fallingcloud.slate.core.widget.SlateColorField;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.SlateProgress;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateSegmented;
import dev.fallingcloud.slate.core.widget.SlateSlider;
import dev.fallingcloud.slate.core.widget.SlateSwatches;
import dev.fallingcloud.slate.core.widget.SlateTextField;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.core.widget.SlateToggle;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Core's own settings page (theme, motion, dev mode, restyle scope). The Config module embeds the same
 * options in its Interface page; this screen exists so Core is complete on its own. Sections are cards
 * on the 8 px grid; every change applies live (the preview card at the top shows the result).
 */
public final class CoreSettingsScreen extends SlateScreen {

    private static final int CARD_PAD = 10, ROW_GAP = 6, CARD_GAP = 8;

    private SlateSwatches swatches;
    private SlateColorField customColor;

    public CoreSettingsScreen(@Nullable final Screen parent) {
        super(Component.translatable("slate.settings.title"), parent);
        this.maxContentWidth = 400;
    }

    /** A card that stacks rows with a Flow; {@link #finish} sizes the card to its content. */
    private final class Section {
        final SlateCard card;
        final Flow flow;
        final int rowW;

        Section(final int width, final Component title) {
            card = new SlateCard(0, 0, width, 40).flat();
            rowW = width - CARD_PAD * 2;
            flow = Flow.column(CARD_PAD, CARD_PAD, ROW_GAP);
            if (title != null) {
                row(new SlateLabel(0, 0, rowW, title).style(SlateLabel.Style.TITLE));
                flow.skip(2);
            }
        }

        <T extends AbstractWidget> T row(final T w) {
            flow.place(w);
            card.add(w, w.getX(), w.getY());
            return w;
        }

        SlateCard finish() {
            card.setHeight(flow.maxY() + CARD_PAD);
            return card;
        }
    }

    /** The page's scroll panel, kept so a rebuild can restore the scroll position. */
    @org.jetbrains.annotations.Nullable private SlateScrollPanel panel;

    /** Rebuilds the page after a setting changed, keeping the scroll position. */
    @Override
    public void rebuildWidgets() {
        final double scroll = panel != null ? panel.scrollAmount() : 0;
        super.rebuildWidgets();
        if (panel != null && scroll > 0) panel.snapScroll(scroll);
    }

    @Override
    protected void build() {
        final Rect c = contentRect();
        final CoreConfig cfg = Slate.config();
        panel = add(new SlateScrollPanel(c.x(), c.y(), c.w(), c.h()).padding(4));
        final int w = panel.innerWidth();
        int y = 0;

        // Preview: a strip of live widgets so skin/accent changes are visible without leaving the page.
        final Section preview = new Section(w, Component.translatable("slate.settings.section.preview"));
        final int half = (preview.rowW - ROW_GAP) / 2;
        final SlateButton primary = new SlateButton(0, 0, half, Component.translatable("slate.settings.preview.primary"), () -> {}).variant(SlateButton.Variant.PRIMARY).icon(Icon.SPARKLE);
        final SlateButton secondary = new SlateButton(half + ROW_GAP, 0, half, Component.translatable("slate.settings.preview.secondary"), () -> {});
        preview.row(primary);
        preview.card.add(secondary, CARD_PAD + half + ROW_GAP, primary.getY());
        preview.row(new SlateToggle(0, 0, half, Component.translatable("slate.settings.preview.toggle"), true, v -> {}));
        final SlateTextField field = new SlateTextField(half + ROW_GAP, 0, half, Component.translatable("slate.settings.preview.field")).placeholder(Component.translatable("slate.settings.preview.field")).clearButton(true);
        preview.card.add(field, CARD_PAD + half + ROW_GAP, preview.flow.y() - SlateToggle.SWITCH_H - 14);
        preview.row(new SlateCheckbox(0, 0, half, Component.translatable("slate.settings.preview.checkbox"), true, v -> {}));
        final SlateProgress progress = new SlateProgress(half + ROW_GAP, 0, half, 6).snap(0.66f);
        preview.card.add(progress, CARD_PAD + half + ROW_GAP, preview.flow.y() - ROW_GAP - 16 + 5);
        panel.add(preview.finish(), 0, y);
        y += preview.card.getHeight() + CARD_GAP;

        // With Slate Config installed these rows live on its Interface page (one place, A6.8); this page then only points there.
        if (dev.fallingcloud.slate.core.module.Modules.isLoaded("slate_config")) {
            final Section modes = new Section(w, Component.translatable("slate.settings.section.modes"));
            modes.row(new SlateLabel(0, 0, modes.rowW, Component.translatable("slate.settings.in_config")).style(SlateLabel.Style.MUTED).wrap(true));
            modes.flow.skip(2);
            modes.row(new SlateButton(0, 0, 200, Component.translatable("slate.settings.open_interface"), () -> CoreActions.openScreen("slate_config:hub/interface"))
                .icon(Icon.SLIDERS).variant(SlateButton.Variant.SECONDARY));
            panel.add(modes.finish(), 0, y);
            y += modes.card.getHeight() + CARD_GAP;
        } else {
            // The three rows of the setup screen: layout, menu style, container style (Core's own controls, applied live;
            // a change rebuilds the page so the descriptions follow, keeping the scroll position).
            final Section modes = new Section(w, Component.translatable("slate.settings.section.modes"));
            modes.row(SettingRow.of(modes.rowW, LayoutStyleRows.layoutLabel(), null,
                LayoutStyleRows.layoutSegmented(0, 0, SettingRow.controlWidth(modes.rowW), l -> rebuildWidgets()), LayoutStyleRows.layoutDescription(cfg.layout())));
            modes.flow.skip(2);
            modes.row(SettingRow.of(modes.rowW, LayoutStyleRows.styleLabel(), null,
                LayoutStyleRows.styleSegmented(0, 0, SettingRow.controlWidth(modes.rowW), s -> rebuildWidgets()), LayoutStyleRows.styleDescription(cfg.style())));
            modes.flow.skip(2);
            modes.row(LayoutStyleRows.containersToggle(0, 0, modes.rowW, v -> rebuildWidgets()));
            modes.row(new SlateLabel(0, 0, modes.rowW, Component.translatable("slate.setup.containers.desc")).style(SlateLabel.Style.MUTED).wrap(true));
            modes.flow.skip(4);
            modes.row(new SlateButton(0, 0, 130, 16, Component.translatable("slate.settings.run_setup"),
                () -> minecraft.setScreen(new dev.fallingcloud.slate.core.client.setup.SlateSetupScreen(this)))
                .icon(Icon.SPARKLE).variant(SlateButton.Variant.GHOST).leftAligned());
            panel.add(modes.finish(), 0, y);
            y += modes.card.getHeight() + CARD_GAP;

            // The Menus table: every menu Slate touches, each with its own layout and style (or the global ones).
            final Section menus = new Section(w, Component.translatable("slate.settings.menus"));
            menus.row(new SlateLabel(0, 0, menus.rowW, Component.translatable("slate.settings.menus.desc")).style(SlateLabel.Style.MUTED).wrap(true));
            menus.flow.skip(2);
            for (final MenuSlot slot : MenuSlots.all()) menus.row(new MenuSlotRow(menus.rowW, slot, this::rebuildWidgets));
            panel.add(menus.finish(), 0, y);
            y += menus.card.getHeight() + CARD_GAP;
        }

        // Look
        final Section look = new Section(w, Component.translatable("slate.settings.section.look"));
        look.row(new SlateLabel(0, 0, look.rowW, Component.translatable("slate.settings.accent")).style(SlateLabel.Style.MUTED));
        final int accent = Colors.fromHex(cfg.accent, Palette.DEFAULT_ACCENT);
        swatches = look.row(new SlateSwatches(0, 0, look.rowW, Palette.ACCENTS, accent, argb -> {
            save(x -> x.accent = Colors.toHex(argb));
            if (customColor != null) customColor.setColor(argb);
        }));
        customColor = look.row(new SlateColorField(0, 0, look.rowW, accent, argb -> {
            save(x -> x.accent = Colors.toHex(argb));
            if (swatches != null) swatches.setSelectedColor(argb);
        }));
        look.flow.skip(2);
        look.row(new SlateSlider(0, 0, look.rowW, Component.translatable("slate.settings.radius"), 0, 4, 1, cfg.radius,
            v -> Integer.toString((int) v) + " px", v -> save(x -> x.radius = (int) v)).compact(true));
        // Each option is written in its own font; the choice only matters while the heading font is on.
        final SlateSegmented<PixelFont> pixelFont = new SlateSegmented<>(0, 0, look.rowW, List.of(PixelFont.values()),
            PixelFont.parse(cfg.pixelFont), PixelFont::label, f -> save(x -> x.pixelFont = f.key()));
        pixelFont.active = cfg.headingFont;
        look.row(new SlateToggle(0, 0, look.rowW, Component.translatable("slate.settings.heading_font"), cfg.headingFont, v -> {
            save(x -> x.headingFont = v);
            pixelFont.active = v;
        }));
        look.row(new SlateLabel(0, 0, look.rowW, Component.translatable("slate.settings.pixel_font")).style(SlateLabel.Style.MUTED));
        look.row(pixelFont);
        look.row(new SlateToggle(0, 0, look.rowW, Component.translatable("slate.settings.blur_in_game"), cfg.blurInGame, v -> save(x -> x.blurInGame = v)));
        panel.add(look.finish(), 0, y);
        y += look.card.getHeight() + CARD_GAP;

        // Motion & feedback
        final Section motion = new Section(w, Component.translatable("slate.settings.section.motion"));
        motion.row(new SlateSlider(0, 0, motion.rowW, Component.translatable("slate.settings.motion"), 0, 2, 0.25, cfg.motion,
            v -> v <= 0 ? Component.translatable("slate.settings.motion.off").getString() : "%.2fx".formatted(v), v -> save(x -> x.motion = v)).compact(true));
        motion.row(new SlateToggle(0, 0, motion.rowW, Component.translatable("slate.settings.transitions"), cfg.transitions, v -> save(x -> x.transitions = v)));
        motion.row(new SlateToggle(0, 0, motion.rowW, Component.translatable("slate.settings.ui_sounds"), cfg.uiSounds, v -> save(x -> x.uiSounds = v)));
        motion.row(new SlateToggle(0, 0, motion.rowW - 90, Component.translatable("slate.settings.toasts"), cfg.toasts, v -> save(x -> x.toasts = v)));
        final SlateButton test = new SlateButton(0, 0, 84, 16, Component.translatable("slate.settings.test_toast"),
            () -> SlateToasts.show(Component.translatable("slate.toast.test.title"), Component.translatable("slate.toast.test.body"), Icon.BELL))
            .variant(SlateButton.Variant.GHOST).icon(Icon.BELL);
        motion.card.add(test, CARD_PAD + motion.rowW - 84, motion.flow.y() - ROW_GAP - 18);
        panel.add(motion.finish(), 0, y);
        y += motion.card.getHeight() + CARD_GAP;

        // Restyle other screens
        final Section restyle = new Section(w, Component.translatable("slate.settings.section.restyle"));
        restyle.row(new SlateLabel(0, 0, restyle.rowW, Component.translatable("slate.settings.restyle.hint")).style(SlateLabel.Style.MUTED).wrap(true));
        restyle.row(new SlateDropdown<>(0, 0, restyle.rowW, List.of("VANILLA_AND_SLATE", "ALLOWLIST", "ALL_NON_CONTAINER", "NONE"), cfg.reskinScope,
            s -> Component.translatable("slate.reskin." + s.toLowerCase(java.util.Locale.ROOT)), s -> { save(x -> x.reskinScope = s); Reskin.invalidate(); })
            .label(Component.translatable("slate.settings.reskin_scope")));
        panel.add(restyle.finish(), 0, y);
        y += restyle.card.getHeight() + CARD_GAP;

        // Development mode
        final Section devSec = new Section(w, Component.translatable("slate.settings.section.dev"));
        devSec.row(new SlateToggle(0, 0, devSec.rowW, Component.translatable("slate.settings.dev_mode"), cfg.devMode, v -> save(x -> x.devMode = v))
            .tip(Component.translatable("slate.settings.dev_mode.tip")));
        devSec.row(new SlateToggle(0, 0, devSec.rowW, Component.translatable("slate.settings.dev_grid"), cfg.devGrid, v -> save(x -> x.devGrid = v)));
        devSec.row(new SlateSlider(0, 0, devSec.rowW, Component.translatable("slate.settings.dev_snap"), 1, 16, 1, cfg.devSnap,
            v -> Integer.toString((int) v) + " px", v -> save(x -> x.devSnap = (int) v)).compact(true));
        devSec.flow.skip(2);
        final int bw = (devSec.rowW - ROW_GAP) / 2;
        final SlateButton folder = new SlateButton(0, 0, bw, Component.translatable("slate.settings.open_config_folder"), () ->
            net.minecraft.Util.getPlatform().openPath(JsonConfig.dir())).icon(Icon.FOLDER);
        devSec.row(folder);
        devSec.card.add(new SlateButton(0, 0, bw, Component.translatable("slate.settings.reset"), this::confirmReset).variant(SlateButton.Variant.DANGER).icon(Icon.UNDO),
            CARD_PAD + bw + ROW_GAP, folder.getY());
        panel.add(devSec.finish(), 0, y);
        y += devSec.card.getHeight();

        panel.setContentHeight(y);
    }

    /** A switch with its explanation under it ({@code slate.setup.<key>} / {@code .desc}, shared with the setup screen). */
    private void switchRow(final Section s, final String key, final boolean value, final Consumer<Boolean> onChange) {
        s.row(new SlateToggle(0, 0, s.rowW, Component.translatable("slate.setup." + key), value, onChange));
        s.row(new SlateLabel(0, 0, s.rowW, Component.translatable("slate.setup." + key + ".desc")).style(SlateLabel.Style.MUTED).wrap(true));
        s.flow.skip(4);
    }

    private void confirmReset() {
        SlateModal.confirmDanger(Component.translatable("slate.settings.reset.title"), Component.translatable("slate.settings.reset.body"),
            Component.translatable("slate.settings.reset"), () -> {
                Slate.configFile().reset();
                Theme.reload();
                Reskin.invalidate();
                rebuildWidgets();
            });
    }

    private void save(final Consumer<CoreConfig> edit) {
        Slate.configFile().update(edit);
        Theme.reload();
    }
}
