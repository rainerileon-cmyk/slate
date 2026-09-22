package dev.fallingcloud.slate.core.widget.popup;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A vertical menu of items (context menus, dropdown lists). Opens at a point, flips to stay on screen,
 * scrolls when taller than the screen, keyboard navigable (arrows, Home/End, Enter), animates in and
 * out. Items may have icons, be disabled, be separators, or be "checked" (dropdown current value).
 */
public class MenuPopup implements Popup {

    public record Item(Component label, @Nullable Icon icon, boolean enabled, boolean checked, boolean separator, boolean danger, @Nullable Runnable action) {
        public static Item of(final Component label, final Runnable action) { return new Item(label, null, true, false, false, false, action); }
        public static Item of(final Component label, final Icon icon, final Runnable action) { return new Item(label, icon, true, false, false, false, action); }
        public static Item danger(final Component label, final Icon icon, final Runnable action) { return new Item(label, icon, true, false, false, true, action); }
        public static Item disabled(final Component label, @Nullable final Icon icon) { return new Item(label, icon, false, false, false, false, null); }
        public static Item checked(final Component label, final boolean checked, final Runnable action) { return new Item(label, null, true, checked, false, false, action); }
        public static Item sep() { return new Item(Component.empty(), null, false, false, true, false, null); }
    }

    public static final int ROW = 14, SEP = 5, PAD = 3;

    private final List<Item> items;
    private int x, y, w, h;
    private int hovered = -1;
    private int scroll;
    private final Anim open = new Anim(0, 140, Ease.OUT_CUBIC);
    private final int minWidth;
    private final boolean opensUpward;
    private boolean closing;
    private int mouseLastX = Integer.MIN_VALUE, mouseLastY = Integer.MIN_VALUE;

    public MenuPopup(final int atX, final int atY, final List<Item> items, final int minWidth) {
        this.items = new ArrayList<>(items);
        this.minWidth = minWidth;
        this.open.snap(0);
        this.open.set(1);
        this.opensUpward = layout(atX, atY);
    }

    public MenuPopup(final int atX, final int atY, final List<Item> items) {
        this(atX, atY, items, 100);
    }

    /** @return true when the menu had to open upward (it grows from its bottom edge). */
    private boolean layout(final int atX, final int atY) {
        int maxW = minWidth;
        int height = PAD * 2;
        for (final Item it : items) {
            if (it.separator) { height += SEP; continue; }
            height += ROW;
            maxW = Math.max(maxW, SlateDraw.width(it.label) + 12 + (it.icon != null ? 16 : 0) + (it.checked ? 12 : 0) + 4);
        }
        final Minecraft mc = Minecraft.getInstance();
        final int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        w = Math.min(maxW, sw - 4);
        h = Math.min(height, sh - 8);
        x = atX + w > sw - 2 ? Math.max(2, atX - w) : Math.max(2, atX);
        boolean up = false;
        if (atY + h > sh - 2) {
            // Flip above the anchor when that fits, else pin to the bottom edge.
            if (atY - h - 1 >= 2) { y = atY - h - 1; up = true; } else y = Math.max(2, sh - 2 - h);
        } else {
            y = atY;
        }
        return up;
    }

    public int width() { return w; }

    public int height() { return h; }

    /** Pre-highlight an item (dropdowns pass the current value) and scroll it into view. */
    public MenuPopup highlight(final int index) {
        if (index >= 0 && index < items.size() && !items.get(index).separator) {
            hovered = index;
            ensureVisible(index);
        }
        return this;
    }

