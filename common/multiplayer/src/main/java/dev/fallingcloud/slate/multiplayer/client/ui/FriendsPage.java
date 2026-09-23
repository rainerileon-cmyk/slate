package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateList;
import dev.fallingcloud.slate.core.widget.SlateSearchField;
import dev.fallingcloud.slate.multiplayer.client.Friend;
import dev.fallingcloud.slate.multiplayer.client.SocialClient;
import dev.fallingcloud.slate.multiplayer.voice.VoiceStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The friend list: search, sort (online first / name / recent), rows with heads, status dots, presence
 * line, a Join button for friends on a joinable server and a message shortcut; right-click for the full
 * menu, double-click to message, click the head for the player card. Voice strip at the bottom when
 * Simple Voice Chat is present.
 */
final class FriendsPage extends FriendsHubScreen.HubPage {

    enum Sort { ONLINE, NAME, RECENT }

    private String query = "";
    private Sort sort = Sort.ONLINE;
    private SlateList<Friend> list;
    private VoiceBar voiceBar;

    FriendsPage(final FriendsHubScreen screen) {
        super(screen, "friends", UiUtil.t("page.friends"), Icon.FRIENDS);
    }

    @Override
    public int badge() { return 0; }

    @Override
    public void build(final SidebarScreen s, final Rect area) {
        final int btnW = 84;
        final int ddW = Math.min(96, Math.max(70, (area.w() - btnW - 12) / 3));
        final int searchW = Math.max(60, area.w() - btnW - ddW - 12);
        s.addPageWidget(new SlateSearchField(area.x(), area.y(), searchW, q -> { query = q; refresh(); }));
        s.addPageWidget(new SlateDropdown<>(area.x() + searchW + 6, area.y(), ddW, List.of(Sort.values()), sort,
            v -> UiUtil.t("friends.sort." + v.name().toLowerCase(Locale.ROOT)), v -> { sort = v; refresh(); }));
        s.addPageWidget(new SlateButton(area.right() - btnW, area.y(), btnW, UiUtil.t("friends.add"), () -> screen.showPage("requests"))
            .variant(SlateButton.Variant.PRIMARY).icon(Icon.PLUS));
        final boolean voice = VoiceStatus.available();
        final int listH = area.h() - 26 - (voice ? VoiceBar.HEIGHT + 6 : 0);
        list = s.addPageWidget(new SlateList<Friend>(area.x(), area.y() + 26, area.w(), listH, 30, new Row()).gap(2)
            .emptyText(UiUtil.t("friends.empty")));
        list.onActivate(f -> FriendsHubScreen.openThread(SocialClient.get().dmThread(f.uuid).key));
        list.onRightClick(f -> {
            final net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            UiUtil.openFriendMenu(f, mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth(),
                mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight());
        });
        if (voice) voiceBar = s.addPageWidget(new VoiceBar(area.x(), area.bottom() - VoiceBar.HEIGHT, area.w()));
        refresh();
    }

    @Override
    void onModelChanged() { refresh(); }

    private void refresh() {
        final List<Friend> items = new ArrayList<>();
        final String q = query.trim().toLowerCase(Locale.ROOT);
        for (final Friend f : SocialClient.get().friends()) {
            if (!q.isEmpty() && !f.display().toLowerCase(Locale.ROOT).contains(q) && !f.name.toLowerCase(Locale.ROOT).contains(q)) continue;
            items.add(f);
        }
        final Comparator<Friend> byName = Comparator.comparing(f -> f.display().toLowerCase(Locale.ROOT));
        switch (sort) {
            case NAME -> items.sort(byName);
            case RECENT -> items.sort(Comparator.comparingLong((Friend f) -> f.online() ? Long.MAX_VALUE : (f.presence == null ? 0 : f.presence.sinceMs())).reversed().thenComparing(byName));
            default -> items.sort(Comparator.comparing((Friend f) -> !f.online()).thenComparing(f -> f.state() != dev.fallingcloud.slate.multiplayer.social.Presence.State.IN_GAME).thenComparing(byName));
        }
        list.items(items);
    }

    /** Row renderer: head + names + presence + Join/Message buttons hit-tested inside the row. */
    private static final class Row implements SlateList.RowRenderer<Friend> {

        private static boolean inJoin(final Friend f, final int x, final int y, final int w, final int h, final double mx, final double my) {
            return f.joinable() && mx >= x + w - 96 && mx < x + w - 46 && my >= y + 6 && my < y + h - 6;
        }

        private static boolean inMessage(final int x, final int y, final int w, final int h, final double mx, final double my) {
            return mx >= x + w - 40 && mx < x + w - 6 && my >= y + 6 && my < y + h - 6;
        }

        private static boolean inHead(final int x, final int y, final int h, final double mx, final double my) {
            return mx >= x + 6 && mx < x + 26 && my >= y + 5 && my < y + h - 5;
        }

        @Override
        public void render(final GuiGraphics g, final Friend f, final int index, final int x, final int y, final int w, final int h, final boolean hovered, final boolean selected, final int mouseX, final int mouseY) {
            final Theme t = Theme.current();
            final Palette p = t.palette();
            UiUtil.drawHead(g, f.uuid, f.name, x + 7, y + 5, 20, f.presence, 1f);
            final int fg = f.online() ? p.text() : p.textDim();
            final int tx = x + 33;
            final int rightW = (f.joinable() ? 96 : 46) + 4;
            g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(f.display()), w - (tx - x) - rightW), tx, y + 5, fg, t.isVanilla());
            if (!f.nick.isEmpty()) {
                final int nw = SlateDraw.width(f.display()) + 4;
                g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(f.name), w - (tx - x) - rightW - nw), tx + nw, y + 5, p.textDim(), t.isVanilla());
            }
            g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(UiUtil.presenceLine(f)), w - (tx - x) - rightW), tx, y + 16, Colors.withAlpha(f.online() ? p.textMuted() : p.textDim(), 0xFF), t.isVanilla());
            if (hovered || selected) {
                if (f.joinable()) {
                    final boolean over = inJoin(f, x, y, w, h, mouseX, mouseY);
                    SlateDraw.pixelRound(g, x + w - 96, y + 7, 50, 16, over ? p.accentHover() : p.accent(), t.radius());
                    SlateDraw.textCentered(g, UiUtil.t("menu.join"), x + w - 71, y + 11, p.accentText());
                }
                final boolean overMsg = inMessage(x, y, w, h, mouseX, mouseY);
                if (!t.isVanilla()) SlateDraw.pixelRound(g, x + w - 40, y + 7, 34, 16, overMsg ? p.surfaceActive() : p.surfaceHover(), t.radius());
                else SlateDraw.vanillaButton(g, x + w - 40, y + 7, 34, 16, overMsg ? 1f : 0f, true, 1f);
                Icons.draw(g, Icon.CHAT, x + w - 29, y + 9, 12, overMsg ? p.text() : p.textMuted());
            }
        }

        @Override
        public boolean click(final Friend f, final int index, final int x, final int y, final int w, final int h, final double mouseX, final double mouseY, final int button) {
            if (button != 0) return false;
            if (inJoin(f, x, y, w, h, mouseX, mouseY)) { CoreActions.joinServer(f.presence.server(), f.display()); return true; }
            if (inMessage(x, y, w, h, mouseX, mouseY)) { FriendsHubScreen.openThread(SocialClient.get().dmThread(f.uuid).key); return true; }
            if (inHead(x, y, h, mouseX, mouseY)) { PlayerCardPopup.open(f.uuid, f.name, (int) mouseX, (int) mouseY); return true; }
            return false;
        }
    }
}
