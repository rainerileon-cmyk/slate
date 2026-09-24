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
 * else a dropdown. Laid out in one or two columns. Edits go straight to {@link ClientModeState#setParam} (the mode
 * controller previews with them; the config saves them).
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

    static int columns(final int width) {
        return width >= 200 ? 2 : 1;
    }

    /** Height the panel needs for {@code m} at {@code width}. */
    static int heightFor(final @Nullable BuildMode m, final int width) {
        if (m == null || m.params().isEmpty()) return TITLE_H + 12;
        final int rows = (m.params().size() + columns(width) - 1) / columns(width);
        return TITLE_H + rows * (CELL_H + ROW_GAP) + 1;
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
        if (m.params().isEmpty()) {
            g.drawString(SlateDraw.font(), Component.translatable("slate_building.ui.menu.no_options"), x, y + TITLE_H,
                Colors.scaleAlpha(vanilla ? 0xFF909090 : p.textDim(), alpha), vanilla);
        }
        for (final Label l : labels) {
            g.drawString(SlateDraw.font(), SlateDraw.truncate(l.text, l.w - 4), l.x, SlateDraw.textY(l.y, CELL_H),
                Colors.scaleAlpha(vanilla ? 0xFFE0E0E0 : p.textMuted(), alpha), vanilla);
        }
    }
}
