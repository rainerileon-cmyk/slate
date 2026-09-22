package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.curated.CuratedPages;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionResolvers;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** A page defined by {@code config/slate/config/pages/<id>.json}: sections of resolved option paths. */
public final class CuratedPage extends OptionPageBase {

    private final CuratedPages.PageDef def;

    public CuratedPage(final CuratedPages.PageDef def) {
        super("curated:" + def.id(), Component.literal(def.title()), def.icon());
        this.def = def;
    }

    @Override
    protected boolean showSingleHeader() { return true; }

    @Override
    protected List<Section> sections() {
        final List<Section> out = new ArrayList<>();
        int i = 0;
        for (final CuratedPages.SectionDef sd : def.sections()) {
            final Section s = new Section("s" + i++, Component.literal(sd.title()), sd.description() == null ? null : Component.literal(sd.description()));
            for (final CuratedPages.OptionDef od : sd.options()) {
                final OptionBinding b = OptionResolvers.resolve(od.path()).map(od::apply).orElseGet(() ->
                    Binding.of(od.path(), OptionType.INFO, Component.literal(od.label() != null ? od.label() : od.path()))
                        .tooltip(Component.translatable("slate_config.curated.unavailable.tip", od.path()))
                        .getter(() -> Component.translatable("slate_config.curated.unavailable").getString()));
                s.add(b);
            }
            out.add(s);
        }
        return out;
    }
}
