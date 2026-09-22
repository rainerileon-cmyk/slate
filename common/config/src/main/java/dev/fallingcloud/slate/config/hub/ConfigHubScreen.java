package dev.fallingcloud.slate.config.hub;

import dev.fallingcloud.slate.config.curated.CuratedPages;
import dev.fallingcloud.slate.config.page.AudioPage;
import dev.fallingcloud.slate.config.page.ControlsPage;
import dev.fallingcloud.slate.config.page.CuratedPage;
import dev.fallingcloud.slate.config.page.FavouritesPage;
import dev.fallingcloud.slate.config.page.InterfacePage;
import dev.fallingcloud.slate.config.page.LanguagePage;
import dev.fallingcloud.slate.config.page.ModsPage;
import dev.fallingcloud.slate.config.page.PresetsPage;
import dev.fallingcloud.slate.config.page.ResourcePacksPage;
import dev.fallingcloud.slate.config.page.SimplePages;
import dev.fallingcloud.slate.config.page.VideoPage;
import dev.fallingcloud.slate.config.search.SearchIndex;
import dev.fallingcloud.slate.config.search.SearchPopup;
import dev.fallingcloud.slate.config.ui.ApplyQueue;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.screen.ScreenSwaps;
import dev.fallingcloud.slate.core.screen.SidebarPage;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.config.ui.ConfigSearchField;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.List;
import net.minecraft.client.Minecraft;
import dev.fallingcloud.slate.config.ui.ConfigTextField;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The unified settings screen: a sidebar of pages (vanilla, Slate, curated, favourites, presets), a
 * header search that filters the current page and lists hits on every other page, per-page reset, and
 * an escape hatch to vanilla's options.
 */
public final class ConfigHubScreen extends SidebarScreen {

    @Nullable private String pendingPage;
    @Nullable private ConfigSearchField searchField;
    @Nullable private SearchIndex index;
    @Nullable private SearchPopup popup;
    @Nullable private OptionPageBase lastOptionPage;
    private String searchText = "";

    public ConfigHubScreen(@Nullable final Screen parent, @Nullable final String page) {
        super(Component.translatable("slate_config.hub.title"), parent, "slate_config:hub");
        this.pendingPage = page;
    }

    @Override
    protected void definePages(final List<SidebarPage> pages) {
        pages.add(new VideoPage());
        pages.add(new AudioPage());
        pages.add(new ControlsPage());
        pages.add(new SimplePages.ChatPage());
        pages.add(new InterfacePage());
        pages.add(new SimplePages.MultiplayerPage());
        pages.add(new SimplePages.AccessibilityPage());
        pages.add(new LanguagePage());
        pages.add(new ResourcePacksPage());
        pages.add(new ModsPage());
        for (final CuratedPages.PageDef def : CuratedPages.load()) pages.add(new CuratedPage(def));
        pages.add(new FavouritesPage());
        pages.add(new PresetsPage());
        index();        // warms the search index and publishes every page-local binding for favourites/presets
    }

