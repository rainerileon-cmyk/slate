package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.option.OptionValues;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * Customization › Skin: vanilla's skin layer toggles (cape, jacket, sleeves, trousers, hat). The options
 * screen now opens the hub, so this keeps "Skin Customization" one click away.
 */
public final class SkinPage extends OptionPageBase {

    public SkinPage() {
        super("skin", Component.translatable("slate_config.page.skin"), Icon.USER);
    }

    @Override
    protected List<Section> sections() {
        final Section s = Section.of("layers", Component.translatable("slate_config.skin.layers")).fixed();
        for (final PlayerModelPart part : PlayerModelPart.values()) {
            s.add(Binding.of("skin:" + part.getId(), OptionType.BOOLEAN, part.getName())
                .getter(() -> Minecraft.getInstance().options.isModelPartEnabled(part))
                .setter(v -> {
                    Minecraft.getInstance().options.toggleModelPart(part, OptionValues.asBoolean(v, true));
                    Minecraft.getInstance().options.save();
                })
                .def(Boolean.TRUE)
                .searchWords("skin layer model part " + part.getId()));
        }
        return List.of(s);
    }
}
