package dev.fallingcloud.slate.menu.client.screenshots;

import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateContextMenu;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateSearchField;
import dev.fallingcloud.slate.core.widget.SlateSpinner;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import dev.fallingcloud.slate.menu.MenuConfig;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.api.ScreenshotShareProvider;
import dev.fallingcloud.slate.menu.api.SlateMenuApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The screenshot gallery ({@code slate_menu:screenshots}): async thumbnails in a grid, search, sort,
 * selection, and per-file actions (view, copy to clipboard, rename, delete, share when the Multiplayer
 * module installs a provider, open folder). Double-click or Enter opens the {@link ScreenshotViewer}.
 */
public final class SlateScreenshotsScreen extends SlateScreen {

    public enum Sort { NEWEST, OLDEST, NAME }

    @Nullable private List<Screenshots.Shot> all;
    private List<Screenshots.Shot> shown = List.of();
    private boolean loading, loadedOnce;
    private String query = "";
    private Sort sort;
    @Nullable private Screenshots.Shot selected;
    @Nullable private Screenshots.Shot lastClick;
    private long lastClickMs;
    private int lastMx, lastMy;

    @Nullable private SlateScrollPanel grid;
    @Nullable private SlateSearchField search;
    private final Map<Screenshots.Shot, ScreenshotCard> cards = new IdentityHashMap<>();
    private Rect gridRect = new Rect(0, 0, 0, 0);
    private int cols = 1;
    private final List<SlateButton> selectionButtons = new ArrayList<>();

    public SlateScreenshotsScreen(@Nullable final Screen parent) {
        super(Component.translatable("slate_menu.screenshots.title"), parent);
        Sort s = Sort.NEWEST;
        try { s = Sort.valueOf(SlateMenu.config().screenshotsSort.toUpperCase(Locale.ROOT)); } catch (final Exception ignored) {}
        sort = s;
    }

    // ------------------------------------------------------------------ build

