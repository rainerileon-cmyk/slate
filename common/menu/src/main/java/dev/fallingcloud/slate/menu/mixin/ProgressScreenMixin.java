package dev.fallingcloud.slate.menu.mixin;

import dev.fallingcloud.slate.menu.client.loading.LoadingScreens;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The generic progress screen (header, stage, percentage): drawn by {@link LoadingScreens}. Once stopped it is left to
 * vanilla, whose render is what closes it.
 */
@Mixin(ProgressScreen.class)
public abstract class ProgressScreenMixin extends Screen {

    @Shadow @Nullable private Component header;
    @Shadow @Nullable private Component stage;
    @Shadow private int progress;
    @Shadow private boolean stop;

    protected ProgressScreenMixin(final Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void slate$render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        if (!LoadingScreens.active() || this.stop) return;
        ci.cancel();
        final Component title = this.header != null && !this.header.getString().isBlank() ? this.header : Component.translatable("slate_menu.loading.generic");
        LoadingScreens.render(this, g, mouseX, mouseY, partialTick, title, this.stage, this.progress > 0 ? this.progress / 100f : -1, null, List.of());
    }
}
