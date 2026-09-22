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
 *
 * <p>Entrance: {@link #playEntrance(int)} fades and slides the widget in after a real delay (not a
 * longer tween), so screens get a true stagger. With {@code Theme.motion() == 0} nothing moves and the
 * widget is drawn in place immediately.</p>
 */
public abstract class SlateWidget extends AbstractWidget {

    /** Entrance slide distance in px (the widget starts this far below its rest position). */
    public static final int ENTER_SLIDE = 6;

    protected final Anim hoverAnim = new Anim(0, 140, Ease.OUT_CUBIC);
    protected final Anim pressAnim = new Anim(0, 90, Ease.OUT_CUBIC);
    protected final Anim focusAnim = new Anim(0, 160, Ease.OUT_CUBIC);
    protected final Anim enterAnim = new Anim(1, 220, Ease.OUT_CUBIC);

    @Nullable private List<Component> tip;
    private long hoverSinceMs;
    private boolean pressed;
    private boolean silent;
    /** Wall time at which a pending entrance starts (0 = nothing pending). */
    private long enterStartMs;

    protected SlateWidget(final int x, final int y, final int width, final int height, final Component message) {
        super(x, y, width, height, message);
    }

    // ------------------------------------------------------------------ fluent config

    /** Slate tooltip (drawn by {@link SlateTooltips} after the screen). */
    public SlateWidget tip(final Component tooltip) {
        this.tip = tooltip == null || tooltip.getString().isEmpty() ? null : List.of(tooltip);
        return this;
    }

    public SlateWidget tip(final List<Component> lines) {
        this.tip = lines == null || lines.isEmpty() ? null : List.copyOf(lines);
        return this;
    }

    @Nullable
    public List<Component> tipLines() { return tip; }

    /**
     * Start an entrance fade/slide from 0 after {@code delayMs} (used by screens for staggered
     * appearance). Honours {@code Theme.motion()}: 0 shows the widget immediately.
     */
    public void playEntrance(final int delayMs) {
        final float motion = Theme.current().motion();
        if (motion <= 0) { enterAnim.snap(1); enterStartMs = 0; return; }
        enterAnim.snap(0);
        enterStartMs = Clock.nowMs() + Math.max(0, Math.round(delayMs * motion));
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

    /** 0..1 entrance progress; starts the tween once its delay has elapsed. */
    protected float enterProgress() {
        if (enterStartMs != 0) {
            if (Theme.current().motion() <= 0) { enterStartMs = 0; enterAnim.snap(1); return 1f; }
            if (Clock.nowMs() < enterStartMs) return 0f;
            enterStartMs = 0;
            enterAnim.set(1f, 220);
        }
        return Math.min(1f, Math.max(0f, enterAnim.get()));
    }

    /** Combined alpha: the widget's own alpha times the entrance animation. */
    public float effectiveAlpha() { return alpha * enterProgress(); }

    /** Pixel offset for the entrance slide (6 px -> 0). */
    protected int enterOffset() { return Math.round((1f - enterProgress()) * ENTER_SLIDE); }

    protected boolean isPressed() { return pressed; }

    /** True while the entrance animation is still running (screens use it to skip hover sounds etc.). */
    public boolean isEntering() { return enterStartMs != 0 || enterAnim.isAnimating(); }

    // ------------------------------------------------------------------ rendering

    @Override
    protected final void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final boolean hovered = this.active && this.isHovered() && g.containsPointInScissor(mouseX, mouseY);
        hoverAnim.set(hovered);
        focusAnim.set(this.active && this.isFocused() && !hovered);
        pressAnim.set(pressed && hovered);
        if (hovered) {
            if (hoverSinceMs == 0) hoverSinceMs = Clock.nowMs();
            if (tip != null && Clock.nowMs() - hoverSinceMs > SlateTooltips.DELAY_MS) SlateTooltips.request(tip, this);
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

    /** Flash the press state (keyboard activation feedback). */
    protected void flashPress() {
        pressAnim.snap(1);
        pressAnim.set(0);
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
