package dev.fallingcloud.slate.core.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * Slate's notification toasts: a stack in the top-right that slides in, waits, and slides out. Drawn by
 * Core's screen and HUD hooks so they show both in menus and in-game (the HUD passes mouse -1). Click
 * to run the toast's action (and dismiss it); hovering pauses its life. Thread-safe: {@link #show} may
 * be called from network threads.
 */
public final class SlateToasts {

    public static final int WIDTH = 160, PAD = 6, LIFE_MS = 5000, MAX_VISIBLE = 5;

    private static final class Toast {
        final Component title;
        @Nullable final Component body;
        @Nullable final Icon icon;
        @Nullable final Runnable onClick;
        long bornMs = Clock.nowMs();
        final Anim slide = new Anim(0, 260, Ease.OUT_BACK);
        final Anim yAnim = new Anim(0, 200, Ease.OUT_CUBIC);
        final int lifeMs;
        boolean closing, placed;
        int drawnX, drawnY, drawnH;

        Toast(final Component title, @Nullable final Component body, @Nullable final Icon icon, @Nullable final Runnable onClick, final int lifeMs) {
            this.title = title; this.body = body; this.icon = icon; this.onClick = onClick; this.lifeMs = lifeMs;
            slide.snap(0);
            slide.set(1);
        }

        int height() {
            return PAD * 2 + 10 + (body == null ? 0 : SlateDraw.font().split(body, WIDTH - PAD * 2 - 16).size() * 10);
        }

        void dismiss() {
            if (closing) return;
            closing = true;
            slide.ease(Ease.OUT_CUBIC);
            slide.set(0, 200);
        }
    }

    private static final List<Toast> TOASTS = new ArrayList<>();

    public static void show(final Component title, @Nullable final Component body, @Nullable final Icon icon) {
        show(title, body, icon, null);
    }

    public static void show(final Component title, @Nullable final Component body, @Nullable final Icon icon, @Nullable final Runnable onClick) {
        show(title, body, icon, onClick, LIFE_MS);
    }

    public static void show(final Component title, @Nullable final Component body, @Nullable final Icon icon, @Nullable final Runnable onClick, final int lifeMs) {
        if (!Theme.current().toasts()) return;
        synchronized (TOASTS) {
            TOASTS.add(new Toast(title, body, icon, onClick, lifeMs));
            int live = 0;
            for (int i = TOASTS.size() - 1; i >= 0; i--) {
                if (TOASTS.get(i).closing) continue;
                if (++live > MAX_VISIBLE) TOASTS.get(i).dismiss();
            }
        }
        if (RenderSystem.isOnRenderThread()) SlateSounds.chime();
        else Minecraft.getInstance().execute(SlateSounds::chime);
    }

    /** Removes every toast immediately. */
    public static void clear() {
        synchronized (TOASTS) { TOASTS.clear(); }
    }

    public static void render(final GuiGraphics g, final int mouseX, final int mouseY, final int screenW) {
        final List<Toast> snapshot;
        synchronized (TOASTS) {
            TOASTS.removeIf(t -> t.closing && t.slide.get() <= 0.01f);
            snapshot = new ArrayList<>(TOASTS);
        }
        if (snapshot.isEmpty()) return;
        final Theme th = Theme.current();
        final Palette p = th.palette();
        final long now = Clock.nowMs();
        int y = 8;
        g.pose().pushPose();
        g.pose().translate(0, 0, 450);
        for (final Toast t : snapshot) {
            final int h = t.height();
            // Stack position eases when toasts above leave.
            if (!t.placed) { t.yAnim.snap(y); t.placed = true; } else t.yAnim.set(y);
            final int ty = Math.round(t.yAnim.get());
            final float s = Math.max(0f, t.slide.get());
            final int x = screenW - 8 - WIDTH + Math.round((1 - s) * (WIDTH + 8));
            t.drawnX = x; t.drawnY = ty; t.drawnH = h;
            final boolean hov = mouseX >= x && mouseX < x + WIDTH && mouseY >= ty && mouseY < ty + h;
            if (hov && !t.closing) t.bornMs += Math.round(Clock.frameDelta());     // pause the life while hovered
            if (!t.closing && now - t.bornMs > t.lifeMs) t.dismiss();
            if (th.isVanilla()) {
                g.fill(x, ty, x + WIDTH, ty + h, 0xF0101010);
                SlateDraw.outline(g, x, ty, WIDTH, h, hov ? 0xFFFFFFFF : 0xFF8B8B8B, 0);
                SlateDraw.rect(g, x + 1, ty + 1, 2, h - 2, hov ? 0xFFFFFFFF : p.accent());
            } else {
                SlateDraw.shadow(g, x, ty, WIDTH, h, 0.5f);
                SlateDraw.pixelRound(g, x, ty, WIDTH, h, Colors.withAlpha(hov ? p.surfaceHover() : p.surface(), 0xF4), th.radius());
                SlateDraw.outline(g, x, ty, WIDTH, h, hov ? p.textDim() : p.borderStrong(), th.radius());
                SlateDraw.rect(g, x, ty + 3, 2, h - 6, p.accent());
            }
            int tx = x + PAD + 2;
            if (t.icon != null) { Icons.draw(g, t.icon, tx, ty + PAD - 1, 12, p.accent()); tx += 16; }
            g.drawString(SlateDraw.font(), SlateDraw.truncate(t.title, x + WIDTH - PAD - tx), tx, ty + PAD, p.text(), th.isVanilla());
            if (t.body != null) {
                int by = ty + PAD + 10;
                for (final FormattedCharSequence line : SlateDraw.font().split(t.body, WIDTH - PAD * 2 - 16)) {
                    g.drawString(SlateDraw.font(), line, tx, by, p.textMuted(), th.isVanilla());
                    by += 10;
                }
            }
            // Life bar
            if (!t.closing) {
                final float life = 1 - (float) (now - t.bornMs) / t.lifeMs;
                SlateDraw.rect(g, x + 4, ty + h - 2, Math.round((WIDTH - 8) * Math.max(0, life)), 1,
                    Colors.withAlpha(th.isVanilla() ? 0xA0A0A0 : p.textDim(), 0x80));
            }
            y += h + 4;
        }
        g.pose().popPose();
    }

    /** Screen hook: click on a toast. Returns true when consumed. */
    public static boolean mouseClicked(final double mx, final double my, final int screenW) {
        final List<Toast> snapshot;
        synchronized (TOASTS) { snapshot = new ArrayList<>(TOASTS); }
        for (final Toast t : snapshot) {
            if (t.closing) continue;
            if (mx >= t.drawnX && mx < t.drawnX + WIDTH && my >= t.drawnY && my < t.drawnY + t.drawnH) {
                t.dismiss();
                if (t.onClick != null) t.onClick.run();
                return true;
            }
        }
        return false;
    }

    /** True while any toast is drawn (for HUD layout decisions). */
    public static boolean anyVisible() {
        synchronized (TOASTS) { return !TOASTS.isEmpty(); }
    }

    private SlateToasts() {}
}
