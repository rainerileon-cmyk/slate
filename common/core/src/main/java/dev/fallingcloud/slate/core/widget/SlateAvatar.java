package dev.fallingcloud.slate.core.widget;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A player's face with an optional status dot. The skin resolves lazily through the SkinManager (default
 * skin until fetched). Static {@link #draw} for use inside list rows and chat.
 */
public class SlateAvatar extends SlateWidget {

    public enum Status { NONE, ONLINE, AWAY, OFFLINE, BUSY }

    private final Supplier<PlayerSkin> skin;
    private Status status = Status.NONE;
    private Runnable onClick;

    public SlateAvatar(final int x, final int y, final int size, final GameProfile profile) {
        super(x, y, size, size, Component.literal(profile.getName() == null ? "" : profile.getName()));
        this.skin = Minecraft.getInstance().getSkinManager().lookupInsecure(profile);
        this.active = false;
    }

    public SlateAvatar(final int x, final int y, final int size, final UUID uuid, final String name) {
        this(x, y, size, new GameProfile(uuid, name == null || name.isEmpty() ? "?" : name));
    }

    public SlateAvatar status(final Status s) { this.status = s; return this; }

    public SlateAvatar onClick(final Runnable r) { this.onClick = r; this.active = r != null; return this; }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        if (onClick != null) onClick.run();
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, skin.get(), getX(), getY(), getWidth(), status, effectiveAlpha());
        if (onClick != null && hover() > 0.01f) SlateDraw.outline(g, getX() - 1, getY() - 1, getWidth() + 2, getWidth() + 2, Colors.scaleAlpha(Theme.current().accent(), hover()), 1);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        renderDark(g, mouseX, mouseY, partialTick);
    }

    public static ResourceLocation skinFor(final UUID uuid, @Nullable final String name) {
        try {
            final PlayerSkin s = Minecraft.getInstance().getSkinManager().getInsecureSkin(new GameProfile(uuid, name == null || name.isEmpty() ? "?" : name));
            return s.texture();
        } catch (final Exception e) {
            return DefaultPlayerSkin.get(uuid).texture();
        }
    }

    public static void draw(final GuiGraphics g, final PlayerSkin skin, final int x, final int y, final int size, final Status status, final float alpha) {
        draw(g, skin == null ? DefaultPlayerSkin.getDefaultTexture() : skin.texture(), x, y, size, status, alpha);
    }

    public static void draw(final GuiGraphics g, final ResourceLocation skinTexture, final int x, final int y, final int size, final Status status, final float alpha) {
        RenderSystem.enableBlend();
        g.setColor(1, 1, 1, alpha);
        PlayerFaceRenderer.draw(g, skinTexture, x, y, size);
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        if (status != Status.NONE) {
            final int d = Math.max(3, size / 4);
            final int c = switch (status) {
                case ONLINE -> Theme.current().palette().success();
                case AWAY -> Theme.current().palette().warning();
                case BUSY -> Theme.current().palette().danger();
                default -> Theme.current().palette().textDim();
            };
            final int dx = x + size - d, dy = y + size - d;
            g.fill(dx - 1, dy - 1, dx + d + 1, dy + d + 1, Colors.scaleAlpha(Theme.current().bg(), alpha));
            g.fill(dx, dy, dx + d, dy + d, Colors.scaleAlpha(c, alpha));
        }
    }
}
