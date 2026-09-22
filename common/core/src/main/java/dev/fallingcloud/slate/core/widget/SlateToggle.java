package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * A switch with a label on the left and the animated knob on the right. Width covers label + switch;
 * the whole row is clickable. Height 20. Keyboard: Enter/Space toggle, Left/Right set off/on.
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

    public SlateToggle onChange(final Consumer<Boolean> c) { this.onChange = c; return this; }

    /** Put the switch on the left and the label after it. */
    public SlateToggle switchFirst() { this.labelLeft = false; return this; }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        toggle();
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible) return false;
        if (keyCode == 257 || keyCode == 32 || keyCode == 335) { toggle(); return true; }
        if (keyCode == 263 && value) { toggle(); return true; }       // left = off
        if (keyCode == 262 && !value) { toggle(); return true; }      // right = on
        return false;
    }

    private void toggle() {
        value = !value;
        knob.set(value ? 1 : 0);
        SlateSounds.tick();
        if (onChange != null) onChange.accept(value);
    }

    @Override
    public void playDownSound(final net.minecraft.client.sounds.SoundManager handler) {
        // The toggle plays its own tick.
    }

    private int switchX() { return labelLeft ? getX() + getWidth() - SWITCH_W : getX(); }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        final float k = knob.get();
        final int sx = switchX();
        final int sy = y + (h - SWITCH_H) / 2;
        // Track
        int track = Colors.lerp(Colors.lerp(p.surfaceActive(), p.surfaceHover(), hover()), p.accent(), k);
        int trackBorder = Colors.lerp(p.borderStrong(), p.accentHover(), k);
        int knobColor = Colors.lerp(p.textMuted(), p.accentText(), k);
        if (!this.active) {
            track = Colors.withAlpha(track, 0x70);
            trackBorder = Colors.withAlpha(trackBorder, 0x70);
            knobColor = p.textDim();
        }
        SlateDraw.pixelRound(g, sx, sy, SWITCH_W, SWITCH_H, Colors.scaleAlpha(track, a), 3);
        SlateDraw.outline(g, sx, sy, SWITCH_W, SWITCH_H, Colors.scaleAlpha(trackBorder, a), 3);
        // Knob: 8x8, travels 2..12 px; the knob grows 1 px while pressed.
        final int kx = sx + 2 + Math.round((SWITCH_W - 4 - 8) * clamp01(k));
        SlateDraw.pixelRound(g, kx, sy + 2, 8, SWITCH_H - 4, Colors.scaleAlpha(knobColor, a), 2);
        SlateDraw.focusRing(g, sx, sy, SWITCH_W, SWITCH_H, focus() * a);
        // Label
        final int fg = Colors.scaleAlpha(this.active ? Colors.lerp(p.text(), 0xFFFFFFFF, hover() * 0.3f) : p.textDim(), a);
        final int tx = labelLeft ? x : x + SWITCH_W + 8;
        final int tw = w - SWITCH_W - 8;
        g.drawString(SlateDraw.font(), SlateDraw.truncate(getMessage(), tw), tx, SlateDraw.textY(y, h), fg, false);
    }

    /** OUT_BACK overshoots below 0 / above 1 at the ends; the knob must stay inside the track. */
    private static float clamp01(final float k) { return Math.max(0f, Math.min(1f, k)); }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        final float k = clamp01(knob.get());
        final int sx = switchX() - (labelLeft ? 2 : 0);
        final int sy = y + (h - SWITCH_H) / 2;
        final int tw = SWITCH_W + 2;
        // The vanilla slider sprites: sunken track, stone handle; a green tint fades in when on.
        final float lift = this.active ? Math.max(hover(), focus()) : 0f;
        final int kx = sx + 1 + Math.round((tw - 2 - 8) * k);
        SlateDraw.vanillaSlider(g, sx, sy, tw, SWITCH_H, kx, lift, this.active, a);
        final int on = Colors.scaleAlpha(Colors.withAlpha(t.palette().success(), 0x90), a * k);
        g.fill(sx + 1, sy + 1, kx, sy + SWITCH_H - 1, on);
        final int fg = Colors.scaleAlpha(this.active ? 0xFFFFFFFF : 0xFFA0A0A0, a);
        final int tx = labelLeft ? x : x + tw + 8;
        g.drawString(SlateDraw.font(), SlateDraw.truncate(getMessage(), w - tw - 8), tx, SlateDraw.textY(y, h), fg, true);
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.button",
            Component.empty().append(getMessage()).append(": ").append(value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF)));
        if (this.active) {
            out.add(NarratedElementType.USAGE, Component.translatable(this.isFocused() ? "narration.button.usage.focused" : "narration.button.usage.hovered"));
        }
    }
}
