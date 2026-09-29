package dev.fallingcloud.slate.menu.client.servers;

import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateBadge;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateTooltips;
import dev.fallingcloud.slate.menu.api.SlateMenuApi;
import dev.fallingcloud.slate.menu.client.Fmt;
import dev.fallingcloud.slate.menu.client.play.model.ServerActions;
import dev.fallingcloud.slate.menu.client.play.model.ServerMeta;
import dev.fallingcloud.slate.menu.client.play.model.ServerPinger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * One server row: favicon, name + chips (group, friends here, saved/LAN), formatted MOTD, and on the
 * right the player count, latency bars and version compatibility. Pings itself lazily the first time
 * it is drawn, like vanilla's list entries. Hovering the player count shows the sample player names.
 */
public class ServerCard extends SlateCard {

    public static final int HEIGHT = 44;
    private static final int RIGHT_W = 76;

    public enum Kind { SAVED, RECENT, LAN, COMMUNITY }

    private final ServerData data;
    private final Kind kind;
    private final SlateServersScreen screen;
    @Nullable private final SlateIconButton star;
    @Nullable private Textures.Loaded favicon;
    @Nullable private byte[] faviconBytes;
    @Nullable private final String description;
    /** RECENT rows: the address also exists in servers.dat (computed once by the screen, not per frame). */
    private boolean savedHint;

    public ServerCard savedHint(final boolean saved) { this.savedHint = saved; return this; }

    public ServerCard(final int x, final int y, final int w, final ServerData data, final Kind kind, final SlateServersScreen screen, @Nullable final String description) {
        super(x, y, w, HEIGHT);
        this.data = data;
        this.kind = kind;
        this.screen = screen;
        this.description = description;
        if (kind == Kind.SAVED) {
            final boolean fav = ServerMeta.isFavorite(data.ip);
            star = add(new SlateIconButton(0, 0, 18, fav ? Icon.STAR_FILLED : Icon.STAR,
                Component.translatable(fav ? "slate_menu.unfavorite" : "slate_menu.favorite"), () -> screen.toggleFavorite(data)), w - RIGHT_W - 20, 4);
            star.toggled(fav);
        } else {
            star = null;
        }
        onClick(() -> screen.clicked(this));
        onRightClick(() -> screen.contextMenu(this));
    }

    public ServerData data() { return data; }

    public Kind kind() { return kind; }

    private void ensureFavicon() {
        final byte[] bytes = data.getIconBytes();
        if (bytes == null || bytes == faviconBytes) return;
        faviconBytes = bytes;
        favicon = Textures.fromBytes(bytes, "slate_menu:favicon:" + ServerActions.normalize(data.ip) + ":" + bytes.length).orElse(null);
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        if (kind != Kind.LAN) screen.pinger().ping(data, screen::onPingChanged);
        ensureFavicon();

        // Favicon
        final int ix = x + 6, iy = y + 6, is = 32;
        if (favicon != null) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.blit(favicon.id(), ix, iy, is, is, 0, 0, favicon.width(), favicon.height(), favicon.width(), favicon.height());
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        } else {
            SlateDraw.pixelRound(g, ix, iy, is, is, van ? 0x80000000 : p.bg2(), t.radius());
            Icons.draw(g, kind == Kind.LAN ? Icon.LAN : Icon.SERVER, ix + 8, iy + 8, 16, van ? 0xFFA0A0A0 : p.textDim());
        }
        SlateDraw.outline(g, ix, iy, is, is, van ? 0xFF000000 : p.border(), t.radius());

        // Name + chips
        final int tx = ix + is + 8;
        final int rightX = x + w - RIGHT_W;
        final int textW = rightX - tx - (star != null ? 22 : 4);
        final Component name = Component.literal(data.name == null || data.name.isBlank() ? data.ip : data.name);
        final int nameW = Math.min(SlateDraw.width(name), textW);
        g.drawString(SlateDraw.font(), SlateDraw.truncate(name, textW), tx, y + 6, p.text(), van);
        int cx = tx + nameW + 6;
        final int cy = y + 5;
        final int friends = SlateMenuApi.friendsOn(data.ip);
        if (friends > 0 && cx + 40 < rightX) cx += SlateBadge.draw(g, Component.translatable("slate_menu.servers.friends_here", friends), cx, cy, p.success()) + 3;
        if (kind == Kind.SAVED) {
            final String group = ServerMeta.group(data.ip);
            if (!group.isEmpty() && cx + SlateDraw.width(group) + 8 < rightX) cx += SlateBadge.draw(g, Component.literal(group), cx, cy, van ? 0xFF8B8B8B : p.borderStrong()) + 3;
        } else if (kind == Kind.LAN) {
            if (cx + 30 < rightX) cx += SlateBadge.draw(g, Component.literal("LAN"), cx, cy, p.accent()) + 3;
        } else if (kind == Kind.RECENT && savedHint && cx + 40 < rightX) {
            cx += SlateBadge.draw(g, Component.translatable("slate_menu.servers.saved"), cx, cy, van ? 0xFF8B8B8B : p.borderStrong()) + 3;
        }

