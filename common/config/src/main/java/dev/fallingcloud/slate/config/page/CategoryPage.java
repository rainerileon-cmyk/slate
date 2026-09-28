package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.ConfigSettings;
import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.config.api.SettingsTabs;
import dev.fallingcloud.slate.config.search.SearchIndex;
import dev.fallingcloud.slate.config.ui.ApplyQueue;
import dev.fallingcloud.slate.config.ui.CategoryHost;
import dev.fallingcloud.slate.config.ui.Enterable;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.config.ui.TabHost;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SidebarPage;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.widget.SlateTabStrip;
import dev.fallingcloud.slate.core.widget.SlateTabs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A sidebar category whose top tabs are whole pages: Gameplay (General + module tabs), Multiplayer (Online,
 * Chat), Customization (Mods, Resource Packs, Shader Packs), Language &amp; Accessibility. Option pages shown
 * here are "hosted" (their own sections become the small secondary tabs). Other modules add tabs through
 * {@link SettingsTabs#register} with this page's id. The tab shown last is remembered in config.json.
 */
public class CategoryPage extends SidebarPage implements TabHost, CategoryHost {

    private static final int STRIP_GAP = 6;

    private final List<SidebarPage> builtins;
    private final Map<String, ContributedTabPage> contributed = new HashMap<>();
    @Nullable private String activeId;
    @Nullable private SidebarScreen screen;
    private Rect childArea = new Rect(0, 0, 0, 0);
    @Nullable private SlateTabStrip strip;
    private int stripFrom = -1;
    private int enterDir;
    private String filterText = "";
    /** The header text each tab (by id) was last handed ({@link #applyFilter}). */
    private final Map<String, String> appliedFilter = new HashMap<>();

    public CategoryPage(final String id, final Component title, final Icon icon, final List<? extends SidebarPage> builtins) {
        super(id, title, icon);
        this.builtins = new ArrayList<>(builtins);
    }

    /** Built-in tabs (in order) followed by contributed ones, merged by {@link SettingsTabs.Tab#order()}. */
    public List<SidebarPage> children() {
        record Ordered(int order, SidebarPage page) {}
        final List<Ordered> all = new ArrayList<>();
        for (int i = 0; i < builtins.size(); i++) all.add(new Ordered(i, builtins.get(i)));
        for (final SettingsTabs.Tab t : SettingsTabs.tabs(id())) {
            ContributedTabPage p = contributed.get(t.id());
            if (p == null || p.tab() != t) {
                p = new ContributedTabPage(t);
                contributed.put(t.id(), p);
            }
            all.add(new Ordered(t.order(), p));
        }
        all.sort((a, b) -> Integer.compare(a.order(), b.order()));
        final List<SidebarPage> out = new ArrayList<>();
        for (final Ordered o : all) {
            if (o.page() instanceof OptionPageBase opb) opb.hosted(id() + "/" + o.page().id(), title());
            out.add(o.page());
        }
        return out;
    }

    private int activeIndex(final List<SidebarPage> kids) {
        final String want = activeId != null ? activeId : ConfigSettings.lastTab(id());
        for (int i = 0; i < kids.size(); i++) if (kids.get(i).id().equals(want)) return i;
        return 0;
    }

    @Override
    @Nullable
    public SidebarPage activeChild() {
        final List<SidebarPage> kids = children();
        return kids.isEmpty() ? null : kids.get(activeIndex(kids));
    }

    /** {@code category/tab} of the tab on screen. */
    public String activePath() {
        final SidebarPage c = activeChild();
        return c == null ? id() : id() + "/" + c.id();
    }

    @Nullable
    public SlateTabStrip strip() { return strip; }

    // ------------------------------------------------------------------ building

    @Override
    public void build(final SidebarScreen screen, final Rect area) {
        this.screen = screen;
        final List<SidebarPage> kids = children();
        if (kids.isEmpty()) return;
        final int idx = activeIndex(kids);
        activeId = kids.get(idx).id();
        final double scroll = strip != null ? strip.scrollOffset() : 0;
        final boolean keyboard = net.minecraft.client.Minecraft.getInstance().getLastInputType().isKeyboard();
        final boolean stripFocused = keyboard && strip != null && strip.isFocused();
        strip = null;
        Rect sub = area;
        if (kids.size() > 1) {
            final List<SlateTabs.Tab> tabs = new ArrayList<>();
            for (final SidebarPage k : kids) tabs.add(new SlateTabs.Tab(k.title(), k.icon()));
            final SlateTabStrip s = new SlateTabStrip(area.x(), area.y(), area.w(), tabs, idx, this::select);
            s.animateFrom(stripFrom >= 0 ? stripFrom : idx, scroll);
            screen.addPageWidget(s);
            if (stripFocused) screen.setFocused(s);
            strip = s;
            final int top = area.y() + s.getHeight() + STRIP_GAP;
            sub = new Rect(area.x(), top, area.w(), area.bottom() - top);
        }
        childArea = sub;
        final SidebarPage child = kids.get(idx);
        applyFilter(child);
        if (enterDir != 0 && child instanceof Enterable e) e.enterFrom(enterDir);
        enterDir = 0;
        stripFrom = -1;
        try {
            child.build(screen, sub);
        } catch (final Exception e) {
            SlateConfig.LOGGER.error("[Slate Config] tab {} of {} failed to build", child.id(), id(), e);
        }
    }

    /**
     * Hands the header search to a tab, once per new header text. Handing it on every build wiped whatever was typed
     * into the tab's own search box: the Mods page rebuilds on every keystroke, so its box threw each one away.
     */
    private void applyFilter(final SidebarPage child) {
        if (filterText.equals(appliedFilter.get(child.id()))) return;
        appliedFilter.put(child.id(), filterText);
        if (child instanceof OptionPageBase o) o.filterSilently(filterText);
        else if (child instanceof ModsPage m) m.filterSilently(filterText);
    }

    private void select(final int i) {
        final List<SidebarPage> kids = children();
        final int cur = activeIndex(kids);
        if (i == cur || i < 0 || i >= kids.size()) return;
        ApplyQueue.flush();
        kids.get(cur).onHide();
        stripFrom = cur;
        enterDir = i > cur ? 1 : -1;
        activeId = kids.get(i).id();
        ConfigSettings.setLastTab(id(), activeId);
        if (screen != null && screen.currentPage() == this) screen.refreshPage();
    }

    /** Show the tab with this page id (next build; rebuilds now when this category is on screen). */
    public boolean select(final String childId) {
        final List<SidebarPage> kids = children();
        for (int i = 0; i < kids.size(); i++) {
            if (!kids.get(i).id().equals(childId)) continue;
            if (screen != null && screen.currentPage() == this) {
                if (strip != null) strip.setIndex(i);
                select(i);
            } else {
                activeId = childId;
                ConfigSettings.setLastTab(id(), childId);
            }
            return true;
        }
        return false;
    }

    /** The child page with this id, if the category has it. */
    @Nullable
    public SidebarPage child(final String childId) {
        for (final SidebarPage k : children()) if (k.id().equals(childId)) return k;
        return null;
    }

    /** Hand the header search to the tab on screen (and to whichever tab is shown next). */
    public void filter(final String text) {
        filterText = text == null ? "" : text;
        final SidebarPage c = activeChild();
        if (c != null) appliedFilter.put(c.id(), filterText);
        if (c instanceof OptionPageBase o) o.filter(filterText);
        else if (c instanceof ModsPage m) m.filter(filterText);
    }

    @Override
    public boolean cycleTab(final int dir, final boolean secondary) {
        final SidebarPage c = activeChild();
        if (secondary) return c instanceof TabHost th && th.cycleTab(dir, false);
        final List<SidebarPage> kids = children();
        if (kids.size() < 2) return c instanceof TabHost th && th.cycleTab(dir, false);
        final int n = kids.size();
        final int to = ((activeIndex(kids) + dir) % n + n) % n;
        if (strip != null) strip.setIndex(to);
        select(to);
        return true;
    }

    @Override
    public boolean hasTopTabs() { return strip != null; }

    @Override
    public int tabCount() { return Math.max(1, children().size()); }

    /** Every option of every tab, with "Category › Tab" crumbs. */
    public List<SearchIndex.Entry> searchEntries() {
        final List<SearchIndex.Entry> out = new ArrayList<>();
        for (final SidebarPage k : children()) {
            final String path = id() + "/" + k.id();
            try {
                if (k instanceof OptionPageBase o) out.addAll(o.searchEntries(path, title(), k.title()));
                else if (k instanceof ModsPage m) out.addAll(m.searchEntries(path, title(), k.title()));
            } catch (final Exception e) {
                SlateConfig.LOGGER.warn("[Slate Config] cannot index {}: {}", path, e.toString());
            }
        }
        return out;
    }

    /** Sections of every option tab (preset "current page" source). */
    public List<Section> allSections() {
        final List<Section> out = new ArrayList<>();
        for (final SidebarPage k : children()) if (k instanceof OptionPageBase o) out.addAll(o.currentSections());
        return out;
    }

    @Override
    public void render(final SidebarScreen screen, final GuiGraphics g, final Rect area, final int mouseX, final int mouseY, final float partialTick) {
        final SidebarPage c = activeChild();
        if (c != null) c.render(screen, g, childArea, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        final SidebarPage c = activeChild();
        if (c != null) c.tick();
    }

    @Override
    public void onHide() {
        final SidebarPage c = activeChild();
        if (c != null) c.onHide();
    }
}
