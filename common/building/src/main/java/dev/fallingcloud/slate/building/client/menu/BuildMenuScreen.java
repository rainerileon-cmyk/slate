package dev.fallingcloud.slate.building.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fallingcloud.slate.building.client.input.BuildKeys;
import dev.fallingcloud.slate.building.client.mode.ClientModeState;
import dev.fallingcloud.slate.building.client.wheel.WheelActions;
import dev.fallingcloud.slate.building.client.wheel.WheelConfig;
import dev.fallingcloud.slate.building.net.Redo;
import dev.fallingcloud.slate.building.net.Undo;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateList;
import dev.fallingcloud.slate.core.widget.SlateSegmented;
import dev.fallingcloud.slate.core.widget.SlateSounds;
import dev.fallingcloud.slate.core.widget.SlateTooltips;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The build menu (R, design §5). Left (~40%): the held material's shape wheel (click applies, ◀ ▶ pages, search
 * glows matches) and its chisel wheel, stacked when there is room, else as two tabs. Right (~60%): "Building modes"
 * with the toolbox summary, Undo / Redo (with counts) and the wheel editor; a table of every mode (click activates
 * and closes, Shift+click keeps the menu open, right-click only shows the options); below it the selected mode's
 * options. Not a pause screen, with a light in-world backdrop so the world stays visible. Keyboard: Tab cycles the
 * areas, arrows move in the table / around a focused wheel, Enter activates, {@code /} searches, PageUp / PageDown
 * turn wheel pages, R or Esc closes.
 */
public final class BuildMenuScreen extends SlateScreen {

    private static final int MARGIN = 8, GAP = 8, PAD = 7, HEADER_H = 18, SECTION_TITLE_H = 13;

    // Kept across openings: the tab, searches, pages and the mode shown in the options panel.
    private static int lastTab;
    private static String lastShapeQuery = "", lastChiselQuery = "";
    private static int lastShapePage, lastChiselPage;
    private static String lastSelected = "";

    private final Anim openAnim = new Anim(0, 220, Ease.OUT_CUBIC);
    private final ModeRows rows = new ModeRows(this::rowClicked);
    private final ModeOptions options = new ModeOptions();
    private @Nullable WheelPanel shapes;
    private @Nullable WheelPanel chisel;
    private @Nullable SlateList<BuildMode> table;
    private @Nullable SlateButton undo;
    private @Nullable SlateButton redo;
    private @Nullable ToolboxChip toolbox;
    private @Nullable SlateSegmented<Integer> tabs;
    private boolean stacked;
    private int tab;
    private Rect left = new Rect(0, 0, 0, 0), right = new Rect(0, 0, 0, 0);
    private Rect shapesTitle = new Rect(0, 0, 0, 0), chiselTitle = new Rect(0, 0, 0, 0);
    private Rect header = new Rect(0, 0, 0, 0), optionsRect = new Rect(0, 0, 0, 0);
    private int headerTitleRight;
    private int headerActionsRight;
    private boolean menuKeyArmed;
    private int undoShown = -1, redoShown = -1;

