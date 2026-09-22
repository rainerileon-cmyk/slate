package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateBadge;
import dev.fallingcloud.slate.core.widget.SlateSounds;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The 14 px channel tab strip above the chat input: compact labels sized to their text, unread bubbles,
 * a sliding underline (dark skin) or small raised tabs (vanilla skin). Reads the channel list live so DM
 * threads appear as they open.
 */
public final class ChannelTabs extends SlateWidget {

    public static final int HEIGHT = 14;

    private final Anim slide = new Anim(0, 180, Ease.OUT_CUBIC);
    private int hovered = -1;

    public ChannelTabs(final int x, final int y, final int width) {
        super(x, y, width, HEIGHT, Component.translatable("slate_chat.tabs"));
        slide.snap(indexOfCurrent());
    }

    private List<ChatChannels.Channel> tabs() { return ChatChannels.channels(); }

    private int indexOfCurrent() {
        final List<ChatChannels.Channel> t = tabs();
        final ChatChannels.Channel c = ChatChannels.current();
        for (int i = 0; i < t.size(); i++) if (t.get(i).kind() == c.kind() && t.get(i).id().equals(c.id())) return i;
        return 0;
    }

    private static Icon iconOf(final ChatChannels.Channel c) {
        return switch (c.kind()) {
            case GLOBAL -> Icon.WORLD;
            case SYSTEM -> Icon.TERMINAL;
            case THREAD -> Icon.CHAT;
        };
    }

    private int tabWidth(final ChatChannels.Channel c) {
        return SlateDraw.width(c.label()) + 22 + (c.unread() > 0 ? 14 : 0);
    }

    private int tabX(final int index) {
        int x = getX();
        final List<ChatChannels.Channel> t = tabs();
        for (int i = 0; i < index && i < t.size(); i++) x += tabWidth(t.get(i)) + 2;
        return x;
    }

    private int tabAt(final double mx, final double my) {
        if (my < getY() || my >= getY() + getHeight()) return -1;
        final List<ChatChannels.Channel> t = tabs();
        for (int i = 0; i < t.size(); i++) {
            final int tx = tabX(i);
            if (mx >= tx && mx < tx + tabWidth(t.get(i))) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.visible || !this.active || button != 0) return false;
        final int i = tabAt(mouseX, mouseY);
        if (i < 0) return false;
        final List<ChatChannels.Channel> t = tabs();
        if (i < t.size()) {
            ChatChannels.select(t.get(i));
            slide.set(i);
            SlateSounds.tick();
        }
        return true;
    }

    @Override
    public boolean isMouseOver(final double mouseX, final double mouseY) {
        return this.active && this.visible && tabAt(mouseX, mouseY) >= 0;
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final List<ChatChannels.Channel> tabs = tabs();
        final int cur = indexOfCurrent();
        if (slide.target() != cur) slide.set(cur);
        hovered = tabAt(mouseX, mouseY);
        final int y = getY(), h = getHeight();
        for (int i = 0; i < tabs.size(); i++) {
            final ChatChannels.Channel c = tabs.get(i);
            final int tx = tabX(i), tw = tabWidth(c);
            final boolean sel = i == cur;
            if (i == hovered && !sel) SlateDraw.pixelRound(g, tx, y, tw, h - 2, Colors.withAlpha(p.surfaceHover(), 0xA0), t.radius() > 0 ? 2 : 0);
            if (sel) SlateDraw.pixelRound(g, tx, y, tw, h - 2, Colors.withAlpha(p.surface(), 0xC0), t.radius() > 0 ? 2 : 0);
            final int fg = sel ? p.text() : i == hovered ? p.textMuted() : p.textDim();
            Icons.draw(g, iconOf(c), tx + 4, y + 2, 8, fg);
            g.drawString(SlateDraw.font(), c.label(), tx + 15, y + 3, fg, false);
            if (c.unread() > 0) SlateBadge.drawCount(g, c.unread(), tx + tw - 13, y + 1);
        }
        // Sliding underline
        if (!tabs.isEmpty()) {
            final float s = slide.get();
            final int i0 = Math.max(0, Math.min(tabs.size() - 1, (int) Math.floor(s))), i1 = Math.min(tabs.size() - 1, i0 + 1);
            final float f = s - i0;
            final int ux = Math.round(tabX(i0) + (tabX(i1) - tabX(i0)) * f) + 3;
            final int uw = Math.round(tabWidth(tabs.get(i0)) + (tabWidth(tabs.get(i1)) - tabWidth(tabs.get(i0))) * f) - 6;
            SlateDraw.rect(g, ux, y + h - 2, uw, 2, p.accent());
        }
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final List<ChatChannels.Channel> tabs = tabs();
        final int cur = indexOfCurrent();
        hovered = tabAt(mouseX, mouseY);
        final int y = getY(), h = getHeight();
        for (int i = 0; i < tabs.size(); i++) {
            final ChatChannels.Channel c = tabs.get(i);
            final int tx = tabX(i), tw = tabWidth(c);
            final boolean sel = i == cur;
            g.fill(tx, y + (sel ? 0 : 2), tx + tw, y + h, sel ? 0xC0000000 : 0x80000000);
            SlateDraw.outline(g, tx, y + (sel ? 0 : 2), tw, h - (sel ? 0 : 2), sel ? 0xFFFFFFFF : i == hovered ? 0xFFA0A0A0 : 0xFF505050, 0);
            final int fg = sel ? 0xFFFFFFFF : i == hovered ? 0xFFE0E0E0 : 0xFFA0A0A0;
            Icons.draw(g, iconOf(c), tx + 4, y + 3, 8, fg);
            g.drawString(SlateDraw.font(), c.label(), tx + 15, y + 3, fg, true);
            if (c.unread() > 0) SlateBadge.drawCount(g, c.unread(), tx + tw - 13, y + 2);
        }
    }
}