    @Override
    public boolean contains(final double mx, final double my) {
        return !closing && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int itemTop(final int index) {
        int yy = PAD;
        for (int i = 0; i < index; i++) yy += items.get(i).separator ? SEP : ROW;
        return yy;
    }

    private int itemAt(final double my) {
        int yy = y + PAD - scroll;
        for (int i = 0; i < items.size(); i++) {
            final int rh = items.get(i).separator ? SEP : ROW;
            if (my >= yy && my < yy + rh) return items.get(i).separator ? -1 : i;
            yy += rh;
        }
        return -1;
    }

    private int contentHeight() {
        int hh = PAD * 2;
        for (final Item it : items) hh += it.separator ? SEP : ROW;
        return hh;
    }

    private boolean scrollable() { return contentHeight() > h; }

    private void ensureVisible(final int index) {
        if (!scrollable()) return;
        final int top = itemTop(index), bottom = top + ROW;
        if (top - scroll < PAD) scroll = Math.max(0, top - PAD);
        else if (bottom - scroll > h - PAD) scroll = Math.min(contentHeight() - h, bottom - h + PAD);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float o = Math.max(0f, Math.min(1f, open.get()));
        final int a = Math.round(255 * Math.min(1, o * 1.4f));
        if (a <= 2) return;
        // Mouse hover only when the mouse actually moved (keyboard navigation must not be overridden by a still cursor).
        if (!closing && (mouseX != mouseLastX || mouseY != mouseLastY)) {
            mouseLastX = mouseX;
            mouseLastY = mouseY;
            if (contains(mouseX, mouseY)) hovered = itemAt(mouseY);
        }
        final int slide = Math.round((1 - o) * 4);
        final int yy0 = opensUpward ? y + slide : y - slide;
        if (t.isVanilla()) {
            g.fill(x, yy0, x + w, yy0 + h, Colors.withAlpha(0x000000, Math.min(a, 0xF0)));
            SlateDraw.outline(g, x, yy0, w, h, Colors.withAlpha(0xFFFFFF, a), 0);
        } else {
            SlateDraw.shadow(g, x, yy0, w, h, 0.6f * o);
            SlateDraw.pixelRound(g, x, yy0, w, h, Colors.withAlpha(p.surface(), a), t.radius());
            SlateDraw.outline(g, x, yy0, w, h, Colors.withAlpha(p.borderStrong(), a), t.radius());
        }
        SlateDraw.scissor(g, x, yy0, w, h);
        int yy = yy0 + PAD - scroll;
        for (int i = 0; i < items.size(); i++) {
            final Item it = items.get(i);
            if (it.separator) {
                SlateDraw.hline(g, x + 4, yy + SEP / 2, w - 8, Colors.withAlpha(t.isVanilla() ? 0x505050 : p.border(), a));
                yy += SEP;
                continue;
            }
            final boolean hov = i == hovered && it.enabled;
            if (hov) SlateDraw.pixelRound(g, x + 2, yy, w - 4, ROW, Colors.withAlpha(t.isVanilla() ? 0x404040 : (it.danger ? Colors.withAlpha(p.danger(), 0x30) : p.surfaceHover()), a), t.radius() > 0 ? 2 : 0);
            int tx = x + 6;
            final int fg = Colors.withAlpha(!it.enabled ? p.textDim() : it.danger ? p.danger() : hov ? p.text() : (t.isVanilla() ? 0xFFE0E0E0 : p.textMuted()), a);
            if (it.icon != null) { Icons.draw(g, it.icon, tx, yy + 2, 10, fg); tx += 14; }
            final int avail = w - (tx - x) - (it.checked ? 14 : 6);
            g.drawString(SlateDraw.font(), SlateDraw.truncate(it.label, avail), tx, yy + 3, fg, t.isVanilla());
            if (it.checked) Icons.draw(g, Icon.CHECK, x + w - 12, yy + 3, 8, Colors.withAlpha(p.accent(), a));
            yy += ROW;
        }
        SlateDraw.unscissor(g);
        if (scrollable()) {
            // Thin scroll indicator on the right.
            final int max = contentHeight() - h;
            final int barH = Math.max(8, h * h / contentHeight());
            final int barY = yy0 + 2 + (int) ((h - 4 - barH) * (scroll / (float) Math.max(1, max)));
            SlateDraw.rect(g, x + w - 3, barY, 2, barH, Colors.withAlpha(t.isVanilla() ? 0x808080 : p.borderStrong(), a));
        }
    }

    private void activate(final int i) {
        final Item it = items.get(i);
        if (!it.enabled) return;
        SlateSounds.click();
        Popups.close(this);
        if (it.action != null) it.action.run();
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        final int i = itemAt(mouseY);
        if (i < 0) return true;
        activate(i);
        return true;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        final int max = Math.max(0, contentHeight() - h);
        scroll = (int) Math.max(0, Math.min(max, scroll - scrollY * ROW));
        return true;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (keyCode == 264 || keyCode == 265) {
            final int dir = keyCode == 264 ? 1 : -1;
            int i = hovered;
            for (int n = 0; n < items.size(); n++) {
                i = ((i + dir) % items.size() + items.size()) % items.size();
                if (!items.get(i).separator && items.get(i).enabled) { hovered = i; ensureVisible(i); break; }
            }
            return true;
        }
        if (keyCode == 268 || keyCode == 269) {
            final int dir = keyCode == 268 ? 1 : -1;
            int i = keyCode == 268 ? -1 : items.size();
            for (int n = 0; n < items.size(); n++) {
                i += dir;
                if (!items.get(i).separator && items.get(i).enabled) { hovered = i; ensureVisible(i); break; }
            }
            return true;
        }
        if ((keyCode == 257 || keyCode == 335 || keyCode == 32) && hovered >= 0) {
            activate(hovered);
            return true;
        }
        return false;
    }

    @Override
    public boolean beginClose() {
        if (Theme.current().motion() <= 0) return false;
        closing = true;
        open.set(0f, 100);
        return true;
    }

    @Override
    public boolean closeFinished() { return open.get() <= 0.02f; }
}
