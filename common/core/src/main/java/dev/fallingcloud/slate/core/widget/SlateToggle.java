package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A switch with a label on the left and the animated knob on the right. Width covers label + switch;
 * the whole row is clickable. Height 20.
 */
public class SlateToggle extends SlateWidget {

    public static final int SWITCH_W = 22, SWITCH_H = 12;

    private boolean value;
    private final Anim knob = new Anim(0, 160, Ease.OUT_BACK);
    private Consumer<Boolean> onChange;
    private boolean labelLeft = true;

    public SlateToggle(final int x, final int y, final int width, final Component label, final boolean value, final Consumer<Boolean> onChange) {
        super(x, y, width, 20, label);
        this.value = value;
        this.knob.snap(value ? 1 : 0);
        this.onChange = onChange;
    }

    public boolean value() { return value; }

    public SlateToggle setValue(final boolean v) {
        this.value = v;
        knob.set(v ? 1 : 0);
        return this;
    }

    /** Put the switch on the left and the label after it. */
    public SlateToggle switchFirst() { this.labelLeft = false; return this; }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        toggle();
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (this.active && this.visible && (keyCode == 257 || keyCode == 32)) { toggle(); return true; }
        return false;
    }

    private void toggle() {
        value = !value;
        knob.set(value ? 1 : 0);
        SlateSounds.tick();
        if (onChange != null) onChange.accept(value);
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        final float k = knob.get();
        final int sx = labelLeft ? x + w - SWITCH_W : x;
        final int sy = y + (h - SWITCH_H) / 2;
        // Track
        final int track = Colors.lerp(Colors.lerp(p.surfaceActive(), p.surfaceHover(), hover()), p.accent(), k);
        SlateDraw.pixelRound(g, sx, sy, SWITCH_W, SWITCH_H, Colors.scaleAlpha(track, a), 3);
        SlateDraw.outline(g, sx, sy, SWITCH_W, SWITCH_H, Colors.scaleAlpha(Colors.lerp(p.borderStrong(), p.accentHover(), k), a), 3);
        // Knob
        final int kx = sx + 2 + Math.round((SWITCH_W - 4 - 8) * k);
        final int knobColor = Colors.lerp(p.textMuted(), p.accentText(), k);
        SlateDraw.pixelRound(g, kx, sy + 2, 8, SWITCH_H - 4, Colors.scaleAlpha(knobColor, a), 2);
        SlateDraw.focusRing(g, sx, sy, SWITCH_W, SWITCH_H, focus() * a);
        // Label
        final int fg = Colors.scaleAlpha(this.active ? Colors.lerp(p.text(), 0xFFFFFFFF, hover() * 0.3f) : p.textDim(), a);
        final int tx = labelLeft ? x : x + SWITCH_W + 8;
        final int tw = labelLeft ? w - SWITCH_W - 8 : w - SWITCH_W - 8;
        g.drawString(SlateDraw.font(), SlateDraw.truncate(getMessage(), tw), tx, y + (h - 9) / 2 + 1, fg, false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final float a = effectiveAlpha();
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        final float k = knob.get();
        final int sx = labelLeft ? x + w - SWITCH_W - 2 : x;
        final int sy = y + (h - SWITCH_H) / 2;
        // A slider-like stone track with the handle sliding; green tint when on.
        SlateDraw.vanillaButton(g, sx, sy, SWITCH_W + 2, SWITCH_H, hover(), this.active, a);
        final int on = Colors.scaleAlpha(Colors.withAlpha(t.palette().success(), 0x90), a * k);
        g.fill(sx + 1, sy + 1, sx + SWITCH_W + 1, sy + SWITCH_H - 1, on);
        final int kx = sx + 1 + Math.round((SWITCH_W - 8) * k);
        SlateDraw.vanillaButton(g, kx, sy, 8, SWITCH_H, Math.max(hover(), focus()), true, a);
        final int fg = Colors.scaleAlpha(this.active ? 0xFFFFFFFF : 0xFFA0A0A0, a);
        final int tx = labelLeft ? x : x + SWITCH_W + 10;
        g.drawString(SlateDraw.font(), SlateDraw.truncate(getMessage(), w - SWITCH_W - 10), tx, y + (h - 9) / 2 + 1, fg, true);
    }
}
