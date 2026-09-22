package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Base of every Slate widget. Owns the three state animations (hover, press, focus), the Slate tooltip
 * hook, an alpha for entrance fades, and the skin dispatch: subclasses implement {@link #renderDark} and
 * {@link #renderVanilla}, never {@code renderWidget} directly.
 *
 * <p>Works inside any vanilla {@code Screen} (it is an {@code AbstractWidget}), so modules can mix Slate
 * widgets into screens they only partially own.</p>
 */
public abstract class SlateWidget extends AbstractWidget {

    protected final Anim hoverAnim = new Anim(0, 140, Ease.OUT_CUBIC);
    protected final Anim pressAnim = new Anim(0, 90, Ease.OUT_CUBIC);
    protected final Anim focusAnim = new Anim(0, 160, Ease.OUT_CUBIC);
    protected final Anim enterAnim = new Anim(1, 220, Ease.OUT_CUBIC);

    @Nullable private List<Component> tip;
    private long hoverSinceMs;
    private boolean pressed;
    private boolean silent;

    protected SlateWidget(final int x, final int y, final int width, final int height, final Component message) {
        super(x, y, width, height, message);
    }

    // ------------------------------------------------------------------ fluent config

    /** Slate tooltip (drawn by {@link SlateTooltips} after the screen). */
    public SlateWidget tip(final Component tooltip) {
        this.tip = tooltip == null ? null : List.of(tooltip);
        return this;
    }

    public SlateWidget tip(final List<Component> lines) {
        this.tip = lines == null || lines.isEmpty() ? null : List.copyOf(lines);
        return this;
    }

    @Nullable
    public List<Component> tipLines() { return tip; }

    /** Start an entrance fade/slide from 0 (used by screens for staggered appearance). */
    public void playEntrance(final int delayMs) {
        enterAnim.snap(0);
        enterAnim.set(1f, Theme.current().ms(220) + delayMs);
    }

    /** No click sound. */
    public SlateWidget silent() { this.silent = true; return this; }

    public SlateWidget enabled(final boolean enabled) { this.active = enabled; return this; }

    public SlateWidget shown(final boolean visible) { this.visible = visible; return this; }

    // ------------------------------------------------------------------ state

    /** 0..1 hover amount this frame. */
    public float hover() { return hoverAnim.get(); }

    public float press() { return pressAnim.get(); }

    public float focus() { return focusAnim.get(); }

    /** Combined alpha: the widget's own alpha times the entrance animation. */
    public float effectiveAlpha() { return alpha * Math.min(1f, enterAnim.get()); }

    /** Pixel offset for the entrance slide (6 px -> 0). */
    protected int enterOffset() { return Math.round((1f - Math.min(1f, enterAnim.get())) * 6f); }

    protected boolean isPressed() { return pressed; }

    // ------------------------------------------------------------------ rendering

    @Override
    protected final void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final boolean hovered = this.active && this.isHovered();
        hoverAnim.set(hovered);
        focusAnim.set(this.active && this.isFocused() && !hovered);
        pressAnim.set(pressed && hovered);
        if (hovered) {
            if (hoverSinceMs == 0) hoverSinceMs = Clock.nowMs();
            if (tip != null && Clock.nowMs() - hoverSinceMs > 350) SlateTooltips.request(tip, this);
        } else {
            hoverSinceMs = 0;
        }
        if (Theme.current().isVanilla()) renderVanilla(g, mouseX, mouseY, partialTick);
        else renderDark(g, mouseX, mouseY, partialTick);
    }

    protected abstract void renderDark(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    protected abstract void renderVanilla(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    // ------------------------------------------------------------------ input

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        pressed = true;
        pressAnim.snap(1);
    }

    @Override
    public void onRelease(final double mouseX, final double mouseY) {
        pressed = false;
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        pressed = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void playDownSound(final SoundManager handler) {
        if (!silent) SlateSounds.click(handler);
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {
        this.defaultButtonNarrationText(out);
    }

    /** True when (mx,my) is inside this widget's rectangle. */
    public boolean contains(final double mx, final double my) {
        return mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
    }
}
