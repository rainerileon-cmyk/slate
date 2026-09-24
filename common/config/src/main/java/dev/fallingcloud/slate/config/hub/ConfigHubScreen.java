package dev.fallingcloud.slate.config.hub;

import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.config.curated.CuratedPages;
import dev.fallingcloud.slate.config.page.AudioPage;
import dev.fallingcloud.slate.config.page.CategoryPage;
import dev.fallingcloud.slate.config.page.ControlsPage;
import dev.fallingcloud.slate.config.page.CuratedPage;
import dev.fallingcloud.slate.config.page.FavouritesPage;
import dev.fallingcloud.slate.config.page.GameplayGeneralPage;
import dev.fallingcloud.slate.config.page.InterfacePage;
import dev.fallingcloud.slate.config.page.LanguagePage;
import dev.fallingcloud.slate.config.page.ModsPage;
import dev.fallingcloud.slate.config.page.PresetsPage;
import dev.fallingcloud.slate.config.page.ResourcePacksPage;
import dev.fallingcloud.slate.config.page.ShaderPacksPage;
import dev.fallingcloud.slate.config.page.SimplePages;
import dev.fallingcloud.slate.config.page.SkinPage;
import dev.fallingcloud.slate.config.page.VideoPage;
import dev.fallingcloud.slate.config.search.SearchIndex;
import dev.fallingcloud.slate.config.search.SearchPopup;
import dev.fallingcloud.slate.config.ui.ApplyQueue;
import dev.fallingcloud.slate.config.ui.ConfigSearchField;
import dev.fallingcloud.slate.config.ui.ConfigTextField;
import dev.fallingcloud.slate.config.ui.Entrance;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.TabHost;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.screen.ScreenSwaps;
import dev.fallingcloud.slate.core.screen.SidebarPage;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The unified settings screen. Sidebar: Video, Audio, Controls, Gameplay, Interface, Multiplayer,
 * Customization, Language &amp; Accessibility, Favourites, Presets, then any curated pages a modpack ships.
 * Every page shows its sections as top tabs; the four categories hold whole pages as tabs (see
 * {@link CategoryPage}). A header search filters the tab on screen and lists hits everywhere else, reset
 * works on the current tab, and an escape hatch opens vanilla's options.
 *
 * <p>Pages are opened by path: a sidebar id, {@code category/tab}, or {@code page/tab} for a top tab of an
 * option page (e.g. {@code controls/keys}); the pre-tabs ids ({@code chat}, {@code packs}, {@code mods},
 * {@code language}, ...) are aliases. Keyboard: Ctrl+Tab cycles the sidebar, Ctrl+PgUp/PgDn the top tabs,
 * Ctrl+Shift+PgUp/PgDn the small tabs under them.</p>
 */
public final class ConfigHubScreen extends SidebarScreen {

    public static final String GAMEPLAY = "gameplay", MULTIPLAYER = "multiplayer", CUSTOMIZATION = "customization",
        LANGUAGE_ACCESSIBILITY = "language_accessibility";

