package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Box + label. Prefer {@link SlateToggle} for settings; use this for multi-select lists and filters. */
public class SlateCheckbox extends SlateWidget {

    private static final int BOX = 12;

    private boolean value;
    private final Anim check = new Anim(0, 140, Ease.OUT_BACK);
    private final Consumer<Boolean> onChange;

    public SlateCheckbox(final int x, final int y, final int width, final Component label, final boolean value, final Consumer<Boolean> onChange) {
        super(x, y, width, 16, label);
        this.value = value;
        this.check.snap(value ? 1 : 0);
        this.onChange = onChange;
    }

    public boolean value() { return value; }

    public SlateCheckbox setValue(final boolean v) { value = v; check.set(v ? 1 : 0); return this; }

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
        check.set(value ? 1 : 0);
        SlateSounds.tick();
        if (onChange != null) onChange.accept(value);
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        final int x = getX(), y = getY() + enterOffset(), h = getHeight();
        final float k = check.get();
        final int by = y + (h - BOX) / 2;
        final int fill = Colors.lerp(Colors.lerp(p.surface(), p.surfaceHover(), hover()), p.accent(), k);
        SlateDraw.pixelRound(g, x, by, BOX, BOX, Colors.scaleAlpha(fill, a), 2);
        SlateDraw.outline(g, x, by, BOX, BOX, Colors.scaleAlpha(Colors.lerp(p.borderStrong(), p.accentHover(), k), a), 2);
        if (k > 0.05f) Icons.draw(g, Icon.CHECK, x + 2, by + 2, 8, Colors.scaleAlpha(p.accentText(), a * k));
        SlateDraw.focusRing(g, x, by, BOX, BOX, focus() * a);
        final int fg = Colors.scaleAlpha(this.active ? p.text() : p.textDim(), a);
        g.drawString(SlateDraw.font(), SlateDraw.truncate(getMessage(), getWidth() - BOX - 6), x + BOX + 6, y + (h - 9) / 2 + 1, fg, false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        final int x = getX(), y = getY() + enterOffset(), h = getHeight();
        final int by = y + (h - 17) / 2;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.setColor(1, 1, 1, a);
        final boolean hl = hover() > 0.5f || focus() > 0.5f;
        g.blitSprite(value ? (hl ? SlateDraw.CHECKBOX_SELECTED_HIGHLIGHTED : SlateDraw.CHECKBOX_SELECTED)
            : (hl ? SlateDraw.CHECKBOX_HIGHLIGHTED : SlateDraw.CHECKBOX), x, by, 17, 17);
        g.setColor(1, 1, 1, 1);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        final int fg = Colors.scaleAlpha(this.active ? 0xFFFFFFFF : 0xFFA0A0A0, a);
        g.drawString(SlateDraw.font(), SlateDraw.truncate(getMessage(), getWidth() - 21), x + 21, y + (h - 9) / 2 + 1, fg, true);
    }
}
