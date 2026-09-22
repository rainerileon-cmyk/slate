package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A square icon-only button. The message is used for narration and (if no tip is set) the tooltip. */
public class SlateIconButton extends SlateButton {

    private boolean toggled;
    private boolean showToggle;

    public SlateIconButton(final int x, final int y, final int size, final Icon icon, final Component label, final Runnable onPress) {
        super(x, y, size, size, label, onPress);
        this.icon = icon;
        this.iconSize = size >= 24 ? 16 : size >= 18 ? 12 : 8;
        this.variant = Variant.GHOST;
        if (label != null && !label.getString().isEmpty()) tip(label);
    }

    /** Draw an "on" state (accent tint) - for mute/deafen/pin style buttons. */
    public SlateIconButton toggled(final boolean on) { this.toggled = on; this.showToggle = true; return this; }

    public boolean isToggled() { return toggled; }

    public SlateIconButton setIcon(final Icon icon) { this.icon = icon; return this; }

    @Override
    public SlateIconButton variant(final Variant v) { super.variant(v); return this; }

    @Override
    public SlateIconButton iconSize(final int size) { super.iconSize(size); return this; }

    @Override
    public SlateIconButton tip(final Component tooltip) { super.tip(tooltip); return this; }

    @Override
    public SlateIconButton tip(final List<Component> lines) { super.tip(lines); return this; }

    @Override
    protected void drawContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final int fg, final boolean shadow) {
        if (icon == null) return;
        final Palette p = Theme.current().palette();
        int color = fg;
        if (showToggle && toggled && this.active) color = Colors.scaleAlpha(p.accent(), effectiveAlpha());
        Icons.draw(g, icon, x + (w - iconSize) / 2, y + (h - iconSize) / 2, iconSize, color);
        if (showToggle && toggled) {
            // A short accent bar under the glyph marks the "on" state without a second colour.
            SlateDraw.rect(g, x + w / 2 - 3, y + h - 3, 6, 1, color);
        }
    }
}
