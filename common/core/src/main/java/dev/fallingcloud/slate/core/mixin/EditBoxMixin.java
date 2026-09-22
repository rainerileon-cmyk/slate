package dev.fallingcloud.slate.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.reskin.ReskinDraw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Reskin of vanilla text boxes: only the bordered frame sprite is replaced (dark skin); the text,
 * cursor, selection and scrolling are vanilla's. Unbordered boxes (chat, Slate's own field) never reach
 * the blit and are untouched.
 */
@Mixin(EditBox.class)
public abstract class EditBoxMixin extends AbstractWidget {

    protected EditBoxMixin(final int x, final int y, final int width, final int height, final Component message) {
        super(x, y, width, height, message);
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"), require = 0)
    private void slate$frame(final GuiGraphics g, final ResourceLocation sprite, final int x, final int y, final int w, final int h,
                             final Operation<Void> original) {
        if (!Reskin.dark()) { original.call(g, sprite, x, y, w, h); return; }
        final boolean focused = sprite.getPath().endsWith("highlighted");
        ReskinDraw.textField(g, x, y, w, h, focused, ReskinDraw.hover(this), this.active);
    }
}
