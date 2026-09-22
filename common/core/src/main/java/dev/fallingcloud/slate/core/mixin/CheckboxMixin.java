package dev.fallingcloud.slate.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.reskin.ReskinDraw;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Reskin of vanilla checkboxes: the box sprite is wrapped - Slate box + check on the dark skin, the
 * highlighted sprite cross-fading in (on hover as well as focus) on the vanilla skin. The label stays
 * vanilla's.
 */
@Mixin(Checkbox.class)
public abstract class CheckboxMixin extends AbstractWidget {

    @Shadow private boolean selected;

    protected CheckboxMixin(final int x, final int y, final int width, final int height, final Component message) {
        super(x, y, width, height, message);
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"), require = 0)
    private void slate$box(final GuiGraphics g, final ResourceLocation sprite, final int x, final int y, final int w, final int h,
                           final Operation<Void> original) {
        if (!Reskin.active()) { original.call(g, sprite, x, y, w, h); return; }
        final float hover = ReskinDraw.hover(this);
        if (Theme.current().isVanilla()) {
            SlateDraw.vanillaCheckbox(g, x, y, w, this.selected, this.active ? hover : 0f, this.alpha);
            return;
        }
        ReskinDraw.checkbox(g, x, y, w, this.selected, hover, this.isFocused(), this.active);
    }
}
