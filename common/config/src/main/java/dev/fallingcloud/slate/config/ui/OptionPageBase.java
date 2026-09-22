package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.config.ConfigSettings;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A settings page made of {@link Section}s of rows inside one scroll panel. Handles the text filter,
 * collapsible sections (remembered in config.json), "jump to row" highlighting, reset-all and the search
 * index. Subclasses only provide {@link #sections()}.
 */
public abstract class OptionPageBase extends SidebarPage {

    protected SidebarScreen screen;
    protected Rect area;
    @Nullable protected SlateScrollPanel panel;
    private final List<OptionRow> rows = new ArrayList<>();
    private List<Section> cachedSections = List.of();
    private String filter = "";
    @Nullable private String pendingFocus;
    private double keepScroll = -1;
    @Nullable private Component emptyText;

    protected OptionPageBase(final String id, final Component title, final Icon icon) {
        super(id, title, icon);
    }

    /** The page's content. Called on every (re)build so values are fresh; keep it cheap. */
    protected abstract List<Section> sections();

    /** Optional line under the rows when nothing is there (e.g. "No favourites yet"). */
    protected OptionPageBase emptyText(final Component c) { this.emptyText = c; return this; }

    public List<OptionRow> rows() { return rows; }

    public List<Section> currentSections() { return cachedSections; }

    // ------------------------------------------------------------------ building

    @Override
    public void build(final SidebarScreen screen, final Rect area) {
        this.screen = screen;
        this.area = area;
        rows.clear();
        final SlateScrollPanel p = new SlateScrollPanel(area.x(), area.y(), area.w(), area.h());
        panel = p;
        final int w = area.w() - 8;
        int y = 0;
        cachedSections = sections();
        final String f = filter.toLowerCase(Locale.ROOT).trim();
        final boolean filtering = !f.isEmpty();
        final List<Section> visible = new ArrayList<>();
        for (final Section s : cachedSections) if (!s.isEmpty()) visible.add(s);
        final boolean headers = visible.size() > 1 || (visible.size() == 1 && visible.get(0).collapsible && !visible.get(0).title.getString().isEmpty() && showSingleHeader());
        for (final Section s : visible) {
            final List<Section.Item> items = new ArrayList<>();
            for (final Section.Item it : s.items) {
                if (it.binding() == null) { if (!filtering) items.add(it); continue; }
                if (!filtering || matches(it.binding(), s, f)) items.add(it);
            }
            if (items.isEmpty()) continue;
            final String key = id() + ":" + s.id;
            final boolean collapsed = !filtering && s.collapsible && ConfigSettings.isCollapsed(key);
            if (headers) {
                final SectionHeader h = new SectionHeader(0, y, w, s.title, s.bindings().size(), collapsed,
                    s.collapsible ? () -> { ConfigSettings.setCollapsed(key, !ConfigSettings.isCollapsed(key)); rebuild(); } : null, 0);
                p.add(h, 0, y);
                y += SectionHeader.HEIGHT + 2;
                if (s.description != null && !collapsed) {
                    final SlateLabel d = new SlateLabel(0, y, w - 8, s.description).style(SlateLabel.Style.MUTED).wrap(true);
                    p.add(d, 4, y);
                    y += d.getHeight() + 4;
                }
            }
            if (collapsed) { y += 4; continue; }
            for (final Section.Item it : items) {
                if (it.binding() != null) {
                    OptionResolvers.publish(it.binding());
                    final OptionRow row = new OptionRow(0, y, w, it.binding(), 0).onChanged(this::onRowChanged);
                    rows.add(row);
                    p.add(row, 0, y);
                    y += OptionRow.HEIGHT + 2;
                } else if (it.custom() != null) {
                    final AbstractWidget cw = it.custom().apply(w);
                    if (cw != null) { p.add(cw, 0, y); y += cw.getHeight() + 4; }
                }
            }
            y += 6;
        }
        if (rows.isEmpty() && emptyText != null && !filtering) {
            final SlateLabel l = new SlateLabel(0, y + 8, w - 16, emptyText).style(SlateLabel.Style.MUTED).wrap(true);
            p.add(l, 8, y + 8);
            y += l.getHeight() + 16;
        }
        p.setContentHeight(y + 4);
        screen.addPageWidget(p);
        if (keepScroll >= 0) { p.snapScroll(keepScroll); keepScroll = -1; }
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
        final String hay = (b.searchText() + " " + s.title.getString()).toLowerCase(Locale.ROOT);
        for (final String tok : f.split("\\s+")) if (!tok.isEmpty() && !hay.contains(tok)) return false;
        return true;
    }

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
        if (screen != null) screen.refreshPage();
    }

    public String filter() { return filter; }

    /** Expand, scroll to and flash a row after the next build. */
    public void focusRow(final String bindingId) {
        pendingFocus = bindingId;
        filter = "";
        for (final Section s : cachedSections) {
            for (final OptionBinding b : s.bindings()) if (b.id().equals(bindingId)) ConfigSettings.setCollapsed(id() + ":" + s.id, false);
        }
        if (screen != null) screen.refreshPage();
    }

    /** Reset every row with a known default. */
    public void resetAll() {
        for (final Section s : sections()) {
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
        final List<SearchIndex.Entry> out = new ArrayList<>();
        for (final Section s : sections()) {
            for (final OptionBinding b : s.bindings()) {
                OptionResolvers.publish(b);
                out.add(new SearchIndex.Entry(id(), title(), s.title, b));
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
        if (!filter.isEmpty() && rows.isEmpty() && !Theme.current().isVanilla()) {
            SlateDraw.textCentered(g, Component.translatable("slate_config.search.no_matches"), area.centerX(), area.y() + 24, Theme.current().palette().textDim());
        } else if (!filter.isEmpty() && rows.isEmpty()) {
            SlateDraw.textCentered(g, Component.translatable("slate_config.search.no_matches"), area.centerX(), area.y() + 24, 0xFFA0A0A0);
        }
    }
}
