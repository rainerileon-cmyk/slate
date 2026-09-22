package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * Slate's tooltip: widgets {@link #request} it while rendering, the screen hook draws it last so it sits
 * above everything. One tooltip per frame (the last request wins). Both skins: dark panel with a border,
 * or vanilla's purple-bordered box colours.
 */
public final class SlateTooltips {

    private static List<Component> pending;
    @Nullable private static AbstractWidget anchor;
    private static int anchorX = -1, anchorY = -1;

    /** Show {@code lines} near the mouse this frame. */
    public static void request(final List<Component> lines, @Nullable final AbstractWidget owner) {
        pending = lines;
        anchor = owner;
        anchorX = anchorY = -1;
    }

    /** Show {@code lines} at a fixed position this frame (editor, lists that draw their own rows). */
    public static void requestAt(final List<Component> lines, final int x, final int y) {
        pending = lines;
        anchor = null;
        anchorX = x;
        anchorY = y;
    }

    public static void request(final Component line, @Nullable final AbstractWidget owner) {
        request(List.of(line), owner);
    }

    public static boolean hasPending() { return pending != null; }

    /** Called from the screen render hook after everything else. Consumes the pending tooltip. */
    public static void render(final GuiGraphics g, final int mouseX, final int mouseY, final int screenW, final int screenH) {
        final List<Component> lines = pending;
        pending = null;
        if (lines == null || lines.isEmpty()) return;

        final int maxWidth = Math.min(220, screenW - 16);
        final List<FormattedCharSequence> wrapped = new ArrayList<>();
        int w = 0;
        for (final Component c : lines) {
            for (final FormattedCharSequence s : SlateDraw.font().split(c, maxWidth)) {
                wrapped.add(s);
                w = Math.max(w, SlateDraw.font().width(s));
            }
        }
        final int pad = 4;
        final int h = wrapped.size() * (SlateDraw.lineHeight() + 1) - 1 + pad * 2;
        w += pad * 2;

        int x, y;
        if (anchorX >= 0) { x = anchorX; y = anchorY; }
        else { x = mouseX + 10; y = mouseY - 6; }
        if (x + w > screenW - 4) x = Math.max(4, mouseX - w - 6);
        if (y + h > screenH - 4) y = screenH - 4 - h;
        if (y < 4) y = 4;

        final Theme t = Theme.current();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        if (t.isVanilla()) {
            g.fill(x, y, x + w, y + h, 0xF0100010);
            SlateDraw.outline(g, x, y, w, h, 0xFF5000FF, 0);
            SlateDraw.outline(g, x + 1, y + 1, w - 2, h - 2, 0xFF28007F, 0);
        } else {
            SlateDraw.shadow(g, x, y, w, h, 0.6f);
            SlateDraw.pixelRound(g, x, y, w, h, Colors.withAlpha(t.palette().surfaceActive(), 0xF4), t.radius());
            SlateDraw.outline(g, x, y, w, h, t.palette().borderStrong(), t.radius());
        }
        int ty = y + pad;
        for (final FormattedCharSequence s : wrapped) {
            g.drawString(SlateDraw.font(), s, x + pad, ty, t.text(), t.isVanilla());
            ty += SlateDraw.lineHeight() + 1;
        }
        g.pose().popPose();
        anchor = null;
    }

    private SlateTooltips() {}
}
