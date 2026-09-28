package dev.fallingcloud.slate.menu.mixin;

import dev.fallingcloud.slate.menu.client.loading.LoadingScreens;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World loading ("Preparing spawn area"): drawn by {@link LoadingScreens}, with the chunk map; vanilla's narration kept. */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {

    @Shadow @Final private StoringChunkProgressListener progressListener;
    @Shadow private long lastNarration;

    protected LevelLoadingScreenMixin(final Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void slate$render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        if (!LoadingScreens.active()) return;
        ci.cancel();
        final long now = Util.getMillis();
        if (now - this.lastNarration > 2000L) {
            this.lastNarration = now;
            this.triggerImmediateNarration(true);
        }
        LoadingScreens.render(this, g, mouseX, mouseY, partialTick, Component.translatable("slate_menu.loading.world"),
            Component.translatable("slate_menu.loading.spawn"), this.progressListener.getProgress() / 100f, this.progressListener, List.of());
    }
}
