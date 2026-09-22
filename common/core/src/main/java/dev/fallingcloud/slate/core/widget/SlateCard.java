package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Anim;
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
 * positioned relative to the card; the card keeps them in place when it moves (e.g. inside a scroll panel).
 * Override {@link #renderContent} to draw custom content underneath the children.
 */
public class SlateCard extends AbstractContainerWidget {

    private record Child(AbstractWidget widget, int relX, int relY) {}

    private final List<Child> children = new ArrayList<>();
    private final List<AbstractWidget> widgets = new ArrayList<>();
    protected final Anim hoverAnim = new Anim(0, 160, Ease.OUT_CUBIC);
    protected final Anim enterAnim = new Anim(1, 220, Ease.OUT_CUBIC);
    @Nullable private Runnable onClick;
    @Nullable private Runnable onRightClick;
    private boolean selected;
    private boolean flat;
    private int fillOverride;

    public SlateCard(final int x, final int y, final int width, final int height) {
        super(x, y, width, height, Component.empty());
    }

    public <T extends AbstractWidget> T add(final T widget, final int relX, final int relY) {
        children.add(new Child(widget, relX, relY));
        widgets.add(widget);
        return widget;
    }

    public void clearChildren() { children.clear(); widgets.clear(); }

    public SlateCard onClick(final Runnable r) { this.onClick = r; return this; }

    public SlateCard onRightClick(final Runnable r) { this.onRightClick = r; return this; }

    public SlateCard selected(final boolean s) { this.selected = s; return this; }

    public boolean isSelected() { return selected; }

    /** No hover lift/shadow (static panel). */
    public SlateCard flat() { this.flat = true; return this; }

    public SlateCard fill(final int argb) { this.fillOverride = argb; return this; }

    public void playEntrance(final int delayMs) {
        enterAnim.snap(0);
        enterAnim.set(1f, Theme.current().ms(220) + delayMs);
        for (final AbstractWidget w : widgets) if (w instanceof SlateWidget sw) sw.playEntrance(delayMs);
    }

    public float hover() { return hoverAnim.get(); }

    private void layout() {
        for (final Child c : children) {
            c.widget.setX(getX() + c.relX);
            c.widget.setY(getY() + c.relY);
        }
    }

    @Override
    public List<? extends GuiEventListener> children() { return widgets; }

    private boolean inside(final double mx, final double my) {
        return mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
    }

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
        return onClick != null;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (!inside(mouseX, mouseY)) return false;
        layout();
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean hov = this.active && inside(mouseX, mouseY) && g.containsPointInScissor(mouseX, mouseY) && onClick != null;
        hoverAnim.set(hov && !flat);
        final float h = hoverAnim.get();
        final float a = alpha * Math.min(1f, enterAnim.get());
        final int lift = Math.round(h * -1f);           // 1 px up on hover
        final int x = getX(), y = getY() + lift + Math.round((1 - Math.min(1, enterAnim.get())) * 6), w = getWidth(), hh = getHeight();
        layout();
        if (t.isVanilla()) {
            g.fill(x, y, x + w, y + hh, Colors.scaleAlpha(selected ? 0x80000000 : 0x60000000, a));
            SlateDraw.outline(g, x, y, w, hh, Colors.scaleAlpha(selected ? 0xFFFFFFFF : Colors.lerp(0xFF404040, 0xFFA0A0A0, h), a), 0);
        } else {
            if (!flat) SlateDraw.shadow(g, x, y, w, hh, (0.3f + 0.3f * h) * a);
            final int fill = fillOverride != 0 ? fillOverride : Colors.lerp(selected ? p.surfaceActive() : p.surface(), p.surfaceHover(), h);
            SlateDraw.pixelRound(g, x, y, w, hh, Colors.scaleAlpha(fill, a), t.radius());
            SlateDraw.outline(g, x, y, w, hh, Colors.scaleAlpha(selected ? p.accent() : Colors.lerp(p.border(), p.borderStrong(), h), a), t.radius());
        }
        renderContent(g, x, y, w, hh, mouseX, mouseY, partialTick);
        for (final AbstractWidget wdg : widgets) if (wdg.visible) wdg.render(g, mouseX, mouseY, partialTick);
    }

    /** Custom drawing inside the card, before the children. */
    protected void renderContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final int mouseX, final int mouseY, final float partialTick) {}

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {}
}
