package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A row (wrapping to more rows when the width runs out) of colour swatches, one selected: the accent
 * preset picker. Hovering names the preset in a tooltip; arrows move the selection, Enter/Space pick.
 */
public class SlateSwatches extends SlateWidget {

    public static final int SIZE = 14, GAP = 4;

    private final List<Palette.AccentPreset> presets;
    private final IntConsumer onPick;
    private int selected = -1;
    private int hoverIdx = -1;
    private int cursor;

    public SlateSwatches(final int x, final int y, final int width, final List<Palette.AccentPreset> presets, final int selectedColor, final IntConsumer onPick) {
        super(x, y, width, SIZE, Component.translatable("slate.settings.accent_preset"));
        this.presets = new ArrayList<>(presets);
        this.onPick = onPick;
        setSelectedColor(selectedColor);
        this.cursor = Math.max(0, selected);
        setHeight(rows() * (SIZE + GAP) - GAP);
    }

    private int perRow() { return Math.max(1, (getWidth() + GAP) / (SIZE + GAP)); }

    private int rows() { return (presets.size() + perRow() - 1) / perRow(); }

    /** Marks the swatch whose colour matches {@code argb} (RGB compare), or none. */
    public void setSelectedColor(final int argb) {
        selected = -1;
        for (int i = 0; i < presets.size(); i++) if ((presets.get(i).color() & 0xFFFFFF) == (argb & 0xFFFFFF)) { selected = i; break; }
        if (selected >= 0) cursor = selected;
    }

    public int selectedIndex() { return selected; }

    private int swatchX(final int i) { return getX() + (i % perRow()) * (SIZE + GAP); }

    private int swatchY(final int i) { return getY() + (i / perRow()) * (SIZE + GAP); }

    private int at(final double mx, final double my) {
        for (int i = 0; i < presets.size(); i++) {
            final int sx = swatchX(i), sy = swatchY(i);
            if (mx >= sx && mx < sx + SIZE && my >= sy && my < sy + SIZE) return i;
        }
        return -1;
    }

    private void pick(final int i) {
        if (i < 0 || i >= presets.size()) return;
        cursor = i;
        if (i == selected) return;
        selected = i;
        SlateSounds.tick();
        if (onPick != null) onPick.accept(presets.get(i).color());
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        pick(at(mouseX, mouseY));
    }

    @Override
    public void playDownSound(final net.minecraft.client.sounds.SoundManager handler) {}

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible || presets.isEmpty()) return false;
        switch (keyCode) {
            case 263 -> cursor = Math.max(0, cursor - 1);
            case 262 -> cursor = Math.min(presets.size() - 1, cursor + 1);
            case 265 -> cursor = Math.max(0, cursor - perRow());
            case 264 -> cursor = Math.min(presets.size() - 1, cursor + perRow());
            case 257, 32, 335 -> { pick(cursor); return true; }
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, mouseX, mouseY, false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, mouseX, mouseY, true);
    }

    private void draw(final GuiGraphics g, final int mouseX, final int mouseY, final boolean vanilla) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final int dy = enterOffset();
        hoverIdx = this.isHovered() && this.active && g.containsPointInScissor(mouseX, mouseY) ? at(mouseX, mouseY) : -1;
        if (hoverIdx >= 0) SlateTooltips.request(Component.literal(presets.get(hoverIdx).name()), this);
        final int radius = vanilla ? 0 : Math.min(3, t.radius());
        for (int i = 0; i < presets.size(); i++) {
            final int sx = swatchX(i), sy = swatchY(i) + dy;
            final int c = presets.get(i).color();
            final boolean sel = i == selected, hov = i == hoverIdx, cur = i == cursor && isFocused();
            final int lift = hov ? -1 : 0;
            if (!vanilla && (hov || sel)) SlateDraw.shadow(g, sx, sy + lift, SIZE, SIZE, 0.35f * a);
            SlateDraw.pixelRound(g, sx, sy + lift, SIZE, SIZE, Colors.scaleAlpha(this.active ? c : Colors.withAlpha(c, 0x80), a), radius);
            SlateDraw.hline(g, sx + 2, sy + lift + 1, SIZE - 4, Colors.scaleAlpha(Colors.withAlpha(0xFFFFFF, 0x30), a));
            final int border = sel ? p.text() : hov ? Colors.brighten(c, 0.35f) : (vanilla ? 0xFF000000 : Colors.brighten(c, -0.35f));
            SlateDraw.outline(g, sx, sy + lift, SIZE, SIZE, Colors.scaleAlpha(border, a), radius);
            if (sel) Icons.draw(g, Icon.CHECK, sx + 3, sy + lift + 3, 8, Colors.scaleAlpha(Colors.readableOn(c), a));
            if (cur) SlateDraw.focusRing(g, sx, sy + lift, SIZE, SIZE, a);
        }
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, getMessage());
        if (cursor >= 0 && cursor < presets.size()) out.add(NarratedElementType.HINT, Component.literal(presets.get(cursor).name()));
    }
}