    @Override
    protected void build() {
        super.build();
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.EXTERNAL, Component.translatable("slate_config.hub.vanilla_options"), () -> {
            final Minecraft mc = Minecraft.getInstance();
            ScreenSwaps.runUnswapped(() -> mc.setScreen(new OptionsScreen(this, mc.options)));
        }));
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.UNDO, Component.translatable("slate_config.hub.reset_page"), this::resetPage));
        final ConfigSearchField f = new ConfigSearchField(0, 0, Math.min(150, Math.max(90, width / 4)), this::onSearch);
        f.setValue(searchText);
        addHeaderAction(f);
        searchField = f;
        if (pendingPage != null) {
            final String p = pendingPage;
            pendingPage = null;
            showPage(p.startsWith("curated:") || p.contains(":") ? p : resolvePageId(p));
        }
        trackOptionPage();
    }

    private String resolvePageId(final String p) {
        for (final SidebarPage page : pages()) if (page.id().equals(p) || page.id().equals("curated:" + p)) return page.id();
        return p;
    }

    private void trackOptionPage() {
        if (currentPage() instanceof OptionPageBase o && !(o instanceof FavouritesPage)) lastOptionPage = o;
    }

    /** The last "real" option page visited (source for "save current page as preset"). */
    @Nullable
    public OptionPageBase lastOptionPage() { return lastOptionPage; }

    @Override
    public void showPage(final int index) {
        super.showPage(index);
        trackOptionPage();
        applyFilterToPage();
    }

    private void applyFilterToPage() {
        final SidebarPage p = currentPage();
        if (p instanceof OptionPageBase o) o.filter(searchText);
        else if (p instanceof ModsPage m) m.filter(searchText);
    }

    /** Re-read every row of the current page (after a preset or reset-all). */
    public void refreshOptionPages() {
        if (currentPage() instanceof OptionPageBase o) o.refreshRows();
    }

    // ------------------------------------------------------------------ search

    private SearchIndex index() {
        if (index == null) {
            final SearchIndex ix = new SearchIndex();
            for (final SidebarPage p : pages()) {
                try {
                    if (p instanceof OptionPageBase o) ix.addAll(o.searchEntries());
                    else if (p instanceof ModsPage m) ix.addAll(m.searchEntries());
                    else if (p instanceof PresetsPage pr) ix.addAll(pr.searchEntries());
                } catch (final Exception e) {
                    dev.fallingcloud.slate.config.SlateConfig.LOGGER.warn("[Slate Config] cannot index page {}: {}", p.id(), e.toString());
                }
            }
            index = ix;
        }
        return index;
    }

    private void onSearch(final String text) {
        searchText = text;
        applyFilterToPage();
        final String q = text.trim();
        if (q.length() < 2 || searchField == null) { closePopup(); return; }
        final String cur = currentPage() == null ? "" : currentPage().id();
        final List<SearchIndex.Hit> hits = index().query(q, cur, 24);
        if (hits.isEmpty()) { closePopup(); return; }
        if (popup == null || Popups.top() != popup) {
            popup = new SearchPopup(searchField.getX(), searchField.getY() + searchField.getHeight() + 2, Math.max(220, searchField.getWidth() + 80), this::jumpTo);
            Popups.open(popup);
        }
        popup.setHits(hits);
    }

    private void closePopup() {
        if (popup != null) { Popups.close(popup); popup = null; }
    }

    private void jumpTo(final SearchIndex.Hit hit) {
        searchText = "";
        if (searchField != null) searchField.setValue("");
        showPage(hit.entry().pageId());
        final SidebarPage p = currentPage();
        if (p instanceof OptionPageBase o) o.focusRow(hit.entry().binding().id());
        else if (p instanceof ModsPage m) m.filter(hit.entry().binding().label().getString());
        else if (p instanceof PresetsPage) { final Runnable r = hit.entry().binding().action(); if (r != null) r.run(); }
    }

    private void resetPage() {
        if (!(currentPage() instanceof OptionPageBase o)) return;
        SlateModal.confirmDanger(Component.translatable("slate_config.hub.reset_page"), Component.translatable("slate_config.hub.reset_page.body", o.title()),
            Component.translatable("slate_config.hub.reset_page"), o::resetAll);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (currentPage() instanceof ControlsPage cp && cp.captureKey(keyCode, scanCode, modifiers)) return true;
        if (!typing() && searchField != null && (keyCode == 47 || (keyCode == 70 && hasControlDown()))) {
            setFocused(searchField);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** True when the innermost focused element is a text field (so "/" must type, not jump to search). */
    private boolean typing() {
        GuiEventListener f = getFocused();
        int guard = 0;
        while (f instanceof ContainerEventHandler c && c.getFocused() != null && guard++ < 8) f = c.getFocused();
        return f instanceof EditBox || f instanceof ConfigTextField;
    }

    /** Test hook: type into the header search as a user would. */
    public void debugSearch(final String text) {
        if (searchField == null) return;
        setFocused(searchField);
        searchField.setValue(text);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (currentPage() instanceof ControlsPage cp && cp.captureMouse(button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        super.tick();
        if (popup != null && searchField != null && !searchField.isFocused() && Popups.top() == popup) closePopup();
    }

    @Override
    public void removed() {
        ApplyQueue.flush();
        super.removed();
    }
}
