package dev.fallingcloud.slate.multiplayer.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.popup.Popup;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A modal image viewer for chat attachments: the picture fit to the screen, click or Esc to close. */
final class ImageViewPopup implements Popup {

    private final Textures.Loaded texture;
    private final String caption;

    private ImageViewPopup(final Textures.Loaded texture, final String caption) {
        this.texture = texture;
        this.caption = caption;
    }

    static void open(final Textures.Loaded texture, final String caption) {
        Popups.open(new ImageViewPopup(texture, caption));
    }

    @Override public boolean isModal() { return true; }

    @Override public boolean contains(final double mouseX, final double mouseY) { return true; }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Minecraft mc = Minecraft.getInstance();
        final int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        final int maxW = sw - 40, maxH = sh - 50;
        final float s = Math.min(1f, Math.min((float) maxW / texture.width(), (float) maxH / texture.height()));
        final int w = Math.max(1, Math.round(texture.width() * s)), h = Math.max(1, Math.round(texture.height() * s));
        final int x = (sw - w) / 2, y = (sh - h) / 2 - 6;
        SlateDraw.outline(g, x - 2, y - 2, w + 4, h + 4, Theme.current().palette().borderStrong(), 0);
        RenderSystem.enableBlend();
        g.blit(texture.id(), x, y, w, h, 0, 0, texture.width(), texture.height(), texture.width(), texture.height());
        RenderSystem.disableBlend();
        SlateDraw.textCentered(g, Component.literal(caption + "  ·  " + texture.width() + "x" + texture.height()), sw / 2, y + h + 8, Theme.current().muted());
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        Popups.close(this);
        return true;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (keyCode == 256 || keyCode == 257) { Popups.close(this); return true; }
        return false;
    }
}