    /** Page ids from before the categories, and handy shortcuts. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
        Map.entry("chat", "multiplayer/chat"),
        Map.entry("online", "multiplayer/online"),
        Map.entry("mods", "customization/mods"),
        Map.entry("packs", "customization/packs"),
        Map.entry("resourcepacks", "customization/packs"),
        Map.entry("shaders", "customization/shaders"),
        Map.entry("shaderpacks", "customization/shaders"),
        Map.entry("skin", "customization/skin"),
        Map.entry("language", "language_accessibility/language"),
        Map.entry("accessibility", "language_accessibility/accessibility"),
        Map.entry("general", "gameplay/general"),
        Map.entry("difficulty", "gameplay/general"),
        Map.entry("keybinds", "controls/keys"),
        Map.entry("keys", "controls/keys"),
        Map.entry("sound", "audio"),
        Map.entry("sounds", "audio"));

    @Nullable private String pendingPage;
    @Nullable private ConfigSearchField searchField;
    @Nullable private SlateIconButton resetButton;
    @Nullable private SearchIndex index;
    @Nullable private SearchPopup popup;
    @Nullable private OptionPageBase lastOptionPage;
    private String searchText = "";
    @Nullable private List<AbstractWidget> capture;
    private boolean firstBuild = true;

    public ConfigHubScreen(@Nullable final Screen parent, @Nullable final String page) {
        super(Component.translatable("slate_config.hub.title"), parent, "slate_config:hub");
        this.pendingPage = page;
    }

    @Override
    protected void definePages(final List<SidebarPage> pages) {
        pages.add(new VideoPage());
        pages.add(new AudioPage());
        pages.add(new ControlsPage());
        pages.add(new CategoryPage(GAMEPLAY, Component.translatable("slate_config.page.gameplay"), Icon.GAMEPLAY, List.of(new GameplayGeneralPage())));
        pages.add(new InterfacePage());
        pages.add(new CategoryPage(MULTIPLAYER, Component.translatable("slate_config.page.multiplayer"), Icon.MULTIPLAYER,
            List.of(new SimplePages.OnlinePage(), new SimplePages.ChatPage())));
        final List<SidebarPage> custom = new ArrayList<>(List.of(new ModsPage(), new ResourcePacksPage()));
        if (SlatePlatform.get().isModLoaded("iris")) custom.add(new ShaderPacksPage());
        custom.add(new SkinPage());
        pages.add(new CategoryPage(CUSTOMIZATION, Component.translatable("slate_config.page.customization"), Icon.CUSTOMIZE, custom));
        pages.add(new CategoryPage(LANGUAGE_ACCESSIBILITY, Component.translatable("slate_config.page.language_accessibility"), Icon.LANGUAGE,
            List.of(new LanguagePage(), new SimplePages.AccessibilityPage())));
        pages.add(new FavouritesPage());
        pages.add(new PresetsPage());
        for (final CuratedPages.PageDef def : CuratedPages.load()) pages.add(new CuratedPage(def));
        index();        // warms the search index and publishes every page-local binding for favourites/presets
    }

    @Override
    protected void build() {
        final List<AbstractWidget> built = new ArrayList<>();
        capture = built;
        super.build();
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.EXTERNAL, Component.translatable("slate_config.hub.vanilla_options"), () -> {
            final Minecraft mc = Minecraft.getInstance();
            ScreenSwaps.runUnswapped(() -> mc.setScreen(new OptionsScreen(this, mc.options)));
        }));
        resetButton = addHeaderAction(new SlateIconButton(0, 0, 20, Icon.UNDO, Component.translatable("slate_config.hub.reset_tab"), this::resetTab));
        final ConfigSearchField f = new ConfigSearchField(0, 0, Math.min(150, Math.max(90, width / 4)), this::onSearch);
        f.setValue(searchText);
        addHeaderAction(f);
        searchField = f;
        if (pendingPage != null) {
            final String p = pendingPage;
            pendingPage = null;
            open(p);
        }
        capture = null;
        if (firstBuild) {
            // Option rows are not reached by the screen's own entrance: stagger the page in here.
            firstBuild = false;
            int i = 0;
            for (final AbstractWidget w : built) i = Entrance.play(w, i);
        }
        trackOptionPage();
        updateResetButton();
    }

    @Override
    public <T extends GuiEventListener & Renderable & NarratableEntry> T addPageWidget(final T widget) {
        if (capture != null && widget instanceof AbstractWidget w) capture.add(w);
        return super.addPageWidget(widget);
    }

    /** The sidebar is as wide as its longest title needs ("Language & Accessibility"), within reason. */
    @Override
    protected int navWidth() {
        if (narrow()) return NAV_W_NARROW;
        int widest = 0;
        for (final SidebarPage p : pages()) widest = Math.max(widest, font.width(p.title()));
        return Math.max(NAV_W, Math.min(widest + 34, Math.max(NAV_W, width / 4)));
    }

    /** With top tabs right below, the title drops its rule: the strip's baseline is the divider. */
    @Override
    protected void renderPageTitle(final GuiGraphics g) {
        final SidebarPage page = currentPage();
        if (!(page instanceof TabHost th) || !th.hasTopTabs()) { super.renderPageTitle(g); return; }
        final Theme t = Theme.current();
        final Rect r = pageTitleRect();
        g.drawString(font, SlateDraw.truncate(Fonts.heading(page.title()), r.w()), r.x(), SlateDraw.textY(r.y(), r.h()),
            t.isVanilla() ? 0xFFFFFFFF : t.palette().text(), t.isVanilla());
    }

    // ------------------------------------------------------------------ navigation

    /**
     * Open a page by path: {@code video}, {@code multiplayer/chat}, {@code controls/keys},
     * {@code customization/shaders}, a curated page id, or an old id such as {@code chat} or {@code language}.
     */
    public void open(final String raw) {
        final String path = resolve(raw);
        final String[] parts = path.split("/", 3);
        SidebarPage page = find(parts[0]);
        if (page == null) page = find("curated:" + parts[0]);
        if (page == null) {
            SlateConfig.LOGGER.warn("[Slate Config] no settings page '{}'", raw);
            return;
        }
        if (parts.length > 1) {
            if (page instanceof CategoryPage cp) {
                cp.select(parts[1]);
                if (parts.length > 2 && cp.child(parts[1]) instanceof OptionPageBase o) o.selectTab(parts[2]);
            } else if (page instanceof OptionPageBase o) {
                o.selectTab(parts[1]);
            }
        }
        if (page != currentPage()) showPage(page.id());
    }

    /** Old ids and shortcuts to their current path ({@code chat} → {@code multiplayer/chat}); the rest is kept. */
    public static String resolve(final String raw) {
        final String p = raw.trim();
        if (p.startsWith("curated:")) return p;
        final int slash = p.indexOf('/');
        final String head = (slash < 0 ? p : p.substring(0, slash)).toLowerCase(Locale.ROOT);
        final String alias = ALIASES.get(head);
        if (alias == null) return p;
        return slash < 0 ? alias : alias + p.substring(slash);
    }

