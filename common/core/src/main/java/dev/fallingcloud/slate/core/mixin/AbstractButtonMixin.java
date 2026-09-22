package dev.fallingcloud.slate.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.reskin.ReskinDraw;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reskin of every vanilla button that renders through {@code AbstractButton.renderWidget} (Button,
 * CycleButton, SpriteIconButton's face, ...; ImageButton/PlainTextButton/Checkbox override it and are
 * not affected). The sprite blit is wrapped: on the dark skin a Slate face replaces it, on the vanilla
 * skin the highlighted sprite cross-fades in. A press nudges the whole button (face and text) 1 px down
 * by translating the pose around the rest of the method. Only the text colour argument changes on the
 * dark skin, so vanilla keeps drawing the (scrolling) label.
 */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin extends AbstractWidget {

    @Unique private boolean slate$pushed;

    protected AbstractButtonMixin(final int x, final int y, final int width, final int height, final Component message) {
        super(x, y, width, height, message);
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"), require = 0)
    private void slate$face(final GuiGraphics g, final ResourceLocation sprite, final int x, final int y, final int w, final int h,
                            final Operation<Void> original) {
        if (!Reskin.active()) { original.call(g, sprite, x, y, w, h); return; }
        final float hover = ReskinDraw.hover(this), press = ReskinDraw.press(this);
        final int dy = Math.round(press);
        if (dy != 0 && !slate$pushed) {
            g.pose().pushPose();
            g.pose().translate(0, dy, 0);
            slate$pushed = true;
        }
        if (Theme.current().isVanilla()) {
            // Keep vanilla's sprites; only cross-fade the highlight instead of snapping.
            if (!this.active || !(SlateDraw.BUTTON.equals(sprite) || SlateDraw.BUTTON_HIGHLIGHTED.equals(sprite))) {
                original.call(g, sprite, x, y, w, h);
                return;
            }
            SlateDraw.vanillaFace(g, SlateDraw.BUTTON, SlateDraw.BUTTON_HIGHLIGHTED, null, x, y, w, h, hover, true, this.alpha);
            return;
        }
        ReskinDraw.button(g, this, x, y, w, h, hover, press);
    }

    @Inject(method = "renderWidget", at = @At("TAIL"), require = 0)
    private void slate$tail(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        if (slate$pushed) {
            slate$pushed = false;
            g.pose().popPose();
        }
    }

    @ModifyArg(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/components/AbstractButton;renderString(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;I)V"),
        index = 2, require = 0)
    private int slate$textColor(final int color) {
        if (!Reskin.dark()) return color;
        final int rgb = color & 0xFFFFFF, a = color & 0xFF000000;
        if (rgb == 0xFFFFFF) return a | (Theme.current().palette().text() & 0xFFFFFF);
        if (rgb == 0xA0A0A0) return a | (Theme.current().palette().textDim() & 0xFFFFFF);
        return color;       // a mod set its own colour (NeoForge packedFGColor): keep it
    }
}
