package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.client.DevHarness;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.multiplayer.client.Friend;
import dev.fallingcloud.slate.multiplayer.client.SocialClient;
import dev.fallingcloud.slate.multiplayer.social.Presence;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The Friends tab of the Overhaul friends screen: the friends in a meadow, side by side on the path. Who is online
 * stands, and waves when pointed at; who is offline sits, in grey. Over every head the name and what they are
 * doing. The arrows at the sides show the friends before and after; a click chooses a friend, who steps forward,
 * and under the line stand what there is to know about them and what can be done: message, join, more.
 */
final class FriendsScenePage extends ScenePage<FriendsPage.Sort> {

    private static final float PACE = 1.5f;

    private List<Friend> shown = List.of();
    private int first;
    @Nullable private UUID chosen;
    private List<UUID> onScene = List.of();

    FriendsScenePage(final FriendsHubScreen screen) {
        super(screen, "friends", UiUtil.t("page.friends"), Icon.FRIENDS, FriendsPage.Sort.ONLINE);
    }

    @Override protected SocialScene.Setting setting() { return SocialScene.Setting.MEADOW; }

    @Override protected List<FriendsPage.Sort> sorts() { return List.of(FriendsPage.Sort.values()); }

    @Override protected Component sortName(final FriendsPage.Sort s) { return UiUtil.t("friends.sort." + s.name().toLowerCase(Locale.ROOT)); }

    @Override protected Component addLabel() { return UiUtil.t("friends.add"); }

    @Override
    protected void add() {
        PromptPopup.open(UiUtil.t("friends.add"), UiUtil.t("scene.add_friend.body"), "", 32, name -> {
            if (name.isBlank()) return;
            SocialClient.get().sendFriendRequestByName(name, said -> dev.fallingcloud.slate.multiplayer.client.Notifications.plain(UiUtil.t("friends.add").getString(), said));
        });
    }

    /** The friends there are: the player's own, or a made-up set when the harness asks for one. */
    static List<Friend> everyone() {
        return DevHarness.sampleData() ? SampleSocial.friends() : new ArrayList<>(SocialClient.get().friends());
    }

    private List<Friend> listed() {
        final List<Friend> items = new ArrayList<>();
        final String q = query.trim().toLowerCase(Locale.ROOT);
        for (final Friend f : everyone()) {
            if (!q.isEmpty() && !f.display().toLowerCase(Locale.ROOT).contains(q) && !f.name.toLowerCase(Locale.ROOT).contains(q)) continue;
            items.add(f);
        }
        final Comparator<Friend> byName = Comparator.comparing(f -> f.display().toLowerCase(Locale.ROOT));
        switch (sort) {
            case NAME -> items.sort(byName);
            case RECENT -> items.sort(Comparator.comparingLong((Friend f) -> f.online() ? Long.MAX_VALUE : (f.presence == null ? 0 : f.presence.sinceMs())).reversed().thenComparing(byName));
            default -> items.sort(Comparator.comparing((Friend f) -> !f.online()).thenComparing(f -> f.state() != Presence.State.IN_GAME).thenComparing(byName));
        }
        return items;
    }

    @Override
    protected void populate() {
        final SocialScene sc = scene;
        if (sc == null) return;
        shown = listed();
        final int n = fitting();
        first = Math.max(0, Math.min(first, Math.max(0, shown.size() - n)));
        final List<Friend> here = shown.subList(first, Math.min(shown.size(), first + n));
        if (chosen == null || here.stream().noneMatch(f -> f.uuid.equals(chosen))) chosen = here.isEmpty() ? null : here.get(0).uuid;

        // The same people as before stay where they stand; only a change of people sets the scene again.
        final List<UUID> ids = new ArrayList<>();
        for (final Friend f : here) ids.add(new UUID(f.uuid.getMostSignificantBits(), f.uuid.getLeastSignificantBits() ^ (f.online() ? 1L : 0L)));
        if (!ids.equals(onScene) || sc.figures().size() != here.size()) {
            sc.clearFigures();
            for (int i = 0; i < here.size(); i++) {
                final Friend f = here.get(i);
                final float x = (i - (here.size() - 1) / 2f) * PACE;
                // Not quite in line, not quite straight: people, not a parade.
                final float z = (i % 2 == 0 ? 0.12f : -0.16f);
                final float yaw = (here.size() <= 1 ? 0f : -x * 3.2f) + (i % 3 - 1) * 4f;
                sc.add(f.uuid, f.name, !f.online(), x, z, yaw, () -> choose(f.uuid));
            }
            if (here.isEmpty() && shown.isEmpty() && query.isBlank()) {
                // Nobody yet: the player stands there alone.
                final SocialClient me = SocialClient.get();
                sc.add(me.selfUuid(), me.self().name(), false, 0f, 0f, 0f, this::add).name = Component.literal(me.self().display());
            }
            onScene = ids;
        }
        for (final SocialScene.Figure fig : sc.figures()) {
            final Friend f = find(here, fig.uuid);
            if (f == null) continue;
            fig.name = Component.literal(f.display());
            // Over the head stands the short of it; the whole line is under the scene once the friend is chosen.
            fig.line = UiUtil.t("scene.state." + f.state().name().toLowerCase(Locale.ROOT));
            fig.lineColor = UiUtil.statusColor(f.presence);
            fig.faded = !f.online();
            fig.body.tooltip(null);
        }
        sc.select(chosen);
        sc.aim(view, Math.max(3, n) * PACE);
    }

    @Nullable
    private static Friend find(final List<Friend> in, final UUID uuid) {
        for (final Friend f : in) if (f.uuid.equals(uuid)) return f;
        return null;
    }

