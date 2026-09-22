package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.ConfigSettings;
import dev.fallingcloud.slate.config.hub.ConfigHubScreen;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.preset.Presets;
import dev.fallingcloud.slate.config.search.SearchIndex;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SidebarPage;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateTextField;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Presets: cards for the shipped and user presets (apply / delete), and "save current as" from a chosen option set. */
public final class PresetsPage extends SidebarPage {

    private enum Source { FAVOURITES, VIDEO, PAGE }

    private SidebarScreen screen;

    public PresetsPage() {
        super("presets", Component.translatable("slate_config.page.presets"), Icon.BOOKMARK);
    }

    @Override
    public void build(final SidebarScreen screen, final Rect area) {
        this.screen = screen;
        final SlateScrollPanel panel = new SlateScrollPanel(area.x(), area.y(), area.w(), area.h());
        final int w = area.w() - 8;
        int y = 0;
        panel.add(new SlateButton(0, 0, 160, Component.translatable("slate_config.presets.save_current"), this::saveDialog).icon(Icon.SAVE).variant(SlateButton.Variant.PRIMARY), 0, y);
        y += 28;
        for (final ConfigSettings.Preset p : Presets.all()) {
            final boolean hasDesc = p.description != null && !p.description.isBlank();
            final int h = hasDesc ? 54 : 42;
            final SlateCard card = new SlateCard(0, y, w, h).flat();
            card.add(new SlateLabel(10, 8, w - 120, Component.literal(p.name)).style(SlateLabel.Style.TITLE), 10, 8);
            final Component meta = p.builtin
                ? Component.translatable("slate_config.presets.builtin", p.values.size())
                : Component.translatable("slate_config.presets.options", p.values.size());
            card.add(new SlateLabel(10, 22, w - 120, meta).style(SlateLabel.Style.CAPTION), 10, 22);
            if (hasDesc) card.add(new SlateLabel(10, 34, w - 120, Component.literal(p.description)).style(SlateLabel.Style.MUTED), 10, 34);
            card.add(new SlateButton(w - 100, 10, 90, Component.translatable("slate_config.presets.apply"), () -> apply(p)).icon(Icon.CHECK), w - 100, 10);
            if (!p.builtin) {
                card.add(new SlateButton(w - 100, h - 26, 90, Component.translatable("slate_config.presets.delete"), () ->
                    SlateModal.confirmDanger(Component.translatable("slate_config.presets.delete"), Component.translatable("slate_config.presets.delete.body", p.name),
                        Component.translatable("slate_config.presets.delete"), () -> { Presets.deleteUser(p); screen.refreshPage(); }))
                    .icon(Icon.TRASH).variant(SlateButton.Variant.DANGER), w - 100, h - 26);
            }
            panel.add(card, 0, y);
            y += h + 8;
        }
        if (Presets.all().isEmpty()) {
            panel.add(new SlateLabel(4, y, w - 8, Component.translatable("slate_config.presets.empty")).style(SlateLabel.Style.MUTED).wrap(true), 4, y);
            y += 20;
        }
        panel.setContentHeight(y);
        screen.addPageWidget(panel);
    }

    private void apply(final ConfigSettings.Preset p) {
        final Presets.ApplyResult r = Presets.apply(p);
        SlateToasts.show(Component.translatable("slate_config.presets.applied", p.name), Component.translatable("slate_config.presets.applied_body", r.applied(), r.skipped()), Icon.BOOKMARK);
        if (screen instanceof ConfigHubScreen hub) hub.refreshOptionPages();
    }

    private void saveDialog() {
        final SlateTextField name = new SlateTextField(0, 0, 200, Component.translatable("slate_config.presets.name"));
        name.placeholder(Component.translatable("slate_config.presets.name"));
        final OptionPageBase last = screen instanceof ConfigHubScreen hub ? hub.lastOptionPage() : null;
        final List<Source> sources = new ArrayList<>(List.of(Source.FAVOURITES, Source.VIDEO));
        if (last != null) sources.add(Source.PAGE);
        final SlateDropdown<Source> source = new SlateDropdown<>(0, 0, 200, sources, Source.VIDEO, s -> switch (s) {
            case FAVOURITES -> Component.translatable("slate_config.presets.source.favourites");
            case VIDEO -> Component.translatable("slate_config.presets.source.video");
            case PAGE -> Component.translatable("slate_config.presets.source.page", last == null ? "" : last.title().getString());
        }, s -> {});
        source.label(Component.translatable("slate_config.presets.source"));
        new SlateModal(Component.translatable("slate_config.presets.save_current"), Component.translatable("slate_config.presets.save_current.body"), Icon.SAVE)
            .extra(name).extra(source)
            .button(Component.translatable("gui.cancel"), SlateButton.Variant.SECONDARY, null)
            .button(Component.translatable("slate_config.list.save"), SlateButton.Variant.PRIMARY, () -> {
                final String n = name.getValue().trim();
                if (n.isEmpty()) { SlateToasts.show(Component.translatable("slate_config.presets.invalid_name"), null, Icon.WARNING); return; }
                final List<OptionBinding> bindings = switch (source.value()) {
                    case FAVOURITES -> FavouritesPage.resolved();
                    case VIDEO -> VideoPage.presetBindings();
                    case PAGE -> {
                        final List<OptionBinding> out = new ArrayList<>();
                        if (last != null) for (final Section s : last.currentSections()) out.addAll(s.bindings());
                        yield out;
                    }
                };
                Presets.saveUser(n, "", Presets.snapshot(bindings));
                SlateToasts.show(Component.translatable("slate_config.presets.saved", n), null, Icon.SAVE);
                screen.refreshPage();
            })
            .show();
    }

    public List<SearchIndex.Entry> searchEntries() {
        final List<SearchIndex.Entry> out = new ArrayList<>();
        for (final ConfigSettings.Preset p : Presets.all()) {
            out.add(new SearchIndex.Entry(id(), title(), Component.translatable("slate_config.page.presets"),
                Binding.of("preset:" + p.name, OptionType.ACTION, Component.translatable("slate_config.presets.apply_named", p.name))
                    .action(Component.translatable("slate_config.presets.apply"), () -> apply(p)).searchWords("preset " + p.description)));
        }
        return out;
    }
}
