package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.client.DevHarness;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateBadge;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateContextMenu;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import dev.fallingcloud.slate.multiplayer.client.Friend;
import dev.fallingcloud.slate.multiplayer.client.SocialClient;
import dev.fallingcloud.slate.multiplayer.social.GroupInfo;
import dev.fallingcloud.slate.multiplayer.social.GroupInvite;
import dev.fallingcloud.slate.multiplayer.social.PlayerRef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The Groups tab of the Overhaul friends screen: one group at a time, its members sitting round a campfire in a
 * clearing at night, whoever is offline in grey. The arrows go to the group before and the group after. Under the
 * line: the group's name, how many of it are about, and what can be done with it: open its chat, ask someone in,
 * and the rest behind the three dots. Invitations to groups wait under the line too, until they are answered.
 */
final class GroupsScenePage extends ScenePage<GroupsScenePage.Sort> {

    enum Sort { NAME, MEMBERS, ONLINE }

    /** How many sit round the fire at most; a larger group shows these and says how many more there are. */
    private static final int SEATS = 9;

    private List<GroupInfo> shown = List.of();
    private int index;
    @Nullable private String chosenId;
    private String onScene = "";

    GroupsScenePage(final FriendsHubScreen screen) {
        super(screen, "groups", UiUtil.t("page.groups"), Icon.GROUP, Sort.NAME);
    }

    @Override protected SocialScene.Setting setting() { return SocialScene.Setting.CAMPFIRE; }

    @Override protected List<Sort> sorts() { return List.of(Sort.values()); }

    @Override protected Component sortName(final Sort s) { return UiUtil.t("scene.groups.sort." + s.name().toLowerCase(Locale.ROOT)); }

    @Override protected Component addLabel() { return UiUtil.t("groups.create"); }

    @Override
    public int badge() { return SocialClient.get().groupInvites().size(); }

    @Override
    protected void add() {
        PromptPopup.open(UiUtil.t("groups.create"), UiUtil.t("groups.create.body"), "", 32, name -> { if (!name.isBlank()) SocialClient.get().createGroup(name); });
    }

    private static List<GroupInfo> everyGroup() {
        return DevHarness.sampleData() ? SampleSocial.groups() : new ArrayList<>(SocialClient.get().groups());
    }

    private static boolean about(final UUID uuid) {
        final SocialClient sc = SocialClient.get();
        if (uuid.equals(sc.selfUuid())) return true;
        if (DevHarness.sampleData()) return SampleSocial.online(uuid);
        final Friend f = sc.friend(uuid);
        return f != null && f.online();
    }

    private static int aboutIn(final GroupInfo g) {
        int n = 0;
        for (final PlayerRef m : g.members()) if (about(m.uuid())) n++;
        return n;
    }

    private List<GroupInfo> listed() {
        final List<GroupInfo> out = new ArrayList<>();
        final String q = query.trim().toLowerCase(Locale.ROOT);
        for (final GroupInfo g : everyGroup()) if (q.isEmpty() || g.name().toLowerCase(Locale.ROOT).contains(q)) out.add(g);
        final Comparator<GroupInfo> byName = Comparator.comparing(g -> g.name().toLowerCase(Locale.ROOT));
        switch (sort) {
            case MEMBERS -> out.sort(Comparator.comparingInt((GroupInfo g) -> g.members().size()).reversed().thenComparing(byName));
            case ONLINE -> out.sort(Comparator.comparingInt(GroupsScenePage::aboutIn).reversed().thenComparing(byName));
            default -> out.sort(byName);
        }
        return out;
    }

    @Nullable
    private GroupInfo chosen() {
        return shown.isEmpty() ? null : shown.get(Mth.clamp(index, 0, shown.size() - 1));
    }

