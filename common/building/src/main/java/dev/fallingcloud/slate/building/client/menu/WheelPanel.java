package dev.fallingcloud.slate.building.client.menu;

import dev.fallingcloud.slate.building.client.BuildingIcons;
import dev.fallingcloud.slate.building.client.wheel.RadialWheel;
import dev.fallingcloud.slate.building.client.wheel.WheelActions;
import dev.fallingcloud.slate.building.client.wheel.WheelConfig;
import dev.fallingcloud.slate.building.client.wheel.WheelPage;
import dev.fallingcloud.slate.building.client.wheel.WheelPages;
import dev.fallingcloud.slate.building.client.wheel.WheelSlice;
import dev.fallingcloud.slate.building.client.wheel.WheelTarget;
import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateTextField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * One wheel of the build menu's left column: a search field (matching slices glow, the rest dim; the page jumps to
 * the first page with a match), the wheel itself (click applies), and a page row with ◀ ▶ arrows, the page name
 * and one dot per page (accented when that page has search matches). Two kinds: the held material's SHAPES (swap,
 * {@code SwapHeld}) and its CHISEL groups ({@code ChiselHeld}), whose locked and empty states explain themselves.
 */
final class WheelPanel {

    enum Kind { SHAPES, CHISEL }

    static final int SEARCH_H = 16;
    static final int PAGE_ROW_H = 16;
    static final int GAP = 4;

    private final Kind kind;
    private final WheelWidget wheel;
    private final SlateTextField search;
    private final SlateIconButton prev;
    private final SlateIconButton next;
    private final Anim pageFlash = new Anim(1, 160, Ease.OUT_CUBIC);

    private @Nullable WheelTarget target;
    private String targetKey = "";
    private List<WheelPage> pages = List.of();
    private @Nullable WheelSlice full;
    private @Nullable Component lock;
    private int page;
    private String query = "";
    private boolean[] pageMatches = new boolean[0];
    private Rect area = new Rect(0, 0, 0, 0);
    private Rect wheelRect = new Rect(0, 0, 0, 0);
    private Rect pageRow = new Rect(0, 0, 0, 0);

    WheelPanel(final Kind kind, final String initialQuery, final int initialPage) {
        this.kind = kind;
        this.query = initialQuery == null ? "" : initialQuery;
        this.page = initialPage;
        this.search = new SlateTextField(0, 0, 10, SEARCH_H, Component.translatable("slate.search"));
        search.icon(Icon.SEARCH).clearButton(true)
            .placeholder(Component.translatable(kind == Kind.SHAPES ? "slate_building.ui.menu.search_shapes" : "slate_building.ui.menu.search_chisel"));
        search.setValue(query);
        search.onChange(this::setQuery);
        search.onEscape(() -> { if (!search.getValue().isEmpty()) search.setValue(""); else search.setFocused(false); });
        this.wheel = new WheelWidget(0, 0, 10, 10).onPick(this::pick);
        this.prev = new SlateIconButton(0, 0, 14, Icon.CHEVRON_LEFT, Component.translatable("slate_building.ui.menu.prev_page"), () -> turn(-1));
        this.next = new SlateIconButton(0, 0, 14, Icon.CHEVRON_RIGHT, Component.translatable("slate_building.ui.menu.next_page"), () -> turn(1));
        refresh(true);
    }

    Kind kind() { return kind; }

    SlateTextField search() { return search; }

    WheelWidget wheel() { return wheel; }

    String query() { return query; }

    int page() { return page; }

    /** Widgets in focus order. */
    List<AbstractWidget> widgets() {
        return List.of(search, wheel, prev, next);
    }

    void visible(final boolean on) {
        search.visible = on;
        wheel.visible = on;
        prev.visible = on;
        next.visible = on;
    }

    boolean isVisible() { return wheel.visible; }

    /** Lays the pieces out inside {@code r}. */
    void layout(final Rect r) {
        this.area = r;
        search.setX(r.x());
        search.setY(r.y());
        search.setWidth(r.w());
        final int wheelTop = r.y() + SEARCH_H + GAP;
        final int wheelH = Math.max(40, r.h() - SEARCH_H - GAP - PAGE_ROW_H - GAP);
        final int side = Math.min(r.w(), wheelH);
        wheelRect = new Rect(r.x() + (r.w() - side) / 2, wheelTop + (wheelH - side) / 2, side, side);
        wheel.setX(wheelRect.x());
        wheel.setY(wheelRect.y());
        wheel.setWidth(wheelRect.w());
        wheel.setHeight(wheelRect.h());
        pageRow = new Rect(r.x(), r.bottom() - PAGE_ROW_H, r.w(), PAGE_ROW_H);
        prev.setX(pageRow.x());
        prev.setY(pageRow.y() + 1);
        next.setX(pageRow.right() - 14);
        next.setY(pageRow.y() + 1);
        updateArrows();
    }

