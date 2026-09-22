package dev.fallingcloud.slate.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.reskin.ReskinDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reskin of vanilla lists (world/server lists, options lists, key binds, packs, language...): dark list
 * background, 1 px separators, a pixel-rounded selection fill instead of the white outline, and a thin
 * scrollbar (the two scroller sprite blits in {@code renderWidget} are wrapped). Rows themselves are
 * drawn by their entries and are untouched.
 */
@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin extends AbstractContainerWidget {

    @Shadow @Final protected Minecraft minecraft;

    protected AbstractSelectionListMixin(final int x, final int y, final int width, final int height, final Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderListBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void slate$listBackground(final GuiGraphics g, final CallbackInfo ci) {
        if (!Reskin.dark()) return;
        ci.cancel();
        ReskinDraw.listBackground(g, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.minecraft.level != null);
    }

    @Inject(method = "renderListSeparators", at = @At("HEAD"), cancellable = true, require = 0)
    private void slate$listSeparators(final GuiGraphics g, final CallbackInfo ci) {
        if (!Reskin.dark()) return;
        ci.cancel();
        ReskinDraw.listSeparators(g, this.getX(), this.getY(), this.getWidth(), this.getBottom(), this.minecraft.level != null);
    }

    @Inject(method = "renderSelection", at = @At("HEAD"), cancellable = true, require = 0)
    private void slate$selection(final GuiGraphics g, final int top, final int width, final int height, final int outerColor,
                                 final int innerColor, final CallbackInfo ci) {
        if (!Reskin.dark()) return;
        ci.cancel();
        final int x = this.getX() + (this.getWidth() - width) / 2;
        // Vanilla passes white for a focused list, grey otherwise.
        ReskinDraw.selection(g, x, top - 2, width, height + 4, (outerColor & 0xFFFFFF) == 0xFFFFFF);
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"), require = 0)
    private void slate$scroller(final GuiGraphics g, final ResourceLocation sprite, final int x, final int y, final int w, final int h,
                                final Operation<Void> original) {
        if (!Reskin.dark()) { original.call(g, sprite, x, y, w, h); return; }
        final String path = sprite.getPath();
        if (path.equals("widget/scroller_background")) ReskinDraw.scrollTrack(g, x, y, w, h);
        else if (path.equals("widget/scroller")) ReskinDraw.scrollThumb(g, x, y, w, h);
        else original.call(g, sprite, x, y, w, h);
    }
}
