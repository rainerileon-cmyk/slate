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
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Box + label. Prefer {@link SlateToggle} for settings; use this for multi-select lists and filters. */
public class SlateCheckbox extends SlateWidget {

    private static final int BOX = 12;
    private static final int VANILLA_BOX = 17;

    private boolean value;
    private final Anim check = new Anim(0, 140, Ease.OUT_BACK);
    private Consumer<Boolean> onChange;

    public SlateCheckbox(final int x, final int y, final int width, final Component label, final boolean value, final Consumer<Boolean> onChange) {
        super(x, y, width, 16, label);
        this.value = value;
        this.check.snap(value ? 1 : 0);
        this.onChange = onChange;
    }

    public boolean value() { return value; }

    public SlateCheckbox setValue(final boolean v) { value = v; check.set(v ? 1 : 0); return this; }

    public SlateCheckbox onChange(final Consumer<Boolean> c) { this.onChange = c; return this; }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        toggle();
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (this.active && this.visible && (keyCode == 257 || keyCode == 32 || keyCode == 335)) { toggle(); return true; }
        return false;
    }

    private void toggle() {
        value = !value;
        check.set(value ? 1 : 0);
        SlateSounds.tick();
        if (onChange != null) onChange.accept(value);
    }

    @Override
    public void playDownSound(final net.minecraft.client.sounds.SoundManager handler) {}

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final int x = getX(), y = getY() + enterOffset(), h = getHeight();
        final float k = Math.max(0f, Math.min(1f, check.get()));
        final int by = y + (h - BOX) / 2;
        int fill = Colors.lerp(Colors.lerp(p.surface(), p.surfaceHover(), hover()), p.accent(), k);
        int border = Colors.lerp(p.borderStrong(), p.accentHover(), k);
        if (!this.active) { fill = Colors.withAlpha(fill, 0x70); border = Colors.withAlpha(border, 0x70); }
        fill = Colors.brighten(fill, -0.12f * press());
        SlateDraw.pixelRound(g, x, by, BOX, BOX, Colors.scaleAlpha(fill, a), 2);
        SlateDraw.outline(g, x, by, BOX, BOX, Colors.scaleAlpha(border, a), 2);
        if (k > 0.05f) {
            // The check pops in: 6 -> 8 px as the OUT_BACK settles.
            final int s = k < 0.5f ? 6 : 8;
            Icons.draw(g, Icon.CHECK, x + (BOX - s) / 2, by + (BOX - s) / 2, s, Colors.scaleAlpha(p.accentText(), a * k));
        }
        SlateDraw.focusRing(g, x, by, BOX, BOX, focus() * a);
        final int fg = Colors.scaleAlpha(this.active ? Colors.lerp(p.text(), 0xFFFFFFFF, hover() * 0.3f) : p.textDim(), a);
        g.drawString(SlateDraw.font(), SlateDraw.truncate(getMessage(), getWidth() - BOX - 6), x + BOX + 6, SlateDraw.textY(y, h), fg, false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final int x = getX(), y = getY() + enterOffset(), h = getHeight();
        final int by = y + (h - VANILLA_BOX) / 2;
        final float lift = this.active ? Math.max(hover(), focus()) : 0f;
        SlateDraw.vanillaCheckbox(g, x, by, VANILLA_BOX, value, lift, a);
        final int fg = Colors.scaleAlpha(this.active ? 0xFFFFFFFF : 0xFFA0A0A0, a);
        g.drawString(SlateDraw.font(), SlateDraw.truncate(getMessage(), getWidth() - VANILLA_BOX - 4), x + VANILLA_BOX + 4, SlateDraw.textY(y, h), fg, true);
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.button",
            Component.empty().append(getMessage()).append(": ").append(value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF)));
        if (this.active) {
            out.add(NarratedElementType.USAGE, Component.translatable(this.isFocused() ? "narration.checkbox.usage.focused" : "narration.checkbox.usage.hovered"));
        }
    }
}
