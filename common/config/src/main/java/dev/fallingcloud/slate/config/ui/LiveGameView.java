package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.core.client.render.GameView;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The "real in-game window" above General and Video in the Overhaul settings hub: the world as rendered this frame
 * ({@link GameView}), letterboxed in a framed plate, with a caption chip and a chevron. Clicking it (or Enter on it)
 * folds it to a slim bar and back; the state is the hub's to keep. Both styles.
 */
public final class LiveGameView extends SlateWidget {

    public static final int BAR_H = 20;
    private static final int INSET = 3;

    private final boolean minimised;
    private final Runnable onToggle;
    private final Anim fade = new Anim(0, 260, Ease.OUT_CUBIC);

    public LiveGameView(final int x, final int y, final int width, final int height, final boolean minimised, final Runnable onToggle) {
        super(x, y, width, height, Component.translatable("slate_config.live.title"));
        this.minimised = minimised;
        this.onToggle = onToggle;
        tip(Component.translatable(minimised ? "slate_config.live.expand" : "slate_config.live.minimise"));
        silent();
    }

    /** The height the view takes in {@code area}: a slim bar when folded, else a wide frame that leaves room below. */
    public static int heightFor(final Rect area, final boolean minimised) {
        if (minimised) return BAR_H;
        return Math.max(60, Math.min(area.h() * 2 / 5, area.w() * 9 / 32));
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        onToggle.run();
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, true);
    }

    private void draw(final GuiGraphics g, final boolean vanilla) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        SlateDraw.floatingPanel(g, x, y, w, h, a);
        final int text = vanilla ? 0xFFFFFFFF : p.text(), muted = vanilla ? 0xFFA0A0A0 : p.textMuted();
        if (minimised) {
            Icons.draw(g, Icon.VIDEO, x + 6, y + (h - 12) / 2, 12, Colors.scaleAlpha(vanilla ? 0xFFE0E0E0 : p.accent(), a));
            g.drawString(SlateDraw.font(), getMessage(), x + 22, SlateDraw.textY(y, h), Colors.scaleAlpha(text, a), vanilla);
            final Component hint = Component.translatable("slate_config.live.expand");
            final int hw = SlateDraw.width(hint);
            if (x + 22 + SlateDraw.width(getMessage()) + 12 + hw < x + w - 24) {
                g.drawString(SlateDraw.font(), hint, x + w - 22 - hw, SlateDraw.textY(y, h), Colors.scaleAlpha(muted, a * (hover() * 0.6f + 0.4f)), vanilla);
            }
            Icons.draw(g, Icon.CHEVRON_DOWN, x + w - 16, y + (h - 10) / 2, 10, Colors.scaleAlpha(muted, a));
            SlateDraw.focusRing(g, x, y, w, h, focus() * a);
            return;
        }
        GameView.request();
        final int ix = x + INSET, iy = y + INSET, iw = w - INSET * 2, ih = h - INSET * 2;
        // The frame, letterboxed; before the first copy arrives the plate stays empty but for the caption.
        fade.set(GameView.hasFrame());
        final float fa = fade.get() * a;
        if (fa > 0.004f) {
            // The frame fills the plate at its own aspect ratio, cropped evenly: a wide band of the world, centre kept.
            g.enableScissor(ix, iy, ix + iw, iy + ih);
            GameView.drawCover(g, ix, iy, iw, ih, fa);
            g.disableScissor();
        } else {
            SlateDraw.textCentered(g, Component.translatable("slate_config.live.waiting"), x + w / 2, y + h / 2 - 4, Colors.scaleAlpha(muted, a), vanilla);
        }
        // Caption chip and the fold chevron, over the frame.
        final int cw = SlateDraw.width(getMessage()) + 24, ch = 16, cx = ix + 6, cy = iy + 6;
        SlateDraw.pixelRound(g, cx, cy, cw, ch, Colors.scaleAlpha(vanilla ? 0xC0000000 : Colors.withAlpha(p.bg(), 0xD8), a), vanilla ? 0 : t.radius());
        Icons.draw(g, Icon.VIDEO, cx + 5, cy + 3, 10, Colors.scaleAlpha(vanilla ? 0xFFE0E0E0 : p.accent(), a));
        g.drawString(SlateDraw.font(), getMessage(), cx + 19, SlateDraw.textY(cy, ch), Colors.scaleAlpha(text, a), vanilla);
        final int bx = ix + iw - 22, by = iy + 6;
        SlateDraw.pixelRound(g, bx, by, 16, 16, Colors.scaleAlpha(vanilla ? 0xC0000000 : Colors.withAlpha(p.bg(), 0xD8), a * (0.6f + 0.4f * hover())), vanilla ? 0 : t.radius());
        Icons.draw(g, Icon.CHEVRON_UP, bx + 3, by + 3, 10, Colors.scaleAlpha(hover() > 0.5f ? text : muted, a));
        SlateDraw.focusRing(g, x, y, w, h, focus() * a);
    }
}