    @Nullable
    private SidebarPage find(final String id) {
        for (final SidebarPage p : pages()) if (p.id().equals(id)) return p;
        return null;
    }

    /** The page on screen, looking through a category to its current tab. */
    @Nullable
    public SidebarPage leaf() {
        final SidebarPage p = currentPage();
        return p instanceof CategoryPage cp ? cp.activeChild() : p;
    }

    /** Path of what is on screen: {@code video} or {@code multiplayer/chat}. */
    public String leafPath() {
        final SidebarPage p = currentPage();
        if (p instanceof CategoryPage cp) return cp.activePath();
        return p == null ? "" : p.id();
    }

    private void trackOptionPage() {
        if (leaf() instanceof OptionPageBase o && !(o instanceof FavouritesPage)) lastOptionPage = o;
    }

    /** The last "real" option page visited (source for "save current page as preset"). */
    @Nullable
    public OptionPageBase lastOptionPage() { return lastOptionPage; }

    @Override
    public void showPage(final int index) {
        final boolean changing = index != currentIndex() && index >= 0 && index < pages().size();
        if (changing) applyFilterTo(pages().get(index));       // before it builds, so it builds filtered once
        final List<AbstractWidget> outer = capture;               // build() may be capturing around us
        final List<AbstractWidget> built = changing ? new ArrayList<>() : null;
        capture = built;
        super.showPage(index);
        capture = outer;
        if (built != null) {
            int i = 0;
            for (final AbstractWidget w : built) i = Entrance.play(w, i);
        }
        trackOptionPage();
        updateResetButton();
    }

    @Override
    public void refreshPage() {
        super.refreshPage();
        trackOptionPage();
        updateResetButton();
    }

    private void applyFilterTo(@Nullable final SidebarPage p) {
        if (p instanceof OptionPageBase o) o.filter(searchText);
        else if (p instanceof CategoryPage c) c.filter(searchText);
        else if (p instanceof ModsPage m) m.filter(searchText);
    }

    /** Re-read every row of the tab on screen (after a preset or reset-all). */
    public void refreshOptionPages() {
        if (leaf() instanceof OptionPageBase o) o.refreshRows();
    }

    private void updateResetButton() {
        if (resetButton != null) resetButton.active = leaf() instanceof OptionPageBase;
    }

    // ------------------------------------------------------------------ search

    private SearchIndex index() {
        if (index == null) {
            final SearchIndex ix = new SearchIndex();
            for (final SidebarPage p : pages()) {
                try {
                    if (p instanceof OptionPageBase o) ix.addAll(o.searchEntries());
                    else if (p instanceof CategoryPage c) ix.addAll(c.searchEntries());
                    else if (p instanceof ModsPage m) ix.addAll(m.searchEntries());
                    else if (p instanceof PresetsPage pr) ix.addAll(pr.searchEntries());
                } catch (final Exception e) {
                    SlateConfig.LOGGER.warn("[Slate Config] cannot index page {}: {}", p.id(), e.toString());
                }
            }
            index = ix;
        }
        return index;
    }

    private void onSearch(final String text) {
        searchText = text;
        applyFilterTo(currentPage());
        final String q = text.trim();
        if (q.length() < 2 || searchField == null) { closePopup(); return; }
        final List<SearchIndex.Hit> hits = index().query(q, leafPath(), 24);
        if (hits.isEmpty()) { closePopup(); return; }
        if (popup == null || Popups.top() != popup) {
            popup = new SearchPopup(searchField.getX(), searchField.getY() + searchField.getHeight() + 2, Math.max(230, searchField.getWidth() + 90), this::jumpTo);
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
        open(hit.entry().path());
        final SidebarPage p = leaf();
        if (p instanceof OptionPageBase o) o.focusRow(hit.entry().binding().id());
        else if (p instanceof ModsPage m) m.filter(hit.entry().binding().label().getString());
        else if (p instanceof PresetsPage) { final Runnable r = hit.entry().binding().action(); if (r != null) r.run(); }
    }

    /** Reset the tab on screen: a category's tab is a whole page, a page's tab is one of its top tabs. */
    private void resetTab() {
        final SidebarPage cur = currentPage();
        final Component scope;
        final Runnable action;
        if (cur instanceof CategoryPage cp && cp.activeChild() instanceof OptionPageBase o) {
            scope = o.fullTitle();
            action = o::resetAll;
        } else if (cur instanceof OptionPageBase o) {
            scope = o.currentTabTitle();
            action = o::resetTab;
        } else return;
        SlateModal.confirmDanger(Component.translatable("slate_config.hub.reset_tab"), Component.translatable("slate_config.hub.reset_tab.body", scope),
            Component.translatable("slate_config.hub.reset_tab.confirm"), action);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (currentPage() instanceof ControlsPage cp && cp.captureKey(keyCode, scanCode, modifiers)) return true;
        if (hasControlDown() && (keyCode == 266 || keyCode == 267) && currentPage() instanceof TabHost th) {
            th.cycleTab(keyCode == 267 ? 1 : -1, hasShiftDown());
            return true;
        }
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
