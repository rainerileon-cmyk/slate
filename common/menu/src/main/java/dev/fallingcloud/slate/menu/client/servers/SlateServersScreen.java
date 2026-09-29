package dev.fallingcloud.slate.menu.client.servers;

import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateSectionHeader;
import dev.fallingcloud.slate.core.widget.SlateContextMenu;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateSearchField;
import dev.fallingcloud.slate.core.widget.SlateSpinner;
import dev.fallingcloud.slate.core.widget.SlateTextField;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import dev.fallingcloud.slate.menu.MenuConfig;
import dev.fallingcloud.slate.menu.SlateMenu;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.LanServer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The Slate server list ({@code slate_menu:servers}). Reads and writes vanilla's {@code servers.dat}
 * through {@link ServerList}; favourites and groups live in Slate's own file. Sections: favourites,
 * groups, other saved servers, recent, LAN, community. Live pings, quick connect, auto refresh.
 */
public final class SlateServersScreen extends SlateScreen {

    public enum Sort { MANUAL, NAME, PING, PLAYERS }

    private static final Set<String> COLLAPSED = new HashSet<>();
    private static final long RETRY_UNREACHABLE_MS = 5 * 60_000L;

    @Nullable private ServerList servers;
    private final ServerPinger pinger = new ServerPinger();
    @Nullable private LanScanner lan;
    private List<LanServer> lanServers = new ArrayList<>();
    private final Map<String, ServerData> lanData = new LinkedHashMap<>();
    private final Map<String, ServerData> recentData = new LinkedHashMap<>();
    private final Map<String, ServerData> communityData = new LinkedHashMap<>();
    private final Map<String, String> communityDescriptions = new LinkedHashMap<>();
    private String query = "";
    private Sort sort;
    @Nullable private ServerData selectedData;
    @Nullable private ServerCard.Kind selectedKind;
    @Nullable private ServerData lastClickData;
    private long lastClickMs;
    private int lastMx, lastMy;
    private long lastRefreshMs = Clock.nowMs();
    /** When servers that could not be reached were last tried again (see {@link #refreshAll}). */
    private long lastRetryMs = Clock.nowMs();

    // quick connect
    @Nullable private SlateTextField quickField;
    @Nullable private SlateButton quickJoin;
    @Nullable private ServerData quickData;
    private String quickAddress = "";
    private long quickTypedMs;
    private boolean quickDirty;
    private int quickStatusY;

    @Nullable private SlateScrollPanel panel;
    @Nullable private SlateSearchField search;
    private Rect listRect = new Rect(0, 0, 0, 0);
    private final List<ServerCard> cards = new ArrayList<>();
    private final List<SlateButton> savedButtons = new ArrayList<>();
    private final List<SlateButton> anyButtons = new ArrayList<>();
    @Nullable private SlateButton addToListBtn;
    private boolean anyRows;

    public SlateServersScreen(@Nullable final Screen parent) {
        super(Component.translatable("multiplayer.title"), parent);
        final String s = SlateMenu.config().serversSort;
        Sort parsed = Sort.MANUAL;
        try { parsed = Sort.valueOf(s == null ? "" : s.toUpperCase(Locale.ROOT)); } catch (final IllegalArgumentException ignored) {}
        sort = parsed;
    }

    public ServerPinger pinger() { return pinger; }

    // ------------------------------------------------------------------ build

