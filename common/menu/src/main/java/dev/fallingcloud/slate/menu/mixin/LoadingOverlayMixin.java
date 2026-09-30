package dev.fallingcloud.slate.menu.mixin;

import dev.fallingcloud.slate.core.client.DevHarness;
import dev.fallingcloud.slate.menu.client.loading.OverhaulLoading;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game's loading overlay in the Overhaul layout: everything the overlay does stays as it is (it fades in over a
 * screen when asked to, waits for the reload, reports what went wrong, fades out and takes itself away), and what it
 * shows is the factory instead of the logo and the bar. A subclass that draws for itself (NeoForge's, which carries
 * on the start-up window) is left alone.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ReloadInstance reload;
    @Shadow @Final private Consumer<Optional<Throwable>> onFinish;
    @Shadow @Final private boolean fadeIn;
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void slate$factory(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        if ((Object) this.getClass() != LoadingOverlay.class || !OverhaulLoading.active()) return;
        final long now = Util.getMillis();
        if (this.fadeIn && this.fadeInStart == -1L) this.fadeInStart = now;
        final float out = this.fadeOutStart > -1L ? (now - this.fadeOutStart) / 1000f : -1f;
        final float in = this.fadeInStart > -1L ? (now - this.fadeInStart) / 500f : -1f;
        final float alpha;
        if (out >= 1f) {
            if (this.minecraft.screen != null) this.minecraft.screen.render(graphics, 0, 0, partialTick);
            alpha = 1f - Mth.clamp(out - 1f, 0f, 1f);
        } else if (this.fadeIn) {
            if (this.minecraft.screen != null && in < 1f) this.minecraft.screen.render(graphics, mouseX, mouseY, partialTick);
            alpha = Mth.clamp(in, 0.15f, 1f);
        } else {
            alpha = 1f;
        }
        graphics.flush();
        // Could not be drawn: the game's own overlay draws this frame and all that follow.
        if (!OverhaulLoading.draw(this.reload, alpha)) return;
        ci.cancel();
        DevHarness.overlayFrame();

        this.currentProgress = Mth.clamp(this.currentProgress * 0.95f + this.reload.getActualProgress() * 0.05f, 0f, 1f);
        if (out >= 2f) this.minecraft.setOverlay(null);
        if (this.fadeOutStart == -1L && this.reload.isDone() && (!this.fadeIn || in >= 2f)) {
            try {
                this.reload.checkExceptions();
                this.onFinish.accept(Optional.empty());
            } catch (final Throwable t) {
                this.onFinish.accept(Optional.of(t));
            }
            this.fadeOutStart = Util.getMillis();
            if (this.minecraft.screen != null) this.minecraft.screen.init(this.minecraft, graphics.guiWidth(), graphics.guiHeight());
        }
    }
}
