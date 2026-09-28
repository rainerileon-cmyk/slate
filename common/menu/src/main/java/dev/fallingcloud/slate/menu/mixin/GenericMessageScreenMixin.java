package dev.fallingcloud.slate.menu.mixin;

import dev.fallingcloud.slate.menu.client.loading.LoadingScreens;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The one-line waits ("Saving world", "Joining world...", "Reading world data..."): drawn by {@link LoadingScreens}.
 * Vanilla's screen has no {@code render} of its own (its message is a text widget), so this adds one.
 */
@Mixin(GenericMessageScreen.class)
public abstract class GenericMessageScreenMixin extends Screen {

    protected GenericMessageScreenMixin(final Component title) {
        super(title);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        if (!LoadingScreens.active()) {
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        LoadingScreens.render(this, g, mouseX, mouseY, partialTick, this.getTitle(), null, -1, null, List.of());
    }
}