    private void choose(final UUID uuid) {
        if (uuid.equals(chosen)) return;
        chosen = uuid;
        refresh();
    }

    @Override
    protected boolean canTurn(final int direction) {
        return direction < 0 ? first > 0 : first + fitting() < shown.size();
    }

    @Override
    protected void turn(final int direction) {
        if (!canTurn(direction)) return;
        first += direction * Math.max(1, fitting() - 1);
        chosen = null;
        refresh();
    }

    // ------------------------------------------------------------------ under the line

    @Override
    protected void details(final SidebarScreen s, final Rect r) {
        final Friend f = chosen == null ? null : find(shown, chosen);
        if (f == null) return;
        final SocialClient sc = SocialClient.get();
        final int by = r.bottom() - 20;
        int x = r.x();
        final int messageW = Math.min(110, Math.max(70, r.w() / 3));
        s.addPageWidget(new SlateButton(x, by, messageW, UiUtil.t("menu.message"), () -> FriendsHubScreen.openThread(sc.dmThread(f.uuid).key))
            .variant(SlateButton.Variant.PRIMARY).icon(Icon.CHAT));
        x += messageW + 4;
        if (f.joinable()) {
            final int joinW = Math.min(90, Math.max(56, r.w() / 4));
            s.addPageWidget(new SlateButton(x, by, joinW, UiUtil.t("menu.join"), () -> CoreActions.joinServer(f.presence.server(), f.display())).icon(Icon.SERVER));
            x += joinW + 4;
        }
        s.addPageWidget(new SlateIconButton(x, by, 20, Icon.USER, UiUtil.t("scene.card"), () -> PlayerCardPopup.open(f.uuid, f.name, -1, -1)));
        final int moreX = r.right() - 20;
        s.addPageWidget(new SlateIconButton(moreX, by, 20, Icon.DOTS, UiUtil.t("scene.more"), () -> UiUtil.openFriendMenu(f, moreX, by - 4)));
    }

    @Override
    public void render(final SidebarScreen s, final GuiGraphics g, final Rect area, final int mouseX, final int mouseY, final float partialTick) {
        super.render(s, g, area, mouseX, mouseY, partialTick);
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final var font = SlateDraw.font();
        final Friend f = chosen == null ? null : find(shown, chosen);
        final Rect r = below;
        if (f == null) {
            final Component none = UiUtil.t(query.isBlank() ? "scene.no_friends" : "scene.no_match");
            SlateDraw.textCentered(g, none, r.centerX(), r.y() + Math.max(2, (r.h() - 9) / 2 - 4), van ? 0xFFC0C0C0 : p.textMuted());
            return;
        }
        // Who it is: the head, the name, the name behind a nickname; what they are doing; the note kept on them.
        UiUtil.drawHead(g, f.uuid, f.name, r.x(), r.y() + 1, 20, f.presence, 1f);
        final int tx = r.x() + 26, room = r.w() - 26;
        final Component name = Fonts.heading(f.display());
        g.drawString(font, SlateDraw.truncate(name, room), tx, r.y() + 1, van ? 0xFFFFFFFF : p.text(), van);
        if (!f.nick.isEmpty()) {
            final int nw = font.width(name) + 6;
            if (nw < room - 30) g.drawString(font, SlateDraw.truncate(Component.literal(f.name), room - nw), tx + nw, r.y() + 1, van ? 0xFFA0A0A0 : p.textDim(), van);
        }
        g.drawString(font, SlateDraw.truncate(Component.literal(UiUtil.presenceLine(f)), room), tx, r.y() + 12, f.online() ? UiUtil.statusColor(f.presence) : (van ? 0xFFA0A0A0 : p.textDim()), van);
        int y = r.y() + 26;
        final int foot = r.bottom() - 24;
        if (f.presence != null && !f.presence.status().isEmpty() && y + 9 <= foot) {
            g.drawString(font, SlateDraw.truncate(Component.literal("“" + f.presence.status() + "”"), r.w()), r.x(), y, van ? 0xFFE0E0E0 : p.textMuted(), van);
            y += 11;
        }
        if (!f.note.isEmpty() && y + 9 <= foot) {
            g.drawString(font, SlateDraw.truncate(UiUtil.t("scene.note", f.note), r.w()), r.x(), y, van ? 0xFFA0A0A0 : p.textDim(), van);
            y += 11;
        }
        if (f.sinceMs > 0 && y + 9 <= foot) {
            g.drawString(font, SlateDraw.truncate(UiUtil.t("scene.friends_since", UiUtil.relativeTime(f.sinceMs)), r.w()), r.x(), y, van ? 0xFFA0A0A0 : p.textDim(), van);
        }
    }

    @Override
    protected void overlay(final GuiGraphics g, final int mouseX, final int mouseY) {
        super.overlay(g, mouseX, mouseY);
        // Where in the list the view is.
        if (shown.size() > fitting()) {
            final Theme t = Theme.current();
            final String at = (first + 1) + " – " + Math.min(shown.size(), first + fitting()) + " / " + shown.size();
            final int w = SlateDraw.width(at) + 8;
            SlateDraw.pixelRound(g, view.centerX() - w / 2, view.bottom() - 15, w, 12, t.isVanilla() ? 0xA0000000 : dev.fallingcloud.slate.core.theme.Colors.withAlpha(t.bg(), 0xC0), t.isVanilla() ? 0 : 2);
            SlateDraw.textCentered(g, at, view.centerX(), view.bottom() - 13, t.isVanilla() ? 0xFFE0E0E0 : t.palette().textMuted());
        }
    }
}