    @Override
    protected void populate() {
        final SocialScene sc = scene;
        if (sc == null) return;
        shown = listed();
        if (chosenId != null) {
            for (int i = 0; i < shown.size(); i++) if (shown.get(i).id().equals(chosenId)) { index = i; break; }
        }
        index = Mth.clamp(index, 0, Math.max(0, shown.size() - 1));
        final GroupInfo g = chosen();
        chosenId = g == null ? null : g.id();

        final List<PlayerRef> seated = new ArrayList<>();
        if (g != null) {
            // Those who are about first: they get the seats facing the viewer.
            final List<PlayerRef> members = new ArrayList<>(g.members());
            members.sort(Comparator.comparing((PlayerRef m) -> !about(m.uuid())).thenComparing(m -> m.display().toLowerCase(Locale.ROOT)));
            for (final PlayerRef m : members) if (seated.size() < SEATS) seated.add(m);
        }
        final StringBuilder key = new StringBuilder(g == null ? "-" : g.id());
        for (final PlayerRef m : seated) key.append('|').append(m.uuid()).append(about(m.uuid()) ? '+' : '-');
        if (!key.toString().equals(onScene)) {
            onScene = key.toString();
            sc.clearFigures();
            final int n = seated.size();
            final float ring = n <= 4 ? 1.55f : n <= 6 ? 1.8f : 2.1f;
            for (int i = 0; i < n; i++) {
                final PlayerRef m = seated.get(i);
                // Round the fire, the side towards the viewer left open. The first sits at the back, facing out of
                // the picture; the next ones take the seats to either side of them, turn by turn.
                final float open = 1.3f;
                final float step = (Mth.TWO_PI - open) / Math.max(3, n);
                final float angle = Mth.PI + (i % 2 == 0 ? -1f : 1f) * ((i + 1) / 2) * step;
                final float x = Mth.sin(angle) * ring, z = Mth.cos(angle) * ring;
                // Everyone faces the fire.
                final float yaw = (float) Math.toDegrees(Math.atan2(-x, -z));
                final SocialScene.Figure fig = sc.add(m.uuid(), m.name(), true, x, z, yaw, () -> PlayerCardPopup.open(m.uuid(), m.name(), -1, -1));
                final Friend f = SocialClient.get().friend(m.uuid());
                fig.name = Component.literal(f != null ? f.display() : m.display());
                fig.faded = !about(m.uuid());
                fig.line = m.uuid().equals(g.owner()) ? UiUtil.t("scene.groups.owner") : Component.empty();
                fig.lineColor = Theme.current().palette().warning();
            }
        }
        sc.select(null);
        sc.aim(view, 5.2f);
    }

    @Override
    protected boolean canTurn(final int direction) {
        return direction < 0 ? index > 0 : index < shown.size() - 1;
    }

    @Override
    protected void turn(final int direction) {
        if (!canTurn(direction)) return;
        index += direction;
        chosenId = shown.get(index).id();
        refresh();
    }

    // ------------------------------------------------------------------ under the line

    @Override
    protected void details(final SidebarScreen s, final Rect r) {
        final SocialClient sc = SocialClient.get();
        final GroupInfo g = chosen();
        final int by = r.bottom() - 20;
        // An invitation waiting: it is answered here, before anything else.
        final List<GroupInvite> invites = new ArrayList<>(sc.groupInvites());
        if (!invites.isEmpty()) {
            final GroupInvite inv = invites.get(0);
            final int w = Math.min(70, Math.max(48, r.w() / 5));
            s.addPageWidget(new SlateButton(r.right() - w * 2 - 4, r.y(), w, 16, UiUtil.t("requests.accept"), () -> sc.answerGroupInvite(inv.group().id(), true)).variant(SlateButton.Variant.PRIMARY));
            s.addPageWidget(new SlateButton(r.right() - w, r.y(), w, 16, UiUtil.t("requests.decline"), () -> sc.answerGroupInvite(inv.group().id(), false)).variant(SlateButton.Variant.GHOST));
        }
        if (g == null) return;
        final boolean mine = g.owner().equals(sc.selfUuid());
        final int chatW = Math.min(110, Math.max(70, r.w() / 3));
        int x = r.x();
        s.addPageWidget(new SlateButton(x, by, chatW, UiUtil.t("scene.groups.chat"), () -> FriendsHubScreen.openThread(g.threadKey()))
            .variant(SlateButton.Variant.PRIMARY).icon(Icon.CHAT));
        x += chatW + 4;
        final int inviteW = Math.min(100, Math.max(60, r.w() / 4));
        final int inviteX = x;
        s.addPageWidget(new SlateButton(x, by, inviteW, UiUtil.t("scene.groups.invite"), () -> invite(g, inviteX, by)).icon(Icon.INVITE));
        final int moreX = r.right() - 20;
        s.addPageWidget(new SlateIconButton(moreX, by, 20, Icon.DOTS, UiUtil.t("scene.more"), () -> {
            final List<MenuPopup.Item> items = new ArrayList<>();
            if (mine) items.add(MenuPopup.Item.of(UiUtil.t("scene.groups.rename"), Icon.EDIT, () ->
                PromptPopup.open(UiUtil.t("scene.groups.rename"), null, g.name(), 32, name -> { if (!name.isBlank()) sc.renameGroup(g.id(), name); })));
            items.add(MenuPopup.Item.danger(UiUtil.t("scene.groups.leave"), Icon.EXIT, () ->
                SlateModal.confirmDanger(UiUtil.t("scene.groups.leave"), UiUtil.t("scene.groups.leave.body", g.name()), UiUtil.t("scene.groups.leave"), () -> sc.leaveGroup(g.id()))));
            SlateContextMenu.open(moreX, by - 4, items);
        }));
    }

