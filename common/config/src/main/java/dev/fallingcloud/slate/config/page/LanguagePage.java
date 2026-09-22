package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.resolver.VanillaOptions;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateList;
import dev.fallingcloud.slate.config.ui.ConfigSearchField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.Component;

/** Language: searchable list of every language the resource packs provide; apply = vanilla's select + reload. */
public final class LanguagePage extends OptionPageBase {

    private record Lang(String code, LanguageInfo info) {}

    private String search = "";
    private String selected;

    public LanguagePage() {
        super("language", Component.translatable("slate_config.page.language"), Icon.LANGUAGE);
    }

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

    @Override
    protected List<Section> sections() {
        if (selected == null) selected = Minecraft.getInstance().getLanguageManager().getSelected();
        final List<Section> out = new ArrayList<>();
        final Section pick = Section.of("pick", Component.translatable("slate_config.language.pick")).fixed();
        pick.custom(w -> new ConfigSearchField(0, 0, Math.min(w, 260), s -> { search = s; rebuild(); }));
        pick.custom(w -> {
            final SlateList<Lang> list = new SlateList<Lang>(0, 0, w, 190, 16, (g, item, index, x, y, rw, h, hovered, sel, mx, my) -> {
                final Palette p = Theme.current().palette();
                final boolean current = item.code().equals(Minecraft.getInstance().getLanguageManager().getSelected());
                final Component name = Component.literal(item.info().name() + " (" + item.info().region() + ")");
                g.drawString(SlateDraw.font(), SlateDraw.truncate(name, rw - 60), x + 6, y + 4, sel ? p.text() : (Theme.current().isVanilla() ? 0xFFE0E0E0 : p.textMuted()), Theme.current().isVanilla());
                if (current) SlateDraw.textRight(g, Component.translatable("slate_config.language.current"), x + rw - 6, y + 4, p.accent());
            }).gap(1).emptyText(Component.translatable("slate_config.search.no_matches"));
            final List<Lang> langs = languages();
            list.items(langs);
            for (int i = 0; i < langs.size(); i++) if (langs.get(i).code.equals(selected)) { list.select(i); break; }
            list.onSelect(l -> selected = l.code());
            list.onActivate(l -> { selected = l.code(); apply(); });
            return list;
        });
        pick.custom(w -> new SlateButton(0, 0, 140, Component.translatable("slate_config.language.apply"), this::apply).icon(Icon.CHECK).variant(SlateButton.Variant.PRIMARY));
        out.add(pick);
        out.add(Section.of("font", Component.translatable("slate_config.language.font"), VanillaOptions.all("forceUnicodeFont", "japaneseGlyphVariants")));
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
