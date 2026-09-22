package dev.fallingcloud.slate.core.mixin;

import dev.fallingcloud.slate.core.client.SlateClient;
import dev.fallingcloud.slate.core.layout.LayoutApplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Init-complete and render-complete hooks for every screen, plus the accessor Core's layout applier
 * needs to add/remove widgets on foreign screens.
 *
 * <p>{@code init(Minecraft,int,int)} is final and every open/resize goes through it; {@code rebuildWidgets}
 * is hooked too for screens that rebuild themselves later, with a flag so a rebuild triggered from
 * inside init does not fire twice. {@code renderWithTooltip} is final and is what the game renderer
 * calls, so overlays drawn from its tail sit above any screen's own rendering.</p>
 */
@Mixin(Screen.class)
public abstract class ScreenMixin implements LayoutApplier.ScreenAccess {

    @Shadow protected abstract <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget);
    @Shadow protected abstract void removeWidget(GuiEventListener listener);

    @Unique private boolean slate$inInit;

    @Override
    public <T extends GuiEventListener & Renderable & NarratableEntry> T slate$add(final T widget) {
        return this.addRenderableWidget(widget);
    }

    @Override
    public void slate$remove(final GuiEventListener widget) {
        this.removeWidget(widget);
    }

    @Inject(method = "init(Lnet/minecraft/client/Minecraft;II)V", at = @At("HEAD"))
    private void slate$initHead(final Minecraft mc, final int w, final int h, final CallbackInfo ci) {
        slate$inInit = true;
    }

    @Inject(method = "init(Lnet/minecraft/client/Minecraft;II)V", at = @At("TAIL"))
    private void slate$initTail(final Minecraft mc, final int w, final int h, final CallbackInfo ci) {
        slate$inInit = false;
        SlateClient.onScreenInit((Screen) (Object) this);
    }

    @Inject(method = "rebuildWidgets", at = @At("TAIL"))
    private void slate$rebuilt(final CallbackInfo ci) {
        if (!slate$inInit) SlateClient.onScreenInit((Screen) (Object) this);
    }

    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void slate$rendered(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        SlateClient.onScreenRendered((Screen) (Object) this, g, mouseX, mouseY, partialTick);
    }
}
