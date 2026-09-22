package dev.fallingcloud.slate.menu.client.title;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateAvatar;
import dev.fallingcloud.slate.core.widget.SlateCard;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The signed-in account: head, name, session state (Microsoft account = online session, anything else
 * = offline/dev session). Click opens skin customisation. Also the {@code slate_menu:account_card} element.
 */
public class AccountCard extends SlateCard {

    public static final int HEIGHT = 36;

    private final boolean online;

    public AccountCard(final int x, final int y, final int w, final int h, @Nullable final Screen parent) {
        super(x, y, w, h);
        final Minecraft mc = Minecraft.getInstance();
        final User user = mc.getUser();
        online = user.getType() == User.Type.MSA;
        final SlateAvatar avatar = add(new SlateAvatar(0, 0, 24, mc.getGameProfile()).status(online ? SlateAvatar.Status.ONLINE : SlateAvatar.Status.OFFLINE), 8, (h - 24) / 2);
        avatar.tip(List.of(Component.literal(user.getName()), Component.literal(user.getProfileId().toString())));
        onClick(() -> mc.setScreen(new SkinCustomizationScreen(parent, mc.options)));
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final Minecraft mc = Minecraft.getInstance();
        final int tx = x + 40;
        g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(mc.getUser().getName()), w - 60), tx, y + 8, p.text(), van);
        final Component status = Component.translatable(online ? "slate_menu.title.session_online" : "slate_menu.title.session_offline");
        g.drawString(SlateDraw.font(), SlateDraw.truncate(status, w - 60), tx, y + 19, van ? 0xFFC0C0C0 : p.textMuted(), van);
        Icons.draw(g, Icon.EDIT, x + w - 18, y + (h - 10) / 2, 10, van ? 0xFFA0A0A0 : p.textDim());
    }
}
