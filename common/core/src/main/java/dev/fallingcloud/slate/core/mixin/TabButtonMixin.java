package dev.fallingcloud.slate.core.mixin;

import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.reskin.ReskinDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reskin of the tabs in a {@code TabNavigationBar} (Create World, Realms...) on the dark skin: the
 * selected tab is a raised surface merging into the page, the others are flat labels with a hover
 * fill. Vanilla's (scrolling) label rendering is reused.
 */
@Mixin(TabButton.class)
public abstract class TabButtonMixin extends AbstractWidget {

    @Shadow public abstract boolean isSelected();
    @Shadow public abstract void renderString(GuiGraphics g, Font font, int color);

    protected TabButtonMixin(final int x, final int y, final int width, final int height, final Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
    private void slate$render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        if (!Reskin.dark()) return;
        ci.cancel();
        final Palette p = Theme.current().palette();
        final boolean selected = isSelected();
        final float hover = ReskinDraw.hover(this);
        ReskinDraw.tab(g, this.getX(), this.getY(), this.getWidth(), this.getHeight(), selected, selected ? 0f : hover, this.isFocused());
        final int color = !this.active ? p.textDim() : selected ? p.text() : Colors.lerp(p.textMuted(), p.text(), hover);
        renderString(g, Minecraft.getInstance().font, color | 0xFF000000);
    }
}
