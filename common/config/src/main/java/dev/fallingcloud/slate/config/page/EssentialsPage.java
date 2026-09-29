package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.resolver.VanillaOptions;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * General › Essentials: the handful of options players change most (FOV, render distance, GUI scale, brightness,
 * fullscreen, frame rate cap, master volume, and the world's difficulty while a world is open), on one short page.
 * Every row also has its proper home on another tab, so this page stays out of the search index and never hides a
 * row as advanced.
 */
public final class EssentialsPage extends OptionPageBase {

    public EssentialsPage() {
        super("essentials", Component.translatable("slate_config.page.essentials"), Icon.SPARKLE);
        level(Level.ALL);
        indexed(false);
    }

    @Override
    protected boolean pills(final String tabKey) { return false; }

    @Override
    protected List<Section> sections() {
        final Section s = new Section("essentials", Component.translatable("slate_config.page.essentials"), Component.translatable("slate_config.essentials.desc"))
            .fixed().basic();
        if (Minecraft.getInstance().level != null) s.add(GameplayGeneralPage.difficultyBinding(this::rebuild));
        s.addAll(VanillaOptions.all("fov", "renderDistance", "guiScale", "gamma", "fullscreen", "maxFps", "soundCategory_master"));
        return List.of(s);
    }
}
