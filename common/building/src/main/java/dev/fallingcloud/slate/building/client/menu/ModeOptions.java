package dev.fallingcloud.slate.building.client.menu;

import dev.fallingcloud.slate.building.client.mode.ClientModeState;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.ModeParam;
import dev.fallingcloud.slate.building.ops.ModeParams;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateSegmented;
import dev.fallingcloud.slate.core.widget.SlateSlider;
import dev.fallingcloud.slate.core.widget.SlateToggle;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The build menu's options panel for the selected mode: a title row (mode name, how it is driven, reset) and one
 * control per {@link ModeParam}: BOOL → toggle, INT → slider, CHOICE → segmented control when its options fit,
 * else a dropdown. Laid out in one or two columns. Below them, anchored to the bottom of the panel, a footer: the
 * corner-distance-in-the-air slider at the bottom left and the build progress (or last result) to its right, each
 * in its own cell, so neither can run into the other or into a control. Edits go straight to
 * {@link ClientModeState#setParam} (the mode controller previews with them; the config saves them).
 */
final class ModeOptions {

    static final int TITLE_H = 16;
    static final int CELL_H = 20;
    static final int ROW_GAP = 3;
    static final int COL_GAP = 8;

    private record Label(Component text, int x, int y, int w) {}

    private final List<Label> labels = new ArrayList<>();
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private @Nullable BuildMode mode;
    private Rect area = new Rect(0, 0, 0, 0);
    private @Nullable SlateIconButton reset;
    /** The build progress cell: its left edge and the top of its row (the panel's right edge bounds it). */
    private int progressX, progressY;

    static int columns(final int width) {
        return width >= 200 ? 2 : 1;
    }

    /** Footer rows: one (slider | progress) in two columns; stacked, the progress under the slider, in one. */
    private static int footerRows(final int width) {
        return columns(width) == 2 ? 1 : 2;
    }

    /** Controls shown for {@code m}: its parameters, plus the corner-distance-in-the-air slider for modes that put corners in the air. */
    private static int controls(final BuildMode m) {
        return m.params().size() + (ClientModeState.airAllowed(m) ? 1 : 0);
    }

    /** Height the panel needs for {@code m} at {@code width}: the parameter rows (or the "nothing to set" line), then the footer. */
    static int heightFor(final @Nullable BuildMode m, final int width) {
        final int footer = footerRows(width) * (CELL_H + ROW_GAP);
        if (m == null || m.params().isEmpty()) return TITLE_H + (m == null || controls(m) == 0 ? 12 : 0) + footer + 1;
        final int rows = (m.params().size() + columns(width) - 1) / columns(width);
        return TITLE_H + rows * (CELL_H + ROW_GAP) + footer + 1;
    }

    List<AbstractWidget> widgets() { return widgets; }

    @Nullable BuildMode mode() { return mode; }

    /** Creates the controls for {@code m} inside {@code r} (the caller adds {@link #widgets()} to the screen). */
    List<AbstractWidget> build(final @Nullable BuildMode m, final Rect r, final Runnable onReset) {
        this.mode = m;
        this.area = r;
        labels.clear();
        widgets.clear();
        reset = null;
        if (m == null) return widgets;
        if (!m.params().isEmpty()) {
            reset = new SlateIconButton(r.right() - 14, r.y(), 14, Icon.UNDO, Component.translatable("slate_building.ui.menu.reset_options"), () -> {
                ClientModeState.setParams(ModeParams.defaults(m));
                onReset.run();
            });
            widgets.add(reset);
        }
        final ModeParams values = ClientModeState.params(m);
        final int cols = columns(r.w());
        final int colW = (r.w() - COL_GAP * (cols - 1)) / cols;
        for (int i = 0; i < m.params().size(); i++) {
            final ModeParam p = m.params().get(i);
            final int cx = r.x() + (i % cols) * (colW + COL_GAP);
            final int cy = r.y() + TITLE_H + (i / cols) * (CELL_H + ROW_GAP);
            final AbstractWidget w = control(m, p, values.get(p.id()), cx, cy, colW);
            if (w instanceof SlateWidget sw) sw.tip(List.of(p.displayName(), p.description().copy().withStyle(net.minecraft.ChatFormatting.GRAY)));
            widgets.add(w);
        }
        // The footer, from the panel's bottom up: the slider's row, and in one column the progress row under it.
        final int lastRowY = r.bottom() - CELL_H;
        final int sliderY = cols == 2 ? lastRowY : lastRowY - (CELL_H + ROW_GAP);
        progressX = cols == 2 ? r.x() + colW + COL_GAP : r.x();
        progressY = lastRowY;
        if (ClientModeState.airAllowed(m)) {
            // The corner distance in the air is one setting for every mode (modes.airDistance), edited here where the
            // mode is picked, so nothing rides on the scroll wheel in the world. Always the bottom-left cell.
            final Component label = Component.translatable("slate_building.settings.modes.air_distance");
            final SlateSlider air = new SlateSlider(r.x(), sliderY, colW, label, 1, 16, 1, ClientModeState.airDistance(),
                d -> Integer.toString((int) Math.round(d)), d -> ClientModeState.setAirDistance((int) Math.round(d))).compact(true);
            air.tip(List.of(label, Component.translatable("slate_building.settings.modes.air_distance.desc").withStyle(net.minecraft.ChatFormatting.GRAY)));
            widgets.add(air);
        }
        return widgets;
    }

    private AbstractWidget control(final BuildMode m, final ModeParam p, final Object value, final int x, final int y, final int w) {
        switch (p.type()) {
            case BOOL -> {
                return new SlateToggle(x, y, w, p.displayName(), Boolean.TRUE.equals(value), v -> ClientModeState.setParam(m, p.id(), v));
            }
            case INT -> {
                final int v = value instanceof Integer n ? n : (Integer) p.def();
                return new SlateSlider(x, y, w, p.displayName(), p.min(), p.max(), 1, v,
                    d -> Integer.toString((int) Math.round(d)), d -> ClientModeState.setParam(m, p.id(), (int) Math.round(d))).compact(true);
            }
            default -> {
                final String cur = value instanceof String s ? s : (String) p.def();
                // Segmented when every option fits next to the label, else a labelled dropdown.
                final int labelW = SlateDraw.width(p.displayName()) + 6;
                final int segW = w - labelW;
                int widest = 0;
                for (final String o : p.options()) widest = Math.max(widest, SlateDraw.width(p.optionName(o)));
                if (p.options().size() <= 4 && segW >= p.options().size() * (widest + 8) && segW >= 60) {
                    labels.add(new Label(p.displayName(), x, y, labelW));
                    return new SlateSegmented<>(x + labelW, y, segW, p.options(), cur, p::optionName, o -> ClientModeState.setParam(m, p.id(), o));
                }
                return new SlateDropdown<>(x, y, w, p.options(), cur, p::optionName, o -> ClientModeState.setParam(m, p.id(), o))
                    .label(p.displayName());
            }
        }
    }

    /**
     * The footer's progress cell (right of the corner-distance slider), drawn live every frame (never a rebuild): the
     * running operation's progress with a bar, else the last result. Right-aligned inside the cell, kept clear of the
     * panel's rounded corner, and shortened rather than overflowing to the left.
     */
    private void renderProgress(final GuiGraphics g, final float alpha, final boolean vanilla, final Palette p) {
        final ClientModeState.Progress progress = ClientModeState.progress();
        final var last = ClientModeState.lastResult();
        if (progress == null && last == null) return;
        final int inset = Theme.current().radius() + 6;
        final int right = area.right() - inset;
        final int room = right - progressX;
        if (room <= 8) return;
        final int textY = progressY + CELL_H - 9;
        if (progress != null) {
            final Component text = fit(Component.translatable("slate_building.ui.menu.progress", progress.done(), progress.total()),
                Component.literal(progress.done() + " / " + progress.total()), room);
            SlateDraw.textRight(g, text, right, textY, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.text(), alpha));
            final int barW = Math.min(room, Math.min(120, Math.max(40, area.w() / 3))), barX = right - barW, barY = textY - 5;
            SlateDraw.rect(g, barX, barY, barW, 3, Colors.scaleAlpha(vanilla ? 0xFF404040 : p.bg2(), alpha));
            SlateDraw.rect(g, barX, barY, Math.round(barW * progress.fraction()), 3, Colors.scaleAlpha(vanilla ? 0xFF55FF55 : p.accent(), alpha));
        } else {
            final int color = Colors.scaleAlpha(vanilla ? 0xFF909090 : p.textDim(), alpha);
            final Component line = Component.translatable("slate_building.ui.menu.result", last.placed(), last.broken());
            if (SlateDraw.width(line) <= room) {
                SlateDraw.textRight(g, line, right, textY, color);
                return;
            }
            // Too wide for one line: placed over broken, level with the slider's label and its track.
            final Component placed = fit(Component.translatable("slate_building.ui.menu.result_placed", last.placed()),
                Component.translatable("slate_building.ui.menu.result_placed_short", last.placed()), room);
            SlateDraw.textRight(g, placed, right, textY - 10, color);
            SlateDraw.textRight(g, fit(Component.translatable("slate_building.ui.menu.result_broken", last.broken()),
                Component.literal(String.valueOf(last.broken())), room), right, textY, color);
        }
    }

    /** {@code full} when it fits {@code width}, else {@code shorter}, cut with an ellipsis as a last resort. */
    private static Component fit(final Component full, final Component shorter, final int width) {
        if (SlateDraw.width(full) <= width) return full;
        if (SlateDraw.width(shorter) <= width) return shorter;
        final StringBuilder out = new StringBuilder();
        SlateDraw.truncate(shorter, width).accept((index, style, codePoint) -> {
            out.appendCodePoint(codePoint);
            return true;
        });
        return Component.literal(out.toString());
    }

    /** Title row and the labels of segmented controls. */
    void render(final GuiGraphics g, final float alpha) {
        final BuildMode m = mode;
        if (m == null || alpha <= 0.01f) return;
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean vanilla = t.isVanilla();
        final int x = area.x(), y = area.y();
        Icons.draw(g, m.icon(), x, y + 2, 8, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.accent(), alpha));
        final Component title = Component.translatable("slate_building.ui.menu.options_of", m.name());
        g.drawString(SlateDraw.font(), title, x + 12, y + 2, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.text(), alpha), vanilla);
        final int howX = x + 12 + SlateDraw.width(title) + 8;
        final int howW = area.right() - (reset != null ? 20 : 0) - howX;
        if (howW > 24) {
            final Component how = Component.translatable("slate_building.ui.menu.how." + m.kind().name().toLowerCase(Locale.ROOT));
            g.drawString(SlateDraw.font(), SlateDraw.truncate(how, howW), howX, y + 2, Colors.scaleAlpha(vanilla ? 0xFF909090 : p.textDim(), alpha), vanilla);
        }
        if (controls(m) == 0) {
            g.drawString(SlateDraw.font(), Component.translatable("slate_building.ui.menu.no_options"), x, y + TITLE_H,
                Colors.scaleAlpha(vanilla ? 0xFF909090 : p.textDim(), alpha), vanilla);
        }
        renderProgress(g, alpha, vanilla, p);
        for (final Label l : labels) {
            g.drawString(SlateDraw.font(), SlateDraw.truncate(l.text, l.w - 4), l.x, SlateDraw.textY(l.y, CELL_H),
                Colors.scaleAlpha(vanilla ? 0xFFE0E0E0 : p.textMuted(), alpha), vanilla);
        }
    }
}