    /** A friend who is not in the group yet is asked in: picked from a list of those there are. */
    private void invite(final GroupInfo g, final int x, final int y) {
        final SocialClient sc = SocialClient.get();
        final List<MenuPopup.Item> items = new ArrayList<>();
        final List<Friend> friends = new ArrayList<>(sc.friends());
        friends.sort(Comparator.comparing((Friend f) -> !f.online()).thenComparing(f -> f.display().toLowerCase(Locale.ROOT)));
        for (final Friend f : friends) {
            if (g.isMember(f.uuid)) continue;
            items.add(MenuPopup.Item.of(Component.literal(f.display()), f.online() ? Icon.ONLINE : Icon.OFFLINE, () -> sc.inviteToGroup(g.id(), f.uuid)));
            if (items.size() >= 12) break;
        }
        if (items.isEmpty()) items.add(MenuPopup.Item.disabled(UiUtil.t("scene.groups.nobody_to_invite"), Icon.FRIENDS));
        SlateContextMenu.open(x, y - 4, items);
    }

    @Override
    public void render(final SidebarScreen s, final GuiGraphics g, final Rect area, final int mouseX, final int mouseY, final float partialTick) {
        super.render(s, g, area, mouseX, mouseY, partialTick);
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final var font = SlateDraw.font();
        final Rect r = below;
        final SocialClient sc = SocialClient.get();
        final List<GroupInvite> invites = new ArrayList<>(sc.groupInvites());
        int y = r.y();
        if (!invites.isEmpty()) {
            final GroupInvite inv = invites.get(0);
            final int w = Math.min(70, Math.max(48, r.w() / 5));
            Icons.draw(g, Icon.INVITE, r.x(), y + 2, 12, van ? 0xFFFFFFFF : p.accent());
            g.drawString(font, SlateDraw.truncate(UiUtil.t("scene.groups.invited", inv.from().display(), inv.group().name()), r.w() - w * 2 - 26), r.x() + 16, y + 4, van ? 0xFFFFFFFF : p.text(), van);
            y += 20;
        }
        final GroupInfo group = chosen();
        if (group == null) {
            final Component none = UiUtil.t(query.isBlank() ? "scene.no_groups" : "scene.no_match");
            SlateDraw.textCentered(g, none, r.centerX(), y + Math.max(2, (r.bottom() - y - 9) / 2 - 4), van ? 0xFFC0C0C0 : p.textMuted());
            return;
        }
        Icons.draw(g, Icon.GROUP, r.x(), y + 1, 12, van ? 0xFFFFFFFF : p.accent());
        final Component name = Fonts.heading(group.name());
        g.drawString(font, SlateDraw.truncate(name, r.w() - 70), r.x() + 17, y + 3, van ? 0xFFFFFFFF : p.text(), van);
        final int unread = DevHarness.sampleData() ? 0 : sc.thread(group.threadKey()).unread;
        if (unread > 0) SlateBadge.drawCount(g, unread, r.x() + 17 + Math.min(font.width(name), r.w() - 70) + 6, y + 2);
        y += 15;
        final int foot = r.bottom() - 24;
        if (y + 9 <= foot) {
            final int n = group.members().size();
            g.drawString(font, SlateDraw.truncate(UiUtil.t("scene.groups.about", aboutIn(group), n), r.w()), r.x(), y, van ? 0xFFC0C0C0 : p.textMuted(), van);
            y += 11;
        }
        if (group.members().size() > SEATS && y + 9 <= foot) {
            g.drawString(font, SlateDraw.truncate(UiUtil.t("scene.groups.more_members", group.members().size() - SEATS), r.w()), r.x(), y, van ? 0xFFA0A0A0 : p.textDim(), van);
            y += 11;
        }
        if (!group.voiceGroup().isEmpty() && y + 9 <= foot) {
            g.drawString(font, SlateDraw.truncate(UiUtil.t("scene.groups.voice", group.voiceGroup()), r.w()), r.x(), y, van ? 0xFFA0A0A0 : p.textDim(), van);
        }
    }

    @Override
    protected void overlay(final GuiGraphics g, final int mouseX, final int mouseY) {
        super.overlay(g, mouseX, mouseY);
        if (shown.size() > 1) {
            final Theme t = Theme.current();
            final String at = (index + 1) + " / " + shown.size();
            final int w = SlateDraw.width(at) + 8;
            SlateDraw.pixelRound(g, view.centerX() - w / 2, view.bottom() - 15, w, 12, t.isVanilla() ? 0xA0000000 : dev.fallingcloud.slate.core.theme.Colors.withAlpha(t.bg(), 0xC0), t.isVanilla() ? 0 : 2);
            SlateDraw.textCentered(g, at, view.centerX(), view.bottom() - 13, t.isVanilla() ? 0xFFE0E0E0 : t.palette().textMuted());
        }
    }
}
