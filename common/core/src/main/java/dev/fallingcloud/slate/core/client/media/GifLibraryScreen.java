package dev.fallingcloud.slate.core.client.media;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.media.Attachment;
import dev.fallingcloud.slate.core.media.GifSearch;
import dev.fallingcloud.slate.core.media.GifStore;
import dev.fallingcloud.slate.core.media.MediaCache;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateSounds;
import dev.fallingcloud.slate.core.widget.SlateTabs;
import dev.fallingcloud.slate.core.widget.SlateTextField;
import dev.fallingcloud.slate.core.widget.SlateTooltips;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The Discord-style GIF picker: Search (Tenor online, when a key is configured), Favourites and Recent.
 * Every card is an animated preview; clicking hands its link to {@code onPick} (the chat module sends it
 * to the current channel), the star toggles favourite. The text row doubles as the search box on the
 * Search tab and as a paste-a-link row on the others, so any GIF URL can be added by hand with no key.
 */
public final class GifLibraryScreen extends SlateScreen {

    private enum Tab { SEARCH, FAVORITES, RECENT }

    private static final int CARD = 72, GAP = 6;

    /** Remembered per session so reopening the library keeps the last tab and results. */
    private static Tab tab = Tab.FAVORITES;
    private static List<String> searchResults = List.of();
    private static String lastQuery = "";
    private static boolean trendingLoaded;

    private final Consumer<String> onPick;
    private final Anim scroll = new Anim(0, 160, Ease.OUT_CUBIC);
    private SlateTabs tabs;
    private SlateTextField field;
    private SlateButton action;
    private List<String> urls = List.of();
    private boolean searching;
    private GifSearch.Result.State searchState = GifSearch.Result.State.OK;
    private int gridTop, gridBottom;
    private int hoverIndex = -1;

    /**
     * @param onPick receives the chosen GIF url; the screen returns to {@code parent} afterwards
     */
    public GifLibraryScreen(@Nullable final Screen parent, final Consumer<String> onPick) {
        super(Component.translatable("slate.gifs.title"), parent);
        this.onPick = onPick;
        this.maxContentWidth = 560;
    }

    @Override
    protected void build() {
        final Rect c = contentRect();
        tabs = add(new SlateTabs(c.x(), c.y() + 2, Math.min(c.w(), 240), List.of(
            new SlateTabs.Tab(Component.translatable("slate.gifs.search"), Icon.SEARCH),
            new SlateTabs.Tab(Component.translatable("slate.gifs.favorites"), Icon.STAR),
            new SlateTabs.Tab(Component.translatable("slate.gifs.recent"), Icon.HISTORY)),
            tab.ordinal(), i -> switchTab(Tab.values()[i])));
        final int fy = c.y() + 30;
        field = add(new SlateTextField(c.x(), fy, c.w() - 92, 20, Component.translatable("slate.gifs.title")));
        field.maxLength(512);
        field.onEnter(this::onAction);
        action = add(new SlateButton(c.right() - 86, fy, 86, 20, Component.empty(), this::onAction).variant(SlateButton.Variant.PRIMARY));
        gridTop = fy + 28;
        gridBottom = c.bottom();
        applyTabChrome();
        refresh();
        if (tab == Tab.SEARCH && searchResults.isEmpty() && !trendingLoaded && GifSearch.hasKey()) loadTrending();
    }

    private void switchTab(final Tab t) {
        tab = t;
        scroll.snap(0);
        applyTabChrome();
        refresh();
        if (t == Tab.SEARCH && searchResults.isEmpty() && !trendingLoaded && GifSearch.hasKey()) loadTrending();
    }

    private void applyTabChrome() {
        if (tab == Tab.SEARCH) {
            field.placeholder(Component.translatable("slate.gifs.search_hint"));
            field.icon(Icon.SEARCH);
            field.setValue(lastQuery);
            action.setMessage(Component.translatable("slate.gifs.go"));
            action.icon(Icon.SEARCH);
        } else {
            field.placeholder(Component.translatable("slate.gifs.url_hint"));
            field.icon(Icon.LINK);
            field.setValue("");
            action.setMessage(Component.translatable("slate.gifs.add"));
            action.icon(Icon.PLUS);
        }
    }

