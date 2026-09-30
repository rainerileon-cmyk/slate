package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Several option pages shown as one: each part's sections land on a tab named after the part. The Advanced
 * category uses it to show the advanced rows of Multiplayer's Online and Chat pages under one "Multiplayer" tab.
 * The parts take this page's {@link Level}.
 */
public final class CompositePage extends OptionPageBase {

    private final List<OptionPageBase> parts;

    public CompositePage(final String id, final Component title, final Icon icon, final List<OptionPageBase> parts) {
        super(id, title, icon);
        this.parts = List.copyOf(parts);
    }

    @Override
    public OptionPageBase level(final Level l) {
        for (final OptionPageBase p : parts) p.level(l);
        // The parts already filtered their rows; this page passes everything they return through.
        return super.level(Level.ALL);
    }

    @Override
    protected List<Section> sections() {
        final List<Section> out = new ArrayList<>();
        for (final OptionPageBase p : parts) {
            for (final Section s : p.sectionsNow()) out.add(s.tab(p.id(), p.title()));
        }
        return out;
    }
}