        // MOTD (two lines, keeps vanilla formatting codes)
        final int muted = van ? 0xFFC0C0C0 : p.textMuted();
        final Component motd = data.motd == null ? Component.empty() : data.motd;
        final List<FormattedCharSequence> lines = SlateDraw.font().split(motd, textW);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawString(SlateDraw.font(), lines.get(i), tx, y + 18 + i * 10, muted, van);
        }
        if (lines.isEmpty() && description != null && !description.isEmpty()) {
            g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(description), textW), tx, y + 18, muted, van);
        }
        if (kind == Kind.RECENT || kind == Kind.COMMUNITY || kind == Kind.LAN) {
            g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(data.ip), textW), tx, y + 18 + Math.min(2, lines.size()) * 10, van ? 0xFFA0A0A0 : p.textDim(), van);
        }

        // Right column: players, ping bars, compatibility
        drawStatus(g, data, rightX, y + 6, RIGHT_W - 6, mouseX, mouseY, kind == Kind.LAN);
        if (description != null && !description.isEmpty() && mouseX >= x && mouseX < rightX && mouseY >= y && mouseY < y + h && g.containsPointInScissor(mouseX, mouseY)) {
            SlateTooltips.request(List.of(Component.literal(description)), null);
        }
    }

    /** Draws the status block (players / bars / version) into (x, y, w, ~32); shared with the quick-connect line. */
    public static void drawStatus(final GuiGraphics g, final ServerData d, final int x, final int y, final int w, final int mouseX, final int mouseY, final boolean lan) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final int right = x + w;
        if (lan) {
            final Component lanText = Component.translatable("lanServer.title");
            g.drawString(SlateDraw.font(), lanText, right - SlateDraw.width(lanText), y, van ? 0xFFC0C0C0 : p.textMuted(), van);
            return;
        }
        switch (d.state()) {
            case INITIAL, PINGING -> {
                final int lit = (int) ((Clock.nowMs() / 120) % 5);
                drawBars(g, right - 12, y + 1, lit + 1, Colors.withAlpha(van ? 0xFFFFFFFF : p.textMuted(), 0xA0), 5);
                final Component txt = Component.translatable("multiplayer.status.pinging");
                g.drawString(SlateDraw.font(), SlateDraw.truncate(txt, w - 16), right - 16 - Math.min(SlateDraw.width(txt), w - 16), y, van ? 0xFFA0A0A0 : p.textDim(), van);
            }
            case UNREACHABLE -> {
                Icons.draw(g, Icon.CLOSE, right - 11, y, 10, p.danger());
                final Component txt = Component.translatable("slate_menu.servers.offline");
                g.drawString(SlateDraw.font(), txt, right - 14 - SlateDraw.width(txt), y, p.danger(), van);
            }
            default -> {
                final int bars = Fmt.pingBars(d.ping);
                final int barColor = bars >= 4 ? p.success() : bars >= 2 ? p.warning() : p.danger();
                drawBars(g, right - 12, y + 1, bars, barColor, 5);
                final String players = ServerPinger.players(d);
                final int pw = SlateDraw.width(players);
                g.drawString(SlateDraw.font(), players, right - 16 - pw, y, van ? 0xFFFFFFFF : p.text(), van);
                final String ms = d.ping + " ms";
                g.drawString(SlateDraw.font(), ms, right - SlateDraw.width(ms), y + 11, van ? 0xFFA0A0A0 : p.textDim(), van);
                if (d.state() == ServerData.State.INCOMPATIBLE) {
                    final Component v = d.version == null ? Component.literal("?") : d.version;
                    final int vw = SlateDraw.width(v) + 8;
                    SlateBadge.draw(g, SlateDraw.width(v) > w - 8 ? Component.literal("!") : v, right - Math.min(vw, w), y + 22, p.danger());
                } else if (SharedConstants.getCurrentVersion().getProtocolVersion() == d.protocol) {
                    Icons.draw(g, Icon.CHECK, right - 10, y + 23, 8, Colors.withAlpha(p.success(), 0xC0));
                }
                // Player sample tooltip over the count / bars
                if (mouseX >= right - 16 - pw && mouseX <= right && mouseY >= y - 2 && mouseY < y + 20 && g.containsPointInScissor(mouseX, mouseY)) {
                    final List<Component> tip = new ArrayList<>();
                    tip.add(Component.translatable("slate_menu.servers.players_online", players));
                    if (d.playerList != null) tip.addAll(d.playerList);
                    if (d.version != null) tip.add(Component.translatable("slate_menu.servers.version", d.version));
                    SlateTooltips.request(tip, null);
                }
            }
        }
    }

    /** Five 2-px bars of rising height, {@code lit} of them in {@code color}. */
    public static void drawBars(final GuiGraphics g, final int x, final int y, final int lit, final int color, final int total) {
        final Palette p = Theme.current().palette();
        for (int i = 0; i < total; i++) {
            final int bh = 2 + i * 2;
            final int bx = x + i * 3;
            final int by = y + 10 - bh;
            g.fill(bx, by, bx + 2, by + bh, i < lit ? color : Colors.withAlpha(Theme.current().isVanilla() ? 0xFF808080 : p.borderStrong(), 0x90));
        }
    }

    public void refreshStar() {
        if (star == null) return;
        final boolean fav = ServerMeta.isFavorite(data.ip);
        star.setIcon(fav ? Icon.STAR_FILLED : Icon.STAR);
        star.toggled(fav);
        star.tip(Component.translatable(fav ? "slate_menu.unfavorite" : "slate_menu.favorite"));
    }
}