    // ------------------------------------------------------------------ content

    /** Re-reads the held stack; rebuilds when it changed. Called every tick. */
    void tick() {
        final WheelTarget now = WheelTarget.heldTarget();
        final String key = now == null ? "" : now.material() + "|" + now.shape() + "|" + now.count() + "|" + now.slot();
        if (key.equals(targetKey)) return;
        final boolean sameMaterial = now != null && target != null && now.material() == target.material();
        refresh(!sameMaterial);
    }

    /** Rebuilds the pages from the held stack (and the wheel settings). */
    void refresh(final boolean animate) {
        final String keepKey = page >= 0 && page < pages.size() ? pages.get(page).key() : "";
        target = WheelTarget.heldTarget();
        targetKey = target == null ? "" : target.material() + "|" + target.shape() + "|" + target.count() + "|" + target.slot();
        if (target == null) {
            pages = List.of();
            full = null;
            lock = null;
        } else if (kind == Kind.SHAPES) {
            pages = WheelPages.shapePages(target, WheelConfig.wheel().hideUnavailable);
            full = WheelPages.fullSlice(target);
            lock = null;
        } else {
            lock = WheelPages.chiselLock(target);
            pages = WheelPages.chiselPages(target);
            full = WheelPages.currentSlice(target);
        }
        int idx = Mth.clamp(page, 0, Math.max(0, pages.size() - 1));
        for (int i = 0; i < pages.size(); i++) if (pages.get(i).key().equals(keepKey)) idx = i;
        page = idx;
        show(animate);
    }

    private void show(final boolean animate) {
        final WheelPage p = currentPage();
        final boolean locked = lock != null;
        wheel.content(p == null || locked ? List.of() : p.slices(), locked || (kind == Kind.CHISEL && p == null) ? null : full, animate);
        wheel.active = !locked && (p != null || (kind == Kind.SHAPES && full != null));
        applySearch(false);
        updateArrows();
    }

    private @Nullable WheelPage currentPage() {
        return pages.isEmpty() ? null : pages.get(Mth.clamp(page, 0, pages.size() - 1));
    }

    private void turn(final int dir) {
        if (pages.size() < 2) return;
        page = Math.floorMod(page + dir, pages.size());
        show(true);
        pageFlash.snap(0f);
        pageFlash.set(1f);
        WheelActions.tick(dir > 0 ? 1.35f : 1.15f);
    }

    /** PageUp / PageDown from the screen. */
    boolean turnPage(final int dir) {
        if (pages.size() < 2 || !isVisible()) return false;
        turn(dir);
        return true;
    }

    private void updateArrows() {
        final boolean many = pages.size() > 1 && lock == null;
        prev.active = many;
        next.active = many;
    }

    private void setQuery(final String q) {
        query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        applySearch(true);
    }

    /** Glow/dim the current page's slices; with {@code jump}, go to the first page with a match if this one has none. */
    private void applySearch(final boolean jump) {
        pageMatches = new boolean[pages.size()];
        if (query.isEmpty()) {
            wheel.wheel().emphasis(null);
            return;
        }
        for (int i = 0; i < pages.size(); i++) {
            for (final WheelSlice s : pages.get(i).slices()) {
                if (s.searchText().contains(query)) { pageMatches[i] = true; break; }
            }
        }
        if (jump && page < pageMatches.length && !pageMatches[page]) {
            for (int i = 0; i < pageMatches.length; i++) {
                if (pageMatches[i]) { page = i; show(true); return; }
            }
        }
        final WheelPage p = currentPage();
        if (p == null) return;
        final boolean[] m = new boolean[p.slices().size()];
        for (int i = 0; i < m.length; i++) m[i] = p.slices().get(i).searchText().contains(query);
        wheel.wheel().emphasis(m);
    }

    private void pick(final int index) {
        final WheelTarget t = target;
        if (t == null) return;
        final WheelSlice s = index == RadialWheel.CENTER ? full : index >= 0 && index < wheel.wheel().slices().size() ? wheel.wheel().slices().get(index) : null;
        if (s == null) return;
        if (s.current()) return;
        if (s.lock() != null || !s.available()) {
            WheelActions.deny();
            return;
        }
        if (WheelActions.apply(t, s) && index >= 0) wheel.wheel().pulse(index);
    }

