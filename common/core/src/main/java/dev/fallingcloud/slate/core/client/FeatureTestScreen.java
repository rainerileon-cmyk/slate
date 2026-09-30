package dev.fallingcloud.slate.core.client;

import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.module.Features;
import dev.fallingcloud.slate.core.module.KnownModule;
import dev.fallingcloud.slate.core.module.KnownModules;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.screen.slot.Layout;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The harness screen {@code slate:feature_test}: rule R3 made visible. One row per feature module of the catalogue,
 * one column per layout, and in each cell what {@link Features#gate} hands a screen of that layout for that
 * module's feature: the button as built (installed), the same button locked (missing, Overhaul), or nothing
 * (missing, Custom or Vanilla). Run it with modules left off the runtime ({@code -PslateModules=core,config}) to see
 * the locked and hidden cells. Not reachable from any menu.
 */
public final class FeatureTestScreen extends SlateScreen {

    private static final int ROW_H = 30, LABEL_W = 120, GAP = 8;

    private final List<Row> rows = new ArrayList<>();
    private Rect grid = new Rect(0, 0, 0, 0);
    private int colW;

    private record Row(KnownModule module, int y) {}

    public FeatureTestScreen(@Nullable final Screen parent) {
        super(Component.translatable("slate.feature_test.title"), parent);
        this.maxContentWidth = 620;
    }

    @Override
    protected void build() {
        rows.clear();
        final Rect c = contentRect();
        final SlateScrollPanel panel = add(new SlateScrollPanel(c.x(), c.y(), c.w(), c.h()).padding(2));
        final int inner = panel.innerWidth();
        colW = (inner - LABEL_W - GAP * 3) / 3;
        grid = new Rect(c.x() + 2, c.y() + 2, inner, 0);
        int y = 22;                                         // the column headings are drawn above the first row
        for (final KnownModule m : KnownModules.features()) {
            rows.add(new Row(m, y));
            int x = LABEL_W + GAP;
            for (final Layout layout : Layout.values()) {
                final SlateButton b = Features.gate(m.id(), layout, () -> new SlateButton(0, 0, colW, SlateButton.HEIGHT,
                    Component.translatable("slate.feature_test.open", m.name()), () -> {}).icon(m.icon()));
                if (b != null) panel.add(b, x, y + (ROW_H - SlateButton.HEIGHT) / 2);
                x += colW + GAP;
            }
            y += ROW_H;
        }
        panel.setContentHeight(y + 4);
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final int muted = van ? 0xFFC0C0C0 : p.textMuted();
        final int dim = van ? 0xFF8B8B8B : p.textDim();
        // Column headings: the three layouts, with the effective layout of the hub slot marked as "this screen".
        int x = grid.x() + LABEL_W + GAP;
        final Layout here = MenuSlots.effective("slate:hub");
        for (final Layout layout : Layout.values()) {
            final Component head = Fonts.heading(Component.translatable("slate.layout." + layout.key()));
            g.drawString(font, head, x, grid.y() + 4, layout == here ? (van ? 0xFFFFFFFF : p.text()) : muted, van);
            if (layout == here) SlateDraw.accentCap(g, x, grid.y() + 15, Math.min(32, font.width(head)), 1f);
            x += colW + GAP;
        }
        // Row labels: module name, installed or missing, and "hidden" in the empty cells.
        for (final Row r : rows) {
            final int ry = grid.y() + r.y();
            final boolean on = r.module().installed();
            Icons.draw(g, r.module().icon(), grid.x(), ry + (ROW_H - 12) / 2, 12, on ? (van ? 0xFFFFFFFF : p.accent()) : dim);
            g.drawString(font, SlateDraw.truncate(r.module().name(), LABEL_W - 18), grid.x() + 16, ry + 6, on ? (van ? 0xFFFFFFFF : p.text()) : muted, van);
            g.drawString(font, Component.translatable(on ? "slate.feature_test.installed" : "slate.feature_test.missing"), grid.x() + 16, ry + 16, dim, van);
            int cx = grid.x() + LABEL_W + GAP;
            for (final Layout layout : Layout.values()) {
                if (Features.state(r.module().id(), layout) == Features.State.HIDDEN) {
                    SlateDraw.textCentered(g, Component.translatable("slate.feature_test.hidden"), cx + colW / 2, ry + 11, Colors.withAlpha(dim, 0xA0), van);
                }
                cx += colW + GAP;
            }
        }
    }
}
