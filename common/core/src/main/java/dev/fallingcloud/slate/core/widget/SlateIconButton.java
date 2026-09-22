package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
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
    protected void drawContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final int fg, final boolean shadow) {
        if (icon == null) return;
        final Palette p = Theme.current().palette();
        int color = fg;
        if (showToggle && toggled) color = Colors.scaleAlpha(p.accent(), effectiveAlpha());
        Icons.draw(g, icon, x + (w - iconSize) / 2, y + (h - iconSize) / 2, iconSize, color);
        if (showToggle && toggled && !Theme.current().isVanilla()) {
            SlateDraw.rect(g, x + w / 2 - 2, y + h - 3, 4, 1, color);
        }
    }
}
