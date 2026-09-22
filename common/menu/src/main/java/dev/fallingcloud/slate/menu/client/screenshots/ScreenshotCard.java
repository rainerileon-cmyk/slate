package dev.fallingcloud.slate.menu.client.screenshots;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateSpinner;
import dev.fallingcloud.slate.menu.client.Fmt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** A thumbnail tile: letterboxed preview (loaded async), file name and date underneath. */
public class ScreenshotCard extends SlateCard {

    public static final int CAPTION_H = 26;
    private static final int THUMB_PX = 256;

    private final Screenshots.Shot shot;
    @Nullable private Textures.Loaded thumb;
    private boolean requested;

    public ScreenshotCard(final int x, final int y, final int w, final int h, final Screenshots.Shot shot, final SlateScreenshotsScreen screen) {
        super(x, y, w, h);
        this.shot = shot;
        onClick(() -> screen.clicked(this));
        onRightClick(() -> screen.contextMenu(this));
    }

    public Screenshots.Shot shot() { return shot; }

    @Override
    protected void renderContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        if (!requested) {
            requested = true;
            Textures.loadThumbnail(shot.path(), THUMB_PX, l -> thumb = l);
        }
        final int tx = x + 4, ty = y + 4, tw = w - 8, th = h - 8 - CAPTION_H;
        SlateDraw.pixelRound(g, tx, ty, tw, th, van ? 0xFF000000 : p.bg(), Math.max(0, t.radius() - 1));
        if (thumb != null) {
            final float s = Math.min((float) tw / thumb.width(), (float) th / thumb.height());
            final int dw = Math.max(1, Math.round(thumb.width() * s)), dh = Math.max(1, Math.round(thumb.height() * s));
            final int dx = tx + (tw - dw) / 2, dy = ty + (th - dh) / 2;
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.blit(thumb.id(), dx, dy, dw, dh, 0, 0, thumb.width(), thumb.height(), thumb.width(), thumb.height());
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        } else {
            SlateSpinner.draw(g, tx + tw / 2 - 8, ty + th / 2 - 8, 16, van ? 0xFFFFFFFF : p.textDim());
        }
        if (isSelected()) SlateDraw.outline(g, tx, ty, tw, th, Colors.withAlpha(p.accent(), 0xC0), Math.max(0, t.radius() - 1));
        g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(shot.name()), w - 12), x + 6, y + h - CAPTION_H + 4, p.text(), van);
        g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(Fmt.date(shot.modified()) + " · " + Fmt.bytes(shot.size())), w - 24), x + 6, y + h - 11, van ? 0xFFA0A0A0 : p.textDim(), van);
        Icons.draw(g, Icon.IMAGE, x + w - 14, y + h - 12, 8, van ? 0xFF808080 : p.textDim());
    }
}
