package dev.fallingcloud.slate.config.search;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateSounds;
import dev.fallingcloud.slate.core.widget.popup.Popup;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The "results on other pages" dropdown under the header search field. Non-modal, updated in place
 * as the user types; Up/Down + Enter pick a hit, click jumps to it.
 */
public final class SearchPopup implements Popup {

    public static final int ROW = 18, MAX_ROWS = 8;

    private final int x, y, w;
    private List<SearchIndex.Hit> hits = new ArrayList<>();
    private int selected = 0;
    private final Consumer<SearchIndex.Hit> onPick;

    public SearchPopup(final int x, final int y, final int w, final Consumer<SearchIndex.Hit> onPick) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.onPick = onPick;
    }

    public void setHits(final List<SearchIndex.Hit> newHits) {
        hits = newHits;
        selected = 0;
    }

    public boolean isEmpty() { return hits.isEmpty(); }

    private int height() {
        return Math.min(hits.size(), MAX_ROWS) * ROW + 6;
    }

    @Override
    public boolean contains(final double mx, final double my) {
        return !hits.isEmpty() && mx >= x && mx < x + w && my >= y && my < y + height();
    }

    private void pick(final int i) {
        if (i < 0 || i >= hits.size()) return;
        final SearchIndex.Hit h = hits.get(i);
        Popups.close(this);
        SlateSounds.tick();
        onPick.accept(h);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        if (hits.isEmpty()) return;
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final int h = height();
        final int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        final int px = Math.min(x, sw - w - 2);
        if (t.isVanilla()) {
            g.fill(px, y, px + w, y + h, 0xF0101010);
            SlateDraw.outline(g, px, y, w, h, 0xFFFFFFFF, 0);
        } else {
            SlateDraw.shadow(g, px, y, w, h, 0.6f);
            SlateDraw.pixelRound(g, px, y, w, h, p.surface(), t.radius());
            SlateDraw.outline(g, px, y, w, h, p.borderStrong(), t.radius());
        }
        int ry = y + 3;
        for (int i = 0; i < Math.min(hits.size(), MAX_ROWS); i++) {
            final SearchIndex.Hit hit = hits.get(i);
            final boolean hov = mouseX >= px && mouseX < px + w && mouseY >= ry && mouseY < ry + ROW;
            if (hov) selected = i;
            final boolean sel = i == selected;
            if (sel) SlateDraw.pixelRound(g, px + 2, ry, w - 4, ROW, t.isVanilla() ? 0x40FFFFFF : p.surfaceHover(), t.radius() > 0 ? 2 : 0);
            final Component crumb = hit.entry().crumb();
            final int fg = sel ? p.text() : (t.isVanilla() ? 0xFFE0E0E0 : p.textMuted());
            g.drawString(SlateDraw.font(), SlateDraw.truncate(hit.entry().binding().label(), w - 14), px + 8, ry + 1, fg, t.isVanilla());
            g.drawString(SlateDraw.font(), SlateDraw.truncate(crumb, w - 14), px + 8, ry + 10, Colors.withAlpha(p.textDim(), 0xFF), false);
            ry += ROW;
        }
        if (hits.size() > MAX_ROWS) Icons.draw(g, Icon.DOTS, px + w - 14, y + h - 10, 8, p.textDim());
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        final int i = (int) ((mouseY - y - 3) / ROW);
        if (i >= 0 && i < Math.min(hits.size(), MAX_ROWS)) pick(i);
        return true;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (hits.isEmpty()) return false;
        if (keyCode == 264) { selected = Math.min(hits.size() - 1, selected + 1); return true; }
        if (keyCode == 265) { selected = Math.max(0, selected - 1); return true; }
        if (keyCode == 257 || keyCode == 335) { pick(selected); return true; }
        return false;
    }
}
