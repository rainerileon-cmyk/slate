package dev.fallingcloud.slate.menu.client.title;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateBadge;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateSpinner;
import dev.fallingcloud.slate.menu.client.Fmt;
import dev.fallingcloud.slate.menu.client.LastPlayed;
import dev.fallingcloud.slate.menu.client.servers.ServerActions;
import dev.fallingcloud.slate.menu.client.servers.ServerPinger;
import dev.fallingcloud.slate.menu.client.worlds.WorldActions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.storage.LevelSummary;
import org.jetbrains.annotations.Nullable;

/**
 * "Continue": the last world or server with a one-click resume. Resolves its target asynchronously,
 * pings a server target for a live player count, and offers "create a world" when nothing was played.
 * Also the {@code slate_menu:continue_card} layout element.
 */
public class ContinueCard extends SlateCard {

    public static final int HEIGHT = 66;

    @Nullable private final Screen parent;
    private boolean loading = true;
    @Nullable private LastPlayed.Target target;
    @Nullable private ServerData server;
    private final ServerPinger pinger = new ServerPinger();
    private final Anim appear = new Anim(0, 260, Ease.OUT_CUBIC);
    @Nullable private Textures.Loaded icon;
    private boolean iconRequested;
    @Nullable private byte[] faviconBytes;
    private final SlateButton play;
    private final SlateButton create;

    public ContinueCard(final int x, final int y, final int w, final int h, @Nullable final Screen parent) {
        super(x, y, w, h);
        this.parent = parent;
        // Icon-only Play in the bottom-right corner, inset 8 like the account card's edit icon ("Play" is its tooltip).
        play = add(new SlateIconButton(0, 0, 20, Icon.PLAY, Component.translatable("slate_menu.title.play"), this::play)
            .variant(SlateButton.Variant.PRIMARY), w - 28, h - 28);
        play.visible = false;
        // Empty state: the CTA sits top-right next to the caption so the two text lines below keep the full width.
        create = add(new SlateButton(0, 0, 104, 18, Component.translatable("slate_menu.title.create_world"), () -> WorldActions.createNew(parent))
            .variant(SlateButton.Variant.SECONDARY).icon(Icon.NEW_WORLD), w - 112, 5);
        create.visible = false;
        onClick(this::play);
        appear.snap(0);
        LastPlayed.resolve(this::accept);
    }

    private void accept(final Optional<LastPlayed.Target> resolved) {
        loading = false;
        target = resolved.orElse(null);
        appear.set(1);
        if (target == null) {
            create.visible = true;
            return;
        }
        play.visible = true;
        if (target.kind() == LastPlayed.Kind.SERVER) {
            server = ServerActions.find(target.id()).orElseGet(() -> new ServerData(target.name(), target.id(), ServerData.Type.OTHER));
            server.setState(ServerData.State.INITIAL);
            pinger.ping(server, () -> {});
        }
    }

    /** Ticks the ping connections; the owning screen calls this from its tick. */
    public void tick() { pinger.tick(); }

    public void close() { pinger.close(); }

    @Nullable public LastPlayed.Target target() { return target; }

    private void play() {
        if (target != null) LastPlayed.play(target, parent);
    }

