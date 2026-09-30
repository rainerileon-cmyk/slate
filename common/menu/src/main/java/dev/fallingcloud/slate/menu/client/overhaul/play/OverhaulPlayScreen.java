package dev.fallingcloud.slate.menu.client.overhaul.play;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.stage.Stage;
import dev.fallingcloud.slate.core.stage.StageFinish;
import dev.fallingcloud.slate.core.stage.StageWidget;
import dev.fallingcloud.slate.core.stage.anim.IdleMotion;
import dev.fallingcloud.slate.core.stage.node.FxNode;
import dev.fallingcloud.slate.core.stage.node.MotesNode;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateContextMenu;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.SlateSearchField;
import dev.fallingcloud.slate.core.widget.SlateSpinner;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import dev.fallingcloud.slate.menu.MenuConfig;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.Fmt;
import dev.fallingcloud.slate.menu.client.overhaul.play.map.MapTiles;
import dev.fallingcloud.slate.menu.client.overhaul.play.map.MapView;
import dev.fallingcloud.slate.menu.client.overhaul.play.map.Waypoints;
import dev.fallingcloud.slate.menu.client.play.model.ServerActions;
import dev.fallingcloud.slate.menu.client.play.model.ServerMeta;
import dev.fallingcloud.slate.menu.client.play.model.ServerPinger;
import dev.fallingcloud.slate.menu.client.play.model.WorldActions;
import dev.fallingcloud.slate.menu.client.play.model.WorldEntry;
import dev.fallingcloud.slate.menu.client.play.model.WorldFavorites;
import dev.fallingcloud.slate.menu.client.servers.ServerDialog;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.storage.LevelSummary;
import org.jetbrains.annotations.Nullable;

/**
 * The Overhaul layout's Play screen: worlds and servers in one. On the left a ring of cube planets, one a world (or
 * a server), the selected one in front; in the middle of the ring hangs the other ring, small. Under the ring stand
 * Play, the star, and the plus that makes a new world (or adds a server). On the right the selected world's map,
 * what there is to know about it, and what can be done with it: edit it, open its folder, back it up, delete it.
 * One header runs over both halves: the title, the search, the order.
 *
 * <p>Turn the ring by clicking a planet, with the arrows under it or on the keyboard, or with the wheel. Click the
 * planet in front, or Play, to play. Click the small ring to swap worlds and servers: the big ring shrinks into the
 * middle as the small one grows out. Favourites always stand first, whatever the order.</p>
 */
public final class OverhaulPlayScreen extends SlateScreen {

    /** How a ring is ordered, after the favourites. */
    public enum Sort { RECENT, NAME, SIZE }

    private static final int STRIP_H = 54;

    private final boolean startOnServers;
    @Nullable private Stage stage;
    @Nullable private Ring worlds, servers;
    private boolean onServers;
    private String query = "";
    private Sort sort;

    // Data
    @Nullable private List<RingEntry.World> allWorlds;
    private boolean loadingWorlds;
    @Nullable private Component worldsError;
    @Nullable private ServerList serverList;
    private final List<RingEntry.Server> allServers = new ArrayList<>();
    private final ServerPinger pinger = new ServerPinger();

    // Layout
    private Rect stageRect = new Rect(0, 0, 0, 0), stripRect = new Rect(0, 0, 0, 0), paneRect = new Rect(0, 0, 0, 0);
    @Nullable private SlateSearchField search;
    @Nullable private SlateButton playButton, addButton, paneEdit, paneFirst, paneSecond;
    @Nullable private SlateIconButton starButton, prevButton, nextButton, trashButton, moreButton;
    @Nullable private MapView map;
    @Nullable private DetailsList details;

    // What the right half shows
    @Nullable private RingEntry shownEntry;
    @Nullable private LevelInfo levelInfo;
    @Nullable private MapTiles tiles;
    private final Map<String, LevelInfo> infoCache = new HashMap<>();
    private final Anim nameSwap = new Anim(1, 200, Ease.OUT_CUBIC);
    private Component shownName = Component.empty();
    private int lastMouseX, lastMouseY;

    public OverhaulPlayScreen(@Nullable final Screen parent, final boolean servers) {
        super(Component.translatable("selectWorld.title"), parent);
        this.startOnServers = servers;
        this.onServers = servers;
        Sort s = Sort.RECENT;
        try { s = Sort.valueOf(SlateMenu.config().playSort.toUpperCase(Locale.ROOT)); } catch (final Exception ignored) {}
        this.sort = s;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(onServers ? "slate_menu.play.select_server" : "selectWorld.title");
    }

    // ------------------------------------------------------------------ build