    private void onAction() {
        if (tab == Tab.SEARCH) {
            runSearch();
            return;
        }
        final String url = field.getValue().trim();
        if (url.startsWith("http")) {
            if (!GifStore.isFavorite(url)) GifStore.toggleFavorite(url);
            field.setValue("");
            switchTab(Tab.FAVORITES);
            tabs.select(Tab.FAVORITES.ordinal());
        } else {
            field.setInvalid(true);
        }
    }

    private void runSearch() {
        final String q = field.getValue().trim();
        if (searching) return;
        if (q.isEmpty()) { loadTrending(); return; }
        lastQuery = q;
        searching = true;
        GifSearch.search(q, result -> {
            searching = false;
            searchState = result.state();
            searchResults = result.urls();
            if (tab == Tab.SEARCH) refresh();
        });
    }

    private void loadTrending() {
        if (searching) return;
        searching = true;
        trendingLoaded = true;
        GifSearch.trending(result -> {
            searching = false;
            searchState = result.state();
            if (result.state() == GifSearch.Result.State.OK && searchResults.isEmpty()) searchResults = result.urls();
            if (tab == Tab.SEARCH) refresh();
        });
    }

    private void refresh() {
        urls = switch (tab) {
            case SEARCH -> searchResults;
            case FAVORITES -> GifStore.favorites();
            case RECENT -> GifStore.recent();
        };
        scroll.set((float) Math.min(scroll.target(), maxScroll()));
    }

    // ------------------------------------------------------------------ grid

    private Rect grid() {
        final Rect c = contentRect();
        return new Rect(c.x(), gridTop, c.w(), Math.max(0, gridBottom - gridTop));
    }

    private int columns() {
        return Math.max(1, (grid().w() + GAP) / (CARD + GAP));
    }

