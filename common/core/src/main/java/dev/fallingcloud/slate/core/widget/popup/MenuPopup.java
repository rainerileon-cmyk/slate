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
 * scrolls when taller than the screen, keyboard navigable. Items may have icons, be disabled, be
 * separators, or be "checked" (dropdown current value).
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

    public MenuPopup(final int atX, final int atY, final List<Item> items, final int minWidth) {
        this.items = new ArrayList<>(items);
        this.minWidth = minWidth;
        this.open.snap(0);
        this.open.set(1);
        layout(atX, atY);
    }

    public MenuPopup(final int atX, final int atY, final List<Item> items) {
        this(atX, atY, items, 100);
    }

    private void layout(final int atX, final int atY) {
        int maxW = minWidth;
        int height = PAD * 2;
        for (final Item it : items) {
            if (it.separator) { height += SEP; continue; }
            height += ROW;
            maxW = Math.max(maxW, SlateDraw.width(it.label) + 12 + (it.icon != null ? 16 : 0) + (it.checked ? 12 : 0));
        }
        final Minecraft mc = Minecraft.getInstance();
        final int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        w = maxW;
        h = Math.min(height, sh - 8);
        x = atX + w > sw - 2 ? Math.max(2, atX - w) : atX;
        y = atY + h > sh - 2 ? Math.max(2, sh - 2 - h) : atY;
    }

    public int width() { return w; }

    @Override
    public boolean contains(final double mx, final double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
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

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float o = open.get();
        final int a = Math.round(255 * Math.min(1, o * 1.4f));
        hovered = contains(mouseX, mouseY) ? itemAt(mouseY) : hovered >= 0 && !contains(mouseX, mouseY) ? hovered : -1;
        final int yy0 = y + Math.round((1 - o) * -4);
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
            if (hov) SlateDraw.pixelRound(g, x + 2, yy, w - 4, ROW, Colors.withAlpha(t.isVanilla() ? 0x404040 : p.surfaceHover(), a), t.radius() > 0 ? 2 : 0);
            int tx = x + 6;
            final int fg = Colors.withAlpha(!it.enabled ? p.textDim() : it.danger ? p.danger() : hov ? p.text() : (t.isVanilla() ? 0xFFE0E0E0 : p.textMuted()), a);
            if (it.icon != null) { Icons.draw(g, it.icon, tx, yy + 2, 10, fg); tx += 14; }
            g.drawString(SlateDraw.font(), it.label, tx, yy + 3, fg, t.isVanilla());
            if (it.checked) Icons.draw(g, Icon.CHECK, x + w - 12, yy + 3, 8, Colors.withAlpha(p.accent(), a));
            yy += ROW;
        }
        SlateDraw.unscissor(g);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        final int i = itemAt(mouseY);
        if (i < 0) return true;
        final Item it = items.get(i);
        if (!it.enabled) return true;
        SlateSounds.click();
        Popups.close(this);
        if (it.action != null) it.action.run();
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
                if (!items.get(i).separator && items.get(i).enabled) { hovered = i; break; }
            }
            return true;
        }
        if ((keyCode == 257 || keyCode == 335) && hovered >= 0) {
            final Item it = items.get(hovered);
            Popups.close(this);
            if (it.action != null) it.action.run();
            return true;
        }
        return false;
    }
}
