package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;

/** Resource packs: what is active, and the vanilla selection screen (returns here when closed). */
public final class ResourcePacksPage extends OptionPageBase {

    public ResourcePacksPage() {
        super("packs", Component.translatable("slate_config.page.packs"), Icon.PACK);
    }

    @Override
    protected List<Section> sections() {
        final Minecraft mc = Minecraft.getInstance();
        final List<Section> out = new ArrayList<>();
        final Section active = Section.of("active", Component.translatable("slate_config.packs.active")).fixed();
        final List<String> packs = mc.options.resourcePacks;
        if (packs.isEmpty()) {
            active.add(Binding.of("packs:none", OptionType.INFO, Component.translatable("slate_config.packs.none")).getter(() -> ""));
        }
        int i = 0;
        for (final String id : packs) {
            final boolean incompatible = mc.options.incompatibleResourcePacks.contains(id);
            active.add(Binding.of("packs:active." + i++, OptionType.INFO, Component.literal(id))
                .getter(() -> incompatible ? Component.translatable("slate_config.packs.incompatible").getString() : "")
                .searchWords("resource pack"));
        }
        out.add(active);
        final Section actions = Section.of("actions", Component.translatable("slate_config.packs.manage")).fixed();
        actions.add(Binding.of("packs:open", OptionType.ACTION, Component.translatable("slate_config.packs.open"))
            .tooltip(Component.translatable("slate_config.packs.open.tip"))
            .action(Component.translatable("slate_config.row.open"), () -> {
                final Screen back = mc.screen;
                mc.setScreen(new PackSelectionScreen(mc.getResourcePackRepository(), repo -> {
                    mc.options.updateResourcePacks(repo);
                    mc.setScreen(back);
                }, mc.getResourcePackDirectory(), Component.translatable("resourcePack.title")));
            })
            .searchWords("resource packs select"));
        actions.add(Binding.of("packs:folder", OptionType.ACTION, Component.translatable("slate_config.packs.folder"))
            .action(Component.translatable("slate_config.row.open"), () -> net.minecraft.Util.getPlatform().openPath(mc.getResourcePackDirectory()))
            .searchWords("resource packs folder"));
        out.add(actions);
        return out;
    }
}
