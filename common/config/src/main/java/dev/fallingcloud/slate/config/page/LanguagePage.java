package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.resolver.VanillaOptions;
import dev.fallingcloud.slate.config.ui.ConfigSearchField;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateList;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Language &amp; Accessibility › Language: a search box and Apply button over the list of every language the
 * resource packs provide (the list fills the tab), then the font options. Searching filters the list in
 * place; apply = vanilla's select + reload.
 */
public final class LanguagePage extends OptionPageBase {

    private static final String TAB = "language";

    private record Lang(String code, LanguageInfo info) {}

    private String search = "";
    private String selected;
    @Nullable private SlateList<Lang> list;
    @Nullable private SlateButton applyButton;

    public LanguagePage() {
        super("language", Component.translatable("slate_config.page.language"), Icon.LANGUAGE);
    }

    @Override
    protected boolean pills(final String tabKey) { return false; }

    private List<Lang> languages() {
        final LanguageManager lm = Minecraft.getInstance().getLanguageManager();
        final List<Lang> out = new ArrayList<>();
        final String q = search.toLowerCase(Locale.ROOT).trim();
        for (final Map.Entry<String, LanguageInfo> e : lm.getLanguages().entrySet()) {
            final String hay = (e.getKey() + " " + e.getValue().name() + " " + e.getValue().region()).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || hay.contains(q)) out.add(new Lang(e.getKey(), e.getValue()));
        }
        return out;
    }

    private void refreshList() {
        if (list == null) return;
        final List<Lang> langs = languages();
        list.items(langs);
        list.clearSelection();
        for (int i = 0; i < langs.size(); i++) if (langs.get(i).code.equals(selected)) { list.select(i); list.ensureVisible(i); break; }
    }

    private void updateApply() {
        if (applyButton != null) applyButton.enabled(selected != null && !selected.equals(Minecraft.getInstance().getLanguageManager().getSelected()));
    }

    private int listHeight() {
        final int h = area == null ? 300 : area.h();
        return Math.max(80, h - 26 - HEADER_H - 2 * 26 - 24);
    }

    private static final int HEADER_H = 22;

    @Override
    protected List<Section> sections() {
        if (selected == null) selected = Minecraft.getInstance().getLanguageManager().getSelected();
        final List<Section> out = new ArrayList<>();
        final Section pick = Section.of("pick", Component.empty()).fixed().tab(TAB, title());
        pick.custom(w -> {
            final ConfigSearchField field = new ConfigSearchField(0, 0, Math.max(80, w - 148), s -> { search = s; refreshList(); });
            field.setValue(search);
            final SlateButton apply = new SlateButton(0, 0, 140, Component.translatable("slate_config.language.apply"), this::apply)
                .icon(Icon.CHECK).variant(SlateButton.Variant.PRIMARY);
            applyButton = apply;
            updateApply();
            return new ControlsPage.Toolbar(w, List.of(field, apply));
        });
        pick.custom(w -> {
            final SlateList<Lang> l = new SlateList<Lang>(0, 0, w, listHeight(), 16, (g, item, index, x, y, rw, h, hovered, sel, mx, my) -> {
                final Palette p = Theme.current().palette();
                final boolean current = item.code().equals(Minecraft.getInstance().getLanguageManager().getSelected());
                final Component name = Component.literal(item.info().name() + " (" + item.info().region() + ")");
                g.drawString(SlateDraw.font(), SlateDraw.truncate(name, rw - 60), x + 6, y + 4, sel ? p.text() : (Theme.current().isVanilla() ? 0xFFE0E0E0 : p.textMuted()), Theme.current().isVanilla());
                if (current) SlateDraw.textRight(g, Component.translatable("slate_config.language.current"), x + rw - 6, y + 4, p.accent());
            }).gap(1).emptyText(Component.translatable("slate_config.search.no_matches"));
            list = l;
            l.onSelect(x -> { selected = x.code(); updateApply(); });
            l.onActivate(x -> { selected = x.code(); apply(); });
            refreshList();
            return l;
        });
        out.add(pick);
        out.add(Section.of("font", Component.translatable("slate_config.language.font"), VanillaOptions.all("forceUnicodeFont", "japaneseGlyphVariants"))
            .fixed().tab(TAB, title()));
        return out;
    }

    private void apply() {
        final Minecraft mc = Minecraft.getInstance();
        final LanguageManager lm = mc.getLanguageManager();
        if (selected == null || selected.equals(lm.getSelected())) return;
        lm.setSelected(selected);
        mc.options.languageCode = selected;
        mc.options.save();
        mc.reloadResourcePacks();
    }
}
