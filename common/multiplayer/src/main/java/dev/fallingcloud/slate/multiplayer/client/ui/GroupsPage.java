package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateContextMenu;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateList;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import dev.fallingcloud.slate.multiplayer.client.Friend;
import dev.fallingcloud.slate.multiplayer.client.SocialClient;
import dev.fallingcloud.slate.multiplayer.social.GroupInfo;
import dev.fallingcloud.slate.multiplayer.social.GroupInvite;
import dev.fallingcloud.slate.multiplayer.social.PlayerRef;
import dev.fallingcloud.slate.multiplayer.voice.VoiceStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Friend groups: create, pending invites, the group list on the left; the selected group's members and
 * actions (invite, chat, rename, voice group, leave) on the right.
 */
final class GroupsPage extends FriendsHubScreen.HubPage {

    @Nullable private String selectedId;
    private SlateScrollPanel left;
    private SlateList<GroupInfo> list;
    private SlateScrollPanel detail;
    private Rect leftRect, rightRect;

    GroupsPage(final FriendsHubScreen screen) {
        super(screen, "groups", UiUtil.t("page.groups"), Icon.GROUP);
    }

    @Override
    public int badge() { return SocialClient.get().groupInvites().size(); }

    @Override
    public void build(final SidebarScreen s, final Rect area) {
        final int leftW = Math.max(120, Math.min(200, area.w() * 2 / 5));
        leftRect = new Rect(area.x(), area.y(), leftW, area.h());
        rightRect = new Rect(area.x() + leftW + 8, area.y(), area.w() - leftW - 8, area.h());
        s.addPageWidget(new SlateButton(leftRect.x(), leftRect.y(), leftRect.w(), UiUtil.t("groups.create"), this::createGroup)
            .variant(SlateButton.Variant.PRIMARY).icon(Icon.PLUS).enabled(SocialClient.get().connected()));
        left = s.addPageWidget(new SlateScrollPanel(leftRect.x(), leftRect.y() + 24, leftRect.w(), leftRect.h() - 24));
        detail = s.addPageWidget(new SlateScrollPanel(rightRect.x(), rightRect.y(), rightRect.w(), rightRect.h()));
        fillLeft();
        fillDetail();
    }

    private void createGroup() {
        SlateModal.prompt(UiUtil.t("groups.create"), UiUtil.t("groups.create.body"), "", name -> { if (!name.isBlank()) SocialClient.get().createGroup(name); });
    }

    @Override
    void onModelChanged() {
        fillLeft();
        fillDetail();
    }

