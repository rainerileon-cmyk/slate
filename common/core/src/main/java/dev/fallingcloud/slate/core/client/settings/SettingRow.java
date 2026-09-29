package dev.fallingcloud.slate.core.client.settings;

import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateTooltips;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A settings row: a label on the left, a control on the right (a segmented row, a toggle, a button), an optional
 * description underneath. The row is a flat {@link SlateCard}, so it works inside scroll panels and cards on any
 * Slate screen; the control keeps its own width, the label takes the rest and shows the tooltip when hovered.
 */
public final class SettingRow extends SlateCard {

    public static final int HEIGHT = 24;

    private final Component label;
    @Nullable private final Component tooltip;
    @Nullable private final SlateLabel description;

    private SettingRow(final int width, final Component label, @Nullable final Component tooltip, final AbstractWidget control,
                       @Nullable final Component description) {
        super(0, 0, width, HEIGHT);
        flat();
        this.label = label;
        this.tooltip = tooltip;
        int h = HEIGHT;
        if (description != null) {
            this.description = new SlateLabel(0, 0, width, description).style(SlateLabel.Style.MUTED).wrap(true);
            add(this.description, 0, HEIGHT + 2);
            h = HEIGHT + 2 + this.description.getHeight() + 4;
        } else {
            this.description = null;
        }
        add(control, width - control.getWidth(), (HEIGHT - control.getHeight()) / 2);
        setHeight(h);
    }

    /** A row holding {@code control} at the right, {@code width} wide. */
    public static SettingRow of(final int width, final Component label, @Nullable final Component tooltip, final AbstractWidget control) {
        return new SettingRow(width, label, tooltip, control, null);
    }

    /** A row with a muted, wrapped description under it. */
    public static SettingRow of(final int width, final Component label, @Nullable final Component tooltip, final AbstractWidget control, @Nullable final Component description) {
        return new SettingRow(width, label, tooltip, control, description);
    }

    /** The control's width for a row of {@code width}: three fifths of the row, at most 320 px, leaving the label at least 80. */
    public static int controlWidth(final int width) {
        return Math.max(Math.min(width - 80, 120), Math.min(320, width * 3 / 5));
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = alpha * enterProgress();
        final int controlW = widgets().isEmpty() ? 0 : widgets().get(widgets().size() - 1).getWidth();
        final int labelW = w - controlW - 8;
        final int color = t.isVanilla() ? 0xFFFFFFFF : p.text();
        g.drawString(SlateDraw.font(), SlateDraw.truncate(label, labelW), x, SlateDraw.textY(y, HEIGHT), Colors.scaleAlpha(color, a), t.isVanilla());
        if (tooltip != null && mouseX >= x && mouseX < x + labelW && mouseY >= y && mouseY < y + HEIGHT) {
            SlateTooltips.request(tooltip, this);
        }
    }
}