    private int contentHeight() {
        final int rows = (urls.size() + columns() - 1) / columns();
        return rows * (CARD + GAP);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - grid().h());
    }

    private int cardX(final int i) {
        final Rect gr = grid();
        final int cols = columns();
        final int used = cols * (CARD + GAP) - GAP;
        return gr.x() + (gr.w() - used) / 2 + (i % cols) * (CARD + GAP);
    }

    private int cardY(final int i) {
        return grid().y() + (i / columns()) * (CARD + GAP) - Math.round(scroll.get());
    }

    private int cardAt(final double mx, final double my) {
        final Rect gr = grid();
        if (!gr.contains(mx, my)) return -1;
        for (int i = 0; i < urls.size(); i++) {
            final int x = cardX(i), y = cardY(i);
            if (mx >= x && mx < x + CARD && my >= y && my < y + CARD) return i;
        }
        return -1;
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final Rect gr = grid();
        hoverIndex = cardAt(mouseX, mouseY);
        if (urls.isEmpty()) {
            final Component msg;
            if (tab == Tab.SEARCH) {
                msg = searching ? Component.translatable("slate.media.loading")
                    : searchState == GifSearch.Result.State.NO_KEY ? Component.translatable("slate.gifs.no_key")
                    : searchState == GifSearch.Result.State.ERROR ? Component.translatable("slate.gifs.search_error")
                    : Component.translatable("slate.gifs.search_prompt");
            } else {
                msg = Component.translatable(tab == Tab.FAVORITES ? "slate.gifs.empty_favorites" : "slate.gifs.empty_recent");
            }
            int y = gr.centerY() - 4;
            for (final net.minecraft.util.FormattedCharSequence line : font.split(msg, Math.min(300, gr.w() - 20))) {
                g.drawString(font, line, gr.centerX() - font.width(line) / 2, y, p.textMuted(), t.isVanilla());
                y += 11;
            }
            return;
        }
        SlateDraw.scissor(g, gr.x(), gr.y(), gr.w(), gr.h());
        for (int i = 0; i < urls.size(); i++) {
            final int x = cardX(i), y = cardY(i);
            if (y > gr.bottom() || y + CARD < gr.y()) continue;
            drawCard(g, urls.get(i), x, y, i == hoverIndex, mouseX, mouseY);
        }
        SlateDraw.unscissor(g);
        if (maxScroll() > 0) {
            final int bx = gr.right() - 3;
            final int barH = Math.max(12, (int) ((long) gr.h() * gr.h() / Math.max(1, contentHeight())));
            final int barY = gr.y() + (int) ((gr.h() - barH) * (scroll.get() / Math.max(1, maxScroll())));
            SlateDraw.pixelRound(g, bx, gr.y(), 3, gr.h(), Colors.withAlpha(t.isVanilla() ? 0xFF000000 : p.surface(), 0x80), 1);
            SlateDraw.pixelRound(g, bx, barY, 3, barH, t.isVanilla() ? 0xFFC0C0C0 : p.borderStrong(), 1);
        }
        if (hoverIndex >= 0) {
            final String url = urls.get(hoverIndex);
            final boolean onStar = onStar(hoverIndex, mouseX, mouseY);
            SlateTooltips.request(Component.translatable(onStar ? (GifStore.isFavorite(url) ? "slate.gifs.unstar" : "slate.gifs.star") : "slate.gifs.send"), null);
        }
    }

    private boolean onStar(final int i, final double mx, final double my) {
        final int x = cardX(i), y = cardY(i);
        return mx >= x + CARD - 16 && mx < x + CARD && my >= y && my < y + 16;
    }

    private void drawCard(final GuiGraphics g, final String url, final int x, final int y, final boolean hover, final int mouseX, final int mouseY) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        if (t.isVanilla()) {
            g.fill(x, y, x + CARD, y + CARD, 0x90000000);
            SlateDraw.outline(g, x, y, CARD, CARD, hover ? 0xFFFFFFFF : 0xFF404040, 0);
        } else {
            SlateDraw.pixelRound(g, x, y, CARD, CARD, hover ? p.surfaceHover() : p.surface(), t.radius());
            SlateDraw.outline(g, x, y, CARD, CARD, hover ? p.accent() : p.border(), t.radius());
        }
        final MediaCache.Entry e = MediaCache.get(new Attachment(Attachment.Kind.IMAGE_URL, Attachment.idForUrl(url), url, 0));
        SlateDraw.scissor(g, x + 1, Math.max(grid().y(), y + 1), CARD - 2, CARD - 2);
        MediaDraw.drawFit(g, e, x + 2, y + 2, CARD - 4, CARD - 4, 1f, true, true);
        SlateDraw.unscissor(g);
        final boolean fav = GifStore.isFavorite(url);
        final boolean starHover = hover && onStar(urls.indexOf(url), mouseX, mouseY);
        if (fav || hover) {
            g.fill(x + CARD - 15, y + 1, x + CARD - 1, y + 15, 0x80000000);
            Icons.draw(g, fav ? Icon.STAR_FILLED : Icon.STAR, x + CARD - 14, y + 2, 12,
                fav ? Colors.withAlpha(p.warning(), starHover ? 0xFF : 0xE0) : Colors.withAlpha(0xFFFFFFFF, starHover ? 0xFF : 0xA0));
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        final int i = cardAt(mouseX, mouseY);
        if (i >= 0 && button == 0) {
            final String url = urls.get(i);
            if (onStar(i, mouseX, mouseY)) {
                GifStore.toggleFavorite(url);
                SlateSounds.tick();
                refresh();
                return true;
            }
            SlateSounds.click();
            GifStore.noteRecent(url);
            if (onPick != null) onPick.accept(url);
            back();
            return true;
        }
        if (i >= 0 && button == 1) {
            GifStore.toggleFavorite(urls.get(i));
            SlateSounds.tick();
            refresh();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double dx, final double dy) {
        if (super.mouseScrolled(mouseX, mouseY, dx, dy)) return true;
        if (!grid().contains(mouseX, mouseY) && mouseY < gridTop) return false;
        scroll.set(Mth.clamp((float) (scroll.target() - dy * (CARD + GAP) / 2), 0, maxScroll()));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
