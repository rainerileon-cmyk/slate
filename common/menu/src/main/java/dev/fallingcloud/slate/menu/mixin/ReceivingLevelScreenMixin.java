package dev.fallingcloud.slate.menu.mixin;

import dev.fallingcloud.slate.menu.client.loading.LoadingScreens;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Loading terrain" (joining, respawning, changing dimension): drawn by {@link LoadingScreens} over the screen's own background, so a portal trip keeps its portal. */
@Mixin(ReceivingLevelScreen.class)
public abstract class ReceivingLevelScreenMixin extends Screen {

    protected ReceivingLevelScreenMixin(final Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void slate$render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        if (!LoadingScreens.active()) return;
        ci.cancel();
        LoadingScreens.render(this, g, mouseX, mouseY, partialTick, Component.translatable("multiplayer.downloadingTerrain"), null, -1, null, List.of());
    }
}