    private void fillLeft() {
        left.clear();
        final SocialClient sc = SocialClient.get();
        final int w = leftRect.w() - 8;
        int y = 0;
        for (final GroupInvite inv : sc.groupInvites()) {
            final SlateCard c = new SlateCard(0, y, w, 44) {
                @Override
                protected void renderContent(final GuiGraphics g, final int x, final int yy, final int cw, final int ch, final int mx, final int my, final float pt) {
                    g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(inv.group().name()), cw - 12), x + 6, yy + 5, Theme.current().text(), Theme.current().isVanilla());
                    g.drawString(SlateDraw.font(), SlateDraw.truncate(UiUtil.t("groups.invited_by", inv.from().display()), cw - 12), x + 6, yy + 15, Theme.current().muted(), Theme.current().isVanilla());
                }
            }.flat();
            c.add(new SlateButton(6, 26, (w - 16) / 2, 16, Component.translatable("slate_multiplayer.requests.accept"), () -> sc.answerGroupInvite(inv.group().id(), true)).variant(SlateButton.Variant.PRIMARY), 6, 26);
            c.add(new SlateButton(10 + (w - 16) / 2, 26, (w - 16) / 2, 16, Component.translatable("slate_multiplayer.requests.decline"), () -> sc.answerGroupInvite(inv.group().id(), false)).variant(SlateButton.Variant.GHOST), 10 + (w - 16) / 2, 26);
            left.add(c, 0, y);
            y += 48;
        }
        final List<GroupInfo> groups = new ArrayList<>(sc.groups());
        groups.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        if (selectedId == null && !groups.isEmpty()) selectedId = groups.get(0).id();
        final int listH = Math.max(60, leftRect.h() - 24 - y);
        list = new SlateList<>(0, y, w, listH, 24, (g, item, index, x, yy, rw, rh, hovered, selected, mx, my) -> {
            final Palette p = Theme.current().palette();
            Icons.draw(g, Icon.GROUP, x + 6, yy + (rh - 12) / 2, 12, selected ? p.accent() : p.textMuted());
            g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(item.name()), rw - 44), x + 22, yy + 4, p.text(), Theme.current().isVanilla());
            final int online = (int) item.members().stream().filter(m -> { final Friend f = sc.friend(m.uuid()); return m.uuid().equals(sc.selfUuid()) || (f != null && f.online()); }).count();
            g.drawString(SlateDraw.font(), online + "/" + item.members().size(), x + 22, yy + 14, p.textDim(), Theme.current().isVanilla());
            final int unread = sc.thread(item.threadKey()).unread;
            if (unread > 0) dev.fallingcloud.slate.core.widget.SlateBadge.drawCount(g, unread, x + rw - 18, yy + 7);
        }).emptyText(UiUtil.t("groups.empty"));
        list.items(groups);
        list.onSelect(gi -> { selectedId = gi.id(); fillDetail(); });
        list.onActivate(gi -> FriendsHubScreen.openThread(gi.threadKey()));
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).id().equals(selectedId)) { list.select(i); break; }
        left.add(list, 0, y);
        left.setContentHeight(y + listH);
    }

    private void fillDetail() {
        detail.clear();
        final SocialClient sc = SocialClient.get();
        final GroupInfo g = selectedId == null ? null : sc.group(selectedId);
        final int w = rightRect.w() - 8;
        if (g == null) {
            detail.add(new SlateLabel(0, 0, w, UiUtil.t("groups.select")).style(SlateLabel.Style.MUTED), 0, 4);
            return;
        }
        final boolean owner = g.owner().equals(sc.selfUuid());
        int y = 0;
        detail.add(new SlateLabel(0, 0, w - 60, Component.literal(g.name())).style(SlateLabel.Style.HEADING), 0, y);
        if (owner) detail.add(new SlateButton(w - 56, 0, 56, 16, UiUtil.t("groups.rename"), () ->
            SlateModal.prompt(UiUtil.t("groups.rename"), null, g.name(), n -> { if (!n.isBlank()) sc.renameGroup(g.id(), n); })).variant(SlateButton.Variant.GHOST).icon(Icon.EDIT), w - 56, y);
        y += 18;
        detail.add(new SlateLabel(0, 0, w, UiUtil.t("groups.members", g.members().size())).style(SlateLabel.Style.MUTED), 0, y);
        y += 14;
        // Action row
        int bx = 0;
        final int bw = Math.max(70, (w - 12) / 3);
        detail.add(new SlateButton(bx, y, bw, 18, UiUtil.t("groups.chat"), () -> FriendsHubScreen.openThread(g.threadKey())).variant(SlateButton.Variant.PRIMARY).icon(Icon.CHAT), bx, y);
        bx += bw + 6;
        detail.add(new SlateButton(bx, y, bw, 18, UiUtil.t("groups.invite"), () -> inviteMenu(g)).icon(Icon.INVITE), bx, y);
        bx += bw + 6;
        detail.add(new SlateButton(bx, y, bw, 18, UiUtil.t("groups.leave"), () ->
            SlateModal.confirmDanger(UiUtil.t("groups.leave"), UiUtil.t("groups.leave.body", g.name()), UiUtil.t("groups.leave"), () -> sc.leaveGroup(g.id())))
            .variant(SlateButton.Variant.DANGER).icon(Icon.EXIT), bx, y);
        y += 24;
        // Voice row (Simple Voice Chat)
        if (VoiceStatus.available()) {
            final boolean inGame = Minecraft.getInstance().level != null && VoiceStatus.connected();
            final UUID mine = VoiceStatus.currentGroup();
            UUID target = null;
            try { if (!g.voiceGroup().isEmpty()) target = UUID.fromString(g.voiceGroup()); } catch (final IllegalArgumentException ignored) {}
            final boolean inThis = mine != null && mine.equals(target);
            final String status = !inGame ? "Voice groups need voice chat on a server" : target == null ? "No voice group yet"
                : inThis ? "You are in this group's voice chat" : "Voice group: " + (VoiceStatus.groupName(target).isEmpty() ? "ready" : VoiceStatus.groupName(target));
            detail.add(new SlateLabel(0, 0, w, Component.literal(status)).style(SlateLabel.Style.CAPTION), 0, y + 4);
            y += 14;
            if (inGame) {
                if (target == null || (!inThis && VoiceStatus.groupName(target).isEmpty())) {
                    detail.add(new SlateButton(0, y, 140, 18, UiUtil.t("groups.voice.start"), () -> startVoice(g)).icon(Icon.HEADSET), 0, y);
                } else if (!inThis) {
                    final UUID t = target;
                    detail.add(new SlateButton(0, y, 140, 18, UiUtil.t("groups.voice.join"), () -> VoiceStatus.joinGroup(t)).icon(Icon.HEADSET).variant(SlateButton.Variant.PRIMARY), 0, y);
                } else {
                    detail.add(new SlateButton(0, y, 140, 18, UiUtil.t("groups.voice.leave"), VoiceStatus::leaveGroup).icon(Icon.EXIT), 0, y);
                }
                y += 24;
            }
        }
        // Members
        for (final PlayerRef m : g.members()) {
            final Friend f = sc.friend(m.uuid());
            final boolean self = m.uuid().equals(sc.selfUuid());
            final SlateCard c = new SlateCard(0, y, w, 26) {
                @Override
                protected void renderContent(final GuiGraphics gg, final int x, final int yy, final int cw, final int ch, final int mx, final int my, final float pt) {
                    final Palette p = Theme.current().palette();
                    UiUtil.drawHead(gg, m.uuid(), m.name(), x + 5, yy + 5, 16, self ? new dev.fallingcloud.slate.multiplayer.social.Presence(dev.fallingcloud.slate.multiplayer.social.Presence.State.IN_GAME, "", "", "", 0) : f == null ? null : f.presence, 1f);
                    final String name = self ? m.display() + " (you)" : f != null ? f.display() : m.display();
                    gg.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(name), cw - 90), x + 26, yy + 4, p.text(), Theme.current().isVanilla());
                    final String line = self ? "" : f != null ? UiUtil.presenceLine(f) : "Not your friend";
                    gg.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(line), cw - 90), x + 26, yy + 14, p.textDim(), Theme.current().isVanilla());
                    if (m.uuid().equals(g.owner())) Icons.draw(gg, Icon.CROWN, x + cw - 16, yy + 7, 10, p.warning());
                }
            }.flat();
            if (!self) {
                c.onClick(() -> PlayerCardPopup.open(m.uuid(), m.name(), c.getX() + 30, c.getY() + 26));
                c.onRightClick(() -> {
                    final List<MenuPopup.Item> items = new ArrayList<>();
                    if (f != null) items.addAll(UiUtil.friendMenu(f));
                    else items.add(MenuPopup.Item.of(UiUtil.t("requests.add"), Icon.PLUS, () -> sc.sendFriendRequest(m.uuid(), m.name())));
                    if (owner) { items.add(MenuPopup.Item.sep()); items.add(MenuPopup.Item.danger(UiUtil.t("groups.kick"), Icon.MINUS, () -> sc.kickFromGroup(g.id(), m.uuid()))); }
                    final Minecraft mc = Minecraft.getInstance();
                    SlateContextMenu.open(mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth(),
                        mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight(), items);
                });
            }
            detail.add(c, 0, y);
            y += 30;
        }
        detail.setContentHeight(y + 4);
    }

    private void inviteMenu(final GroupInfo g) {
        final SocialClient sc = SocialClient.get();
        final List<MenuPopup.Item> items = new ArrayList<>();
        for (final Friend f : sc.friends()) {
            if (g.isMember(f.uuid)) continue;
            items.add(MenuPopup.Item.of(Component.literal(f.display()), f.online() ? Icon.ONLINE : Icon.OFFLINE, () -> sc.inviteToGroup(g.id(), f.uuid)));
        }
        if (items.isEmpty()) items.add(MenuPopup.Item.disabled(UiUtil.t("groups.invite.none"), null));
        final Minecraft mc = Minecraft.getInstance();
        SlateContextMenu.open(mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth(),
            mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight(), items);
    }

    @Nullable private String pendingVoiceFor;
    private long voiceDeadlineMs;

    private void startVoice(final GroupInfo g) {
        if (!VoiceStatus.createGroup(g.name())) return;
        // The voice server answers with our new group id a moment later; publish it to the group then (see tick).
        pendingVoiceFor = g.id();
        voiceDeadlineMs = System.currentTimeMillis() + 5000;
    }

    @Override
    public void tick() {
        if (pendingVoiceFor == null) return;
        final UUID id = VoiceStatus.currentGroup();
        if (id != null) {
            SocialClient.get().setGroupVoice(pendingVoiceFor, id.toString());
            pendingVoiceFor = null;
        } else if (System.currentTimeMillis() > voiceDeadlineMs) {
            pendingVoiceFor = null;
        }
    }
}
