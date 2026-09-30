package dev.fallingcloud.slate.menu.client.overhaul.play;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateBadge;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.core.widget.SlateTooltips;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * What the Play screen says about the selected world or server, under the map: its name with a line of chips (game
 * mode, hardcore, online), then facts as label and value. The name and the chips stay put; the facts scroll under
 * them. The main ones fit the box, the rest is a scroll away, and a line at the foot says so while there is more
 * below. A fact that is worth having elsewhere (a seed, an address) copies itself when clicked.
 */
final class DetailsList extends SlateWidget {

    /** One line. A null {@code value} makes it a heading for the lines under it. */
    record Row(Component label, @Nullable Component value, int color, boolean copies) {
        static Row of(final Component label, final Component value) { return new Row(label, value, 0, false); }

        static Row of(final Component label, final String value) { return new Row(label, Component.literal(value), 0, false); }

        static Row colored(final Component label, final Component value, final int argb) { return new Row(label, value, argb, false); }

        static Row copy(final Component label, final String value) { return new Row(label, Component.literal(value), 0, true); }

        static Row heading(final Component label) { return new Row(label, null, 0, false); }
    }

    record Chip(Component text, int color) {}

    private static final int ROW_H = 12, HEAD_H = 12, HINT_H = 12;

    private Component title = Component.empty();
    private Component subtitle = Component.empty();
    private List<Chip> chips = List.of();
    private List<Row> rows = List.of();
    private final Anim scroll = new Anim(0, 160, Ease.OUT_CUBIC);
    private final Anim swap = new Anim(1, 220, Ease.OUT_CUBIC);
    private int contentH;

    DetailsList(final int x, final int y, final int width, final int height) {
        super(x, y, width, height, Component.empty());
        silent();
    }

    /** Shows another world's or server's facts: scrolled to the top, faded in. */
    void show(final Component title, final Component subtitle, final List<Chip> chips, final List<Row> rows) {
        final boolean other = !title.getString().equals(this.title.getString());
        this.title = title;
        this.subtitle = subtitle;
        this.chips = List.copyOf(chips);
        this.rows = new ArrayList<>(rows);
        setMessage(title);
        if (other) {
            scroll.snap(0f);
            swap.snap(0f);
            swap.set(1f);
        }
    }

    boolean isEmpty() { return title.getString().isEmpty(); }

    /** A box with room to spare says where the world lives on disk too; a small one keeps its lines for the facts. */
    private boolean roomy() { return getHeight() >= 132 && !subtitle.getString().isEmpty(); }

    private int chipsWidth() {
        int w = 0;
        for (final Chip c : chips) w += SlateDraw.width(c.text()) + 8 + 3;
        return Math.max(0, w - 3);
    }

    /** The chips stand on the name's line when both fit, under it when they do not. */
    private boolean chipsBeside() {
        if (chips.isEmpty()) return false;
        final int name = SlateDraw.font().width(Fonts.heading(title));
        return Math.min(name, getWidth() / 2) + 8 + chipsWidth() <= getWidth();
    }

    private int headerHeight() {
        return 14 + (roomy() ? 11 : 0) + (chips.isEmpty() || chipsBeside() ? 0 : 15) + 3;
    }

    /** The part the facts scroll in: under the header, over the hint. */
    private int listTop() { return getY() + headerHeight(); }

    /** Whole lines only while there is more than fits: a line cut in half reads as a fault. */
    private int listHeight() {
        final int room = Math.max(0, getHeight() - headerHeight());
        if (!overflows()) return room;
        return Math.max(ROW_H, (room - HINT_H) / ROW_H * ROW_H);
    }

    private boolean overflows() { return contentH > getHeight() - headerHeight(); }

