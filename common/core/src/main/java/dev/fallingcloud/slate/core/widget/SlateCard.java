package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A panel that holds child widgets and (optionally) is itself clickable, lifting on hover. Children are
 * positioned relative to the card; the card keeps them in place when it moves (e.g. inside a scroll
 * panel) and carries them along with its hover lift and entrance slide. Override {@link #renderContent}
 * to draw custom content underneath the children and {@link #renderOverlay} to draw above them.
 */
public class SlateCard extends AbstractContainerWidget {

    private record Child(AbstractWidget widget, int relX, int relY) {}

    private final List<Child> children = new ArrayList<>();
    private final List<AbstractWidget> widgets = new ArrayList<>();
    protected final Anim hoverAnim = new Anim(0, 160, Ease.OUT_CUBIC);
    protected final Anim enterAnim = new Anim(1, 220, Ease.OUT_CUBIC);
    private long enterStartMs;
    @Nullable private Runnable onClick;
    @Nullable private Runnable onRightClick;
    private boolean selected;
    private boolean flat;
    private int fillOverride;
    private boolean propagateAlpha = true;

    public SlateCard(final int x, final int y, final int width, final int height) {
        super(x, y, width, height, Component.empty());
    }

    public <T extends AbstractWidget> T add(final T widget, final int relX, final int relY) {
        children.add(new Child(widget, relX, relY));
        widgets.add(widget);
        widget.setX(getX() + relX);
        widget.setY(getY() + relY);
        return widget;
    }

    public void clearChildren() { children.clear(); widgets.clear(); setFocused(null); }

    /** The child widgets, in add order. */
    public List<AbstractWidget> widgets() { return widgets; }

    public SlateCard onClick(final Runnable r) { this.onClick = r; return this; }

    public SlateCard onRightClick(final Runnable r) { this.onRightClick = r; return this; }

    public SlateCard selected(final boolean s) { this.selected = s; return this; }

    public boolean isSelected() { return selected; }

    /** No hover lift/shadow (static panel). */
    public SlateCard flat() { this.flat = true; return this; }

    public SlateCard fill(final int argb) { this.fillOverride = argb; return this; }

    /** Children keep their own alpha instead of following the card's entrance fade. */
    public SlateCard keepChildAlpha() { this.propagateAlpha = false; return this; }

    /** Entrance fade/slide after {@code delayMs}; children move with the card (they get no own entrance). */
    public void playEntrance(final int delayMs) {
        final float motion = Theme.current().motion();
        if (motion <= 0) { enterAnim.snap(1); enterStartMs = 0; return; }
        enterAnim.snap(0);
        enterStartMs = Clock.nowMs() + Math.max(0, Math.round(delayMs * motion));
    }

    public float hover() { return hoverAnim.get(); }

    private float enterProgress() {
        if (enterStartMs != 0) {
            if (Theme.current().motion() <= 0) { enterStartMs = 0; enterAnim.snap(1); return 1f; }
            if (Clock.nowMs() < enterStartMs) return 0f;
            enterStartMs = 0;
            enterAnim.set(1f, 220);
        }
        return Math.min(1f, Math.max(0f, enterAnim.get()));
    }

    /** Vertical displacement this frame: hover lift (-1) plus the entrance slide. */
    private int dy() {
        final int lift = flat ? 0 : Math.round(-hoverAnim.get());
        return lift + Math.round((1f - enterProgress()) * SlateWidget.ENTER_SLIDE);
    }

    private void layout() {
        final int dy = dy();
        for (final Child c : children) {
            c.widget.setX(getX() + c.relX);
            c.widget.setY(getY() + c.relY + dy);
        }
    }

    @Override
    public List<? extends GuiEventListener> children() { return widgets; }

    private boolean inside(final double mx, final double my) {
        return mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
    }

    private boolean clickable() { return onClick != null || onRightClick != null; }

    @Override
    public boolean isMouseOver(final double mouseX, final double mouseY) {
        return this.active && this.visible && inside(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.visible || !this.active || !inside(mouseX, mouseY)) return false;
        layout();
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && onClick != null) { SlateSounds.click(); onClick.run(); return true; }
        if (button == 1 && onRightClick != null) { onRightClick.run(); return true; }
        return clickable();
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (!inside(mouseX, mouseY)) return false;
        layout();
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        // Enter/Space on a clickable card with no focused child activates it.
        if (this.active && onClick != null && getFocused() == null && (keyCode == 257 || keyCode == 32 || keyCode == 335)) {
            SlateSounds.click();
            onClick.run();
            return true;
        }
        return false;
    }

    @Override
    protected void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean hov = this.active && clickable() && inside(mouseX, mouseY) && g.containsPointInScissor(mouseX, mouseY);
        hoverAnim.set(hov && !flat);
        final float h = hoverAnim.get();
        final float a = alpha * enterProgress();
        layout();
        if (a <= 0.004f) return;
        final int x = getX(), y = getY() + dy(), w = getWidth(), hh = getHeight();
        if (t.isVanilla()) {
            g.fill(x, y, x + w, y + hh, Colors.scaleAlpha(selected ? 0x90000000 : 0x60000000, a));
            SlateDraw.outline(g, x, y, w, hh, Colors.scaleAlpha(selected ? 0xFFFFFFFF : Colors.lerp(0xFF404040, 0xFFA0A0A0, h), a), 0);
        } else {
            if (!flat) SlateDraw.shadow(g, x, y, w, hh, (0.3f + 0.3f * h) * a);
            final int fill = fillOverride != 0 ? fillOverride : Colors.lerp(selected ? p.surfaceActive() : p.surface(), p.surfaceHover(), h);
            SlateDraw.pixelRound(g, x, y, w, hh, Colors.scaleAlpha(fill, a), t.radius());
            SlateDraw.outline(g, x, y, w, hh, Colors.scaleAlpha(selected ? p.accent() : Colors.lerp(p.border(), p.borderStrong(), h), a), t.radius());
            if (isFocused() && getFocused() == null && clickable()) SlateDraw.focusRing(g, x, y, w, hh, a);
        }
        renderContent(g, x, y, w, hh, mouseX, mouseY, partialTick);
        for (final AbstractWidget wdg : widgets) {
            if (!wdg.visible) continue;
            if (propagateAlpha) wdg.setAlpha(a);
            wdg.render(g, mouseX, mouseY, partialTick);
        }
        renderOverlay(g, x, y, w, hh, mouseX, mouseY, partialTick);
    }

    /** Custom drawing inside the card, before the children. {@code y} already includes the lift/slide. */
    protected void renderContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final int mouseX, final int mouseY, final float partialTick) {}

    /** Custom drawing after the children (badges, overlays). */
    protected void renderOverlay(final GuiGraphics g, final int x, final int y, final int w, final int h, final int mouseX, final int mouseY, final float partialTick) {}

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {}
}
