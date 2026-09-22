package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateSeparator;
import dev.fallingcloud.slate.core.widget.SlateTextField;
import dev.fallingcloud.slate.multiplayer.client.SocialClient;
import dev.fallingcloud.slate.multiplayer.client.stream.ScreenShare;
import dev.fallingcloud.slate.multiplayer.client.stream.StreamViewer;
import dev.fallingcloud.slate.multiplayer.social.StreamInfo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Your own screen share (start/stop, live stats) and the friends' streams you can watch. */
final class StreamsPage extends FriendsHubScreen.HubPage {

    private SlateScrollPanel panel;
    private SlateTextField title;
    private int panelW;

    StreamsPage(final FriendsHubScreen screen) {
        super(screen, "streams", UiUtil.t("page.streams"), Icon.STREAM);
    }

    @Override
    public int badge() { return (int) SocialClient.get().streams().stream().filter(s -> !s.owner().uuid().equals(SocialClient.get().selfUuid())).count(); }

    @Override
    public void build(final SidebarScreen s, final Rect area) {
        panelW = area.w();
        panel = s.addPageWidget(new SlateScrollPanel(area.x(), area.y(), area.w(), area.h()));
        fill();
    }

    @Override
    void onModelChanged() { fill(); }

    private void fill() {
        final String keepTitle = title == null ? "" : title.getValue();
        panel.clear();
        final SocialClient sc = SocialClient.get();
        final int w = panelW - 8;
        int y = 0;
        // Own share
        final SlateCard own = new SlateCard(0, y, w, 64) {
            @Override
            protected void renderContent(final GuiGraphics g, final int x, final int yy, final int cw, final int ch, final int mx, final int my, final float pt) {
                final Palette p = Theme.current().palette();
                final boolean sharing = ScreenShare.isSharing();
                Icons.draw(g, Icon.SCREEN_SHARE, x + 8, yy + 8, 16, sharing ? p.accent() : p.textMuted());
                g.drawString(SlateDraw.font(), UiUtil.t(sharing ? "streams.own.sharing" : ScreenShare.isStarting() ? "streams.own.starting" : "streams.own.idle"), x + 30, yy + 8, p.text(), Theme.current().isVanilla());
                final String stats = sharing
                    ? ScreenShare.viewers() + (ScreenShare.viewers() == 1 ? " viewer" : " viewers") + "  ·  " + ScreenShare.width() + "x" + ScreenShare.height()
                        + "  ·  " + Math.round(ScreenShare.fps()) + " fps  ·  " + (ScreenShare.lastFrameBytes() / 1024) + " KB  ·  " + (ScreenShare.uptimeMs() / 1000) + " s"
                    : sc.connected() ? "Share what is on your screen with friends (they get a toast)" : "Needs a hub connection";
                g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(stats), cw - 40), x + 30, yy + 19, p.textMuted(), Theme.current().isVanilla());
            }
        }.flat();
        title = new SlateTextField(0, 0, Math.max(80, w - 130), UiUtil.t("streams.title"));
        title.placeholder(UiUtil.t("streams.title.placeholder")).maxLength(64).setValue(keepTitle);
        title.setX(0);
        own.add(title, 8, 36);
        own.add(new SlateButton(w - 116, 36, 108, 20, UiUtil.t(ScreenShare.isSharing() || ScreenShare.isStarting() ? "streams.stop" : "streams.start"), () -> {
            if (ScreenShare.isSharing() || ScreenShare.isStarting()) ScreenShare.stop(); else ScreenShare.start(title.getValue());
            fill();
        }).variant(ScreenShare.isSharing() ? SlateButton.Variant.DANGER : SlateButton.Variant.PRIMARY).icon(ScreenShare.isSharing() ? Icon.STOP : Icon.PLAY)
            .enabled(sc.connected()), w - 116, 36);
        panel.add(own, 0, y);
        y += 70;
        panel.add(new SlateSeparator(0, 0, w, UiUtil.t("streams.live")), 0, y);
        y += 14;
        final List<StreamInfo> live = new ArrayList<>();
        for (final StreamInfo si : sc.streams()) if (!si.owner().uuid().equals(sc.selfUuid())) live.add(si);
        if (live.isEmpty()) {
            panel.add(new SlateLabel(4, 0, w - 8, UiUtil.t("streams.none")).style(SlateLabel.Style.MUTED), 4, y);
            y += 14;
        }
        for (final StreamInfo si : live) {
            final boolean watching = StreamViewer.isWatching(si.id());
            final SlateCard c = new SlateCard(0, y, w, 40) {
                @Override
                protected void renderContent(final GuiGraphics g, final int x, final int yy, final int cw, final int ch, final int mx, final int my, final float pt) {
                    final Palette p = Theme.current().palette();
                    UiUtil.drawHead(g, si.owner().uuid(), si.owner().name(), x + 8, yy + 8, 24, null, 1f);
                    g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(si.title()), cw - 220), x + 40, yy + 8, p.text(), Theme.current().isVanilla());
                    final String line = si.owner().display() + "  ·  " + si.viewers() + (si.viewers() == 1 ? " viewer" : " viewers") + "  ·  " + UiUtil.relativeTime(si.startedMs()).replace(" ago", "");
                    g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(line), cw - 220), x + 40, yy + 20, p.textMuted(), Theme.current().isVanilla());
                }
            }.flat();
            if (watching) {
                c.add(new SlateButton(w - 200, 10, 90, 20, UiUtil.t("streams.open"), () -> StreamViewer.watch(si.id(), false)).variant(SlateButton.Variant.PRIMARY).icon(Icon.FULLSCREEN), w - 200, 10);
                c.add(new SlateButton(w - 104, 10, 96, 20, UiUtil.t("stream.stop_watching"), () -> { StreamViewer.stop(si.id()); fill(); }).variant(SlateButton.Variant.GHOST).icon(Icon.STOP), w - 104, 10);
            } else {
                c.add(new SlateButton(w - 200, 10, 90, 20, UiUtil.t("streams.watch"), () -> StreamViewer.watch(si.id(), false)).variant(SlateButton.Variant.PRIMARY).icon(Icon.PLAY), w - 200, 10);
                c.add(new SlateButton(w - 104, 10, 96, 20, UiUtil.t("stream.pip"), () -> { StreamViewer.watch(si.id(), true); fill(); }).variant(SlateButton.Variant.SECONDARY).icon(Icon.PIP), w - 104, 10);
            }
            panel.add(c, 0, y);
            y += 44;
        }
        panel.setContentHeight(y + 4);
    }

    @Override
    public void tick() {
        // Live stats refresh cheaply through the card's render; nothing else to do.
    }
}
