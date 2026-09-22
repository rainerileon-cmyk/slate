package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.popup.Popup;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import dev.fallingcloud.slate.multiplayer.client.Friend;
import dev.fallingcloud.slate.multiplayer.client.SocialClient;
import dev.fallingcloud.slate.multiplayer.social.GroupInfo;
import dev.fallingcloud.slate.multiplayer.voice.VoiceStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The player card: a large face, name/nickname, presence, mutual groups, and the actions (Message, Join,
 * Invite, more...). Opens near a point (clamped to the screen) as a non-modal popup.
 */
public final class PlayerCardPopup implements Popup {

    private static final int W = 200, FACE = 40;

    private final UUID uuid;
    private final String name;
    private final int x, y, h;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();

    private PlayerCardPopup(final UUID uuid, final String name, final int nearX, final int nearY) {
        this.uuid = uuid;
        final SocialClient sc = SocialClient.get();
        final Friend f = sc.friend(uuid);
        this.name = f != null ? f.name : name;
        if (f != null) {
            lines.add(UiUtil.presenceLine(f));
            if (!f.note.isEmpty()) lines.add("\"" + f.note + "\"");
            final List<GroupInfo> mutual = UiUtil.mutualGroups(uuid);
            if (!mutual.isEmpty()) lines.add("Groups: " + String.join(", ", mutual.stream().map(GroupInfo::name).toList()));
            if (f.sinceMs > 0) lines.add("Friends since " + UiUtil.relativeTime(f.sinceMs));
            if (VoiceStatus.available() && VoiceStatus.hasVoice(uuid)) lines.add("Voice volume " + Math.round(VoiceStatus.volume(uuid) * 100) + "%");
        } else if (sc.blocked().stream().anyMatch(b -> b.uuid().equals(uuid))) {
            lines.add("Blocked");
        } else {
            lines.add("Not a friend");
        }
        final int textH = lines.size() * 10;
        final int bodyH = Math.max(FACE + 8, 24 + textH);
        this.h = 10 + bodyH + 6 + 20 + 8;
        final Minecraft mc = Minecraft.getInstance();
        final int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int px = nearX < 0 ? (sw - W) / 2 : nearX, py = nearY < 0 ? (sh - h) / 2 : nearY;
        if (px + W > sw - 4) px = Math.max(4, sw - 4 - W);
        if (py + h > sh - 4) py = Math.max(4, sh - 4 - h);
        this.x = px;
        this.y = py;
        buildButtons(f);
    }

    private void buildButtons(@Nullable final Friend f) {
        final SocialClient sc = SocialClient.get();
        final int by = y + h - 28;
        int bx = x + 8;
        if (f != null) {
            widgets.add(new SlateButton(bx, by, 60, Component.translatable("slate_multiplayer.menu.message"), () -> {
                Popups.close(this);
                FriendsHubScreen.openThread(sc.dmThread(uuid).key);
            }).variant(SlateButton.Variant.PRIMARY).icon(Icon.CHAT));
            bx += 64;
            if (f.joinable()) {
                widgets.add(new SlateButton(bx, by, 52, Component.translatable("slate_multiplayer.menu.join"), () -> {
                    Popups.close(this);
                    CoreActions.joinServer(f.presence.server(), f.display());
                }).icon(Icon.SERVER));
                bx += 56;
            }
            widgets.add(new SlateButton(bx, by, x + W - 8 - bx, Component.translatable("slate_multiplayer.menu.more"), () -> {
                Popups.close(this);
                UiUtil.openFriendMenu(f, x + W - 100, by);
            }).variant(SlateButton.Variant.GHOST).icon(Icon.DOTS));
        } else if (sc.blocked().stream().anyMatch(b -> b.uuid().equals(uuid))) {
            widgets.add(new SlateButton(bx, by, W - 16, Component.translatable("slate_multiplayer.menu.unblock"), () -> {
                Popups.close(this);
                sc.unblock(uuid);
            }).icon(Icon.UNLOCK));
        } else {
            widgets.add(new SlateButton(bx, by, W - 16, Component.translatable("slate_multiplayer.requests.add"), () -> {
                Popups.close(this);
                sc.sendFriendRequest(uuid, name);
            }).variant(SlateButton.Variant.PRIMARY).icon(Icon.PLUS));
        }
    }

    /** Opens the card near (x, y); -1 centres it. */
    public static void open(final UUID uuid, final String name, final int nearX, final int nearY) {
        Popups.open(new PlayerCardPopup(uuid, name, nearX, nearY));
    }

    @Override
    public boolean contains(final double mx, final double my) {
        return mx >= x && mx < x + W && my >= y && my < y + h;
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        if (t.isVanilla()) {
            SlateDraw.vanillaPanel(g, x, y, W, h, false);
            SlateDraw.outline(g, x, y, W, h, 0xFF000000, 0);
            SlateDraw.outline(g, x + 1, y + 1, W - 2, h - 2, 0xFF8B8B8B, 0);
        } else {
            SlateDraw.shadow(g, x, y, W, h, 0.6f);
            SlateDraw.pixelRound(g, x, y, W, h, p.surface(), t.radius());
            SlateDraw.outline(g, x, y, W, h, p.borderStrong(), t.radius());
        }
        final Friend f = SocialClient.get().friend(uuid);
        UiUtil.drawHead(g, uuid, name, x + 10, y + 10, FACE, f == null ? null : f.presence, 1f);
        final int tx = x + 10 + FACE + 8;
        final String display = f == null ? name : f.display();
        g.drawString(SlateDraw.font(), Fonts.heading(Component.literal(display)), tx, y + 10, p.text(), t.isVanilla());
        int ly = y + 22;
        if (f != null && !f.nick.isEmpty()) {
            g.drawString(SlateDraw.font(), Component.literal(f.name), tx, ly, p.textMuted(), t.isVanilla());
            ly += 10;
        }
        for (final String line : lines) {
            g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(line), W - (tx - x) - 8), tx, ly, Colors.withAlpha(p.textMuted(), 0xFF), t.isVanilla());
            ly += 10;
        }
        if (f != null && f.online()) Icons.draw(g, Icon.ONLINE, x + W - 18, y + 8, 8, UiUtil.statusColor(f.presence));
        for (final AbstractWidget w : widgets) w.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        for (final AbstractWidget w : widgets) if (w.mouseClicked(mx, my, button)) return true;
        return true;
    }

    @Override
    public boolean mouseReleased(final double mx, final double my, final int button) {
        for (final AbstractWidget w : widgets) w.mouseReleased(mx, my, button);
        return true;
    }
}
