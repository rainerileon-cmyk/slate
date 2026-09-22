package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateSounds;
import dev.fallingcloud.slate.core.widget.SlateTooltips;
import dev.fallingcloud.slate.core.widget.popup.Popup;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A grid of the pixel emotes that pops up above the emote button; clicking inserts {@code :name:}. */
public final class EmotePickerPopup implements Popup {

    private static final int COLS = 8, CELL = 16, PAD = 4;

    private final Consumer<String> onPick;
    private final Anim open = new Anim(0, 140, Ease.OUT_CUBIC);
    private final int x, y, w, h;
    private int hovered = -1;

    private EmotePickerPopup(final int anchorX, final int anchorBottom, final Consumer<String> onPick) {
        this.onPick = onPick;
        final int rows = (Emotes.NAMES.size() + COLS - 1) / COLS;
        this.w = COLS * CELL + PAD * 2;
        this.h = rows * CELL + PAD * 2;
        final Minecraft mc = Minecraft.getInstance();
        final int sw = mc.getWindow().getGuiScaledWidth();
        this.x = Math.max(2, Math.min(sw - w - 2, anchorX - w / 2));
        this.y = Math.max(2, anchorBottom - h - 4);
        open.snap(0);
        open.set(1);
    }

    /** Opens above the given anchor (x centre, bottom edge). */
    public static EmotePickerPopup open(final int anchorX, final int anchorBottom, final Consumer<String> onPick) {
        final EmotePickerPopup p = new EmotePickerPopup(anchorX, anchorBottom, onPick);
        Popups.open(p);
        return p;
    }

    @Override
    public boolean contains(final double mx, final double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int indexAt(final double mx, final double my) {
        if (!contains(mx, my)) return -1;
        final int cx = (int) ((mx - x - PAD) / CELL), cy = (int) ((my - y - PAD) / CELL);
        if (cx < 0 || cx >= COLS || cy < 0) return -1;
        final int i = cy * COLS + cx;
        return i < Emotes.NAMES.size() ? i : -1;
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float o = open.get();
        final int a = Math.round(255 * Math.min(1, o * 1.4f));
        final int yy = y + Math.round((1 - o) * 4);
        hovered = indexAt(mouseX, mouseY);
        if (t.isVanilla()) {
            g.fill(x, yy, x + w, yy + h, Colors.withAlpha(0x000000, Math.min(a, 0xF0)));
            SlateDraw.outline(g, x, yy, w, h, Colors.withAlpha(0xFFFFFF, a), 0);
        } else {
            SlateDraw.shadow(g, x, yy, w, h, 0.6f * o);
            SlateDraw.pixelRound(g, x, yy, w, h, Colors.withAlpha(p.surface(), a), t.radius());
            SlateDraw.outline(g, x, yy, w, h, Colors.withAlpha(p.borderStrong(), a), t.radius());
        }
        for (int i = 0; i < Emotes.NAMES.size(); i++) {
            final int cx = x + PAD + (i % COLS) * CELL, cy = yy + PAD + (i / COLS) * CELL;
            if (i == hovered) SlateDraw.pixelRound(g, cx, cy, CELL, CELL, Colors.withAlpha(t.isVanilla() ? 0x404040 : p.surfaceHover(), a), t.radius() > 0 ? 2 : 0);
            // The glyph is 8 px tall in the font; draw it doubled so the picker shows the art properly.
            g.pose().pushPose();
            g.pose().translate(cx + 1, cy + 1, 0);
            g.pose().scale(1.75f, 1.75f, 1f);
            g.drawString(SlateDraw.font(), Emotes.component(Emotes.NAMES.get(i)), 0, 0, Colors.withAlpha(0xFFFFFF, a), false);
            g.pose().popPose();
        }
        if (hovered >= 0) SlateTooltips.request(Component.literal(Emotes.token(Emotes.NAMES.get(hovered))), null);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        final int i = indexAt(mouseX, mouseY);
        if (i < 0) return true;
        SlateSounds.tick();
        onPick.accept(Emotes.NAMES.get(i));
        if (!net.minecraft.client.gui.screens.Screen.hasShiftDown()) Popups.close(this);
        return true;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (keyCode == 256) { Popups.close(this); return true; }
        return false;
    }
}
