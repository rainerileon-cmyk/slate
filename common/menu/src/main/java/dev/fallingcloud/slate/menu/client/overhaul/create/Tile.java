package dev.fallingcloud.slate.menu.client.overhaul.create;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateTooltips;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * One setting of the world creation screen, as the Overhaul layout shows settings: a plate of its own, the name
 * on its first line and the control under it, lighting up under the pointer. A word of explanation shows as a
 * tooltip over the name; a short note (the folder a world goes to, what a seed does) may stand beside the name.
 */
final class Tile extends AbstractContainerWidget {

    static final int HEIGHT = 42;
    private static final int LINE = 20, PAD = 8;

    private final List<AbstractWidget> children = new ArrayList<>();
    private final AbstractWidget control;
    @Nullable private AbstractWidget extra;
    @Nullable private Supplier<Component> note;
    @Nullable private Supplier<Component> tip;
    @Nullable private Supplier<Boolean> enabled;
    private final Anim hover = new Anim(0, 150, Ease.OUT_CUBIC);
    private final Anim enter = new Anim(1, 220, Ease.OUT_CUBIC);
    private long enterStartMs;

    /** @param control its width is set to what the plate has; its height is kept */
    Tile(final int width, final Component label, final AbstractWidget control) {
        super(0, 0, width, HEIGHT, label);
        this.control = control;
        children.add(control);
    }

    /** A second, small control at the end of the control's line (the dice beside the seed). */
    Tile extra(final AbstractWidget widget) {
        this.extra = widget;
        children.add(widget);
        return this;
    }

    /** A short note beside the name, asked for every frame. */
    Tile note(final Supplier<Component> text) { this.note = text; return this; }

    Tile tip(final Supplier<Component> text) { this.tip = text; return this; }

    Tile tip(final Component text) { this.tip = () -> text; return this; }

    /** Whether the setting can be changed right now (hardcore fixes the difficulty): asked every frame. */
    Tile enabled(final Supplier<Boolean> on) { this.enabled = on; return this; }

    AbstractWidget control() { return control; }

    void playEntrance(final int delayMs) {
        for (final AbstractWidget c : children) if (c instanceof SlateWidget w) w.playEntrance(delayMs);
        final float motion = Theme.current().motion();
        if (motion <= 0) { enter.snap(1); enterStartMs = 0; return; }
        enter.snap(0);
        enterStartMs = Clock.nowMs() + Math.max(0, Math.round(delayMs * motion));
    }

    private float enterProgress() {
        if (enterStartMs != 0) {
            if (Theme.current().motion() <= 0) { enterStartMs = 0; enter.snap(1); return 1f; }
            if (Clock.nowMs() < enterStartMs) return 0f;
            enterStartMs = 0;
            enter.set(1f, 220);
        }
        return Math.min(1f, Math.max(0f, enter.get()));
    }

    private void layout() {
        final int x = getX() + PAD, y = getY() + LINE - 1 + (HEIGHT - LINE - 2 - control.getHeight()) / 2;
        int w = getWidth() - PAD * 2;
        if (extra != null) {
            w -= extra.getWidth() + 4;
            extra.setX(x + w + 4);
            extra.setY(y + (control.getHeight() - extra.getHeight()) / 2);
        }
        if (control.getWidth() != w && resizable()) control.setWidth(w);
        control.setX(x);
        control.setY(y);
        final boolean on = enabled == null || Boolean.TRUE.equals(enabled.get());
        control.active = on;
        if (extra != null) extra.active = on;
    }

    /** A switch keeps its own width: it is a switch, not a bar. */
    private boolean resizable() {
        return !(control instanceof dev.fallingcloud.slate.core.widget.SlateToggle);
    }

    @Override public List<? extends GuiEventListener> children() { return children; }

    private boolean inside(final double mx, final double my) {
        return mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
    }

    @Override
    public boolean isMouseOver(final double mouseX, final double mouseY) {
        return this.active && this.visible && inside(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.visible || !inside(mouseX, mouseY)) return false;
        layout();
        for (final AbstractWidget c : children) {
            if (c.mouseClicked(mouseX, mouseY, button)) {
                setFocused(c);
                if (button == 0) setDragging(true);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (!inside(mouseX, mouseY)) return false;
        layout();
        for (final AbstractWidget c : children) if (c.isMouseOver(mouseX, mouseY) && c.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        return false;
    }

    @Override
    protected void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        layout();
        final float ea = enterProgress();
        if (ea <= 0.01f) return;
        final int x = getX(), w = getWidth(), h = getHeight();
        final int y = getY() + Math.round((1f - ea) * SlateWidget.ENTER_SLIDE);
        final boolean hov = inside(mouseX, mouseY) && g.containsPointInScissor(mouseX, mouseY);
        hover.set(hov);
        final float hv = hover.get();
        if (van) {
            g.fill(x, y, x + w, y + h, Colors.scaleAlpha(Colors.lerp(0x50000000, 0x70202020, hv), ea));
            SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(Colors.lerp(0xFF000000, 0xFFFFFFFF, hv), ea), 0);
        } else {
            SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(Colors.lerp(Colors.withAlpha(p.surface(), 0x9C), Colors.withAlpha(p.surfaceHover(), 0xE0), hv), ea), t.radius());
            SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(Colors.lerp(Colors.withAlpha(p.border(), 0x90), Colors.withAlpha(p.accent(), 0xA0), hv), ea), t.radius());
        }
        final boolean on = control.active;
        final int fg = van ? (on ? 0xFFFFFFFF : 0xFFA0A0A0) : on ? p.text() : p.textDim();
        if (ea > 0.03f) {
            final var font = SlateDraw.font();
            final int ty = y + (LINE - 9) / 2 + 2;
            final Component said = note == null ? null : note.get();
            final int noteW = said == null || said.getString().isEmpty() ? 0 : Math.min(font.width(said), Math.max(0, w - PAD * 2 - font.width(getMessage()) - 10));
            g.drawString(font, SlateDraw.truncate(getMessage(), w - PAD * 2 - (noteW > 0 ? noteW + 8 : 0)), x + PAD, ty, Colors.scaleAlpha(fg, ea), van);
            if (noteW > 24 && said != null) {
                final var cut = SlateDraw.truncate(said, noteW);
                g.drawString(font, cut, x + w - PAD - font.width(cut), ty, Colors.scaleAlpha(van ? 0xFFA0A0A0 : p.textDim(), ea), van);
            }
        }
        for (final AbstractWidget c : children) if (c.visible) c.render(g, mouseX, mouseY, partialTick);
        if (hov && tip != null && mouseY < y + LINE) {
            final Component said = tip.get();
            if (said != null && !said.getString().isEmpty()) {
                final List<Component> lines = new ArrayList<>();
                for (final var line : SlateDraw.font().getSplitter().splitLines(said, 190, net.minecraft.network.chat.Style.EMPTY)) lines.add(Component.literal(line.getString()));
                SlateTooltips.request(lines, this);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, getMessage());
    }
}
