package dev.fallingcloud.slate.menu.client.worlds;

import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateBadge;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateContextMenu;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateList;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateSearchField;
import dev.fallingcloud.slate.core.widget.SlateSpinner;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import dev.fallingcloud.slate.menu.MenuConfig;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.Fmt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;
import org.jetbrains.annotations.Nullable;

/**
 * The Slate world list ({@code slate_menu:worlds}): search, sort, grid/list view, favourites, tags, a
 * details panel and every vanilla world action. Reads worlds through vanilla's level source and runs
 * actions through {@link WorldActions}, so it stays a drop-in for {@code SelectWorldScreen}.
 */
public final class SlateWorldsScreen extends SlateScreen {

    public enum Sort { LAST_PLAYED, NAME, SIZE, FAVORITES }

    public enum View { GRID, LIST }

    private static final int CARD_MIN_W = 236;

    @Nullable private List<WorldEntry> all;
    private boolean loading, loadedOnce;
    @Nullable private Component loadError;
    private String query = "";
    private Sort sort;
    private View view;
    @Nullable private WorldEntry selected;
    @Nullable private WorldEntry lastClickEntry;
    private long lastClickMs;
    private int lastMx, lastMy;
    private List<WorldEntry> shown = List.of();

    @Nullable private SlateScrollPanel grid;
    @Nullable private SlateList<WorldEntry> list;
    @Nullable private SlateSearchField search;
    private final Map<WorldEntry, WorldCard> cards = new IdentityHashMap<>();
    private Rect listRect = new Rect(0, 0, 0, 0), detailsRect = new Rect(0, 0, 0, 0);
    private boolean showDetails;
    private int gridCols = 1;
    private final List<SlateButton> selectionButtons = new ArrayList<>();
    @Nullable private SlateButton emptyCta;

    public SlateWorldsScreen(@Nullable final Screen parent) {
        super(Component.translatable("selectWorld.title"), parent);
        final MenuConfig cfg = SlateMenu.config();
        sort = parse(Sort.class, cfg.worldsSort, Sort.LAST_PLAYED);
        view = parse(View.class, cfg.worldsView, View.GRID);
    }

    private static <E extends Enum<E>> E parse(final Class<E> cls, final String s, final E def) {
        try { return Enum.valueOf(cls, s == null ? "" : s.toUpperCase(Locale.ROOT)); } catch (final IllegalArgumentException e) { return def; }
    }

    // ------------------------------------------------------------------ build

