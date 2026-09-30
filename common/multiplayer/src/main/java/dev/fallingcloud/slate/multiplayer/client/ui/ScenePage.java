package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.stage.StageWidget;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateSearchField;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * A page of the Overhaul friends screen that shows its people in a piece of Minecraft (docs/LAYOUTS.md, 6): the
 * search, the order and the plus in the header, the scene with an arrow at either side, a line, and under the line
 * what there is to know about whoever is chosen, with the buttons for them.
 *
 * @param <S> what the page can be ordered by
 */
abstract class ScenePage<S> extends FriendsHubScreen.HubPage {

    protected String query = "";
    protected S sort;
    @Nullable protected SocialScene scene;
    protected Rect view = new Rect(0, 0, 0, 0), below = new Rect(0, 0, 0, 0);
    @Nullable private SlateIconButton previous, next;
    @Nullable protected SidebarScreen host;
    private boolean typing;

    ScenePage(final FriendsHubScreen screen, final String id, final Component title, final Icon icon, final S sort) {
        super(screen, id, title, icon);
        this.sort = sort;
    }

    protected abstract SocialScene.Setting setting();

    protected abstract List<S> sorts();

    protected abstract Component sortName(S sort);

    /** What the plus in the header does, and what it says it does. */
    protected abstract void add();

    protected abstract Component addLabel();

    /** Puts the people for the current search, order and place in the list on the scene. */
    protected abstract void populate();

    /** Builds the widgets under the line for whoever is chosen. */
    protected abstract void details(SidebarScreen s, Rect area);

    /** -1 or 1: shows what comes before, or after. */
    protected abstract void turn(int direction);

    protected abstract boolean canTurn(int direction);

    /** How many people fit side by side in a view this wide. */
    protected int fitting() {
        return Mth.clamp(view.w() / 58, 3, 7);
    }

    @Override
    public void build(final SidebarScreen s, final Rect area) {
        host = s;
        // The header holds what the sketch puts over the scene: the search, the order, the plus.
        final int hy = (SlateScreen.HEADER_H - 20) / 2;
        int hx = s.width - SlateScreen.PAD - 20;
        s.addPageWidget(new SlateIconButton(hx, hy, 20, Icon.PLUS, addLabel(), this::add).variant(dev.fallingcloud.slate.core.widget.SlateButton.Variant.PRIMARY));
        final int ddW = s.width >= 520 ? 100 : 78;
        hx -= ddW + 4;
        s.addPageWidget(new SlateDropdown<>(hx, hy, ddW, sorts(), sort, this::sortName, v -> { sort = v; refresh(); }));
        final int searchW = s.width >= 640 ? 150 : s.width >= 460 ? 112 : 84;
        hx -= searchW + 4;
        final SlateSearchField search = new SlateSearchField(hx, hy, searchW, q -> { if (!q.equals(query)) { query = q; typing = true; refresh(); } });
        search.setValue(query);
        s.addPageWidget(search);
        if (typing) {
            // The page was rebuilt under the typing: the new box takes over where the old one was.
            typing = false;
            s.setFocused(search);
            search.setFocused(true);
        }

        final int viewH = Mth.clamp(Math.round(area.h() * 0.6f), 64, Math.max(64, Math.round(area.w() * 0.62f)));
        view = new Rect(area.x(), area.y(), area.w(), viewH);
        below = new Rect(area.x(), view.bottom() + 8, area.w(), Math.max(20, area.bottom() - view.bottom() - 8));
        if (scene == null || scene.stage.isClosed()) scene = new SocialScene(s, setting());
        final SocialScene sc = scene;
        s.addPageWidget(new StageWidget(view.x() + 1, view.y() + 1, view.w() - 2, view.h() - 2, sc.stage, title()));
        s.addPageWidget(new Overlay(view, this::overlay));
        previous = s.addPageWidget(new SlateIconButton(view.x() + 4, view.centerY() - 10, 20, Icon.CHEVRON_LEFT, UiUtil.t("scene.previous"), () -> turn(-1)));
        next = s.addPageWidget(new SlateIconButton(view.right() - 24, view.centerY() - 10, 20, Icon.CHEVRON_RIGHT, UiUtil.t("scene.next"), () -> turn(1)));
        populate();
        arrows();
        details(s, below);
    }

    /** Everything again, for another search, order, choice or place in the list: the page is rebuilt in place. */
    protected void refresh() {
        if (host != null && host.currentPage() == this) host.refreshPage();
    }

    protected void arrows() {
        if (previous != null) { previous.active = canTurn(-1); previous.visible = canTurn(-1) || canTurn(1); }
        if (next != null) { next.active = canTurn(1); next.visible = canTurn(-1) || canTurn(1); }
    }

    @Override
    void onModelChanged() { refresh(); }

    @Override
    public void onHide() {
        if (scene != null) { scene.close(); scene = null; }
    }

    /** What is drawn over the scene: the names over the heads, and the page's own say (nobody here yet). */
    protected void overlay(final GuiGraphics g, final int mouseX, final int mouseY) {
        if (scene != null) scene.drawTags(g, view);
    }

    @Override
    public void render(final SidebarScreen s, final GuiGraphics g, final Rect area, final int mouseX, final int mouseY, final float partialTick) {
        // The plate the scene lies in, and the line under it.
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        SlateDraw.shadow(g, view.x(), view.y(), view.w(), view.h(), 0.55f);
        SlateDraw.pixelRound(g, view.x(), view.y(), view.w(), view.h(), van ? 0xFF000000 : 0xFF0B0C0F, van ? 0 : t.radius());
        final int line = van ? 0xFF6F6F6F : p.border();
        SlateDraw.hgradient(g, area.x(), view.bottom() + 3, area.w() / 6, 1, Colors.withAlpha(line, 0), line);
        SlateDraw.rect(g, area.x() + area.w() / 6, view.bottom() + 3, area.w() - 2 * (area.w() / 6), 1, line);
        SlateDraw.hgradient(g, area.right() - area.w() / 6, view.bottom() + 3, area.w() / 6, 1, line, Colors.withAlpha(line, 0));
    }

    /** Draws over the scene and takes no input: the names over the heads live here. */
    private static final class Overlay extends SlateWidget {

        interface Painter {
            void paint(GuiGraphics g, int mouseX, int mouseY);
        }

        private final Rect frame;
        private final Painter painter;

        Overlay(final Rect view, final Painter painter) {
            super(view.x(), view.y(), view.w(), view.h(), Component.empty());
            this.frame = view;
            this.painter = painter;
            this.active = false;
            silent();
        }

        @Override
        public boolean isMouseOver(final double mouseX, final double mouseY) { return false; }

        @Override
        public boolean mouseClicked(final double mouseX, final double mouseY, final int button) { return false; }

        @Override
        public net.minecraft.client.gui.ComponentPath nextFocusPath(final net.minecraft.client.gui.navigation.FocusNavigationEvent event) { return null; }

        @Override
        protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            painter.paint(g, mouseX, mouseY);
            final Theme t = Theme.current();
            SlateDraw.outline(g, frame.x(), frame.y(), frame.w(), frame.h(), t.palette().borderStrong(), t.radius());
        }

        @Override
        protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            painter.paint(g, mouseX, mouseY);
            SlateDraw.outline(g, frame.x(), frame.y(), frame.w(), frame.h(), 0xFF000000, 0);
        }
    }
}