    private int maxScroll() { return Math.max(0, contentH - listHeight()); }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || maxScroll() <= 0) return false;
        scroll.set(Mth.clamp(scroll.target() - (float) scrollY * ROW_H * 2f, 0f, maxScroll()));
        return true;
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        final Row r = rowAt(mouseY);
        if (r == null || !r.copies() || r.value() == null) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(r.value().getString());
        SlateToasts.show(Component.translatable("slate.copied"), r.value(), Icon.COPY);
    }

    @Nullable
    private Row rowAt(final double mouseY) {
        if (mouseY < listTop() || mouseY >= listTop() + listHeight()) return null;
        int y = listTop() - Math.round(scroll.get());
        for (final Row r : rows) {
            final int h = r.value() == null ? HEAD_H : ROW_H;
            if (mouseY >= y && mouseY < y + h) return r;
            y += h;
        }
        return null;
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, mouseX, mouseY, false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, mouseX, mouseY, true);
    }

    private void draw(final GuiGraphics g, final int mouseX, final int mouseY, final boolean vanilla) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha() * swap.get();
        if (a <= 0.004f) return;
        final var font = SlateDraw.font();
        final int x = getX(), w = getWidth(), top = getY() + enterOffset() + Math.round((1f - swap.get()) * 5f);
        final int text = Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.text(), a), muted = Colors.scaleAlpha(vanilla ? 0xFFC0C0C0 : p.textMuted(), a);
        final int dim = Colors.scaleAlpha(vanilla ? 0xFFA0A0A0 : p.textDim(), a);

        // ---- the header: the name, the chips beside or under it
        int y = top;
        final boolean beside = chipsBeside();
        final int chipsW = chipsWidth();
        g.drawString(font, SlateDraw.truncate(Fonts.heading(title), beside ? w - chipsW - 8 : w), x, y + 3, text, vanilla);
        if (beside) chips(g, x + w - chipsW, y + 1, a);
        y += 14;
        if (roomy()) {
            g.drawString(font, SlateDraw.truncate(subtitle, w), x, y, dim, vanilla);
            y += 11;
        }
        if (!chips.isEmpty() && !beside) {
            chips(g, x, y + 1, a);
            y += 15;
        }
        y += 3;

        // ---- the facts
        final int listTop = y, listH = listHeight();
        Component tip = null;
        g.enableScissor(x - 2, listTop, x + w + 2, listTop + listH);
        y = listTop - Math.round(scroll.get());
        for (final Row r : rows) {
            if (r.value() == null) {
                if (y + HEAD_H > listTop && y < listTop + listH) SlateDraw.sectionRule(g, r.label(), x, y + 1, w, a);
                y += HEAD_H;
                continue;
            }
            if (y + ROW_H > listTop && y < listTop + listH) {
                final boolean over = isHovered() && mouseY >= Math.max(y, listTop) && mouseY < Math.min(y + ROW_H, listTop + listH)
                    && mouseX >= x && mouseX < x + w;
                if (over && r.copies()) {
                    SlateDraw.rect(g, x - 2, y - 2, w + 4, ROW_H, Colors.scaleAlpha(vanilla ? 0x30FFFFFF : Colors.withAlpha(p.surfaceHover(), 0xC0), a));
                    tip = Component.translatable("slate_menu.play.copy");
                }
                g.drawString(font, r.label(), x, y, muted, vanilla);
                final int room = w - font.width(r.label()) - 8;
                final FormattedCharSequence v = SlateDraw.truncate(r.value(), room);
                final int color = r.color() != 0 ? Colors.scaleAlpha(r.color(), a) : text;
                g.drawString(font, v, x + w - font.width(v), y, color, vanilla);
                if (over && font.width(r.value()) > room) tip = r.value();
            }
            y += ROW_H;
        }
        contentH = y + Math.round(scroll.get()) - listTop;
        g.disableScissor();
        if (scroll.target() > maxScroll()) scroll.set(maxScroll());

        // ---- more below: a line of its own at the foot says so, and goes when the end is reached
        if (overflows()) {
            final float more = Mth.clamp((maxScroll() - scroll.get()) / 10f, 0f, 1f);
            final int hy = listTop + listH + 2;
            SlateDraw.hline(g, x, hy - 2, w, Colors.scaleAlpha(vanilla ? 0x40FFFFFF : p.border(), a));
            final Component hint = Component.translatable(more > 0.5f ? "slate_menu.play.scroll_more" : "slate_menu.play.scroll_end");
            final int hw = font.width(hint) + (more > 0.5f ? 11 : 0);
            if (hw <= w) {
                final int hx = x + (w - hw) / 2;
                if (more > 0.5f) {
                    final float breath = t.motion() > 0f ? 0.65f + 0.35f * Mth.sin(System.nanoTime() / 1.0e9f * 2.6f) : 1f;
                    Icons.draw(g, Icon.CHEVRON_DOWN, hx, hy, 8, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.accent(), a * breath));
                }
                g.drawString(font, hint, hx + (more > 0.5f ? 11 : 0), hy, dim, vanilla);
            }
        }
        if (tip != null) SlateTooltips.request(tip, this);
    }

    private void chips(final GuiGraphics g, final int x, final int y, final float a) {
        int cx = x;
        for (final Chip c : chips) {
            final int cw = SlateDraw.width(c.text()) + 8;
            if (cx + cw > getX() + getWidth()) break;
            cx += SlateBadge.draw(g, c.text(), cx, y, Colors.scaleAlpha(c.color(), a)) + 3;
        }
    }
}
