package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.ConfigSettings;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionResolvers;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.OptionRow;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Every starred option in one place. Un-starring a row here removes it on the next rebuild. */
public final class FavouritesPage extends OptionPageBase {

    public FavouritesPage() {
        super("favourites", Component.translatable("slate_config.page.favourites"), Icon.STAR);
        emptyText(Component.translatable("slate_config.favourites.empty"));
        level(Level.ALL);
    }

    /** Resolve the pinned ids; unknown ones show as info rows so they can be un-pinned. */
    public static List<OptionBinding> resolved() {
        final List<OptionBinding> out = new ArrayList<>();
        for (final String id : new ArrayList<>(ConfigSettings.get().favourites)) {
            out.add(OptionResolvers.resolve(id).orElseGet(() ->
                Binding.of(id, OptionType.INFO, Component.literal(id)).getter(() -> Component.translatable("slate_config.favourites.unavailable").getString())));
        }
        return out;
    }

    @Override
    protected List<Section> sections() {
        return List.of(Section.of("pinned", Component.translatable("slate_config.page.favourites"), resolved()).fixed());
    }

    @Override
    protected void onRowChanged(final OptionRow row) {
        super.onRowChanged(row);
        if (!ConfigSettings.isFavourite(row.binding().id())) rebuild();
    }
}
