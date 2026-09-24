package dev.fallingcloud.slate.core.mixin;

import dev.fallingcloud.slate.core.screen.reskin.ContainerReskin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opens and closes the two windows {@link ContainerReskin} works in: the screen's {@code renderBg} (its background
 * blit becomes Slate's panel) and its {@code renderLabels} (dark titles become light). A subclass that overrides
 * {@code render} or {@code renderBackground} without calling super simply never opens them.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {

    private static final String RENDER_BG = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V";
    private static final String RENDER_LABELS = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V";

    @Inject(method = "renderBackground", at = @At(value = "INVOKE", target = RENDER_BG), require = 0)
    private void slate$beforeBg(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        ContainerReskin.beginBackground();
    }

    @Inject(method = "renderBackground", at = @At(value = "INVOKE", target = RENDER_BG, shift = At.Shift.AFTER), require = 0)
    private void slate$afterBg(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        ContainerReskin.endBackground(g);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = RENDER_LABELS), require = 0)
    private void slate$beforeLabels(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        ContainerReskin.beginLabels();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = RENDER_LABELS, shift = At.Shift.AFTER), require = 0)
    private void slate$afterLabels(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        ContainerReskin.endLabels();
    }
}
