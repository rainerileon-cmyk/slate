package dev.fallingcloud.slate.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.fallingcloud.slate.core.layout.LayoutBackground;
import dev.fallingcloud.slate.core.layout.editor.EditorOverlay;
import dev.fallingcloud.slate.core.layout.editor.LayoutEditor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Layout-system hooks on every screen (cosmetic, {@code require = 0}):
 * <ul>
 *   <li>the layout's background (colour / image / panorama / none) replaces the vanilla background.
 *       The {@code renderBackground} call inside {@code Screen.render} is wrapped so overrides that never
 *       call super ({@code SlateScreen}) are covered, and {@code renderBackground} itself is hooked for
 *       screens that draw their background by hand; {@link LayoutBackground} draws at most once per frame;</li>
 *   <li>while the layout editor is open the screen is rendered with the mouse parked off-screen, so
 *       nothing underneath hovers, tooltips or animates while the editor owns the pointer.</li>
 * </ul>
 * Priority 1500 so this runs before same-target reskin hooks: a background the user chose in the
 * editor wins over the generic restyle.
 */
@Mixin(value = Screen.class, priority = 1500)
public abstract class ScreenLayoutMixin {

    @Shadow @Final protected static PanoramaRenderer PANORAMA;

    @Inject(method = "renderWithTooltip", at = @At("HEAD"), require = 0)
    private void slate$beginFrame(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        LayoutBackground.beginFrame((Screen) (Object) this);
    }

    @ModifyArgs(method = "renderWithTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"), require = 0)
    private void slate$editorMouse(final Args args) {
        final EditorOverlay ed = LayoutEditor.overlay();
        if (ed != null && ed.screen() == (Screen) (Object) this && ed.hidesScreenMouse()) {
            args.set(1, -9999);
            args.set(2, -9999);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"), require = 0)
    private void slate$wrapBackground(final Screen screen, final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final Operation<Void> original) {
        if (!LayoutBackground.render(screen, g, partialTick, PANORAMA)) original.call(screen, g, mouseX, mouseY, partialTick);
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void slate$directBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        if (LayoutBackground.render((Screen) (Object) this, g, partialTick, PANORAMA)) ci.cancel();
    }
}
