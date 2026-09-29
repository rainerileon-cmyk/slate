package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.chat.ChatConfig;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.media.Attachment;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateAvatar;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.jetbrains.annotations.Nullable;

/**
 * The HUD chat, drawn in place of vanilla's {@code ChatComponent.render}. Same coordinate system as
 * vanilla (chat scale, left edge at 4, rows stacked upward from {@code guiHeight - 40}) so nothing about
 * scrolling or click maths changes for other mods; what differs is that rows have their own heights
 * (header, gap, attachment) and are drawn as groups: head + name + time once, text under it.
 *
 * <p>Dark skin: one rounded panel behind the visible rows, palette text. Vanilla skin: vanilla's per-row
 * translucent black lines and white shadowed text - the grouping, heads, cards and motion on top.</p>
 *
 * <p>Every frame the renderer also hands the input side its hit rectangles ({@link ChatRenderState}).</p>
 */
public final class ChatRenderer {

    private record RowLayout(int index, GuiMessage.Line line, int y, int h, float fade, @Nullable GuiMessage message) {}

    public static void render(final ChatAccess a, final GuiGraphics g, final int tickCount, final int mouseX, final int mouseY, final boolean focused) {
        ChatRenderState.beginFrame();
        final Minecraft mc = Minecraft.getInstance();
        final ChatConfig cfg = ChatConfig.get();
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final Font font = mc.font;
        if (mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) return;

        final List<GuiMessage.Line> lines = a.slate$lines();
        final float f = (float) a.slate$scale();
        final int k = Mth.ceil(a.slate$width() / f);
        final int i1 = Mth.floor((g.guiHeight() - 40) / f);
        final int lineH = a.slate$lineHeight();
        final ChatLayout.Geometry geo = ChatLayout.Geometry.of(lineH);
        final int page = ChatLayout.pageHeight(a);
        final float textOpacity = (float) (mc.options.chatOpacity().get() * 0.9 + 0.1);
        final float bgOpacity = (float) (cfg.opacity >= 0 ? Mth.clamp(cfg.opacity, 0, 1) : mc.options.textBackgroundOpacity().get());
        final int slidePx = ChatRenderState.slidePixels(lineH, f);
        ChatRenderState.panelLeft = 0;
        ChatRenderState.panelRight = Math.round((k + 8) * f);
        ChatRenderState.panelBottom = Math.round((i1 + 2) * f);

        if (!focused) drawUnreadBadge(g, cfg, t, p, font);
        if (lines.isEmpty()) return;

        // ---- layout pass: which rows are on the page, where, and how faded
        final List<RowLayout> rows = new ArrayList<>();
        final int scroll = a.slate$scroll();
        int y = i1, topVisible = i1;
        float maxFade = 0f;
        for (int i = scroll; i < lines.size(); i++) {
            final GuiMessage.Line line = lines.get(i);
            final int h = geo.rowHeight(line);
            if (y - h < i1 - page) break;
            y -= h;
            if (h <= 0) continue;
            final int age = tickCount - line.addedTime();
            final float fade = focused ? 1f : (cfg.fadeUnfocused ? (float) timeFactor(age) : 1f);
            if (fade <= 0.004f) continue;
            rows.add(new RowLayout(i, line, y, h, fade, ChatRows.messageOf(line.content())));
            topVisible = y;
            maxFade = Math.max(maxFade, fade);
        }
        if (rows.isEmpty()) return;

        // ---- hover (chat-screen only)
        final double cx = mouseX / f - 4, cy = (mouseY - slidePx) / f;
        GuiMessage hovered = null;
        if (focused && cfg.hoverActions && cx >= -4 && cx < k + 4) {
            for (final RowLayout r : rows) {
                if (cy >= r.y && cy < r.y + r.h && r.message != null) { hovered = r.message; break; }
            }
        }
        ChatRenderState.hovered = hovered;

        // ---- row hit rects (screen space) for clicks and link hover
        for (final RowLayout r : rows) {
            final boolean text = r.line.content() instanceof ChatRows.TextRow || !(r.line.content() instanceof ChatRows.Row);
            final ChatMeta.Meta meta = r.message == null ? null : ChatMeta.of(r.message);
            ChatRenderState.rowHits.add(new ChatRenderState.RowHit(r.index, r.line, r.message,
                0, Math.round(r.y * f) + slidePx, Math.round((k + 8) * f), Math.round((r.y + r.h) * f) + slidePx,
                geo.indentFor(meta), f, text));
        }

        // ---- draw
        final int clipTop = Math.max(0, Math.round((i1 - page - 4) * f) + slidePx);
        final int clipBottom = Math.min(g.guiHeight(), Math.round((i1 + 3) * f) + slidePx);
        g.enableScissor(0, clipTop, g.guiWidth(), clipBottom);
        g.pose().pushPose();
        g.pose().translate(0, slidePx, 0);
        g.pose().scale(f, f, 1f);
        g.pose().translate(4f, 0f, 0f);

        if (!t.isVanilla()) {
            final int px0 = -4, px1 = k + 4, py0 = topVisible - 2, py1 = i1 + 2;
            final float pa = bgOpacity * maxFade;
            SlateDraw.pixelRound(g, px0, py0, px1 - px0, py1 - py0, Colors.scaleAlpha(p.bg(), pa * 0.94f), t.radius());
            SlateDraw.outline(g, px0, py0, px1 - px0, py1 - py0, Colors.scaleAlpha(p.border(), pa), t.radius());
        }

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 50f);
        final Set<String> drawnCards = new HashSet<>(4);
        for (final RowLayout r : rows) {
            drawRow(g, font, r, geo, k, lineH, f, slidePx, textOpacity, bgOpacity, hovered, focused, cfg, t, p, drawnCards);
        }
        if (focused) {
            drawScrollbar(g, a, rows, i1, page, k, f, slidePx, t, p);
            if (hovered != null && cfg.hoverActions) drawHoverActions(g, rows, hovered, k, i1 - page, f, slidePx, cx, cy, t, p);
            if (scroll > 0 && a.slate$newSinceScroll()) drawNewMessagesPill(g, font, k, i1, f, slidePx, t, p);
        }
        g.pose().popPose();
        g.pose().popPose();
        g.disableScissor();
    }

    // ------------------------------------------------------------------ rows

    private static void drawRow(final GuiGraphics g, final Font font, final RowLayout r, final ChatLayout.Geometry geo, final int k, final int lineH,
                                final float f, final int slidePx, final float textOpacity, final float bgOpacity, @Nullable final GuiMessage hovered,
                                final boolean focused, final ChatConfig cfg, final Theme t, final Palette p, final Set<String> drawnCards) {
        final FormattedCharSequence content = r.line.content();
        final ChatMeta.Meta meta = r.message == null ? null : ChatMeta.of(r.message);
        final float fade = r.fade;
        float alpha = fade * textOpacity;
        if (meta != null && meta.history) alpha *= 0.55f;
        final int y = r.y, h = r.h;
        final int x = geo.indentFor(meta);

        if (t.isVanilla()) {
            g.fill(-4, y, k + 4 + 4, y + h, Colors.withAlpha(0x000000, Math.round(bgOpacity * fade * 255)));
        }
        if (hovered != null && r.message == hovered) {
            g.fill(-3, y, k + 3, y + h, t.isVanilla() ? Colors.withAlpha(0xFFFFFF, Math.round(fade * 0x18)) : Colors.scaleAlpha(p.surfaceHover(), fade * 0.7f));
        }
        if (meta != null && meta.mention) {
            g.fill(-3, y, k + 3, y + h, Colors.scaleAlpha(p.accent(), fade * 0.16f));
            g.fill(-4, y, -2, y + h, Colors.scaleAlpha(p.accent(), fade));
        }
        final GuiMessageTag tag = r.line.tag();
        if (tag != null && showsIndicator(tag) && !(meta != null && meta.mention)) {
            g.fill(-4, y, -2, y + h, Colors.withAlpha(tag.indicatorColor(), Math.round(fade * 255)));
        }

        if (content instanceof ChatRows.HeaderRow hr) {
            drawHeader(g, font, hr.meta(), y, h, alpha, k, geo, cfg, t, p, focused && cfg.hoverActions && hovered == r.message);
        } else if (content instanceof ChatRows.GapRow) {
            // nothing
        } else if (content instanceof ChatRows.AttachmentRow ar) {
            final String key = System.identityHashCode(ar.message()) + ":" + ar.attachment().id();
            if (drawnCards.add(key)) {
                final int yTop = y - ar.rowIndex() * lineH;
                AttachmentCards.draw(g, ar.attachment(), ar.message(), x, yTop + 1, k - x, ar.totalRows() * lineH - 1, alpha, f, slidePx, hovered == ar.message());
            }
        } else if (content instanceof ChatRows.TextRow tr) {
            if (geo.compact() && geo.heads() && meta != null && meta.hasSender() && tr.first() && tr.groupStart()) {
                SlateAvatar.draw(g, skinFor(meta), 0, y + (h - 8) / 2, 8, SlateAvatar.Status.NONE, alpha);
            }
            final int color = Colors.scaleAlpha(t.isVanilla() ? 0xFFFFFFFF : (meta != null && meta.history ? p.textMuted() : p.text()), alpha);
            g.drawString(font, tr.text(), x, y + h - 8, color, t.isVanilla());
            if (geo.compact() && cfg.timestamps && meta != null && meta.hasSender() && tr.first() && tr.groupStart()) {
                final String ts = ChatLayout.time(meta.timeMs);
                final int tw = font.width(ts);
                if (x + font.width(tr.text()) + 6 + tw <= k) {
                    g.drawString(font, ts, k - tw, y + h - 8, Colors.scaleAlpha(t.isVanilla() ? 0xFFA0A0A0 : p.textDim(), alpha), t.isVanilla());
                }
            }
        } else {
            // A line another mod inserted: draw it like vanilla would.
            g.drawString(font, content, 0, y + h - 8, Colors.withAlpha(0xFFFFFF, Math.round(alpha * 255)), true);
        }
    }

    /** {@code actionsShown}: the hover pill sits over the row's right end, so the history tag steps aside for it. */
    private static void drawHeader(final GuiGraphics g, final Font font, final ChatMeta.Meta meta, final int y, final int h, final float alpha,
                                   final int k, final ChatLayout.Geometry geo, final ChatConfig cfg, final Theme t, final Palette p,
                                   final boolean actionsShown) {
        int x = 0;
        if (geo.heads()) {
            SlateAvatar.draw(g, skinFor(meta), 0, y + (h - geo.avatar()) / 2, geo.avatar(), SlateAvatar.Status.NONE, alpha);
            x = geo.indent();
        }
        final int ty = y + (h - 8) / 2;
        final int nameColor = Colors.scaleAlpha(ChatLayout.nameColor(meta), alpha);
        final Component name = Component.literal(meta.sender);
        g.drawString(font, name, x, ty, nameColor, t.isVanilla());
        x += font.width(name) + 5;
        if (cfg.timestamps) {
            final String ts = ChatLayout.time(meta.timeMs);
            if (x + font.width(ts) <= k) g.drawString(font, ts, x, ty, Colors.scaleAlpha(t.isVanilla() ? 0xFFA0A0A0 : p.textDim(), alpha), t.isVanilla());
        }
        if (meta.history && !actionsShown) {
            final Component hist = Component.translatable("slate_chat.history.tag");
            final int hw = font.width(hist);
            if (k - hw > x + 40) g.drawString(font, hist, k - hw, ty, Colors.scaleAlpha(p.textDim(), alpha * 0.8f), t.isVanilla());
        }
    }

    private static ResourceLocation skinFor(final ChatMeta.Meta meta) {
        final UUID id = meta.uuid != null ? meta.uuid : SenderResolver.placeholderUuid(meta.sender);
        return SlateAvatar.skinFor(id, meta.sender);
    }

    private static boolean showsIndicator(final GuiMessageTag tag) {
        final String log = tag.logTag();
        if (log == null) return false;
        return !log.startsWith("System") && !"SlateHistory".equals(log) && !"SlateThread".equals(log);
    }

    // ------------------------------------------------------------------ chrome

    private static void drawScrollbar(final GuiGraphics g, final ChatAccess a, final List<RowLayout> rows, final int i1, final int page, final int k,
                                      final float f, final int slidePx, final Theme t, final Palette p) {
        final int maxScroll = ChatLayout.maxScroll(a);
        if (maxScroll <= 0) return;
        final int total = a.slate$lines().size();
        final int scroll = a.slate$scroll();
        final int x = k + 5;
        final int trackTop = i1 - page, trackH = page;
        final int thumbH = Math.max(8, Math.round(trackH * Math.min(1f, rows.size() / (float) Math.max(1, total))));
        final int thumbY = i1 - thumbH - Math.round((trackH - thumbH) * (scroll / (float) maxScroll));
        if (t.isVanilla()) {
            g.fill(x, trackTop, x + 2, i1, 0x80000000);
            g.fill(x, thumbY, x + 2, thumbY + thumbH, 0xFFC0C0C0);
        } else {
            SlateDraw.pixelRound(g, x, trackTop, 2, trackH, Colors.withAlpha(p.surfaceActive(), 0x90), 1);
            SlateDraw.pixelRound(g, x, thumbY, 2, thumbH, p.borderStrong(), 1);
        }
        ChatRenderState.scrollbar = new ChatRenderState.Scrollbar(
            Math.round((x + 4) * f), Math.round(trackTop * f) + slidePx, Math.round((x + 6) * f), Math.round(i1 * f) + slidePx,
            Math.round(thumbY * f) + slidePx, Math.round((thumbY + thumbH) * f) + slidePx, maxScroll);
    }

    private static void drawHoverActions(final GuiGraphics g, final List<RowLayout> rows, final GuiMessage hovered, final int k, final int pageTop,
                                         final float f, final int slidePx, final double cx, final double cy, final Theme t, final Palette p) {
        int top = Integer.MAX_VALUE;
        for (final RowLayout r : rows) if (r.message == hovered && r.y < top) top = r.y;
        if (top == Integer.MAX_VALUE) return;
        final ChatMeta.Meta meta = ChatMeta.of(hovered);
        final List<ChatRenderState.Action> actions = new ArrayList<>(3);
        if (meta.hasSender() && !meta.self) actions.add(ChatRenderState.Action.REPLY);
        actions.add(ChatRenderState.Action.COPY);
        if (!Attachment.links(meta.plain).isEmpty()) actions.add(ChatRenderState.Action.LINK);
        final int bs = 12, gap = 1;
        final int w = actions.size() * (bs + gap) + 3;
        final int x0 = k + 3 - w;
        final int y0 = Math.max(pageTop, top - 5);
        if (t.isVanilla()) {
            g.fill(x0, y0, x0 + w, y0 + bs + 4, 0xE0000000);
            SlateDraw.outline(g, x0, y0, w, bs + 4, 0xFFFFFFFF, 0);
        } else {
            SlateDraw.shadow(g, x0, y0, w, bs + 4, 0.4f);
            SlateDraw.pixelRound(g, x0, y0, w, bs + 4, p.surface(), t.radius());
            SlateDraw.outline(g, x0, y0, w, bs + 4, p.borderStrong(), t.radius());
        }
        int bx = x0 + 2;
        for (final ChatRenderState.Action act : actions) {
            final boolean over = cx >= bx && cx < bx + bs && cy >= y0 + 2 && cy < y0 + 2 + bs;
            if (over) SlateDraw.pixelRound(g, bx, y0 + 2, bs, bs, t.isVanilla() ? 0xFF404040 : p.surfaceHover(), t.radius() > 0 ? 2 : 0);
            final Icon icon = switch (act) {
                case REPLY -> Icon.REPLY;
                case COPY -> Icon.COPY;
                case LINK -> Icon.EXTERNAL;
                default -> Icon.ARROW_DOWN;
            };
            Icons.draw(g, icon, bx + 2, y0 + 4, 8, over ? p.accent() : (t.isVanilla() ? 0xFFE0E0E0 : p.textMuted()));
            ChatRenderState.actionRects.add(new ChatRenderState.ActionRect(
                Math.round((bx + 4) * f), Math.round((y0 + 2) * f) + slidePx, Math.round((bx + 4 + bs) * f), Math.round((y0 + 2 + bs) * f) + slidePx, act, hovered));
            bx += bs + gap;
        }
    }

    private static void drawNewMessagesPill(final GuiGraphics g, final Font font, final int k, final int i1, final float f, final int slidePx, final Theme t, final Palette p) {
        final Component text = Component.translatable("slate_chat.new_messages");
        final int w = font.width(text) + 18, h = 11;
        final int x = (k - w) / 2, y = i1 - h - 2;
        SlateDraw.shadow(g, x, y, w, h, 0.3f);
        SlateDraw.pixelRound(g, x, y, w, h, p.accent(), 3);
        Icons.draw(g, Icon.ARROW_DOWN, x + 3, y + 2, 8, p.accentText());
        g.drawString(font, text, x + 13, y + 2, p.accentText(), false);
        ChatRenderState.actionRects.add(new ChatRenderState.ActionRect(
            Math.round((x + 4) * f), Math.round(y * f) + slidePx, Math.round((x + 4 + w) * f), Math.round((y + h) * f) + slidePx, ChatRenderState.Action.SCROLL_BOTTOM, null));
    }

    /** "N new" pill at the closed chat's bottom-left, slides in from the left. */
    private static void drawUnreadBadge(final GuiGraphics g, final ChatConfig cfg, final Theme t, final Palette p, final Font font) {
        final int n = ChatRenderState.unread();
        final float v = ChatRenderState.unreadVisibility();
        if (!cfg.unreadBadge || n <= 0 || v <= 0.01f) return;
        final Component text = Component.translatable("slate_chat.unread", n);
        final int w = font.width(text) + 18, h = 11;
        final int x = 4 - Math.round((1 - v) * (w + 6)), y = g.guiHeight() - 40 + 4;
        SlateDraw.shadow(g, x, y, w, h, 0.3f);
        SlateDraw.pixelRound(g, x, y, w, h, p.accent(), t.isVanilla() ? 0 : 3);
        Icons.draw(g, Icon.CHAT, x + 3, y + 2, 8, p.accentText());
        g.drawString(font, text, x + 13, y + 2, p.accentText(), false);
    }

    // ------------------------------------------------------------------ hit tests used by the mixin

    /** Vanilla's {@code getClickedComponentStyleAt}: the style under the mouse (GUI px), if a text row is there. */
    @Nullable
    public static Style styleAt(final double mx, final double my) {
        final ChatRenderState.RowHit r = ChatRenderState.rowAt(mx, my);
        if (r == null || !r.textRow()) return null;
        final double cx = mx / r.scale() - 4 - r.textX();
        if (cx < 0) return null;
        final FormattedCharSequence text = ChatRows.text(r.line().content());
        return Minecraft.getInstance().font.getSplitter().componentStyleAtWidth(text, Mth.floor(cx));
    }

    /** Vanilla's {@code getMessageTagAt}: the tag when hovering a row's indicator strip. */
    @Nullable
    public static GuiMessageTag tagAt(final double mx, final double my) {
        final ChatRenderState.RowHit r = ChatRenderState.rowAt(mx, my);
        if (r == null || r.line().tag() == null || r.line().tag().text() == null) return null;
        final double cx = mx / r.scale() - 4;
        return cx < 0 ? r.line().tag() : null;
    }

    /** Vanilla's fade curve for a line of the given age (ticks). */
    public static double timeFactor(final int age) {
        double d = 1.0 - age / 200.0;
        d *= 10.0;
        d = Mth.clamp(d, 0.0, 1.0);
        return d * d;
    }

    private ChatRenderer() {}
}