    private void ensureIcon() {
        if (iconRequested || target == null) return;
        if (target.kind() == LastPlayed.Kind.WORLD) {
            iconRequested = true;
            final LevelSummary s = target.summary();
            final Path p = s == null ? null : s.getIcon();
            if (p != null && Files.isRegularFile(p)) Textures.load(p, l -> icon = l);
        } else if (server != null && server.getIconBytes() != null && server.getIconBytes() != faviconBytes) {
            faviconBytes = server.getIconBytes();
            icon = Textures.fromBytes(faviconBytes, "slate_menu:favicon:" + target.id() + ":" + faviconBytes.length).orElse(null);
        }
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final int muted = van ? 0xFFC0C0C0 : p.textMuted();
        pinger.tick();      // also ticks when used as a layout element (no owning screen tick)
        g.drawString(SlateDraw.font(), Component.translatable("slate_menu.title.continue"), x + 10, y + 7, van ? 0xFFA0A0A0 : p.textDim(), van);
        if (loading) {
            SlateSpinner.draw(g, x + 10, y + 24, 16, van ? 0xFFFFFFFF : p.accent());
            g.drawString(SlateDraw.font(), Component.translatable("slate_menu.title.looking"), x + 32, y + 28, muted, van);
            return;
        }
        final float a = appear.get();
        if (target == null) {
            g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.translatable("slate_menu.title.nothing_yet"), w - 124), x + 10, y + 26, Colors.scaleAlpha(p.text(), a), van);
            g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.translatable("slate_menu.title.nothing_yet_hint"), w - 20), x + 10, y + 40, Colors.scaleAlpha(muted, a), van);
            return;
        }
        ensureIcon();
        final int ix = x + 10, iy = y + 21, is = 32;
        if (icon != null) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.setColor(1, 1, 1, a);
            g.blit(icon.id(), ix, iy, is, is, 0, 0, icon.width(), icon.height(), icon.width(), icon.height());
            g.setColor(1, 1, 1, 1);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        } else {
            SlateDraw.pixelRound(g, ix, iy, is, is, Colors.scaleAlpha(van ? 0x80000000 : p.bg2(), a), t.radius());
            Icons.draw(g, target.kind() == LastPlayed.Kind.WORLD ? Icon.WORLD : Icon.SERVER, ix + 8, iy + 8, 16, Colors.scaleAlpha(muted, a));
        }
        SlateDraw.outline(g, ix, iy, is, is, Colors.scaleAlpha(van ? 0xFF000000 : p.border(), a), t.radius());
        // Every row level with the Play button stops short of it, so text and chips never run under it; the name
        // row sits above the button and gets the full width.
        final int tx = ix + is + 8, full = x + w - 10, lh = SlateDraw.lineHeight();
        final FormattedCharSequence name = SlateDraw.truncate(Component.literal(target.name() == null || target.name().isBlank() ? target.id() : target.name()), rowRight(y + 22, lh, full) - tx);
        g.drawString(SlateDraw.font(), name, tx, y + 22, Colors.scaleAlpha(p.text(), a), van);
        g.drawString(SlateDraw.font(), SlateDraw.truncate(subtitle(), rowRight(y + 34, lh, full) - tx), tx, y + 34, Colors.scaleAlpha(muted, a), van);
        // Third line: status dot + detail (server) or mode chips (world)
        final int right = rowRight(y + 46, 10, full);
        if (target.kind() == LastPlayed.Kind.SERVER && server != null) {
            final int dot;
            final Component detail;
            switch (server.state()) {
                case SUCCESSFUL, INCOMPATIBLE -> {
                    dot = p.success();
                    final String players = ServerPinger.players(server);
                    detail = players.isEmpty() ? Component.translatable("slate_menu.servers.online")
                        : Component.translatable("slate_menu.servers.online_count", players, server.ping);
                }
                case UNREACHABLE -> { dot = p.danger(); detail = Component.translatable("slate_menu.servers.offline"); }
                default -> { dot = p.warning(); detail = Component.translatable("slate_menu.servers.pinging"); }
            }
            g.fill(tx, y + 48, tx + 4, y + 52, Colors.scaleAlpha(dot, a));
            g.drawString(SlateDraw.font(), SlateDraw.truncate(detail, right - tx - 6), tx + 6, y + 46, Colors.scaleAlpha(muted, a), van);
        } else if (target.summary() != null) {
            final LevelSummary s = target.summary();
            int cx = chip(g, s.getGameMode().getShortDisplayName(), tx, y + 46, right, Colors.scaleAlpha(p.accent(), a));
            if (s.isHardcore()) cx = chip(g, Component.translatable("slate_menu.worlds.hardcore"), cx, y + 46, right, Colors.scaleAlpha(p.danger(), a));
            if (s.hasCommands()) chip(g, Component.translatable("slate_menu.worlds.cheats"), cx, y + 46, right, Colors.scaleAlpha(p.warning(), a));
        }
    }

    /** Right edge for a row at {@code rowY}, {@code rowH} tall: short of the Play button when level with it, else {@code full}. */
    private int rowRight(final int rowY, final int rowH, final int full) {
        final boolean level = play.visible && rowY + rowH > play.getY() && rowY < play.getY() + play.getHeight();
        return level ? play.getX() - 4 : full;
    }

    /** A mode chip if it fits before {@code right}; returns the next x, or {@code right} once one did not fit so later chips skip too. */
    private static int chip(final GuiGraphics g, final Component text, final int x, final int y, final int right, final int color) {
        if (x + SlateDraw.width(text) + 8 > right) return right;
        return x + SlateBadge.draw(g, text, x, y, color) + 4;
    }

    private Component subtitle() {
        if (target == null) return Component.empty();
        final Component kind = Component.translatable(target.kind() == LastPlayed.Kind.WORLD ? "slate_menu.title.singleplayer" : "slate_menu.title.multiplayer");
        return Component.empty().append(kind).append(" · ").append(Component.translatable("slate_menu.title.last_played", Fmt.ago(target.at())));
    }
}