    @Override
    protected void build() {
        cards.clear();
        selectionButtons.clear();
        grid = null;
        list = null;
        final Rect c = contentRect();
        final MenuConfig cfg = SlateMenu.config();

        // Header actions (added right to left)
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.REFRESH, Component.translatable("slate_menu.refresh"), this::reload));
        addHeaderAction(new SlateIconButton(0, 0, 20, view == View.GRID ? Icon.LIST : Icon.GRID, Component.translatable("slate_menu.view_toggle"), this::toggleView));
        if (width >= 500) {
            addHeaderAction(new SlateDropdown<>(0, 0, 112, Arrays.asList(Sort.values()), sort,
                s -> Component.translatable("slate_menu.sort." + s.name().toLowerCase(Locale.ROOT)),
                s -> { sort = s; SlateMenu.configFile().update(x -> x.worldsSort = s.name()); rebuildList(); }));
        }
        final SlateSearchField sf = new SlateSearchField(0, 0, width >= 640 ? 160 : 100, q -> { query = q; rebuildList(); });
        sf.onEnter(() -> { if (selected != null) play(selected); });
        search = addHeaderAction(sf);
        sf.setValue(query);

        // Body: list area, optional details panel, bottom bar
        final int bottomH = 30;
        showDetails = cfg.worldsShowDetails && width >= 600;
        final int detailsW = showDetails ? 204 : 0;
        listRect = new Rect(c.x(), c.y() + 4, c.w() - detailsW - (showDetails ? 10 : 0), c.h() - 4 - bottomH);
        detailsRect = new Rect(listRect.right() + 10, listRect.y(), detailsW, listRect.h());
        if (view == View.GRID) {
            grid = add(new SlateScrollPanel(listRect.x(), listRect.y(), listRect.w(), listRect.h()).scrollStep(40));
        } else {
            list = add(new SlateList<WorldEntry>(listRect.x(), listRect.y(), listRect.w(), listRect.h(), 40, new RowRenderer())
                .gap(2)
                .onSelect(e -> select(e, false))
                .onActivate(this::play)
                .onRightClick(this::contextMenu)
                .emptyText(Component.translatable("slate_menu.worlds.none_match")));
        }

        final int by = c.bottom() - 22;
        add(new SlateButton(c.x(), by, 130, Component.translatable("selectWorld.create"), () -> WorldActions.createNew(this))
            .variant(SlateButton.Variant.PRIMARY).icon(Icon.NEW_WORLD));
        add(new SlateIconButton(c.x() + 136, by, 20, Icon.FOLDER, Component.translatable("slate_menu.worlds.open_saves"), WorldActions::openSavesFolder));
        if (!showDetails) {
            int bx = c.right();
            bx -= 20;
            final int moreX = bx;
            selectionButtons.add(add(new SlateIconButton(moreX, by, 20, Icon.DOTS, Component.translatable("slate_menu.more"), () -> { if (selected != null) contextMenuAt(selected, moreX, by); })));
            bx -= 66; selectionButtons.add(add(new SlateButton(bx, by, 62, Component.translatable("selectWorld.edit"), () -> { if (selected != null) edit(selected); }).icon(Icon.EDIT)));
            bx -= 70; selectionButtons.add(add(new SlateButton(bx, by, 66, Component.translatable("slate_menu.worlds.play"), () -> { if (selected != null) play(selected); })
                .variant(SlateButton.Variant.PRIMARY).icon(Icon.PLAY)));
        } else {
            final int px = detailsRect.x() + 8, pw = detailsRect.w() - 16, half = (pw - 4) / 2;
            int py = detailsRect.bottom() - 8 - 20;
            selectionButtons.add(add(new SlateButton(px, py, pw, Component.translatable("selectWorld.delete"), () -> { if (selected != null) confirmDelete(selected); })
                .variant(SlateButton.Variant.DANGER).icon(Icon.TRASH)));
            py -= 24;
            selectionButtons.add(add(new SlateButton(px, py, half, Component.translatable("slate_menu.worlds.open_folder"), () -> { if (selected != null) WorldActions.openFolder(selected.summary); }).icon(Icon.FOLDER)));
            selectionButtons.add(add(new SlateButton(px + half + 4, py, half, Component.translatable("slate_menu.worlds.tags"), () -> { if (selected != null) editTags(selected); }).icon(Icon.TAG)));
            py -= 24;
            selectionButtons.add(add(new SlateButton(px, py, half, Component.translatable("slate_menu.worlds.duplicate"), () -> { if (selected != null) duplicate(selected); }).icon(Icon.DUPLICATE)));
            selectionButtons.add(add(new SlateButton(px + half + 4, py, half, Component.translatable("selectWorld.recreate"), () -> { if (selected != null) WorldActions.recreate(selected.summary, this); }).icon(Icon.REFRESH)));
            py -= 24;
            selectionButtons.add(add(new SlateButton(px, py, half, Component.translatable("selectWorld.edit"), () -> { if (selected != null) edit(selected); }).icon(Icon.EDIT)));
            selectionButtons.add(add(new SlateButton(px + half + 4, py, half, Component.translatable("slate_menu.worlds.backup"), () -> { if (selected != null) backup(selected); }).icon(Icon.BACKUP)));
            py -= 26;
            selectionButtons.add(add(new SlateButton(px, py, pw, Component.translatable("selectWorld.select"), () -> { if (selected != null) play(selected); })
                .variant(SlateButton.Variant.PRIMARY).icon(Icon.PLAY)));
        }
        emptyCta = add(new SlateButton(listRect.centerX() - 84, listRect.centerY() + 12, 168, Component.translatable("slate_menu.worlds.create_first"), () -> WorldActions.createNew(this))
            .variant(SlateButton.Variant.PRIMARY).icon(Icon.NEW_WORLD));
        emptyCta.visible = false;

        if (all == null && !loading) load();
        else rebuildList();
        updateSelection();
    }

    @Override
    public void added() {
        super.added();
        if (loadedOnce && !loading) reload();
    }

    // ------------------------------------------------------------------ data

    private void load() {
        loading = true;
        loadError = null;
        WorldActions.loadAll().whenCompleteAsync((summaries, err) -> {
            loading = false;
            loadedOnce = true;
            final List<WorldEntry> fresh = new ArrayList<>();
            if (err != null) {
                SlateMenu.LOGGER.error("[Slate Menu] could not load worlds", err);
                loadError = Component.translatable("selectWorld.unable_to_load");
            } else {
                for (final LevelSummary s : summaries) fresh.add(new WorldEntry(s));
            }
            // Keep the selection across reloads by folder name.
            final String selId = selected == null ? null : selected.id();
            all = fresh;
            selected = null;
            if (selId != null) for (final WorldEntry e : fresh) if (e.id().equals(selId)) selected = e;
            rebuildList();
            updateSelection();
        }, minecraft);
    }

    public void reload() {
        if (loading) return;
        load();
    }

    private List<WorldEntry> filtered() {
        if (all == null) return List.of();
        final List<WorldEntry> out = new ArrayList<>();
        for (final WorldEntry e : all) if (e.matches(query)) out.add(e);
        final Comparator<WorldEntry> byPlayed = Comparator.comparingLong((WorldEntry e) -> e.summary.getLastPlayed()).reversed();
        switch (sort) {
            case NAME -> out.sort(Comparator.comparing((WorldEntry e) -> e.name().toLowerCase(Locale.ROOT)));
            case SIZE -> out.sort(Comparator.comparingLong((WorldEntry e) -> e.size < 0 ? Long.MIN_VALUE : e.size).reversed().thenComparing(byPlayed));
            case FAVORITES -> out.sort(Comparator.comparing((WorldEntry e) -> !e.favorite).thenComparing(byPlayed));
            default -> out.sort(byPlayed);
        }
        return out;
    }

    private void rebuildList() {
        if (grid == null && list == null) return;
        shown = filtered();
        cards.clear();
        if (grid != null) {
            grid.clear();
            gridCols = Math.max(1, (listRect.w() - 6) / CARD_MIN_W);
            final int cardW = (listRect.w() - 8 - (gridCols - 1) * 8) / gridCols;
            int i = 0;
            for (final WorldEntry e : shown) {
                final int col = i % gridCols, row = i / gridCols;
                final WorldCard card = new WorldCard(0, 0, cardW, e, this);
                card.selected(e == selected);
                grid.add(card, col * (cardW + 8), row * (WorldCard.HEIGHT + 8));
                cards.put(e, card);
                i++;
            }
            final int rows = (shown.size() + gridCols - 1) / gridCols;
            grid.setContentHeight(rows * (WorldCard.HEIGHT + 8));
        } else if (list != null) {
            list.items(shown);
            if (selected != null && shown.contains(selected) && list.selectedItem() != selected) list.select(selected);
        }
        if (emptyCta != null) emptyCta.visible = !loading && all != null && all.isEmpty() && loadError == null;
    }

    // ------------------------------------------------------------------ selection + actions

    void clicked(final WorldEntry e) {
        final long now = Clock.nowMs();
        final boolean dbl = e == lastClickEntry && now - lastClickMs < 400;
        lastClickMs = now;
        lastClickEntry = e;
        select(e, true);
        if (dbl) play(e);
    }

    private void select(@Nullable final WorldEntry e, final boolean syncList) {
        selected = e;
        for (final Map.Entry<WorldEntry, WorldCard> c : cards.entrySet()) c.getValue().selected(c.getKey() == e);
        if (syncList && list != null && e != null && list.selectedItem() != e) list.select(e);
        updateSelection();
    }

    private void updateSelection() {
        final boolean has = selected != null;
        for (final SlateButton b : selectionButtons) {
            b.visible = showDetails ? has : true;
            b.active = has;
        }
    }

    void toggleFavorite(final WorldEntry e) {
        e.favorite = WorldFavorites.toggleFavorite(e.id());
        final WorldCard card = cards.get(e);
        if (card != null) card.refresh();
        if (sort == Sort.FAVORITES) rebuildList();
    }

    void contextMenu(final WorldEntry e) {
        contextMenuAt(e, lastMx, lastMy);
    }

    private void contextMenuAt(final WorldEntry e, final int x, final int y) {
        select(e, true);
        final LevelSummary s = e.summary;
        final List<MenuPopup.Item> items = new ArrayList<>();
        items.add(s.primaryActionActive() ? MenuPopup.Item.of(Component.translatable("slate_menu.worlds.play"), Icon.PLAY, () -> play(e))
            : MenuPopup.Item.disabled(Component.translatable("slate_menu.worlds.play"), Icon.PLAY));
        items.add(s.canEdit() ? MenuPopup.Item.of(Component.translatable("selectWorld.edit"), Icon.EDIT, () -> edit(e))
            : MenuPopup.Item.disabled(Component.translatable("selectWorld.edit"), Icon.EDIT));
        items.add(MenuPopup.Item.of(Component.translatable("slate_menu.worlds.backup"), Icon.BACKUP, () -> backup(e)));
        items.add(MenuPopup.Item.of(Component.translatable("slate_menu.worlds.duplicate"), Icon.DUPLICATE, () -> duplicate(e)));
        items.add(s.canRecreate() ? MenuPopup.Item.of(Component.translatable("selectWorld.recreate"), Icon.REFRESH, () -> WorldActions.recreate(s, this))
            : MenuPopup.Item.disabled(Component.translatable("selectWorld.recreate"), Icon.REFRESH));
        items.add(MenuPopup.Item.sep());
        items.add(MenuPopup.Item.of(Component.translatable(e.favorite ? "slate_menu.unfavorite" : "slate_menu.favorite"), e.favorite ? Icon.STAR_FILLED : Icon.STAR, () -> toggleFavorite(e)));
        items.add(MenuPopup.Item.of(Component.translatable("slate_menu.worlds.edit_tags"), Icon.TAG, () -> editTags(e)));
        items.add(MenuPopup.Item.of(Component.translatable("slate_menu.worlds.open_folder"), Icon.FOLDER, () -> WorldActions.openFolder(s)));
        items.add(MenuPopup.Item.of(Component.translatable("slate_menu.worlds.copy_folder"), Icon.COPY, () -> {
            minecraft.keyboardHandler.setClipboard(e.id());
            SlateToasts.show(Component.translatable("slate.copied"), Component.literal(e.id()), Icon.COPY);
        }));
        items.add(MenuPopup.Item.sep());
        items.add(s.canDelete() ? MenuPopup.Item.danger(Component.translatable("selectWorld.delete"), Icon.TRASH, () -> confirmDelete(e))
            : MenuPopup.Item.disabled(Component.translatable("selectWorld.delete"), Icon.TRASH));
        SlateContextMenu.open(x, y, items);
    }

    private void play(final WorldEntry e) {
        if (!e.summary.primaryActionActive()) {
            SlateToasts.show(Component.translatable("slate_menu.worlds.cannot_play"), e.summary.getInfo(), Icon.WARNING);
            return;
        }
        WorldActions.play(e.summary, this);
    }

    private void edit(final WorldEntry e) {
        if (!e.summary.canEdit()) return;
        WorldActions.edit(e.summary, this, this::reload);
    }

    private void backup(final WorldEntry e) {
        WorldActions.backup(e.summary);
    }

    private void duplicate(final WorldEntry e) {
        SlateModal.prompt(Component.translatable("slate_menu.worlds.duplicate"), Component.translatable("slate_menu.worlds.duplicate_body"),
            e.name() + " copy", name -> WorldActions.duplicate(e.summary, name, this::reload));
    }

    private void editTags(final WorldEntry e) {
        SlateModal.prompt(Component.translatable("slate_menu.worlds.edit_tags"), Component.translatable("slate_menu.worlds.edit_tags_body"),
            String.join(", ", e.tags), text -> {
                WorldFavorites.setTags(e.id(), Arrays.asList(text.split(",")));
                e.reloadMeta();
                rebuildList();
            });
    }

    private void confirmDelete(final WorldEntry e) {
        SlateModal.confirmDanger(Component.translatable("selectWorld.deleteQuestion"), Component.translatable("selectWorld.deleteWarning", e.name()),
            Component.translatable("selectWorld.deleteButton"), () -> {
                if (WorldActions.delete(e.summary) && all != null) {
                    all.remove(e);
                    if (selected == e) selected = null;
                    rebuildList();
                    updateSelection();
                    SlateToasts.show(Component.translatable("slate_menu.worlds.deleted"), Component.literal(e.name()), Icon.TRASH);
                }
            });
    }

    private void toggleView() {
        view = view == View.GRID ? View.LIST : View.GRID;
        SlateMenu.configFile().update(c -> c.worldsView = view.name());
        rebuildWidgets();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        final boolean typing = search != null && search.isFocused();
        if (!typing && keyCode == 47) {                                   // '/'
            if (search != null) { setFocused(search); search.setFocused(true); }
            return true;
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (typing) return false;
        if (keyCode == 294) { reload(); return true; }                     // F5
        if (keyCode == 261 && selected != null) { confirmDelete(selected); return true; }   // Delete
        if ((keyCode == 257 || keyCode == 335) && selected != null) { play(selected); return true; }
        if (grid != null && !shown.isEmpty()) {
            int i = selected == null ? -1 : shown.indexOf(selected);
            int next = switch (keyCode) {
                case 262 -> i + 1;               // right
                case 263 -> i - 1;               // left
                case 264 -> i < 0 ? 0 : i + gridCols;   // down
                case 265 -> i - gridCols;        // up
                default -> Integer.MIN_VALUE;
            };
            if (next != Integer.MIN_VALUE) {
                next = Math.max(0, Math.min(shown.size() - 1, next));
                final WorldEntry e = shown.get(next);
                select(e, false);
                final WorldCard card = cards.get(e);
                if (card != null) grid.ensureVisible(card);
                return true;
            }
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
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final int muted = van ? 0xFFC0C0C0 : p.textMuted();
        if (loading && all == null) {
            SlateSpinner.draw(g, listRect.centerX() - 8, listRect.centerY() - 20, 16, van ? 0xFFFFFFFF : p.accent());
            SlateDraw.textCentered(g, Component.translatable("slate_menu.worlds.loading"), listRect.centerX(), listRect.centerY() + 2, muted);
        } else if (loadError != null) {
            SlateDraw.textCentered(g, loadError, listRect.centerX(), listRect.centerY() - 4, p.danger());
        } else if (all != null && all.isEmpty()) {
            g.drawString(font, Fonts.heading(Component.translatable("slate_menu.worlds.empty")), listRect.centerX() - font.width(Fonts.heading(Component.translatable("slate_menu.worlds.empty"))) / 2, listRect.centerY() - 22, p.text(), van);
            SlateDraw.textCentered(g, Component.translatable("slate_menu.worlds.empty_hint"), listRect.centerX(), listRect.centerY() - 8, muted);
        } else if (shown.isEmpty() && grid != null) {
            SlateDraw.textCentered(g, Component.translatable("slate_menu.worlds.none_match"), listRect.centerX(), listRect.centerY() - 4, muted);
        }
        if (showDetails) renderDetails(g, mouseX, mouseY);
    }

    private void renderDetails(final GuiGraphics g, final int mouseX, final int mouseY) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final Rect r = detailsRect;
        if (van) {
            SlateDraw.vanillaListBackground(g, r.x(), r.y(), r.w(), r.h(), minecraft.level != null);
            SlateDraw.outline(g, r.x(), r.y(), r.w(), r.h(), 0xFF000000, 0);
        } else {
            SlateDraw.panel(g, r.x(), r.y(), r.w(), r.h(), p.bg2(), p.border());
        }
        final int muted = van ? 0xFFC0C0C0 : p.textMuted();
        if (selected == null) {
            SlateDraw.textCentered(g, Component.translatable("slate_menu.worlds.select_one"), r.centerX(), r.centerY() - 4, muted);
            return;
        }
        final WorldEntry e = selected;
        final LevelSummary s = e.summary;
        e.requestIcon();
        e.requestSize();
        final int is = 48, ix = r.x() + 10, iy = r.y() + 10;
        if (e.icon != null) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.blit(e.icon.id(), ix, iy, is, is, 0, 0, e.icon.width(), e.icon.height(), e.icon.width(), e.icon.height());
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        } else {
            SlateDraw.pixelRound(g, ix, iy, is, is, van ? 0x80000000 : p.surface(), t.radius());
            Icons.draw(g, Icon.WORLD, ix + 16, iy + 16, 16, muted);
        }
        SlateDraw.outline(g, ix, iy, is, is, van ? 0xFF000000 : p.border(), t.radius());
        final int tx = ix + is + 8, tw = r.right() - 10 - tx;
        g.drawString(font, SlateDraw.truncate(Fonts.heading(Component.literal(e.name())), tw), tx, iy + 2, p.text(), van);
        g.drawString(font, SlateDraw.truncate(Component.literal(e.id()), tw), tx, iy + 16, van ? 0xFFA0A0A0 : p.textDim(), van);
        int cx = tx;
        cx += SlateBadge.draw(g, s.getGameMode().getShortDisplayName(), cx, iy + 30, p.accent()) + 3;
        if (s.isHardcore()) cx += SlateBadge.draw(g, Component.translatable("slate_menu.worlds.hardcore"), cx, iy + 30, p.danger()) + 3;
        if (s.hasCommands()) SlateBadge.draw(g, Component.translatable("slate_menu.worlds.cheats"), cx, iy + 30, p.warning());

        int y = iy + is + 12;
        final int lx = r.x() + 10, lw = r.w() - 20;
        y = row(g, lx, y, lw, Component.translatable("slate_menu.worlds.last_played"), Component.literal(Fmt.date(s.getLastPlayed())), muted);
        y = row(g, lx, y, lw, Component.translatable("slate_menu.worlds.version"), s.getWorldVersionName(), muted);
        y = row(g, lx, y, lw, Component.translatable("slate_menu.worlds.size"), Component.literal(e.size >= 0 ? Fmt.bytes(e.size) : "..."), muted);
        y = row(g, lx, y, lw, Component.translatable("slate_menu.worlds.mode"), s.getGameMode().getLongDisplayName(), muted);
        y = row(g, lx, y, lw, Component.translatable("slate_menu.worlds.cheats"), Component.translatable(s.hasCommands() ? "options.on" : "options.off"), muted);
        if (s.isExperimental()) y = row(g, lx, y, lw, Component.translatable("slate_menu.worlds.experimental"), Component.translatable("options.on"), muted);
        if (!e.tags.isEmpty()) {
            g.drawString(font, Component.translatable("slate_menu.worlds.tags"), lx, y, muted, van);
            y += 11;
            int tx2 = lx;
            for (final String tag : e.tags) {
                final int bw = SlateDraw.width(tag) + 8;
                if (tx2 + bw > r.right() - 10) { tx2 = lx; y += 12; }
                tx2 += SlateBadge.draw(g, Component.literal(tag), tx2, y, van ? 0xFF8B8B8B : p.borderStrong()) + 3;
            }
            y += 14;
        }
        if (s.isLocked() || s.isDisabled() || s.requiresManualConversion() || s.backupStatus().shouldBackup()) {
            final Component info = s.isLocked() ? Component.translatable("selectWorld.locked") : s.getInfo();
            for (final net.minecraft.util.FormattedCharSequence line : font.split(info, lw)) {
                if (y > r.bottom() - 130) break;
                g.drawString(font, line, lx, y, p.warning(), van);
                y += 10;
            }
        }
    }

    private int row(final GuiGraphics g, final int x, final int y, final int w, final Component label, final Component value, final int muted) {
        final boolean van = Theme.current().isVanilla();
        g.drawString(font, label, x, y, muted, van);
        final net.minecraft.util.FormattedCharSequence v = SlateDraw.truncate(value, w - font.width(label) - 6);
        g.drawString(font, v, x + w - font.width(v), y, Theme.current().palette().text(), van);
        return y + 12;
    }

    /** Compact list rows: 32 px icon, name, one info line, star at the right. */
    private final class RowRenderer implements SlateList.RowRenderer<WorldEntry> {
        @Override
        public void render(final GuiGraphics g, final WorldEntry item, final int index, final int x, final int y, final int w, final int h,
                           final boolean hovered, final boolean selected, final int mouseX, final int mouseY) {
            WorldCard.drawSummary(g, item, x + 4, y + 4, w - 8 - 22, 32, false, mouseX, mouseY);
            final Palette p = Theme.current().palette();
            Icons.draw(g, item.favorite ? Icon.STAR_FILLED : Icon.STAR, x + w - 16, y + (h - 10) / 2, 10, item.favorite ? p.accent() : (Theme.current().isVanilla() ? 0xFFA0A0A0 : p.textDim()));
        }

        @Override
        public boolean click(final WorldEntry item, final int index, final int x, final int y, final int w, final int h, final double mouseX, final double mouseY, final int button) {
            if (button == 0 && mouseX >= x + w - 22) { toggleFavorite(item); return true; }
            return false;
        }
    }
}
