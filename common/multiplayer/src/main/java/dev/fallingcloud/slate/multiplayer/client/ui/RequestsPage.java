package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateSeparator;
import dev.fallingcloud.slate.core.widget.SlateTextField;
import dev.fallingcloud.slate.multiplayer.client.SocialClient;
import dev.fallingcloud.slate.multiplayer.social.PlayerRef;
import dev.fallingcloud.slate.multiplayer.social.RequestInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Add a friend by name; incoming requests (Accept/Decline), outgoing (Cancel), blocked players (Unblock). */
final class RequestsPage extends FriendsHubScreen.HubPage {

    private SlateTextField nameField;
    private SlateLabel feedback;
    private SlateScrollPanel panel;
    private int panelW;

    RequestsPage(final FriendsHubScreen screen) {
        super(screen, "requests", UiUtil.t("page.requests"), Icon.INVITE);
    }

    @Override
    public int badge() { return SocialClient.get().requestsIn().size(); }

    @Override
    public void build(final SidebarScreen s, final Rect area) {
        final SocialClient sc = SocialClient.get();
        nameField = new SlateTextField(area.x(), area.y(), Math.min(200, area.w() - 110), UiUtil.t("requests.add"));
        nameField.placeholder(UiUtil.t("requests.add.placeholder")).icon(Icon.USER).maxLength(16);
        nameField.onEnter(this::submit);
        s.addPageWidget(nameField);
        s.addPageWidget(new SlateButton(nameField.frameX() + nameField.frameWidth() + 6, area.y(), 100, UiUtil.t("requests.add"), this::submit)
            .variant(SlateButton.Variant.PRIMARY).icon(Icon.PLUS).enabled(sc.connected()));
        feedback = s.addPageWidget(new SlateLabel(area.x(), area.y() + 24, area.w(), Component.empty()).style(SlateLabel.Style.MUTED));
        panelW = area.w();
        panel = s.addPageWidget(new SlateScrollPanel(area.x(), area.y() + 38, area.w(), area.h() - 38));
        fill();
    }

    private void submit() {
        final String name = nameField.getValue().trim();
        if (name.isEmpty()) return;
        if (!SocialClient.get().connected()) { feedback.text(UiUtil.t("requests.offline")); return; }
        feedback.text(UiUtil.t("requests.sending", name));
        SocialClient.get().sendFriendRequestByName(name, msg -> { feedback.text(Component.literal(msg)); nameField.setValue(""); });
    }

    @Override
    void onModelChanged() { fill(); }

    private void fill() {
        panel.clear();
        final SocialClient sc = SocialClient.get();
        final int w = panelW - 8;
        int y = 0;
        y = section(y, w, UiUtil.t("requests.incoming", sc.requestsIn().size()));
        if (sc.requestsIn().isEmpty()) y = empty(y, w, UiUtil.t("requests.incoming.empty"));
        for (final RequestInfo r : sc.requestsIn()) {
            final SlateCard c = card(y, w, r.ref(), UiUtil.t("requests.asked", UiUtil.relativeTime(r.atMs())));
            c.add(new SlateButton(w - 150, 6, 70, 18, Component.translatable("slate_multiplayer.requests.accept"), () -> sc.acceptRequest(r.ref().uuid())).variant(SlateButton.Variant.PRIMARY).icon(Icon.CHECK), w - 150, 6);
            c.add(new SlateButton(w - 76, 6, 70, 18, Component.translatable("slate_multiplayer.requests.decline"), () -> sc.declineRequest(r.ref().uuid())).variant(SlateButton.Variant.GHOST).icon(Icon.CLOSE), w - 76, 6);
            panel.add(c, 0, y);
            y += 34;
        }
        y = section(y + 4, w, UiUtil.t("requests.outgoing", sc.requestsOut().size()));
        if (sc.requestsOut().isEmpty()) y = empty(y, w, UiUtil.t("requests.outgoing.empty"));
        for (final RequestInfo r : sc.requestsOut()) {
            final SlateCard c = card(y, w, r.ref(), UiUtil.t("requests.sent", UiUtil.relativeTime(r.atMs())));
            c.add(new SlateButton(w - 76, 6, 70, 18, Component.translatable("slate_multiplayer.requests.cancel"), () -> sc.cancelRequest(r.ref().uuid())).variant(SlateButton.Variant.GHOST).icon(Icon.CLOSE), w - 76, 6);
            panel.add(c, 0, y);
            y += 34;
        }
        y = section(y + 4, w, UiUtil.t("requests.blocked", sc.blocked().size()));
        if (sc.blocked().isEmpty()) y = empty(y, w, UiUtil.t("requests.blocked.empty"));
        for (final PlayerRef b : sc.blocked()) {
            final SlateCard c = card(y, w, b, UiUtil.t("requests.blocked.line"));
            c.add(new SlateButton(w - 76, 6, 70, 18, Component.translatable("slate_multiplayer.menu.unblock"), () -> sc.unblock(b.uuid())).variant(SlateButton.Variant.GHOST).icon(Icon.UNLOCK), w - 76, 6);
            panel.add(c, 0, y);
            y += 34;
        }
        panel.setContentHeight(y + 4);
    }

    private int section(final int y, final int w, final Component title) {
        panel.add(new SlateSeparator(0, 0, w, title), 0, y);
        return y + 14;
    }

    private int empty(final int y, final int w, final Component text) {
        panel.add(new SlateLabel(4, 0, w - 8, text).style(SlateLabel.Style.MUTED), 4, y);
        return y + 14;
    }

    private SlateCard card(final int y, final int w, final PlayerRef ref, final Component line) {
        final SlateCard c = new SlateCard(0, y, w, 30) {
            @Override
            protected void renderContent(final GuiGraphics g, final int x, final int yy, final int cw, final int ch, final int mouseX, final int mouseY, final float partialTick) {
                UiUtil.drawHead(g, ref.uuid(), ref.name(), x + 7, yy + 7, 16, null, 1f);
                g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(ref.display()), cw - 190), x + 28, yy + 6, Theme.current().text(), Theme.current().isVanilla());
                g.drawString(SlateDraw.font(), SlateDraw.truncate(line, cw - 190), x + 28, yy + 16, Theme.current().muted(), Theme.current().isVanilla());
            }
        }.flat();
        c.onClick(() -> PlayerCardPopup.open(ref.uuid(), ref.name(), c.getX() + 30, c.getY() + 30));
        return c;
    }
}