    // ------------------------------------------------------------------ extras drawn by the screen

    void render(final GuiGraphics g, final float alpha) {
        if (!isVisible() || alpha <= 0.01f) return;
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean vanilla = t.isVanilla();
        final WheelPage cur = currentPage();

        // Page row: name (and n/m) between the arrows, dots underneath.
        if (lock == null && cur != null) {
            final Component name = pages.size() > 1
                ? Component.translatable("slate_building.ui.menu.page", cur.name(), page + 1, pages.size()) : cur.name();
            final int tw = pageRow.w() - 36;
            final int col = Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.text(), alpha * (0.5f + 0.5f * pageFlash.get()));
            g.drawString(SlateDraw.font(), SlateDraw.truncate(name, tw), pageRow.centerX() - Math.min(tw, SlateDraw.width(name)) / 2,
                pageRow.y() + 2, col, vanilla);
            if (pages.size() > 1) {
                final int n = pages.size();
                final int dotsW = n * 5 - 1;
                int dx = pageRow.centerX() - dotsW / 2;
                for (int i = 0; i < n; i++) {
                    final boolean match = i < pageMatches.length && pageMatches[i];
                    int c = i == page ? (vanilla ? 0xFFFFFFFF : p.accent()) : (vanilla ? 0xFF555555 : p.borderStrong());
                    if (match && i != page) c = vanilla ? 0xFFFFFF55 : Colors.lerp(p.borderStrong(), p.accent(), 0.6f);
                    SlateDraw.rect(g, dx, pageRow.y() + 12, 4, 1, Colors.scaleAlpha(c, alpha));
                    dx += 5;
                }
            }
        }

        // Empty / locked states, centred in the wheel area.
        final Component msg;
        final Icon icon;
        Component sub = null;
        if (target == null) {
            icon = BuildingIcons.SHAPE_FULL;
            msg = Component.translatable(kind == Kind.SHAPES ? "slate_building.ui.menu.hold_block" : "slate_building.ui.menu.hold_block_chisel");
        } else if (lock != null) {
            icon = Icon.LOCK;
            msg = lock;
            sub = Component.translatable("slate_building.ui.menu.chisel_explain", ToolType.CHISEL.displayName());
        } else if (pages.isEmpty() && kind == Kind.CHISEL) {
            icon = BuildingIcons.CHISEL;
            msg = Component.translatable("slate_building.ui.menu.no_chisel", target.material().getName());
        } else if (pages.isEmpty() && kind == Kind.SHAPES && full == null) {
            icon = BuildingIcons.WHEEL;
            msg = Component.translatable("slate_building.ui.wheel.no_shapes");
        } else {
            return;
        }
        final int cx = wheelRect.centerX(), cy = wheelRect.centerY();
        final int maxW = Math.max(40, area.w() - 16);
        final List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>(SlateDraw.font().split(msg, maxW));
        final List<net.minecraft.util.FormattedCharSequence> subLines = sub == null ? List.of() : SlateDraw.font().split(sub, maxW);
        final int total = 24 + lines.size() * 10 + (subLines.isEmpty() ? 0 : 4 + subLines.size() * 10);
        int y = cy - total / 2;
        final int iconCol = lock != null ? (vanilla ? 0xFFFFAA00 : p.warning()) : (vanilla ? 0xFFA0A0A0 : p.textDim());
        Icons.draw(g, icon, cx - 8, y, 16, Colors.scaleAlpha(iconCol, alpha));
        y += 24;
        for (final var line : lines) {
            g.drawString(SlateDraw.font(), line, cx - SlateDraw.width(line) / 2, y, Colors.scaleAlpha(vanilla ? 0xFFE0E0E0 : p.textMuted(), alpha), vanilla);
            y += 10;
        }
        if (!subLines.isEmpty()) {
            y += 4;
            for (final var line : subLines) {
                g.drawString(SlateDraw.font(), line, cx - SlateDraw.width(line) / 2, y, Colors.scaleAlpha(vanilla ? 0xFF909090 : p.textDim(), alpha), vanilla);
                y += 10;
            }
        }
    }

    /** Dev harness: hover a slice as if the mouse were on it. */
    int[] slicePos(final int index) {
        return wheel.wheel().itemPos(index);
    }
}