    @Override
    protected void build() {
        final int split = Math.max(150, Math.round(width * 0.54f));
        stageRect = new Rect(0, HEADER_H, split, Math.max(40, height - HEADER_H));
        stripRect = new Rect(0, height - STRIP_H, split, STRIP_H);
        paneRect = new Rect(split + 1, HEADER_H, width - split - 1, height - HEADER_H);
        if (stage == null || stage.isClosed()) createScene();

        // One header over both halves: the search and the order sit with the title, a line under all three.
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.REFRESH, Component.translatable("slate_menu.refresh"), this::reload));
        final List<Sort> orders = onServers ? List.of(Sort.RECENT, Sort.NAME) : Arrays.asList(Sort.values());
        if (!orders.contains(sort)) sort = Sort.RECENT;
        if (width >= 340) {
            addHeaderAction(new SlateDropdown<>(0, 0, width >= 520 ? 104 : 84, orders, sort,
                s -> Component.translatable("slate_menu.play.sort." + s.name().toLowerCase(Locale.ROOT)),
                s -> { sort = s; SlateMenu.configFile().update(c -> c.playSort = s.name()); applyFilter(); }));
        }
        final SlateSearchField sf = new SlateSearchField(0, 0, width >= 640 ? 150 : width >= 440 ? 110 : 84, q -> { query = q; applyFilter(); });
        sf.onEnter(this::playSelected);
        search = addHeaderAction(sf);
        sf.setValue(query);
        slashFocusTarget = sf;

        // The scene fills the left half, the controls lie over its foot. It is drawn first and asked last: a button
        // over the scene takes its own clicks.
        final StageWidget view = new StageWidget(stageRect.x(), stageRect.y(), stageRect.w(), stageRect.h(), stage, getTitle());
        addRenderableOnly(view);
        addRenderableOnly((g, mx, my, pt) -> {
            final Theme t = Theme.current();
            SlateDraw.vgradient(g, 0, stripRect.y() - 26, stripRect.w(), STRIP_H + 26, 0x00000000, t.isVanilla() ? 0xB0000000 : Colors.withAlpha(t.bg(), 0xE6));
        });

        // Under the ring: the arrows at the corners, Play in the middle with the star and the plus beside it.
        final int row = height - PAD + 2 - 20, mid = stripRect.centerX();
        prevButton = add(new SlateIconButton(PAD - 2, row, 20, Icon.CHEVRON_LEFT, Component.translatable("slate_menu.play.previous"), () -> step(-1)));
        nextButton = add(new SlateIconButton(split - PAD - 18, row, 20, Icon.CHEVRON_RIGHT, Component.translatable("slate_menu.play.next"), () -> step(1)));
        final int playW = Mth.clamp(split - 150, 56, 96);
        playButton = add(new SlateButton(mid - playW / 2, row, playW, Component.translatable(onServers ? "slate_menu.play.join" : "slate_menu.play.play"), this::playSelected)
            .variant(SlateButton.Variant.PRIMARY).icon(Icon.PLAY));
        addButton = add(new SlateIconButton(mid + playW / 2 + 4, row, 20, Icon.PLUS,
            Component.translatable(onServers ? "slate_menu.play.add_server" : "selectWorld.create"), this::createNew));
        starButton = add(new SlateIconButton(mid - playW / 2 - 24, row, 20, Icon.STAR, Component.translatable("slate_menu.favorite"), this::toggleFavorite));

        // The right half: the map, the facts, the row of buttons.
        final int px = paneRect.x() + PAD, pw = paneRect.w() - PAD * 2;
        final int top = paneRect.y() + 8;
        final int mapH = Mth.clamp(Math.round(paneRect.h() * 0.4f), 48, Math.max(48, Math.round(pw * 0.62f)));
        map = add(new MapView(px, top, pw, mapH));
        final int by = row;
        final int detailsY = top + mapH + 6;
        details = add(new DetailsList(px, detailsY, pw, Math.max(20, by - 7 - detailsY)));
        int bx = px + pw - 20;
        final int moreX = bx;
        moreButton = add(new SlateIconButton(moreX, by, 20, Icon.DOTS, Component.translatable("slate_menu.more"), () -> menu(moreX, by)));
        bx -= 24;
        trashButton = add(new SlateIconButton(bx, by, 20, Icon.TRASH, Component.translatable("selectWorld.delete"), this::deleteSelected));
        trashButton.variant(SlateButton.Variant.DANGER);
        // Edit says so in words where there is room for the word, else it is the pencil alone.
        final SlateButton edit = new SlateButton(px, by, 40, Component.translatable("selectWorld.edit"), this::editSelected).icon(Icon.EDIT);
        final int editW = edit.preferredWidth() + 4;
        final int left;
        if (bx - 4 - px - editW - 4 >= 56) {
            edit.setWidth(editW);
            paneEdit = add(edit);
            left = px + editW + 4;
        } else {
            paneEdit = add(new SlateIconButton(px, by, 20, Icon.EDIT, Component.translatable("selectWorld.edit"), this::editSelected));
            left = px + 24;
        }
        // Beside it, what is done with a world's files: its folder opened, a backup made. A server has no files here:
        // it is asked again how it is, and its address copied. In words where both have room, else as icons.
        final Component firstName = Component.translatable(onServers ? "slate_menu.refresh" : "slate_menu.worlds.open_folder");
        final Component secondName = Component.translatable(onServers ? "slate_menu.play.copy_address" : "slate_menu.worlds.backup");
        final Icon firstIcon = onServers ? Icon.REFRESH : Icon.FOLDER, secondIcon = onServers ? Icon.COPY : Icon.BACKUP;
        final int each = (bx - 4 - left - 4) / 2;
        if (each >= Math.max(font.width(firstName), font.width(secondName)) + 26) {
            paneFirst = add(new SlateButton(left, by, each, firstName, this::paneFirst).icon(firstIcon));
            paneSecond = add(new SlateButton(left + each + 4, by, each, secondName, this::paneSecond).icon(secondIcon));
        } else {
            paneFirst = add(new SlateIconButton(left, by, 20, firstIcon, firstName, this::paneFirst));
            paneSecond = add(new SlateIconButton(left + 24, by, 20, secondIcon, secondName, this::paneSecond));
        }
        addWidget(view);

        if (allWorlds == null && !loadingWorlds) loadWorlds();
        if (serverList == null) loadServers();
        shownEntry = null;                       // the widgets are new: fill them again
        syncSelection();
    }

    private void createScene() {
        final Theme theme = Theme.current();
        final Stage s = new Stage().bind(this);
        stage = s;
        // The ring floats over the screen's own dark: no background of its own, so the pane beside it joins on.
        s.background(0);
        s.finish(StageFinish.glow());
        s.soft(true);
        s.resolutionScale(Minecraft.getInstance().getWindow().getWidth() <= 2048 ? 2f : 1.5f);
        // Looked down on from the front, a third of the way up to overhead: the ring shows as a ring, and its planets
        // show their sides. The view is aimed under the ring's middle, which lifts the ring clear of the controls.
        s.camera().at(0f, 5.2f, 10f).lookAt(0f, -0.92f, 0.6f).fov(42f).clip(0.05f, 140f);
        s.camera().idle(IdleMotion.sway(21000f, 1.4f, 0.4f, 0.02f));
        s.lighting().key(3.4f, 7.5f, 8.5f).sun(0.3f, 1f, 0.7f);

        // Far behind, stars; under the ring a floor that is only light: a wide faint pool, a warmer one in the
        // middle, the brightest where the chosen planet stands.
        final int warm = Colors.lerp(0xFFFFE9C8, theme.accent(), theme.isVanilla() ? 0f : 0.35f);
        s.add(FxNode.sparkles(150, 48f, 18f, 6f, 0.055f, 0xA0DCE6FF, 5L)).at(0f, 5f, -17f);
        s.add(FxNode.glow(11f, 0x70302E2B).fade(2.2f)).at(0f, -1.15f, 0f);
        s.add(FxNode.glow(4.6f, Colors.withAlpha(warm, 0x26)).fade(1.8f)).at(0f, -1.14f, 0f);
        s.add(FxNode.glow(2.1f, Colors.withAlpha(warm, 0x48)).fade(1.7f)).at(0f, -1.13f, Ring.RADIUS);
        s.add(new MotesNode(44, 9f, 3.4f, 9f, 0.026f, 0x55FFE7C2, 9L)).at(0f, -0.6f, 0f);

        worlds = new Ring(s, false, false, () -> swapTo(false), Component.translatable("slate_menu.play.to_worlds"));
        servers = new Ring(s, true, false, () -> swapTo(true), Component.translatable("slate_menu.play.to_servers"));
        worlds.capacity(SlateMenu.config().ringPlanets);
        servers.capacity(SlateMenu.config().ringPlanets);
        worlds.onPick(this::picked);
        servers.onPick(this::picked);
        // The ring in use grows out of the middle as the screen opens.
        current().active(true);

        if (allWorlds != null) applyFilter();
    }

    private Ring current() {
        return onServers ? servers : worlds;
    }

    private Ring other() {
        return onServers ? worlds : servers;
    }

    // ------------------------------------------------------------------ data

    private void loadWorlds() {
        loadingWorlds = true;
        worldsError = null;
        WorldActions.loadAll().whenCompleteAsync((summaries, err) -> {
            loadingWorlds = false;
            final List<RingEntry.World> fresh = new ArrayList<>();
            if (err != null) {
                SlateMenu.LOGGER.error("[Slate Menu] could not load worlds", err);
                worldsError = Component.translatable("selectWorld.unable_to_load");
            } else {
                for (final LevelSummary s : summaries) fresh.add(new RingEntry.World(new WorldEntry(s)));
            }
            if (worlds != null) worlds.clear();
            allWorlds = fresh;
            applyFilter();
        }, minecraft);
    }

    private void loadServers() {
        serverList = new ServerList(minecraft);
        serverList.load();
        if (servers != null) servers.clear();
        allServers.clear();
        final Map<String, Long> joined = new HashMap<>();
        for (final MenuConfig.RecentServer r : SlateMenu.config().recentServers) {
            if (r != null && r.address != null) joined.put(ServerActions.normalize(r.address), r.lastJoinedAt);
        }
        for (int i = 0; i < serverList.size(); i++) {
            final ServerData d = serverList.get(i);
            d.setState(ServerData.State.INITIAL);
            allServers.add(new RingEntry.Server(d, joined.getOrDefault(ServerActions.normalize(d.ip), 0L)));
            pinger.ping(d, () -> { if (shownEntry instanceof RingEntry.Server s && s.data == d) answered(s); });
        }
        applyFilter();
    }

    private void reload() {
        infoCache.clear();
        if (!loadingWorlds) loadWorlds();
        loadServers();
    }

    /** Puts what passes the search on both rings, favourites first, then in the chosen order. */
    private void applyFilter() {
        if (worlds == null || servers == null) return;
        final Comparator<RingEntry> byName = Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT));
        final Comparator<RingEntry> byRecent = Comparator.comparingLong(RingEntry::lastPlayed).reversed();
        final Comparator<RingEntry> bySize = Comparator.comparingLong((RingEntry e) -> e instanceof RingEntry.World w ? w.world.size : 0L).reversed();
        final Comparator<RingEntry> order = Comparator.comparing((RingEntry e) -> !e.favorite())
            .thenComparing(sort == Sort.NAME ? byName : sort == Sort.SIZE ? bySize.thenComparing(byRecent) : byRecent.thenComparing(byName));

        final List<RingEntry> w = new ArrayList<>();
        if (allWorlds != null) {
            for (final RingEntry.World e : allWorlds) {
                if (sort == Sort.SIZE) e.world.requestSize();
                if (e.matches(query)) w.add(e);
            }
        }
        w.sort(order);
        final RingEntry keptWorld = worlds.selected();
        worlds.show(w, keptWorld == null ? null : keptWorld.id());

        final List<RingEntry> sv = new ArrayList<>();
        for (final RingEntry.Server e : allServers) if (e.matches(query)) sv.add(e);
        sv.sort(order);
        final RingEntry keptServer = servers.selected();
        servers.show(sv, keptServer == null ? null : keptServer.id());
        syncSelection();
    }

    // ------------------------------------------------------------------ the rings

    private void picked(final RingEntry e) {
        final Ring ring = current();
        if (ring == null) return;
        if (ring.selected() == e) playSelected();
        else ring.select(e);
    }

    private void step(final int by) {
        final Ring ring = current();
        if (ring != null) ring.step(by);
    }

    private void swapTo(final boolean toServers) {
        if (onServers == toServers || worlds == null || servers == null) return;
        current().active(false);
        onServers = toServers;
        current().active(true);
        if (sort == Sort.SIZE && onServers) sort = Sort.RECENT;
        rebuildWidgets();
    }

    /** Follows the ring: when another planet has come to the front, the right half changes to it. */
    private void syncSelection() {
        final Ring ring = current();
        final RingEntry now = ring == null ? null : ring.selected();
        final boolean has = now != null;
        for (final var b : new net.minecraft.client.gui.components.AbstractWidget[] {playButton, starButton, paneEdit, paneFirst, paneSecond, trashButton, moreButton}) {
            if (b != null) b.active = has;
        }
        final boolean several = ring != null && ring.shown().size() > 1;
        if (prevButton != null) prevButton.active = several;
        if (nextButton != null) nextButton.active = several;
        if (starButton != null && has) {
            starButton.setIcon(now.favorite() ? Icon.STAR_FILLED : Icon.STAR);
            starButton.tip(Component.translatable(now.favorite() ? "slate_menu.unfavorite" : "slate_menu.favorite"));
        }
        if (now == shownEntry) return;
        shownEntry = now;
        nameSwap.snap(0f);
        nameSwap.set(1f);
        shownName = now == null ? Component.empty() : Component.literal(now.name());
        levelInfo = null;
        if (tiles != null) { tiles.close(); tiles = null; }
        if (map != null) map.clearMarks().show(null, 0, 0).placeholder(Component.empty(), null);
        if (now == null) {
            if (details != null) details.show(Component.empty(), Component.empty(), List.of(), List.of());
            return;
        }
        if (now instanceof RingEntry.World w) openWorld(w);
        else if (now instanceof RingEntry.Server s) openServer(s);
    }

    private void openWorld(final RingEntry.World w) {
        final Minecraft mc = Minecraft.getInstance();
        final Path folder = mc.getLevelSource().getLevelPath(w.id());
        w.world.requestSize();
        fillDetails(w);
        final LevelInfo cached = infoCache.get(w.id());
        if (cached != null) {
            worldInfo(w, folder, cached);
        } else {
            LevelInfo.read(folder).thenAccept(info -> {
                infoCache.put(w.id(), info);
                if (shownEntry == w) worldInfo(w, folder, info);
            });
        }
    }

    private void worldInfo(final RingEntry.World w, final Path folder, final LevelInfo info) {
        levelInfo = info;
        fillDetails(w);
        if (map == null) return;
        tiles = MapTiles.ofWorld(folder);
        map.clearMarks().show(tiles, info.centreX() + 0.5, info.centreZ() + 0.5);
        if (info.known()) {
            map.spawn(info.spawnX(), info.spawnZ());
            if (info.hasPlayer() && info.overworld()) map.player(info.playerX(), info.playerZ());
        }
        if (Waypoints.mapModInstalled()) {
            CompletableFuture.supplyAsync(() -> Waypoints.ofWorld(w.id(), w.name()), PlayIo.POOL)
                .thenAcceptAsync(list -> { if (shownEntry == w && map != null) map.waypoints(list); }, minecraft);
        }
    }

    private void openServer(final RingEntry.Server s) {
        fillDetails(s);
        pinger.ping(s.data, () -> answered(s));
        if (map == null) return;
        // A server has no files here to draw a map from, unless a map mod has been keeping one.
        final Path day = Waypoints.journeyMapInstalled() ? Waypoints.journeyMapTilesOfServer(s.name()) : null;
        tiles = day == null ? null : MapTiles.journeyMap(day);
        if (tiles != null) map.clearMarks().show(tiles, 0.5, 0.5);
        else serverPicture(s);
        if (Waypoints.mapModInstalled()) {
            CompletableFuture.supplyAsync(() -> Waypoints.ofServer(s.data.ip, s.name()), PlayIo.POOL)
                .thenAcceptAsync(list -> { if (shownEntry == s && map != null) map.waypoints(list); }, minecraft);
        }
    }

    /** Where a server has no map, its own picture stands in the map's place. */
    private void serverPicture(final RingEntry.Server s) {
        if (map == null) return;
        final byte[] icon = s.data.getIconBytes();
        final Textures.Loaded picture = icon == null ? null
            : Textures.fromBytes(icon, "slate_menu:favicon:" + s.id() + ":" + icon.length).orElse(null);
        map.clearMarks().show(null, 0, 0).placeholder(Component.translatable(Waypoints.mapModInstalled()
            ? "slate_menu.play.map.none_yet" : "slate_menu.play.map.needs_mod"), picture);
    }

    /** A server answered its ping (or did not): what is shown about it is brought up to date. */
    private void answered(final RingEntry.Server s) {
        if (shownEntry != s) return;
        fillDetails(s);
        if (tiles == null) serverPicture(s);
    }

    // ------------------------------------------------------------------ the facts

    private static Component t(final String key, final Object... args) {
        return Component.translatable("slate_menu.play." + key, args);
    }

    private void fillDetails(final RingEntry e) {
        if (details == null) return;
        final Palette p = Theme.current().palette();
        final List<DetailsList.Chip> chips = new ArrayList<>();
        final List<DetailsList.Row> rows = new ArrayList<>();
        final Component subtitle;
        if (e instanceof RingEntry.World w) {
            final LevelSummary s = w.world.summary;
            subtitle = Component.literal(w.id());
            chips.add(new DetailsList.Chip(s.getGameMode().getShortDisplayName(), p.accent()));
            if (s.isHardcore()) chips.add(new DetailsList.Chip(Component.translatable("slate_menu.worlds.hardcore"), p.danger()));
            if (s.hasCommands()) chips.add(new DetailsList.Chip(Component.translatable("slate_menu.worlds.cheats"), p.warning()));
            if (s.isExperimental()) chips.add(new DetailsList.Chip(Component.translatable("slate_menu.worlds.experimental"), p.warning()));
            if (w.favorite()) chips.add(new DetailsList.Chip(t("favourite"), 0xFFE9C46A));
            if (s.isLocked() || s.isDisabled() || s.requiresManualConversion()) {
                rows.add(DetailsList.Row.colored(t("state"), s.isLocked() ? Component.translatable("selectWorld.locked") : s.getInfo(), p.warning()));
            }
            // The main facts, which fit the box.
            rows.add(DetailsList.Row.of(Component.translatable("slate_menu.worlds.last_played"), Fmt.ago(s.getLastPlayed())));
            rows.add(DetailsList.Row.of(Component.translatable("slate_menu.worlds.mode"), s.getGameMode().getLongDisplayName()));
            final LevelInfo info = levelInfo;
            if (info != null && info.known() && info.difficulty() >= 0) {
                rows.add(DetailsList.Row.of(t("difficulty"), Difficulty.byId(info.difficulty()).getDisplayName()));
            }
            rows.add(DetailsList.Row.of(Component.translatable("slate_menu.worlds.version"), s.getWorldVersionName()));
            // And the rest, a scroll away.
            rows.add(DetailsList.Row.heading(t("more")));
            rows.add(DetailsList.Row.of(Component.translatable("slate_menu.worlds.size"), w.world.size >= 0 ? Fmt.bytes(w.world.size) : "..."));
            if (info != null && info.known()) {
                rows.add(DetailsList.Row.of(t("days"), Long.toString(info.days())));
                rows.add(DetailsList.Row.of(t("played"), Fmt.duration(info.gameTime() * 50L)));
                if (info.hasSeed()) rows.add(DetailsList.Row.copy(t("seed"), Long.toString(info.seed())));
                rows.add(DetailsList.Row.of(t("spawn"), info.spawnX() + ", " + info.spawnY() + ", " + info.spawnZ()));
                if (info.hasPlayer()) {
                    rows.add(DetailsList.Row.of(t("position"), (int) Math.floor(info.playerX()) + ", " + (int) Math.floor(info.playerY()) + ", " + (int) Math.floor(info.playerZ())));
                    rows.add(DetailsList.Row.of(t("dimension"), dimensionName(info.dimension())));
                    rows.add(DetailsList.Row.of(t("health"), Math.round(info.health()) + " / 20"));
                    rows.add(DetailsList.Row.of(t("level"), Integer.toString(info.xpLevel())));
                }
                rows.add(DetailsList.Row.of(t("weather"), t(info.thundering() ? "weather.thunder" : info.raining() ? "weather.rain" : "weather.clear")));
                if (!info.dataPacks().isEmpty()) rows.add(DetailsList.Row.of(t("data_packs"), String.join(", ", info.dataPacks())));
            }
            rows.add(DetailsList.Row.of(Component.translatable("slate_menu.worlds.last_played"), Fmt.date(s.getLastPlayed())));
            rows.add(DetailsList.Row.copy(t("folder"), w.id()));
            if (!w.world.tags.isEmpty()) rows.add(DetailsList.Row.of(Component.translatable("slate_menu.worlds.tags"), String.join(", ", w.world.tags)));
        } else if (e instanceof RingEntry.Server sv) {
            final ServerData d = sv.data;
            subtitle = Component.literal(d.ip);
            final boolean online = sv.online();
            chips.add(new DetailsList.Chip(t(switch (d.state()) {
                case SUCCESSFUL -> "status.online";
                case INCOMPATIBLE -> "status.outdated";
                case UNREACHABLE -> "status.offline";
                default -> "status.pinging";
            }), sv.statusColor()));
            if (sv.favorite()) chips.add(new DetailsList.Chip(t("favourite"), 0xFFE9C46A));
            if (d.isLan()) chips.add(new DetailsList.Chip(t("lan"), p.accent()));
            if (online && d.players != null) {
                rows.add(DetailsList.Row.of(t("players"), d.players.online() + " / " + d.players.max()));
            }
            if (online) rows.add(DetailsList.Row.of(t("ping"), d.ping + " ms"));
            if (online && d.version != null && !d.version.getString().isEmpty()) rows.add(DetailsList.Row.of(Component.translatable("slate_menu.worlds.version"), d.version));
            if (!online && d.state() == ServerData.State.UNREACHABLE && d.motd != null && !d.motd.getString().isBlank()) {
                rows.add(DetailsList.Row.colored(t("state"), Component.literal(d.motd.getString().trim()), p.danger()));
            }
            if (sv.lastJoined > 0) rows.add(DetailsList.Row.of(t("last_joined"), Fmt.ago(sv.lastJoined)));
            rows.add(DetailsList.Row.heading(t("more")));
            rows.add(DetailsList.Row.copy(t("address"), d.ip));
            if (online && d.motd != null && !d.motd.getString().isBlank()) {
                for (final String line : d.motd.getString().split("\n")) {
                    if (!line.isBlank()) rows.add(DetailsList.Row.of(t("motd"), line.trim()));
                }
            }
            if (online && d.players != null && !d.players.sample().isEmpty()) {
                final StringBuilder names = new StringBuilder();
                for (final var profile : d.players.sample()) {
                    if (names.length() > 0) names.append(", ");
                    names.append(profile.getName());
                }
                rows.add(DetailsList.Row.of(t("playing"), names.toString()));
            }
            final String group = ServerMeta.group(d.ip);
            if (!group.isEmpty()) rows.add(DetailsList.Row.of(t("group"), group));
            rows.add(DetailsList.Row.of(t("resource_packs"), Component.translatable("addServer.resourcePack." + switch (d.getResourcePackStatus()) {
                case ENABLED -> "enabled";
                case DISABLED -> "disabled";
                default -> "prompt";
            })));
        } else {
            subtitle = Component.empty();
        }
        details.show(Component.literal(e.name()), subtitle, chips, rows);
    }

    private static Component dimensionName(final String id) {
        if (id.isEmpty() || id.endsWith("overworld")) return t("dimension.overworld");
        if (id.endsWith("the_nether")) return t("dimension.nether");
        if (id.endsWith("the_end")) return t("dimension.end");
        return Component.literal(id);
    }

    // ------------------------------------------------------------------ actions

    private void playSelected() {
        final RingEntry e = current() == null ? null : current().selected();
        if (e instanceof RingEntry.World w) {
            if (!w.world.summary.primaryActionActive()) {
                SlateToasts.show(Component.translatable("slate_menu.worlds.cannot_play"), w.world.summary.getInfo(), Icon.WARNING);
                return;
            }
            WorldActions.play(w.world.summary, this);
        } else if (e instanceof RingEntry.Server s) {
            ServerActions.join(s.data, this);
        }
    }

    private void editSelected() {
        final RingEntry e = current() == null ? null : current().selected();
        if (e instanceof RingEntry.World w) {
            if (w.world.summary.canEdit()) WorldActions.edit(w.world.summary, this, this::reload);
        } else if (e instanceof RingEntry.Server s && serverList != null) {
            ServerDialog.open(Component.translatable("addServer.title"), s.data, edited -> {
                s.data.name = edited.name;
                s.data.ip = edited.ip;
                s.data.setResourcePackStatus(edited.getResourcePackStatus());
                serverList.save();
                loadServers();
            });
        }
    }

    private void deleteSelected() {
        final RingEntry e = current() == null ? null : current().selected();
        if (e instanceof RingEntry.World w) {
            if (!w.world.summary.canDelete()) return;
            SlateModal.confirmDanger(Component.translatable("selectWorld.deleteQuestion"), Component.translatable("selectWorld.deleteWarning", w.name()),
                Component.translatable("selectWorld.deleteButton"), () -> {
                    if (WorldActions.delete(w.world.summary) && allWorlds != null) {
                        allWorlds.remove(w);
                        applyFilter();
                        SlateToasts.show(Component.translatable("slate_menu.worlds.deleted"), Component.literal(w.name()), Icon.TRASH);
                    }
                });
        } else if (e instanceof RingEntry.Server s && serverList != null) {
            SlateModal.confirmDanger(Component.translatable("selectServer.deleteQuestion"), Component.translatable("selectServer.deleteWarning", s.name()),
                Component.translatable("selectServer.deleteButton"), () -> {
                    serverList.remove(s.data);
                    serverList.save();
                    ServerMeta.forget(s.data.ip);
                    loadServers();
                });
        }
    }

    private void toggleFavorite() {
        final RingEntry e = current() == null ? null : current().selected();
        if (e == null) return;
        e.favorite(!e.favorite());
        applyFilter();                     // favourites stand first: the ring may have to be laid out again
        fillDetails(e);
    }

    /** The first button beside Edit: the world's folder, or the server asked again. */
    private void paneFirst() {
        final RingEntry e = current() == null ? null : current().selected();
        if (e instanceof RingEntry.World w) {
            WorldActions.openFolder(w.world.summary);
        } else if (e instanceof RingEntry.Server s) {
            pinger.refresh(s.data, () -> answered(s));
            fillDetails(s);
        }
    }

    /** The second: a backup of the world, or the server's address to the clipboard. */
    private void paneSecond() {
        final RingEntry e = current() == null ? null : current().selected();
        if (e instanceof RingEntry.World w) WorldActions.backup(w.world.summary);
        else if (e instanceof RingEntry.Server s) ServerActions.copyAddress(s.data.ip);
    }

    private void createNew() {
        if (!onServers) {
            WorldActions.createNew(this);
            return;
        }
        ServerDialog.open(Component.translatable("addServer.title"), null, added -> {
            if (serverList == null) return;
            serverList.add(added, false);
            serverList.save();
            loadServers();
            if (servers != null) {
                for (final RingEntry e : servers.shown()) if (e instanceof RingEntry.Server s && s.data.ip.equals(added.ip)) servers.select(e);
            }
        });
    }

    /** The three dots: everything else there is to do with the selected world or server. */
    private void menu(final int x, final int y) {
        final RingEntry e = current() == null ? null : current().selected();
        if (e == null) return;
        final List<MenuPopup.Item> items = new ArrayList<>();
        items.add(MenuPopup.Item.of(Component.translatable(e.favorite() ? "slate_menu.unfavorite" : "slate_menu.favorite"),
            e.favorite() ? Icon.STAR_FILLED : Icon.STAR, this::toggleFavorite));
        if (e instanceof RingEntry.World w) {
            final LevelSummary s = w.world.summary;
            items.add(MenuPopup.Item.of(Component.translatable("slate_menu.worlds.duplicate"), Icon.DUPLICATE, () ->
                SlateModal.prompt(Component.translatable("slate_menu.worlds.duplicate"), Component.translatable("slate_menu.worlds.duplicate_body"),
                    w.name() + " copy", name -> WorldActions.duplicate(s, name, this::reload))));
            items.add(MenuPopup.Item.of(Component.translatable("slate_menu.worlds.backup"), Icon.BACKUP, () -> WorldActions.backup(s)));
            items.add(s.canRecreate() ? MenuPopup.Item.of(Component.translatable("selectWorld.recreate"), Icon.REFRESH, () -> WorldActions.recreate(s, this))
                : MenuPopup.Item.disabled(Component.translatable("selectWorld.recreate"), Icon.REFRESH));
            items.add(MenuPopup.Item.of(Component.translatable("slate_menu.worlds.edit_tags"), Icon.TAG, () ->
                SlateModal.prompt(Component.translatable("slate_menu.worlds.edit_tags"), Component.translatable("slate_menu.worlds.edit_tags_body"),
                    String.join(", ", w.world.tags), text -> {
                        WorldFavorites.setTags(w.id(), Arrays.asList(text.split(",")));
                        w.world.reloadMeta();
                        fillDetails(w);
                    })));
            items.add(MenuPopup.Item.sep());
            items.add(MenuPopup.Item.of(Component.translatable("slate_menu.worlds.open_folder"), Icon.FOLDER, () -> WorldActions.openFolder(s)));
            items.add(MenuPopup.Item.of(Component.translatable("slate_menu.worlds.open_saves"), Icon.FOLDER, WorldActions::openSavesFolder));
        } else if (e instanceof RingEntry.Server sv) {
            items.add(MenuPopup.Item.of(Component.translatable("slate_menu.refresh"), Icon.REFRESH, () -> {
                pinger.refresh(sv.data, () -> answered(sv));
                fillDetails(sv);
            }));
            items.add(MenuPopup.Item.of(t("copy_address"), Icon.COPY, () -> ServerActions.copyAddress(sv.data.ip)));
        }
        items.add(MenuPopup.Item.sep());
        items.add(MenuPopup.Item.of(Component.translatable(onServers ? "slate_menu.play.add_server" : "selectWorld.create"), Icon.PLUS, this::createNew));
        SlateContextMenu.open(x, y - 4, items);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (scrollY != 0 && mouseX < paneRect.x() && mouseY >= HEADER_H) {
            step(scrollY > 0 ? -1 : 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        final boolean typing = search != null && search.isFocused();
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (typing) return false;
        switch (keyCode) {
            case 263 -> { step(-1); return true; }                              // left
            case 262 -> { step(1); return true; }                               // right
            case 257, 335 -> { playSelected(); return true; }                   // enter
            case 261 -> { deleteSelected(); return true; }                      // delete
            case 294 -> { reload(); return true; }                              // F5
            case 258 -> { if (hasControlDown()) { swapTo(!onServers); return true; } return false; }
            default -> { return false; }
        }
    }

    @Override
    public void tick() {
        super.tick();
        pinger.tick();
        syncSelection();
    }

    @Override
    public void removed() {
        super.removed();
        pinger.close();
        if (tiles != null) { tiles.close(); tiles = null; }
        // The planets leave the entries: the entries are kept for when the screen comes back (from the world creation
        // screen, say), and would go on holding planets of a stage that is gone, and show nothing.
        if (worlds != null) worlds.clear();
        if (servers != null) servers.clear();
        if (stage != null) { stage.close(); stage = null; }
        worlds = null;
        servers = null;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        final Theme t = Theme.current();
        if (t.isVanilla()) return;
        // The left half is open space: near black above, a breath of warmth towards the floor.
        SlateDraw.vgradient(g, 0, HEADER_H, paneRect.x(), height - HEADER_H, 0xFF07080B, 0xFF191715);
        SlateDraw.vignette(g, 0, HEADER_H, paneRect.x(), height - HEADER_H, 0.45f);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        syncSelection();
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        // The line between the halves.
        SlateDraw.vline(g, paneRect.x() - 1, HEADER_H, height - HEADER_H, van ? 0xFF000000 : p.border());

        // Under the ring: the name of the planet in front.
        final float in = nameSwap.get();
        final Component name = Fonts.heading(shownName);
        final int room = stripRect.w() - 24;
        final var line = SlateDraw.truncate(name, room);
        g.drawString(font, line, stripRect.centerX() - font.width(line) / 2, stripRect.y() + 4 + Math.round((1f - in) * 4f),
            Colors.scaleAlpha(van ? 0xFFFFFFFF : p.text(), in), van);

        final Ring ring = current();
        final int cx = stageRect.centerX(), cy = stageRect.y() + (stripRect.y() - stageRect.y()) / 2;
        final int muted = van ? 0xFFC0C0C0 : p.textMuted();
        if (!onServers && loadingWorlds && allWorlds == null) {
            SlateSpinner.draw(g, cx - 8, cy - 20, 16, van ? 0xFFFFFFFF : p.accent());
            SlateDraw.textCentered(g, Component.translatable("slate_menu.worlds.loading"), cx, cy + 2, muted);
        } else if (!onServers && worldsError != null) {
            SlateDraw.textCentered(g, worldsError, cx, cy - 4, p.danger());
        } else if (ring != null && ring.shown().isEmpty() && ring.activeAmount() > 0.9f) {
            final boolean none = onServers ? allServers.isEmpty() : allWorlds != null && allWorlds.isEmpty();
            final Component head = t(none ? (onServers ? "empty.servers" : "empty.worlds") : "empty.search");
            final int y = stripRect.y() - 30;
            g.drawString(font, Fonts.heading(head), cx - font.width(Fonts.heading(head)) / 2, y, van ? 0xFFFFFFFF : p.text(), van);
            if (none) SlateDraw.textCentered(g, t(onServers ? "empty.servers_hint" : "empty.worlds_hint"), cx, y + 13, muted);
        }
    }
}