    @Override
    protected void build() {
        cards.clear();
        savedButtons.clear();
        anyButtons.clear();
        final MenuConfig cfg = SlateMenu.config();
        if (servers == null) { servers = new ServerList(minecraft); servers.load(); }
        if (lan == null && cfg.showLan) lan = new LanScanner();
        final Rect c = contentRect();

        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.REFRESH, Component.translatable("selectServer.refresh"), () -> refreshAll(true)));
        if (width >= 500) {
            addHeaderAction(new SlateDropdown<>(0, 0, 100, Arrays.asList(Sort.values()), sort,
                s -> Component.translatable("slate_menu.servers.sort." + s.name().toLowerCase(Locale.ROOT)),
                s -> { sort = s; SlateMenu.configFile().update(x -> x.serversSort = s.name()); rebuildList(); }));
        }
        final SlateSearchField sf = new SlateSearchField(0, 0, width >= 640 ? 150 : 96, q -> { query = q; rebuildList(); });
        search = addHeaderAction(sf);
        sf.setValue(query);

        // Quick connect bar
        final int qy = c.y() + 4, joinW = 62;
        final SlateTextField qf = new SlateTextField(c.x(), qy, c.w() - joinW - 6, Component.translatable("slate_menu.servers.quick_connect"));
        qf.placeholder(Component.translatable("slate_menu.servers.quick_connect_hint")).icon(Icon.BOLT).text(quickAddress).maxLength(128);
        qf.onChange(this::quickChanged);
        qf.onEnter(this::quickJoin);
        quickField = add(qf);
        quickJoin = add(new SlateButton(c.right() - joinW, qy, joinW, Component.translatable("slate_menu.servers.join"), this::quickJoin)
            .variant(SlateButton.Variant.PRIMARY).icon(Icon.PLAY));
        quickJoin.active = ServerAddress.isValidAddress(quickAddress);
        quickStatusY = qy + 23;

        final int bottomH = 30;
        listRect = new Rect(c.x(), quickStatusY + 14, c.w(), c.bottom() - bottomH - (quickStatusY + 14));
        panel = add(new SlateScrollPanel(listRect.x(), listRect.y(), listRect.w(), listRect.h()).scrollStep(48));

        // Bottom bar
        final int by = c.bottom() - 22;
        add(new SlateButton(c.x(), by, 96, Component.translatable("selectServer.add"), this::addServer).variant(SlateButton.Variant.PRIMARY).icon(Icon.PLUS));
        int bx = c.right();
        if (width >= 460) {
            bx -= 20; savedButtons.add(add(new SlateIconButton(bx, by, 20, Icon.ARROW_DOWN, Component.translatable("slate_menu.servers.move_down"), () -> move(1))));
            bx -= 22; savedButtons.add(add(new SlateIconButton(bx, by, 20, Icon.ARROW_UP, Component.translatable("slate_menu.servers.move_up"), () -> move(-1))));
            bx -= 2;
        }
        bx -= 20; savedButtons.add(add(new SlateIconButton(bx, by, 20, Icon.TRASH, Component.translatable("selectServer.delete"), this::removeSelected)));
        bx -= 22; savedButtons.add(add(new SlateIconButton(bx, by, 20, Icon.EDIT, Component.translatable("selectServer.edit"), this::editSelected)));
        bx -= 22; anyButtons.add(add(new SlateIconButton(bx, by, 20, Icon.COPY, Component.translatable("slate_menu.servers.copy_address"), () -> { if (selectedData != null) ServerActions.copyAddress(selectedData.ip); })));
        bx -= 22; anyButtons.add(add(new SlateIconButton(bx, by, 20, Icon.DOTS, Component.translatable("slate_menu.more"), () -> { if (selectedCard() != null) contextMenuAt(selectedCard(), lastMx, lastMy); })));
        bx -= 76; addToListBtn = add(new SlateButton(bx, by, 72, Component.translatable("slate_menu.servers.add_to_list"), this::addSelectedToList).icon(Icon.PLUS));
        bx -= 62; anyButtons.add(add(new SlateButton(bx, by, 58, Component.translatable("selectServer.select"), this::joinSelected).variant(SlateButton.Variant.PRIMARY).icon(Icon.PLAY)));

        rebuildList();
        updateSelection();
    }

    // ------------------------------------------------------------------ list

    private boolean matches(final ServerData d) {
        if (query == null || query.isBlank()) return true;
        final String q = query.toLowerCase(Locale.ROOT).trim();
        if (d.name != null && d.name.toLowerCase(Locale.ROOT).contains(q)) return true;
        if (d.ip != null && d.ip.toLowerCase(Locale.ROOT).contains(q)) return true;
        return d.motd != null && d.motd.getString().toLowerCase(Locale.ROOT).contains(q);
    }

    private List<ServerData> filterSort(final List<ServerData> in) {
        final List<ServerData> out = new ArrayList<>();
        for (final ServerData d : in) if (matches(d)) out.add(d);
        switch (sort) {
            case NAME -> out.sort(Comparator.comparing(d -> (d.name == null ? d.ip : d.name).toLowerCase(Locale.ROOT)));
            case PING -> out.sort(Comparator.comparing((ServerData d) -> !ServerPinger.isOnline(d)).thenComparingLong(d -> d.ping));
            case PLAYERS -> out.sort(Comparator.comparingInt((ServerData d) -> d.players == null ? -1 : d.players.online()).reversed());
            default -> {}
        }
        return out;
    }

    private boolean isSaved(final String address) {
        if (servers == null) return false;
        final String key = ServerActions.normalize(address);
        for (int i = 0; i < servers.size(); i++) if (ServerActions.normalize(servers.get(i).ip).equals(key)) return true;
        return false;
    }

    private void rebuildList() {
        if (panel == null || servers == null) return;
        final MenuConfig cfg = SlateMenu.config();
        panel.clear();
        cards.clear();
        anyRows = false;
        final int w = listRect.w() - 6;
        int y = 0;

        final List<ServerData> saved = new ArrayList<>();
        for (int i = 0; i < servers.size(); i++) saved.add(servers.get(i));
        final List<ServerData> favs = new ArrayList<>(), rest = new ArrayList<>();
        final Map<String, List<ServerData>> grouped = new LinkedHashMap<>();
        for (final String g : ServerMeta.groups()) grouped.put(g, new ArrayList<>());
        for (final ServerData d : saved) {
            if (ServerMeta.isFavorite(d.ip)) favs.add(d);
            final String g = ServerMeta.group(d.ip);
            if (!g.isEmpty() && grouped.containsKey(g)) grouped.get(g).add(d);
            else if (!ServerMeta.isFavorite(d.ip)) rest.add(d);
        }
        y = section("favorites", Component.translatable("slate_menu.servers.favorites"), favs, ServerCard.Kind.SAVED, y, w, false);
        for (final Map.Entry<String, List<ServerData>> e : grouped.entrySet()) {
            y = section("group:" + e.getKey(), Component.literal(e.getKey()), e.getValue(), ServerCard.Kind.SAVED, y, w, false);
        }
        y = section("servers", Component.translatable("slate_menu.servers.saved_section"), rest, ServerCard.Kind.SAVED, y, w, saved.isEmpty());

        if (cfg.showRecent) {
            final List<ServerData> recents = new ArrayList<>();
            for (final MenuConfig.RecentServer r : cfg.recentServers) {
                if (r == null || r.address == null || r.address.isBlank()) continue;
                recents.add(recentData.computeIfAbsent(ServerActions.normalize(r.address), k -> new ServerData(r.name == null || r.name.isBlank() ? r.address : r.name, r.address, ServerData.Type.OTHER)));
            }
            y = section("recent", Component.translatable("slate_menu.servers.recent"), recents, ServerCard.Kind.RECENT, y, w, false);
        }
        if (cfg.showLan) {
            final List<ServerData> lans = new ArrayList<>();
            for (final LanServer l : lanServers) {
                final ServerData d = lanData.computeIfAbsent(l.getAddress(), k -> new ServerData(I18n.get("lanServer.title"), l.getAddress(), ServerData.Type.LAN));
                d.motd = Component.literal(l.getMotd() == null ? "" : l.getMotd());
                lans.add(d);
            }
            y = section("lan", Component.translatable("lanServer.scanning"), lans, ServerCard.Kind.LAN, y, w, false);
        }
        if (cfg.showCommunity) {
            final List<ServerData> community = new ArrayList<>();
            for (final CommunityServers.Entry e : CommunityServers.all()) {
                final ServerData d = communityData.computeIfAbsent(ServerActions.normalize(e.address), k -> new ServerData(e.name == null || e.name.isBlank() ? e.address : e.name, e.address, ServerData.Type.OTHER));
                communityDescriptions.put(ServerActions.normalize(e.address), e.description == null ? "" : e.description);
                community.add(d);
            }
            y = section("community", Component.translatable("slate_menu.servers.community"), community, ServerCard.Kind.COMMUNITY, y, w, false);
        }
        panel.setContentHeight(y);
    }

    private int section(final String key, final Component title, final List<ServerData> entries, final ServerCard.Kind kind, int y, final int w, final boolean showWhenEmpty) {
        final List<ServerData> list = filterSort(entries);
        if (list.isEmpty() && !showWhenEmpty) return y;
        if (panel == null) return y;
        final boolean collapsed = COLLAPSED.contains(key);
        panel.add(new SlateSectionHeader(0, 0, w, title, list.size(), collapsed, () -> {
            if (!COLLAPSED.remove(key)) COLLAPSED.add(key);
            minecraft.tell(this::rebuildList);
        }), 0, y);
        y += SlateSectionHeader.HEIGHT + 2;
        if (!collapsed) {
            for (final ServerData d : list) {
                final ServerCard card = new ServerCard(0, 0, w, d, kind, this, kind == ServerCard.Kind.COMMUNITY ? communityDescriptions.get(ServerActions.normalize(d.ip)) : null);
                if (kind == ServerCard.Kind.RECENT) card.savedHint(isSaved(d.ip));
                card.selected(d == selectedData && kind == selectedKind);
                panel.add(card, 0, y);
                cards.add(card);
                anyRows = true;
                y += ServerCard.HEIGHT + 4;
            }
        }
        return y + 6;
    }

    // ------------------------------------------------------------------ selection + actions

    @Nullable
    private ServerCard selectedCard() {
        for (final ServerCard c : cards) if (c.data() == selectedData && c.kind() == selectedKind) return c;
        return null;
    }

    void clicked(final ServerCard card) {
        final long now = Clock.nowMs();
        final boolean dbl = card.data() == lastClickData && now - lastClickMs < 400;
        lastClickMs = now;
        lastClickData = card.data();
        select(card);
        if (dbl) join(card.data(), card.kind());
    }

    private void select(@Nullable final ServerCard card) {
        selectedData = card == null ? null : card.data();
        selectedKind = card == null ? null : card.kind();
        for (final ServerCard c : cards) c.selected(c.data() == selectedData && c.kind() == selectedKind);
        updateSelection();
    }

    private void updateSelection() {
        final boolean has = selectedData != null;
        final boolean saved = has && selectedKind == ServerCard.Kind.SAVED;
        for (final SlateButton b : savedButtons) b.active = saved;
        for (final SlateButton b : anyButtons) b.active = has;
        if (addToListBtn != null) addToListBtn.active = has && !saved && !isSaved(selectedData.ip);
    }

    void onPingChanged() {
        if (sort == Sort.PING || sort == Sort.PLAYERS) rebuildList();
    }

    void toggleFavorite(final ServerData d) {
        ServerMeta.setFavorite(d.ip, !ServerMeta.isFavorite(d.ip));
        minecraft.tell(this::rebuildList);
    }

    void contextMenu(final ServerCard card) {
        contextMenuAt(card, lastMx, lastMy);
    }

    private void contextMenuAt(final ServerCard card, final int x, final int y) {
        select(card);
        final ServerData d = card.data();
        final List<MenuPopup.Item> items = new ArrayList<>();
        items.add(MenuPopup.Item.of(Component.translatable("selectServer.select"), Icon.PLAY, () -> join(d, card.kind())));
        items.add(MenuPopup.Item.of(Component.translatable("slate_menu.servers.copy_address"), Icon.COPY, () -> ServerActions.copyAddress(d.ip)));
        items.add(MenuPopup.Item.of(Component.translatable("slate_menu.servers.refresh_one"), Icon.REFRESH, () -> pinger.refresh(d, this::onPingChanged)));
        items.add(MenuPopup.Item.sep());
        if (card.kind() == ServerCard.Kind.SAVED) {
            final boolean fav = ServerMeta.isFavorite(d.ip);
            items.add(MenuPopup.Item.of(Component.translatable(fav ? "slate_menu.unfavorite" : "slate_menu.favorite"), fav ? Icon.STAR_FILLED : Icon.STAR, () -> toggleFavorite(d)));
            items.add(MenuPopup.Item.of(Component.translatable("selectServer.edit"), Icon.EDIT, this::editSelected));
            if (sort == Sort.MANUAL) {
                items.add(MenuPopup.Item.of(Component.translatable("slate_menu.servers.move_up"), Icon.ARROW_UP, () -> move(-1)));
                items.add(MenuPopup.Item.of(Component.translatable("slate_menu.servers.move_down"), Icon.ARROW_DOWN, () -> move(1)));
            }
            items.add(MenuPopup.Item.sep());
            final String current = ServerMeta.group(d.ip);
            items.add(MenuPopup.Item.checked(Component.translatable("slate_menu.servers.no_group"), current.isEmpty(), () -> { ServerMeta.setGroup(d.ip, ""); minecraft.tell(this::rebuildList); }));
            for (final String g : ServerMeta.groups()) {
                items.add(MenuPopup.Item.checked(Component.literal(g), g.equals(current), () -> { ServerMeta.setGroup(d.ip, g); minecraft.tell(this::rebuildList); }));
            }
            items.add(MenuPopup.Item.of(Component.translatable("slate_menu.servers.new_group"), Icon.PLUS, () ->
                SlateModal.prompt(Component.translatable("slate_menu.servers.new_group"), Component.translatable("slate_menu.servers.new_group_body"), "", name -> {
                    if (name.isBlank()) return;
                    ServerMeta.setGroup(d.ip, name.trim());
                    rebuildList();
                })));
            if (!current.isEmpty()) {
                items.add(MenuPopup.Item.danger(Component.translatable("slate_menu.servers.delete_group", current), Icon.TRASH, () -> { ServerMeta.removeGroup(current); minecraft.tell(this::rebuildList); }));
            }
            items.add(MenuPopup.Item.sep());
            items.add(MenuPopup.Item.danger(Component.translatable("selectServer.delete"), Icon.TRASH, this::removeSelected));
        } else {
            if (!isSaved(d.ip)) items.add(MenuPopup.Item.of(Component.translatable("slate_menu.servers.add_to_list"), Icon.PLUS, this::addSelectedToList));
            if (card.kind() == ServerCard.Kind.RECENT) {
                items.add(MenuPopup.Item.danger(Component.translatable("slate_menu.servers.forget_recent"), Icon.CLOSE, () -> {
                    SlateMenu.configFile().update(c -> c.recentServers.removeIf(r -> r != null && ServerActions.normalize(r.address).equals(ServerActions.normalize(d.ip))));
                    recentData.remove(ServerActions.normalize(d.ip));
                    minecraft.tell(this::rebuildList);
                }));
            }
        }
        SlateContextMenu.open(x, y, items);
    }

    private void join(final ServerData d, final ServerCard.Kind kind) {
        if (kind == ServerCard.Kind.LAN) {
            ServerActions.join(new ServerData(I18n.get("selectServer.defaultName"), d.ip, ServerData.Type.LAN), this);
            return;
        }
        if (kind == ServerCard.Kind.SAVED) { ServerActions.join(d, this); return; }
        // Transient rows: prefer the saved entry when the address is in the list, else vanilla's hidden entry.
        final ServerData target = ServerActions.find(d.ip).orElseGet(() -> { ServerList.saveSingleServer(d); return d; });
        ServerActions.join(target, this);
    }

    private void joinSelected() {
        if (selectedData != null && selectedKind != null) join(selectedData, selectedKind);
    }

    private void addServer() {
        ServerDialog.open(Component.translatable("addServer.title"), null, d -> {
            if (servers == null) return;
            servers.add(d, false);
            servers.save();
            rebuildList();
            SlateToasts.show(Component.translatable("slate_menu.servers.added"), Component.literal(d.name), Icon.SERVER);
        });
    }

    private void editSelected() {
        if (servers == null || selectedData == null || selectedKind != ServerCard.Kind.SAVED) return;
        final ServerData d = selectedData;
        final String oldIp = d.ip;
        ServerDialog.open(Component.translatable("editServer.title"), d, edited -> {
            servers.save();
            if (!ServerActions.normalize(oldIp).equals(ServerActions.normalize(edited.ip))) {
                ServerMeta.forget(oldIp);
                edited.setState(ServerData.State.INITIAL);
            }
            rebuildList();
        });
    }

    private void removeSelected() {
        if (servers == null || selectedData == null || selectedKind != ServerCard.Kind.SAVED) return;
        final ServerData d = selectedData;
        SlateModal.confirmDanger(Component.translatable("selectServer.deleteQuestion"), Component.translatable("selectServer.deleteWarning", d.name),
            Component.translatable("selectServer.deleteButton"), () -> {
                servers.remove(d);
                servers.save();
                ServerMeta.forget(d.ip);
                select(null);
                rebuildList();
            });
    }

    private void addSelectedToList() {
        if (servers == null || selectedData == null || selectedKind == ServerCard.Kind.SAVED || isSaved(selectedData.ip)) return;
        final ServerData src = selectedData;
        final ServerData d = new ServerData(src.name == null || src.name.isBlank() ? src.ip : src.name, src.ip, ServerData.Type.OTHER);
        servers.add(d, false);
        servers.save();
        SlateToasts.show(Component.translatable("slate_menu.servers.added"), Component.literal(d.name), Icon.SERVER);
        rebuildList();
        updateSelection();
    }

    private void move(final int dir) {
        if (servers == null || selectedData == null || selectedKind != ServerCard.Kind.SAVED) return;
        int idx = -1;
        for (int i = 0; i < servers.size(); i++) if (servers.get(i) == selectedData) idx = i;
        final int to = idx + dir;
        if (idx < 0 || to < 0 || to >= servers.size()) return;
        servers.swap(idx, to);
        servers.save();
        rebuildList();
    }

    /**
     * Pings every server again; {@code manual} (the refresh button, F5) also re-reads the community file. The automatic
     * refresh tries servers that could not be reached only every {@link #RETRY_UNREACHABLE_MS}: a dead or misspelt
     * address fails every time, and vanilla's pinger logs an error for each try.
     */
    private void refreshAll(final boolean manual) {
        final long now = Clock.nowMs();
        lastRefreshMs = now;
        final boolean retry = manual || now - lastRetryMs > RETRY_UNREACHABLE_MS;
        if (retry) lastRetryMs = now;
        if (manual) CommunityServers.reload();
        final List<ServerData> all = new ArrayList<>();
        if (servers != null) for (int i = 0; i < servers.size(); i++) all.add(servers.get(i));
        all.addAll(recentData.values());
        all.addAll(communityData.values());
        if (quickData != null) all.add(quickData);
        for (final ServerData d : all) {
            if (d.state() == ServerData.State.PINGING || d.state() == ServerData.State.UNREACHABLE && !retry) continue;
            d.setState(ServerData.State.INITIAL);
        }
        rebuildList();
    }

    // ------------------------------------------------------------------ quick connect

    private void quickChanged(final String text) {
        quickAddress = text == null ? "" : text.trim();
        quickTypedMs = Clock.nowMs();
        quickDirty = true;
        if (quickJoin != null) quickJoin.active = ServerAddress.isValidAddress(quickAddress);
        if (quickAddress.isEmpty()) quickData = null;
    }

    private void quickJoin() {
        if (quickAddress.isEmpty() || !ServerAddress.isValidAddress(quickAddress)) {
            SlateToasts.show(Component.translatable("slate_menu.servers.invalid_address"), Component.literal(quickAddress), Icon.WARNING);
            return;
        }
        final ServerData d = ServerActions.find(quickAddress).orElseGet(() -> {
            final ServerData n = new ServerData(I18n.get("selectServer.defaultName"), quickAddress, ServerData.Type.OTHER);
            ServerList.saveSingleServer(n);
            return n;
        });
        minecraft.options.lastMpIp = quickAddress;
        minecraft.options.save();
        ServerActions.join(d, this);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void tick() {
        super.tick();
        pinger.tick();
        if (lan != null) {
            final List<LanServer> l = lan.poll();
            if (l != null) { lanServers = l; rebuildList(); }
        }
        final long now = Clock.nowMs();
        if (quickDirty && now - quickTypedMs > 600) {
            quickDirty = false;
            if (ServerAddress.isValidAddress(quickAddress)) {
                quickData = new ServerData(quickAddress, quickAddress, ServerData.Type.OTHER);
                pinger.ping(quickData, () -> {});
            } else {
                quickData = null;
            }
        }
        final MenuConfig cfg = SlateMenu.config();
        if (cfg.serverAutoRefresh && now - lastRefreshMs > Math.max(5, cfg.serverRefreshSeconds) * 1000L) refreshAll(false);
    }

    @Override
    public void removed() {
        super.removed();
        pinger.close();
        if (lan != null) { lan.close(); lan = null; }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        final boolean typing = (search != null && search.isFocused()) || (quickField != null && quickField.isFocused());
        if (!typing && keyCode == 47 && search != null) { setFocused(search); search.setFocused(true); return true; }
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (typing) return false;
        if (keyCode == 294) { refreshAll(true); return true; }
        if (keyCode == 261 && selectedKind == ServerCard.Kind.SAVED) { removeSelected(); return true; }
        if ((keyCode == 257 || keyCode == 335) && selectedData != null) { joinSelected(); return true; }
        if (hasControlDown() && keyCode == 265) { move(-1); return true; }
        if (hasControlDown() && keyCode == 264) { move(1); return true; }
        if ((keyCode == 264 || keyCode == 265) && !cards.isEmpty()) {
            int i = -1;
            for (int k = 0; k < cards.size(); k++) if (cards.get(k).data() == selectedData && cards.get(k).kind() == selectedKind) i = k;
            final int next = Math.max(0, Math.min(cards.size() - 1, keyCode == 264 ? i + 1 : (i < 0 ? 0 : i - 1)));
            final ServerCard card = cards.get(next);
            select(card);
            if (panel != null) panel.ensureVisible(card);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        lastMx = mouseX;
        lastMy = mouseY;
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        // The quick-connect bar and its status line sit on one floating plate (drawn under the widgets).
        if (quickField != null && !Theme.current().isVanilla()) {
            final Rect c = contentRect();
            SlateDraw.floatingPanel(g, c.x() - 6, quickField.getY() - 6, c.w() + 12, quickStatusY + 12 - (quickField.getY() - 6), 1f);
        }
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final int muted = van ? 0xFFC0C0C0 : p.textMuted();
        final Rect c = contentRect();
        // Quick-connect status line
        if (quickData != null) {
            final int sx = c.x() + 4;
            switch (quickData.state()) {
                case INITIAL, PINGING -> {
                    SlateSpinner.draw(g, sx, quickStatusY, 10, van ? 0xFFFFFFFF : p.accent());
                    g.drawString(font, Component.translatable("multiplayer.status.pinging"), sx + 14, quickStatusY + 1, muted, van);
                }
                case UNREACHABLE -> {
                    g.fill(sx + 2, quickStatusY + 3, sx + 6, quickStatusY + 7, p.danger());
                    g.drawString(font, quickData.motd == null ? Component.translatable("slate_menu.servers.offline") : quickData.motd, sx + 12, quickStatusY + 1, p.danger(), van);
                }
                default -> {
                    g.fill(sx + 2, quickStatusY + 3, sx + 6, quickStatusY + 7, quickData.state() == ServerData.State.INCOMPATIBLE ? p.warning() : p.success());
                    final Component line = Component.empty()
                        .append(Component.translatable("slate_menu.servers.online_count", ServerPinger.players(quickData), quickData.ping))
                        .append(quickData.state() == ServerData.State.INCOMPATIBLE && quickData.version != null ? Component.empty().append(" · ").append(quickData.version) : Component.empty())
                        .append(quickData.motd == null || quickData.motd.getString().isBlank() ? Component.empty() : Component.empty().append(" · ").append(quickData.motd));
                    g.drawString(font, SlateDraw.truncate(line, c.w() - 16), sx + 12, quickStatusY + 1, muted, van);
                }
            }
        } else if (!quickAddress.isEmpty() && !ServerAddress.isValidAddress(quickAddress)) {
            g.drawString(font, Component.translatable("slate_menu.servers.invalid_address"), c.x() + 4, quickStatusY + 1, p.danger(), van);
        }
        if (!anyRows) {
            final Component empty = query.isBlank() ? Component.translatable("slate_menu.servers.empty") : Component.translatable("slate_menu.servers.none_match");
            SlateDraw.textCentered(g, empty, listRect.centerX(), listRect.centerY() - 10, muted);
            if (query.isBlank()) SlateDraw.textCentered(g, Component.translatable("slate_menu.servers.empty_hint"), listRect.centerX(), listRect.centerY() + 2, van ? 0xFFA0A0A0 : p.textDim());
        }
    }
}