    public BuildMenuScreen(final @Nullable Screen parent) {
        super(Component.translatable("slate_building.ui.menu.title"), parent);
        this.showHeader = false;
        this.tab = lastTab;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ build

    @Override
    protected void build() {
        final boolean first = openAnim.target() == 0f;
        if (first) openAnim.set(1f);
        final Rect content = new Rect(MARGIN, MARGIN, width - MARGIN * 2, height - MARGIN * 2);
        final int leftW = Math.round((content.w() - GAP) * 0.4f);
        left = new Rect(content.x(), content.y(), leftW, content.h());
        right = new Rect(left.right() + GAP, content.y(), content.w() - GAP - leftW, content.h());

        buildLeft(left.inset(PAD));
        buildRight(right.inset(PAD));
        if (!first) {
            rows.skipEntrance();
        }
    }

    private void buildLeft(final Rect in) {
        final WheelPanel sp = shapes != null ? shapes : new WheelPanel(WheelPanel.Kind.SHAPES, lastShapeQuery, lastShapePage);
        final WheelPanel cp = chisel != null ? chisel : new WheelPanel(WheelPanel.Kind.CHISEL, lastChiselQuery, lastChiselPage);
        shapes = sp;
        chisel = cp;
        // Stack both wheels only when each still gets a comfortable size; otherwise one wheel, two tabs.
        final int sectionMin = SECTION_TITLE_H + WheelPanel.SEARCH_H + WheelPanel.GAP + 128 + WheelPanel.GAP + WheelPanel.PAGE_ROW_H;
        stacked = in.h() >= sectionMin * 2 + 10;
        tabs = null;
        if (stacked) {
            final int half = (in.h() - 10) / 2;
            shapesTitle = new Rect(in.x(), in.y(), in.w(), SECTION_TITLE_H);
            sp.layout(new Rect(in.x(), in.y() + SECTION_TITLE_H, in.w(), half - SECTION_TITLE_H));
            final int cy = in.y() + half + 10;
            chiselTitle = new Rect(in.x(), cy, in.w(), SECTION_TITLE_H);
            cp.layout(new Rect(in.x(), cy + SECTION_TITLE_H, in.w(), in.bottom() - cy - SECTION_TITLE_H));
            sp.visible(true);
            cp.visible(true);
        } else {
            final SlateSegmented<Integer> seg = new SlateSegmented<>(in.x(), in.y(), in.w(), List.of(0, 1), tab,
                i -> Component.translatable(i == 0 ? "slate_building.ui.menu.shapes" : "slate_building.ui.menu.chisel"), this::selectTab);
            tabs = seg;
            add(seg);
            final Rect body = new Rect(in.x(), in.y() + 24, in.w(), in.h() - 24);
            sp.layout(body);
            cp.layout(body);
            sp.visible(tab == 0);
            cp.visible(tab == 1);
        }
        for (final AbstractWidget w : sp.widgets()) addWidgetOf(w);
        for (final AbstractWidget w : cp.widgets()) addWidgetOf(w);
        slashFocusTarget = stacked || tab == 0 ? sp.search() : cp.search();
    }

    private void selectTab(final int i) {
        tab = i;
        lastTab = i;
        if (shapes != null) shapes.visible(i == 0);
        if (chisel != null) chisel.visible(i == 1);
        final WheelPanel shown = i == 0 ? shapes : chisel;
        if (shown != null) {
            shown.wheel().wheel().replayEntrance();
            slashFocusTarget = shown.search();
        }
    }

    private void buildRight(final Rect in) {
        // Header: title ... [toolbox] [undo] [redo] [wheels] [close]
        header = new Rect(in.x(), in.y(), in.w(), HEADER_H);
        int x = in.right();
        final int by = in.y() + (HEADER_H - 16) / 2;
        final SlateIconButton close = new SlateIconButton(x - 16, by, 16, Icon.CLOSE, Component.translatable("slate_building.ui.menu.close"), this::onClose);
        add(close);
        x -= 16 + 3;
        final SlateIconButton wheels = new SlateIconButton(x - 16, by, 16, Icon.SETTINGS, Component.translatable("slate_building.ui.menu.edit_wheels"),
            () -> minecraft.setScreen(new WheelEditorScreen(this)));
        add(wheels);
        headerActionsRight = x - 16 - 6;
        final SlateButton rb = new SlateButton(0, by, 30, SlateButton.HEIGHT_SMALL, Component.literal("0"), this::redo);
        rb.icon(Icon.REDO).iconSize(8);
        final SlateButton ub = new SlateButton(0, by, 30, SlateButton.HEIGHT_SMALL, Component.literal("0"), this::undo);
        ub.icon(Icon.UNDO).iconSize(8);
        redo = rb;
        undo = ub;
        toolbox = new ToolboxChip(0, in.y() + (HEADER_H - ToolboxChip.H) / 2);
        undoShown = redoShown = -1;
        updateHistory();
        add(toolbox);
        add(ub);
        add(rb);

        // Options panel height: the tallest mode at this width, so the table never jumps while browsing.
        int optH = 0;
        for (final BuildMode m : BuildModes.all()) optH = Math.max(optH, ModeOptions.heightFor(m, in.w()));
        optionsRect = new Rect(in.x(), in.bottom() - optH, in.w(), optH);
        final int tableTop = header.bottom() + 5;
        final int tableH = Math.max(ModeRows.ROW_H * 2, optionsRect.y() - 7 - tableTop);

        rows.refreshLocks(BuildModes.all());
        final SlateList<BuildMode> list = new SlateList<>(in.x(), tableTop, in.w(), tableH, ModeRows.ROW_H, rows);
        list.plainRows().gap(1).items(BuildModes.all());
        list.onSelect(this::showOptions);
        list.onActivate(this::activate);
        table = list;
        add(list);
        BuildMode sel = BuildModes.byId(lastSelected);
        if (sel == null) sel = ClientModeState.current();
        if (sel == null) sel = ClientModeState.lastMode();
        if (sel == null) sel = BuildModes.all().get(0);
        list.select(sel);
        if (options.mode() == null) showOptions(sel);
    }

    /** {@code add} for widgets built by helpers (generic bound workaround). */
    private void addWidgetOf(final AbstractWidget w) {
        addRenderableWidget(w);
    }

    // ------------------------------------------------------------------ modes

    private void showOptions(final BuildMode m) {
        lastSelected = m.id();
        for (final AbstractWidget w : options.widgets()) removeWidget(w);
        final List<AbstractWidget> created = options.build(m, optionsRect.inset(0, 2), () -> showOptions(m));
        int i = 0;
        for (final AbstractWidget w : created) {
            addWidgetOf(w);
            if (w instanceof SlateWidget sw) sw.playEntrance(Math.min(120, i++ * 20));
        }
    }

    private void rowClicked(final BuildMode m, final int button) {
        if (table != null) table.select(m);
        activate(m);
    }

    private void activate(final BuildMode m) {
        if (rows.lock(m) != null) {
            rows.shake(m);
            WheelActions.deny();
            return;
        }
        if (ClientModeState.current() == m) {
            ClientModeState.setMode(null);
        } else {
            ClientModeState.setMode(m);
        }
        SlateSounds.click();
        if (!hasShiftDown()) onClose();
    }

    private void undo() {
        SlateNetwork.get().sendToServer(Undo.INSTANCE);
    }

    private void redo() {
        SlateNetwork.get().sendToServer(Redo.INSTANCE);
    }

    private void updateHistory() {
        final SlateButton u = undo, r = redo;
        if (u == null || r == null) return;
        final int uc = ClientModeState.undoCount(), rc = ClientModeState.redoCount();
        if (uc != undoShown) {
            undoShown = uc;
            u.setMessage(Component.literal(Integer.toString(uc)));
            u.active = uc > 0;
            final String label = ClientModeState.undoLabel();
            final List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("slate_building.ui.menu.undo"));
            if (!label.isEmpty()) tip.add(Component.literal(label).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("slate_building.ui.menu.steps", uc).withStyle(ChatFormatting.DARK_GRAY));
            u.tip(tip);
        }
        if (rc != redoShown) {
            redoShown = rc;
            r.setMessage(Component.literal(Integer.toString(rc)));
            r.active = rc > 0;
            r.tip(List.of(Component.translatable("slate_building.ui.menu.redo"),
                Component.translatable("slate_building.ui.menu.steps", rc).withStyle(ChatFormatting.DARK_GRAY)));
        }
        layoutHeader();
    }