    @Override
    protected void build() {
        cards.clear();
        selectionButtons.clear();
        grid = null;
        final Rect c = contentRect();
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.REFRESH, Component.translatable("slate_menu.refresh"), this::reload));
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.FOLDER, Component.translatable("slate_menu.screenshots.open_folder"), () -> Util.getPlatform().openPath(Screenshots.dir())));
        if (width >= 480) {
            addHeaderAction(new SlateDropdown<>(0, 0, 96, Arrays.asList(Sort.values()), sort,
                s -> Component.translatable("slate_menu.screenshots.sort." + s.name().toLowerCase(Locale.ROOT)),
                s -> { sort = s; SlateMenu.configFile().update(x -> x.screenshotsSort = s.name()); rebuild(); }));
        }
        final SlateSearchField sf = new SlateSearchField(0, 0, width >= 600 ? 150 : 96, q -> { query = q; rebuild(); });
        sf.onEnter(() -> { if (selected != null) view(selected); });
        search = addHeaderAction(sf);
        sf.setValue(query);

        final int bottomH = 30;
        gridRect = new Rect(c.x(), c.y() + 4, c.w(), c.h() - 4 - bottomH);
        grid = add(new SlateScrollPanel(gridRect.x(), gridRect.y(), gridRect.w(), gridRect.h()).scrollStep(60));

        final int by = c.bottom() - 22;
        int bx = c.right();
        final ScreenshotShareProvider share = SlateMenuApi.screenshotShareProvider();
        if (share != null) {
            bx -= 66; selectionButtons.add(add(new SlateButton(bx, by, 62, Component.translatable("slate_menu.screenshots.share"), () -> { if (selected != null) share.share(selected.path()); }).icon(Icon.SEND)));
            bx -= 4;
        }
        bx -= 20; selectionButtons.add(add(new SlateIconButton(bx, by, 20, Icon.TRASH, Component.translatable("slate_menu.screenshots.delete"), () -> { if (selected != null) confirmDelete(selected); })));
        bx -= 22; selectionButtons.add(add(new SlateIconButton(bx, by, 20, Icon.EDIT, Component.translatable("slate_menu.screenshots.rename"), () -> { if (selected != null) rename(selected); })));
        bx -= 22; selectionButtons.add(add(new SlateIconButton(bx, by, 20, Icon.COPY, Component.translatable("slate_menu.screenshots.copy"), () -> { if (selected != null) copy(selected); })));
        bx -= 66; selectionButtons.add(add(new SlateButton(bx, by, 62, Component.translatable("slate_menu.screenshots.view"), () -> { if (selected != null) view(selected); })
            .variant(SlateButton.Variant.PRIMARY).icon(Icon.EYE)));

        if (all == null && !loading) load();
        else rebuild();
        updateSelection();
    }

    @Override
    public void added() {
        super.added();
        if (loadedOnce && !loading) reload();
    }

    private void load() {
        loading = true;
        Screenshots.scan().whenCompleteAsync((shots, err) -> {
            loading = false;
            loadedOnce = true;
            if (err != null) SlateMenu.LOGGER.error("[Slate Menu] screenshot scan failed", err);
            final java.nio.file.Path selPath = selected == null ? null : selected.path();
            all = shots == null ? new ArrayList<>() : new ArrayList<>(shots);
            selected = null;
            if (selPath != null) for (final Screenshots.Shot s : all) if (s.path().equals(selPath)) selected = s;
            rebuild();
            updateSelection();
        }, minecraft);
    }

    private void reload() {
        if (!loading) load();
    }

    private void rebuild() {
        if (grid == null) return;
        grid.clear();
        cards.clear();
        final List<Screenshots.Shot> list = new ArrayList<>();
        if (all != null) {
            final String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
            for (final Screenshots.Shot s : all) if (q.isEmpty() || s.fileName().toLowerCase(Locale.ROOT).contains(q)) list.add(s);
        }
        switch (sort) {
            case OLDEST -> list.sort(Comparator.comparingLong(Screenshots.Shot::modified));
            case NAME -> list.sort(Comparator.comparing(s -> s.fileName().toLowerCase(Locale.ROOT)));
            default -> list.sort(Comparator.comparingLong(Screenshots.Shot::modified).reversed());
        }
        shown = list;
        final MenuConfig cfg = SlateMenu.config();
        cols = cfg.screenshotColumns > 0 ? cfg.screenshotColumns : Math.max(1, (gridRect.w() - 6) / 176);
        final int cardW = (gridRect.w() - 8 - (cols - 1) * 8) / cols;
        final int cardH = (cardW - 8) * 9 / 16 + 8 + ScreenshotCard.CAPTION_H;
        int i = 0;
        for (final Screenshots.Shot s : shown) {
            final ScreenshotCard card = new ScreenshotCard(0, 0, cardW, cardH, s, this);
            card.selected(s == selected);
            grid.add(card, (i % cols) * (cardW + 8), (i / cols) * (cardH + 8));
            cards.put(s, card);
            i++;
        }
        grid.setContentHeight(((shown.size() + cols - 1) / cols) * (cardH + 8));
    }

    // ------------------------------------------------------------------ selection + actions

    void clicked(final ScreenshotCard card) {
        final long now = Clock.nowMs();
        final boolean dbl = card.shot() == lastClick && now - lastClickMs < 400;
        lastClickMs = now;
        lastClick = card.shot();
        select(card.shot());
        if (dbl) view(card.shot());
    }

    private void select(@Nullable final Screenshots.Shot s) {
        selected = s;
        for (final Map.Entry<Screenshots.Shot, ScreenshotCard> e : cards.entrySet()) e.getValue().selected(e.getKey() == s);
        updateSelection();
    }

    private void updateSelection() {
        for (final SlateButton b : selectionButtons) b.active = selected != null;
    }

    void contextMenu(final ScreenshotCard card) {
        select(card.shot());
        final Screenshots.Shot s = card.shot();
        final List<MenuPopup.Item> items = new ArrayList<>();
        items.add(MenuPopup.Item.of(Component.translatable("slate_menu.screenshots.view"), Icon.EYE, () -> view(s)));
        items.add(MenuPopup.Item.of(Component.translatable("slate_menu.screenshots.copy"), Icon.COPY, () -> copy(s)));
        items.add(MenuPopup.Item.of(Component.translatable("slate_menu.screenshots.rename"), Icon.EDIT, () -> rename(s)));
        final ScreenshotShareProvider share = SlateMenuApi.screenshotShareProvider();
        if (share != null) items.add(MenuPopup.Item.of(Component.translatable("slate_menu.screenshots.share"), Icon.SEND, () -> share.share(s.path())));
        items.add(MenuPopup.Item.of(Component.translatable("slate_menu.screenshots.open_folder"), Icon.FOLDER, () -> Util.getPlatform().openPath(Screenshots.dir())));
        items.add(MenuPopup.Item.of(Component.translatable("slate_menu.screenshots.copy_path"), Icon.LINK, () -> {
            minecraft.keyboardHandler.setClipboard(s.path().toAbsolutePath().toString());
            SlateToasts.show(Component.translatable("slate.copied"), Component.literal(s.fileName()), Icon.COPY);
        }));
        items.add(MenuPopup.Item.sep());
        items.add(MenuPopup.Item.danger(Component.translatable("slate_menu.screenshots.delete"), Icon.TRASH, () -> confirmDelete(s)));
        SlateContextMenu.open(lastMx, lastMy, items);
    }

    private void view(final Screenshots.Shot s) {
        final int index = shown.indexOf(s);
        minecraft.setScreen(new ScreenshotViewer(this, new ArrayList<>(shown), Math.max(0, index)));
    }

    static void copy(final Screenshots.Shot s) {
        ClipboardImages.copy(s.path(), ok -> {
            if (ok) SlateToasts.show(Component.translatable("slate_menu.screenshots.copied"), Component.literal(s.fileName()), Icon.COPY);
            else SlateToasts.show(Component.translatable("slate_menu.screenshots.copy_unsupported"), Component.translatable("slate_menu.screenshots.copy_unsupported_body"), Icon.WARNING);
        });
    }

    private void rename(final Screenshots.Shot s) {
        SlateModal.prompt(Component.translatable("slate_menu.screenshots.rename"), Component.translatable("slate_menu.screenshots.rename_body"), s.name(), name -> {
            if (name.isBlank() || name.trim().equals(s.name())) return;
            if (Screenshots.rename(s, name)) reload();
            else SlateToasts.show(Component.translatable("slate_menu.screenshots.rename_failed"), Component.literal(name), Icon.WARNING);
        });
    }

    private void confirmDelete(final Screenshots.Shot s) {
        SlateModal.confirmDanger(Component.translatable("slate_menu.screenshots.delete"), Component.translatable("slate_menu.screenshots.delete_body", s.fileName()),
            Component.translatable("slate_menu.screenshots.delete"), () -> {
                if (Screenshots.delete(s)) {
                    if (all != null) all.remove(s);
                    if (selected == s) selected = null;
                    rebuild();
                    updateSelection();
                    SlateToasts.show(Component.translatable("slate_menu.screenshots.deleted"), Component.literal(s.fileName()), Icon.TRASH);
                }
            });
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        final boolean typing = search != null && search.isFocused();
        if (!typing && keyCode == 47 && search != null) { setFocused(search); search.setFocused(true); return true; }
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (typing) return false;
        if (keyCode == 294) { reload(); return true; }
        if (keyCode == 261 && selected != null) { confirmDelete(selected); return true; }
        if ((keyCode == 257 || keyCode == 335) && selected != null) { view(selected); return true; }
        if (hasControlDown() && keyCode == 67 && selected != null) { copy(selected); return true; }
        if (!shown.isEmpty()) {
            final int i = selected == null ? -1 : shown.indexOf(selected);
            int next = switch (keyCode) {
                case 262 -> i + 1;
                case 263 -> i - 1;
                case 264 -> i < 0 ? 0 : i + cols;
                case 265 -> i - cols;
                default -> Integer.MIN_VALUE;
            };
            if (next != Integer.MIN_VALUE) {
                next = Math.max(0, Math.min(shown.size() - 1, next));
                final Screenshots.Shot s = shown.get(next);
                select(s);
                final ScreenshotCard card = cards.get(s);
                if (card != null && grid != null) grid.ensureVisible(card);
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
            SlateSpinner.draw(g, gridRect.centerX() - 8, gridRect.centerY() - 20, 16, van ? 0xFFFFFFFF : p.accent());
            SlateDraw.textCentered(g, Component.translatable("slate_menu.screenshots.loading"), gridRect.centerX(), gridRect.centerY() + 2, muted);
        } else if (all != null && all.isEmpty()) {
            final Component h = Fonts.heading(Component.translatable("slate_menu.screenshots.empty"));
            g.drawString(font, h, gridRect.centerX() - font.width(h) / 2, gridRect.centerY() - 16, p.text(), van);
            SlateDraw.textCentered(g, Component.translatable("slate_menu.screenshots.empty_hint", minecraft.options.keyScreenshot.getTranslatedKeyMessage()), gridRect.centerX(), gridRect.centerY() - 2, muted);
        } else if (shown.isEmpty() && all != null) {
            SlateDraw.textCentered(g, Component.translatable("slate_menu.screenshots.none_match"), gridRect.centerX(), gridRect.centerY() - 4, muted);
        }
        if (all != null) {
            final Component count = Component.translatable("slate_menu.screenshots.count", shown.size(), all.size());
            final Rect c = contentRect();
            g.drawString(font, count, c.x(), c.bottom() - 16, van ? 0xFFA0A0A0 : p.textDim(), van);
        }
    }
}
