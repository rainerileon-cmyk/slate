package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.config.ConfigSettings;
import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.config.api.SettingsTabs;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionResolvers;
import dev.fallingcloud.slate.config.search.SearchIndex;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SidebarPage;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateTabStrip;
import dev.fallingcloud.slate.core.widget.SlateTabs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A settings page made of {@link Section}s, shown as top tabs instead of one long scroll: every section is
 * a tab (sections sharing a {@link Section#tab tab id} share one, shown as small secondary tabs or as
 * headers, see {@link #pills}); a page with a single section has no tab bar. The tab shown last is
 * remembered per page in config.json. Handles the header search filter (which shows every match of the page
 * at once, grouped by tab), "jump to row" (selects the owning tab, scrolls and flashes the row), reset of the
 * current tab, the search index and the tab switch animation. Subclasses only provide {@link #sections()}.
 *
 * <p>Inside a {@link CategoryHost} the page is "hosted": the category's strip already names the page, so all of
 * its sections go on that one tab under collapsible headers; there is never a second strip.</p>
 */
public abstract class OptionPageBase extends SidebarPage implements TabHost, Enterable {

    /** A top tab: its memory key, label and the sections it shows. */
    public record Tab(String key, Component title, List<Section> sections) {}

    private static final int STRIP_GAP = 6;

    protected SidebarScreen screen;
    protected Rect area;
    @Nullable protected SlateScrollPanel panel;
    private final List<OptionRow> rows = new ArrayList<>();
    private List<Section> cachedSections = List.of();
    private String filter = "";
    @Nullable private String pendingFocus;
    private double keepScroll = -1;
    @Nullable private Component emptyText;

    // Hosting inside a category page
    @Nullable private String hostPath;
    @Nullable private Component hostTitle;

    // Tabs of the last build
    private List<Tab> tabs = List.of();
    private int tabIndex;
    private boolean promoted;
    private List<Section> subs = List.of();
    private int subIndex;
    @Nullable private String wantTab, wantSub;
    @Nullable private SlateTabStrip strip, subStrip;
    private int stripFrom = -1, subFrom = -1;
    private int enterDir;
    private boolean subChanged;
    /** Entering as a category tab: the page's own strip animates in too. */
    private boolean enterAll;

    protected OptionPageBase(final String id, final Component title, final Icon icon) {
        super(id, title, icon);
    }

    /** The page's content. Called on every (re)build so values are fresh; keep it cheap. */
    protected abstract List<Section> sections();

    /**
     * How a tab holding several sections shows them: false = all of them on one scrolling tab under headers
     * (collapsible unless {@link Section#fixed}), true = small secondary tabs (one section at a time). Default:
     * headers; a second strip of tabs under the first is never wanted, and a hosted page (a category's tab) never
     * gets one at all.
     */
    protected boolean pills(final String tabKey) { return false; }

    /** Optional line under the rows when nothing is there (e.g. "No favourites yet"). */
    protected OptionPageBase emptyText(final Component c) { this.emptyText = c; return this; }

    public List<OptionRow> rows() { return rows; }

    /** Every section of the page (all tabs), as last built. */
    public List<Section> currentSections() { return cachedSections; }

    /** Called by a category page that shows this page as one of its tabs. */
    public OptionPageBase hosted(final String path, final Component categoryTitle) {
        this.hostPath = path;
        this.hostTitle = categoryTitle;
        return this;
    }

    public boolean isHosted() { return hostPath != null; }

    /** Where this page is opened from: its id, or {@code category/tab} when hosted. */
    public String path() { return hostPath != null ? hostPath : id(); }

    /** Title with its category when hosted ("Multiplayer › Chat"). */
    public Component fullTitle() {
        return hostTitle == null ? title() : Component.literal(hostTitle.getString() + " › " + title().getString());
    }

    private String memKey() { return path(); }

    /** The page's sections plus tabs other modules contributed to it through {@link SettingsTabs}. */
    private List<Section> collect() {
        final List<Section> out = new ArrayList<>(sections());
        if (!isHosted()) {
            for (final SettingsTabs.Tab t : SettingsTabs.tabs(id())) {
                try {
                    for (final Section s : t.sections().get()) if (s != null) out.add(s.tab(t.id(), t.title()));
                } catch (final Exception e) {
                    SlateConfig.LOGGER.warn("[Slate Config] tab {} of {} failed to build: {}", t.id(), id(), e.toString());
                }
            }
        }
        return out;
    }

    private static List<Tab> group(final List<Section> visible) {
        final Map<String, List<Section>> by = new LinkedHashMap<>();
        final Map<String, Component> titles = new LinkedHashMap<>();
        for (final Section s : visible) {
            by.computeIfAbsent(s.tabKey(), k -> new ArrayList<>()).add(s);
            titles.putIfAbsent(s.tabKey(), s.tabLabel());
        }
        final List<Tab> out = new ArrayList<>();
        for (final Map.Entry<String, List<Section>> e : by.entrySet()) out.add(new Tab(e.getKey(), titles.get(e.getKey()), e.getValue()));
        return out;
    }

    private static int indexOf(final List<Tab> tabs, @Nullable final String key) {
        if (key == null) return -1;
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).key().equals(key)) return i;
        return -1;
    }

    private static int sectionIndex(final List<Section> secs, @Nullable final String id) {
        if (id == null) return -1;
        for (int i = 0; i < secs.size(); i++) if (secs.get(i).id.equals(id)) return i;
        return -1;
    }

    // ------------------------------------------------------------------ building

    @Override
    public void build(final SidebarScreen screen, final Rect area) {
        this.screen = screen;
        this.area = area;
        rows.clear();
        cachedSections = collect();
        final List<Section> visible = new ArrayList<>();
        for (final Section s : cachedSections) if (!s.isEmpty()) visible.add(s);
        final double stripScroll = strip != null ? strip.scrollOffset() : 0;
        final double subScroll = subStrip != null ? subStrip.scrollOffset() : 0;
        // A strip rebuilt under the keyboard keeps the focus, so arrow keys keep walking the tabs.
        final boolean keyboard = net.minecraft.client.Minecraft.getInstance().getLastInputType().isKeyboard();
        final boolean stripFocused = keyboard && strip != null && strip.isFocused();
        final boolean subFocused = keyboard && subStrip != null && subStrip.isFocused();
        strip = null;
        subStrip = null;
        final String f = filter.toLowerCase(Locale.ROOT).trim();
        if (!f.isEmpty()) {
            buildFiltered(visible);
            return;
        }

        // Top level: the page's tabs, or, when it has a single tab of several sections, those sections. A hosted page
        // is one tab of its category already: everything it has goes on that one tab, under headers.
        final List<Tab> groups = group(visible);
        final List<Tab> primary;
        if (isHosted()) {
            promoted = false;
            primary = visible.isEmpty() ? List.of() : List.of(new Tab(id(), title(), visible));
        } else {
            promoted = groups.size() == 1 && groups.get(0).sections().size() > 1 && pills(groups.get(0).key());
            if (promoted) {
                primary = new ArrayList<>();
                for (final Section s : groups.get(0).sections()) primary.add(new Tab(s.id, s.title, List.of(s)));
            } else primary = groups;
        }
        tabs = primary;
        int ti = indexOf(primary, promoted ? wantSub : wantTab);
        if (ti >= 0) ConfigSettings.setLastTab(memKey(), primary.get(ti).key());
        else ti = indexOf(primary, ConfigSettings.lastTab(memKey()));
        tabIndex = Math.max(0, ti);

        int y = area.y();
        if (primary.size() > 1) {
            final SlateTabStrip s = new SlateTabStrip(area.x(), y, area.w(), labels(primary), tabIndex, this::selectTab)
                .style(isHosted() ? SlateTabStrip.Style.PILLS : SlateTabStrip.Style.UNDERLINE);
            s.animateFrom(stripFrom >= 0 ? stripFrom : tabIndex, stripScroll);
            screen.addPageWidget(s);
            if (stripFocused) screen.setFocused(s);
            strip = s;
            y += s.getHeight() + STRIP_GAP;
        }
        final Tab cur = primary.isEmpty() ? null : primary.get(tabIndex);
        List<Section> shown = cur == null ? List.of() : cur.sections();

        // Second level: small tabs for the sections of the current tab (not when hosted: that level is taken).
        subs = List.of();
        if (cur != null && !promoted && !isHosted() && shown.size() > 1 && pills(cur.key())) {
            subs = shown;
            final String subKey = memKey() + "/" + cur.key();
            int si = cur.key().equals(wantTab) ? sectionIndex(shown, wantSub) : -1;
            if (si >= 0) ConfigSettings.setLastTab(subKey, shown.get(si).id);
            else si = sectionIndex(shown, ConfigSettings.lastTab(subKey));
            subIndex = Math.max(0, si);
            final List<SlateTabs.Tab> labels = new ArrayList<>();
            for (final Section s : shown) labels.add(new SlateTabs.Tab(s.title));
            final SlateTabStrip ss = new SlateTabStrip(area.x(), y, area.w(), labels, subIndex, this::selectSub).style(SlateTabStrip.Style.PILLS);
            ss.animateFrom(subFrom >= 0 ? subFrom : subIndex, subChanged ? 0 : subScroll);
            screen.addPageWidget(ss);
            if (subFocused) screen.setFocused(ss);
            subStrip = ss;
            y += ss.getHeight() + STRIP_GAP;
            shown = List.of(shown.get(subIndex));
        }
        wantTab = null;
        wantSub = null;

        final boolean headers = shown.size() > 1
            || (shown.size() == 1 && primary.size() <= 1 && shown.get(0).collapsible && !shown.get(0).title.getString().isEmpty() && showSingleHeader());
        final TabPanel p = new TabPanel(area.x(), y, area.w(), Math.max(20, area.bottom() - y));
        panel = p;
        final int w = area.w() - 8;
        int cy = 0;
        for (final Section s : shown) cy = layoutSection(p, s, cy, w, headers, false, null);
        if (rows.isEmpty() && emptyText != null) {
            final SlateLabel l = new SlateLabel(0, cy + 8, w - 16, emptyText).style(SlateLabel.Style.MUTED).wrap(true);
            p.add(l, 8, cy + 8);
            cy += l.getHeight() + 16;
        }
        finish(p, cy);
    }

    private static List<SlateTabs.Tab> labels(final List<Tab> ts) {
        final List<SlateTabs.Tab> out = new ArrayList<>();
        for (final Tab t : ts) out.add(new SlateTabs.Tab(t.title()));
        return out;
    }

    /** Header search active: every match of the page, grouped under "Tab › Section" headers. */
    private void buildFiltered(final List<Section> visible) {
        final TabPanel p = new TabPanel(area.x(), area.y(), area.w(), area.h());
        panel = p;
        final int w = area.w() - 8;
        final List<Tab> groups = group(visible);
        int cy = 0;
        for (final Tab t : groups) {
            for (final Section s : t.sections()) {
                final String head = groups.size() > 1 && !t.title().getString().equals(s.title.getString())
                    ? t.title().getString() + " › " + s.title.getString() : s.title.getString();
                cy = layoutSection(p, s, cy, w, true, true, Component.literal(head));
            }
        }
        tabs = List.of();
        subs = List.of();
        finish(p, cy);
    }

    /** Adds one section's header (optional), description and rows; returns the next y. */
    private int layoutSection(final SlateScrollPanel p, final Section s, int y, final int w, final boolean headers, final boolean filtering,
                              @Nullable final Component headerTitle) {
        final String f = filter.toLowerCase(Locale.ROOT).trim();
        final List<Section.Item> items = new ArrayList<>();
        for (final Section.Item it : s.items) {
            if (it.binding() == null) { if (!filtering) items.add(it); continue; }
            if (!filtering || matches(it.binding(), s, f)) items.add(it);
        }
        if (items.isEmpty()) return y;
        final String key = id() + ":" + s.id;
        final boolean titled = headerTitle != null ? !headerTitle.getString().isEmpty() : !s.title.getString().isEmpty();
        final boolean foldable = !filtering && s.collapsible;
        final boolean collapsed = headers && titled && foldable && ConfigSettings.isCollapsed(key);
        if (headers && titled) {
            final SectionHeader h = new SectionHeader(0, y, w, headerTitle != null ? headerTitle : s.title, s.bindings().size(), collapsed,
                foldable ? () -> { ConfigSettings.setCollapsed(key, !ConfigSettings.isCollapsed(key)); rebuild(); } : null, 0);
            p.add(h, 0, y);
            y += SectionHeader.HEIGHT + 2;
        }
        if (s.description != null && !collapsed && !filtering) {
            final SlateLabel d = new SlateLabel(0, y, w - 8, s.description).style(SlateLabel.Style.MUTED).wrap(true);
            p.add(d, 4, y + 2);
            y += d.getHeight() + 8;
        }
        if (collapsed) return y + 4;
        for (final Section.Item it : items) {
            if (it.binding() != null) {
                OptionResolvers.publish(it.binding());
                final OptionRow row = new OptionRow(0, y, w, it.binding(), 0).onChanged(this::onRowChanged);
                rows.add(row);
                p.add(row, 0, y);
                y += OptionRow.HEIGHT + 2;
            } else if (it.custom() != null) {
                final AbstractWidget cw = it.custom().apply(w);
                if (cw != null) { p.add(cw, 0, y + 2); y += cw.getHeight() + 6; }
            }
        }
        return y + 6;
    }

    private void finish(final TabPanel p, final int contentY) {
        p.setContentHeight(contentY + 4);
        screen.addPageWidget(p);
        if (keepScroll >= 0) { p.snapScroll(keepScroll); keepScroll = -1; }
        if (enterDir != 0) {
            p.slideFrom(enterDir);
            int i = 0;
            if (enterAll && strip != null) i = Entrance.play(strip, i);
            if ((subChanged || enterAll) && subStrip != null) i = Entrance.play(subStrip, i);
            Entrance.play(p, i);
        }
        enterDir = 0;
        enterAll = false;
        stripFrom = -1;
        subFrom = -1;
        subChanged = false;
        if (pendingFocus != null) {
            final String id = pendingFocus;
            pendingFocus = null;
            for (final OptionRow r : rows) {
                if (r.binding().id().equals(id)) { p.ensureVisible(r); r.flash(); break; }
            }
        }
    }

    /** Pages with one section normally skip the header; override to keep it (e.g. curated pages). */
    protected boolean showSingleHeader() { return false; }

    private static boolean matches(final OptionBinding b, final Section s, final String f) {
        final String hay = (b.searchText() + " " + s.title.getString() + " " + s.tabLabel().getString()).toLowerCase(Locale.ROOT);
        for (final String tok : f.split("\\s+")) if (!tok.isEmpty() && !hay.contains(tok)) return false;
        return true;
    }

    // ------------------------------------------------------------------ tabs

    private void selectTab(final int i) {
        if (i == tabIndex || i < 0 || i >= tabs.size()) return;
        ApplyQueue.flush();
        stripFrom = tabIndex;
        enterDir = i > tabIndex ? 1 : -1;
        subChanged = true;
        tabIndex = i;
        ConfigSettings.setLastTab(memKey(), tabs.get(i).key());
        keepScroll = 0;
        if (screen != null) screen.refreshPage();
    }

    private void selectSub(final int i) {
        if (i == subIndex || i < 0 || i >= subs.size() || tabs.isEmpty()) return;
        ApplyQueue.flush();
        subFrom = subIndex;
        enterDir = i > subIndex ? 1 : -1;
        subIndex = i;
        ConfigSettings.setLastTab(memKey() + "/" + tabs.get(tabIndex).key(), subs.get(i).id);
        keepScroll = 0;
        if (screen != null) screen.refreshPage();
    }

    /** Show the tab {@code key} (a tab id, or a section id) on the next build, rebuilding now when on screen. */
    public void selectTab(final String key) {
        wantTab = key;
        wantSub = key;
        for (final Section s : collect()) {
            if (s.id.equals(key)) { wantTab = s.tabKey(); break; }
        }
        if (isOnScreen()) screen.refreshPage();
    }

    /** True when this page (or the category page hosting it) is the one the screen shows. */
    private boolean isOnScreen() {
        if (screen == null) return false;
        final SidebarPage cur = screen.currentPage();
        return cur == this || (cur instanceof CategoryHost c && c.activeChild() == this);
    }

    @Override
    public boolean cycleTab(final int dir, final boolean secondary) {
        if (!filter.isEmpty()) return false;
        if (secondary && !subs.isEmpty() && subStrip != null) {
            final int n = subs.size();
            final int to = ((subIndex + dir) % n + n) % n;
            subStrip.setIndex(to);
            selectSub(to);
            return true;
        }
        if (tabs.size() < 2 || strip == null) return false;
        final int n = tabs.size();
        final int to = ((tabIndex + dir) % n + n) % n;
        strip.setIndex(to);
        selectTab(to);
        return true;
    }

    @Override
    public boolean hasTopTabs() { return !isHosted() && strip != null; }

    @Override
    public int tabCount() { return Math.max(1, tabs.size()); }

    /** The sections of the top tab on screen (the whole page when it has no tabs). */
    public List<Section> currentTabSections() {
        if (tabs.isEmpty()) {
            final List<Section> out = new ArrayList<>();
            for (final Section s : cachedSections) if (!s.isEmpty()) out.add(s);
            return out;
        }
        return tabs.get(Math.min(tabIndex, tabs.size() - 1)).sections();
    }

    /** "Page › Tab" of the top tab on screen, for the reset confirmation. */
    public Component currentTabTitle() {
        final String base = fullTitle().getString();
        if (tabs.size() < 2) return Component.literal(base);
        return Component.literal(base + " › " + tabs.get(Math.min(tabIndex, tabs.size() - 1)).title().getString());
    }

    // ------------------------------------------------------------------ actions

    /** Rebuild in place keeping the scroll position. */
    public void rebuild() {
        if (screen == null) return;
        keepScroll = panel != null ? panel.scrollAmount() : -1;
        screen.refreshPage();
    }

    protected void onRowChanged(final OptionRow row) {
        for (final OptionRow r : rows) if (r != row) r.refresh();     // dependent rows (enabled state) follow
    }

    /** Filter rows by text (header search). Empty shows everything. */
    public void filter(final String text) {
        final String t = text == null ? "" : text;
        if (t.equals(filter)) return;
        filter = t;
        keepScroll = 0;
        if (isOnScreen()) screen.refreshPage();
    }

    public String filter() { return filter; }

    /** Set the filter for the next build without rebuilding (a category handing its search to a new tab). */
    public void filterSilently(final String text) {
        filter = text == null ? "" : text;
    }

    @Override
    public void enterFrom(final int dir) {
        enterDir = Integer.signum(dir);
        enterAll = dir != 0;
        keepScroll = 0;
    }

    /** Select the owning tab, un-collapse, scroll to and flash a row after the next build. */
    public void focusRow(final String bindingId) {
        pendingFocus = bindingId;
        filter = "";
        outer:
        for (final Section s : collect()) {
            for (final OptionBinding b : s.bindings()) {
                if (b.id().equals(bindingId)) {
                    wantTab = s.tabKey();
                    wantSub = s.id;
                    ConfigSettings.setCollapsed(id() + ":" + s.id, false);
                    break outer;
                }
            }
        }
        if (isOnScreen()) screen.refreshPage();
    }

    /** Reset every row with a known default on every tab of the page. */
    public void resetAll() {
        reset(collect());
    }

    /** Reset the rows of the top tab on screen (all its secondary tabs and headers). */
    public void resetTab() {
        reset(currentTabSections());
    }

    private void reset(final List<Section> sections) {
        for (final Section s : sections) {
            for (final OptionBinding b : s.bindings()) {
                if (b.hasDefault() && b.type().snapshotable() && b.enabled()) {
                    try { b.reset(); } catch (final Exception ignored) {}
                }
            }
        }
        rebuild();
    }

    public void refreshRows() {
        for (final OptionRow r : rows) r.refresh();
    }

    /** Also publishes every binding so favourites and presets resolve page-local ids before the page was ever shown. */
    public List<SearchIndex.Entry> searchEntries() {
        return searchEntries(path(), hostTitle != null ? hostTitle : title(), hostTitle != null ? title() : null);
    }

    /** Entries under {@code path}; {@code tabTitle} is the category tab this page is (null for a top-level page). */
    public List<SearchIndex.Entry> searchEntries(final String path, final Component pageTitle, @Nullable final Component tabTitle) {
        final List<SearchIndex.Entry> out = new ArrayList<>();
        final List<Section> visible = new ArrayList<>();
        for (final Section s : collect()) if (!s.isEmpty()) visible.add(s);
        final List<Tab> groups = group(visible);
        final boolean single = groups.size() == 1;
        for (final Tab t : groups) {
            for (final Section s : t.sections()) {
                final Component tab = tabTitle != null ? tabTitle : single ? (t.sections().size() > 1 ? s.title : null) : t.title();
                for (final OptionBinding b : s.bindings()) {
                    OptionResolvers.publish(b);
                    out.add(new SearchIndex.Entry(path, pageTitle, tab, s.title, b));
                }
            }
        }
        return out;
    }

    @Override
    public void onHide() {
        ApplyQueue.flush();
    }

    @Override
    public void render(final SidebarScreen screen, final GuiGraphics g, final Rect area, final int mouseX, final int mouseY, final float partialTick) {
        if (filter.isEmpty() || !rows.isEmpty()) return;
        final int y = (panel != null ? panel.getY() : area.y()) + 24;
        SlateDraw.textCentered(g, Component.translatable("slate_config.search.no_matches"), area.centerX(), y,
            Theme.current().isVanilla() ? 0xFFA0A0A0 : Theme.current().palette().textDim());
    }
}