    /** Right-to-left: redo, undo, toolbox chip; the title gets what is left. Re-run when a width changes. */
    private void layoutHeader() {
        final SlateButton u = undo, r = redo;
        final ToolboxChip tc = toolbox;
        if (u == null || r == null || tc == null) return;
        int x = headerActionsRight;
        r.setWidth(r.preferredWidth());
        r.setX(x - r.getWidth());
        x -= r.getWidth() + 2;
        u.setWidth(u.preferredWidth());
        u.setX(x - u.getWidth());
        x -= u.getWidth() + 6;
        tc.setX(x - tc.getWidth());
        headerTitleRight = tc.getX() - 6;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void tick() {
        super.tick();
        if (shapes != null) shapes.tick();
        if (chisel != null) chisel.tick();
        updateHistory();
        rows.refreshLocks(BuildModes.all());
        if (toolbox != null) {
            final int before = toolbox.getWidth();
            toolbox.refresh();
            if (toolbox.getWidth() != before) layoutHeader();
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (shapes != null) { lastShapeQuery = shapes.search().getValue(); lastShapePage = shapes.page(); }
        if (chisel != null) { lastChiselQuery = chisel.search().getValue(); lastChiselPage = chisel.page(); }
    }

    @Override
    public void added() {
        super.added();
        // The build-menu key opens this screen; ignore it until it has been let go, or key repeat would close us.
        menuKeyArmed = !isKeyDown(BuildKeys.BUILD_MENU);
    }

    private static boolean isKeyDown(final net.minecraft.client.KeyMapping k) {
        if (k.isUnbound()) return false;
        final InputConstants.Key key = InputConstants.getKey(k.saveString());
        if (key.getType() != InputConstants.Type.KEYSYM) return false;
        return InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(), key.getValue());
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        final boolean typing = getFocused() instanceof EditBox eb && eb.isFocused();
        if (!typing && menuKeyArmed && BuildKeys.BUILD_MENU.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        final GuiEventListener focus = getFocused();
        final SlateList<BuildMode> list = table;
        // PageUp / PageDown turn the wheel pages, except in the table, which pages through its rows.
        if (!typing && focus != list && (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN)) {
            final int dir = keyCode == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1;
            final WheelPanel focused = focusedPanel();
            if (focused != null && focused.turnPage(dir)) return true;
        }
        if (!typing && list != null && list.visible && focus != list) {
            // Arrows move in the modes table (design §5). Vanilla would spend Up / Down on spatial focus moves first, so
            // they are routed here, unless the focused widget uses them itself (a wheel steps along its slices).
            if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP) {
                if (focus != null && focus.keyPressed(keyCode, scanCode, modifiers)) return true;
                setFocused(list);
                return list.keyPressed(keyCode, scanCode, modifiers);
            }
            // Enter with nothing focused activates the selected mode, like Enter in the table.
            if (focus == null && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
                setFocused(list);
                return list.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(final int keyCode, final int scanCode, final int modifiers) {
        if (BuildKeys.BUILD_MENU.matches(keyCode, scanCode)) menuKeyArmed = true;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private @Nullable WheelPanel focusedPanel() {
        for (final WheelPanel p : new WheelPanel[] {shapes, chisel}) {
            if (p == null || !p.isVisible()) continue;
            for (final AbstractWidget w : p.widgets()) if (w == getFocused()) return p;
        }
        if (shapes != null && shapes.isVisible()) return shapes;
        return chisel != null && chisel.isVisible() ? chisel : null;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = openAnim.get();
        if (t.isVanilla()) {
            g.fillGradient(0, 0, width, height, Colors.scaleAlpha(0x50101010, a), Colors.scaleAlpha(0x78101010, a));
        } else {
            g.fill(0, 0, width, height, Colors.scaleAlpha(0x38000000, a));
            SlateDraw.vignette(g, 0, 0, width, height, 0.35f * a);
        }
        panel(g, left.move(Math.round((1f - a) * -10f), 0), a, t, p);
        panel(g, right.move(Math.round((1f - a) * 10f), 0), a, t, p);
    }

    private static void panel(final GuiGraphics g, final Rect r, final float a, final Theme t, final Palette p) {
        if (t.isVanilla()) {
            SlateDraw.rect(g, r.x(), r.y(), r.w(), r.h(), Colors.scaleAlpha(0xB0000000, a));
            SlateDraw.outline(g, r.x(), r.y(), r.w(), r.h(), Colors.scaleAlpha(0xFF000000, a), 0);
            SlateDraw.outline(g, r.x() + 1, r.y() + 1, r.w() - 2, r.h() - 2, Colors.scaleAlpha(0x40FFFFFF, a), 0);
        } else {
            SlateDraw.shadow(g, r.x(), r.y(), r.w(), r.h(), 0.6f * a);
            SlateDraw.pixelRound(g, r.x(), r.y(), r.w(), r.h(), Colors.scaleAlpha(Colors.withAlpha(p.bg(), 0xEC), a), t.radius());
            SlateDraw.outline(g, r.x(), r.y(), r.w(), r.h(), Colors.scaleAlpha(p.border(), a), t.radius());
        }
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean vanilla = t.isVanilla();
        final float a = openAnim.get();

        // Right header title.
        // Right header title: "Building modes", "Modes" when the header is crowded.
        final int titleW = headerTitleRight - header.x();
        Component title = Fonts.heading(getTitle());
        if (SlateDraw.width(title) > titleW) title = Fonts.heading(Component.translatable("slate_building.ui.menu.title_short"));
        if (SlateDraw.width(title) <= titleW) {
            g.drawString(font, title, header.x(), header.y() + (HEADER_H - 8) / 2, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.text(), a), vanilla);
        }
        // Rules between header / table / options.
        final int ruleCol = Colors.scaleAlpha(vanilla ? 0x40FFFFFF : p.border(), a);
        SlateDraw.hline(g, header.x(), header.bottom() + 2, header.w(), ruleCol);
        SlateDraw.hline(g, optionsRect.x(), optionsRect.y() - 4, optionsRect.w(), ruleCol);
        options.render(g, a);

        // Left: section titles (stacked) and wheel extras.
        if (stacked) {
            sectionTitle(g, shapesTitle, Component.translatable("slate_building.ui.menu.shapes"), a, vanilla, p);
            sectionTitle(g, chiselTitle, Component.translatable("slate_building.ui.menu.chisel"), a, vanilla, p);
        }
        if (shapes != null) shapes.render(g, a);
        if (chisel != null) chisel.render(g, a);

        // Row tooltip (the list draws rows itself, so it asks here).
        final SlateList<BuildMode> list = table;
        if (list != null && list.isMouseOver(mouseX, mouseY) && !list.items().isEmpty()) {
            final BuildMode hovered = rowAt(list, mouseY);
            if (hovered != null) hoverTip(hovered, mouseX, mouseY);
            else hoverSince = 0;
        } else {
            hoverSince = 0;
        }
    }

    private long hoverSince;
    private @Nullable BuildMode hoverMode;

    private void hoverTip(final BuildMode m, final int mx, final int my) {
        if (m != hoverMode) {
            hoverMode = m;
            hoverSince = dev.fallingcloud.slate.core.gfx.Clock.nowMs();
        }
        if (dev.fallingcloud.slate.core.gfx.Clock.nowMs() - hoverSince < SlateTooltips.DELAY_MS + 250) return;
        SlateTooltips.request(rows.tooltip(m), null);
    }

    private static @Nullable BuildMode rowAt(final SlateList<BuildMode> list, final double my) {
        final int stride = ModeRows.ROW_H + 1;
        final int rel = (int) (my - list.getY() + list.scrollAmount());
        final int i = rel / stride;
        if (rel < 0 || i < 0 || i >= list.items().size() || rel - i * stride >= ModeRows.ROW_H) return null;
        return list.items().get(i);
    }

    private static void sectionTitle(final GuiGraphics g, final Rect r, final Component text, final float a, final boolean vanilla, final Palette p) {
        final Component t = Fonts.heading(text);
        g.drawString(SlateDraw.font(), t, r.x(), r.y() + 1, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.textMuted(), a), vanilla);
        final int lx = r.x() + SlateDraw.width(t) + 6;
        SlateDraw.hline(g, lx, r.y() + 5, r.right() - lx, Colors.scaleAlpha(vanilla ? 0x40FFFFFF : p.border(), a));
    }

    // ------------------------------------------------------------------ dev harness

    /** Dev harness: selects (options only) a mode in the table. */
    public void debugSelect(final String modeId) {
        final BuildMode m = BuildModes.byId(modeId);
        if (m != null && table != null) table.select(m);
    }

    /** Dev harness: types {@code text} into the visible shape (or chisel) search field. */
    public void debugSearch(final String text) {
        final WheelPanel p = shapes != null && shapes.isVisible() ? shapes : chisel;
        if (p != null) p.search().setValue(text);
    }

    /** Dev harness: whether the shape search (as restored or typed) matches any slice of the held material. */
    public boolean debugShapeSearchMatches() {
        return shapes != null && shapes.hasMatches();
    }

    /** Dev harness: the mode selected in the table, or "" (keyboard navigation checks). */
    public String debugSelectedId() {
        final BuildMode m = table == null ? null : table.selectedItem();
        return m == null ? "" : m.id();
    }

    /** Dev harness: whether keyboard focus is on the modes table. */
    public boolean debugTableFocused() {
        return table != null && getFocused() == table;
    }

    /** Whether the wheel settings changed while the menu was open (editor returned): rebuild the wheels. */
    void wheelsChanged() {
        if (shapes != null) shapes.refresh(true);
        if (chisel != null) chisel.refresh(true);
        WheelConfig.changed();
    }
}
