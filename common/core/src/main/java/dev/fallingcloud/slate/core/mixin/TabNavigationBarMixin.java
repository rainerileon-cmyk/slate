package dev.fallingcloud.slate.core.mixin;

import com.google.common.collect.ImmutableList;
import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.reskin.ReskinDraw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.LinearLayout;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reskin of the tab bar itself on the dark skin: a bg2 strip with a 1 px border instead of the header
 * separator texture, then the tab buttons (restyled by {@link TabButtonMixin}).
 */
@Mixin(TabNavigationBar.class)
public abstract class TabNavigationBarMixin {

    @Shadow @Final private LinearLayout layout;
    @Shadow private int width;
    @Shadow @Final private ImmutableList<TabButton> tabButtons;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void slate$render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        if (!Reskin.dark()) return;
        ci.cancel();
        ReskinDraw.tabBar(g, 0, 0, this.width, this.layout.getY() + this.layout.getHeight());
        for (final TabButton b : this.tabButtons) b.render(g, mouseX, mouseY, partialTick);
    }
}
