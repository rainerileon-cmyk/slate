package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.core.client.media.MediaDraw;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.media.Attachment;
import dev.fallingcloud.slate.core.media.MediaCache;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.Locale;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Draws one attachment card into the rows its {@link ChatRows.AttachmentRow}s reserved and registers its
 * click rectangle for the ChatScreen mixin. Called from the chat renderer with the chat pose active
 * (scale, then translate(4,0)), so drawing happens in chat space; the click rect converts to GUI space
 * with the same constants plus the slide offset, keeping the mouse side free of chat maths.
 */
public final class AttachmentCards {

    public static final int MAX_WIDTH = 220;

    /**
     * @param x0     left edge in chat space
     * @param yTop   top of the card block in chat space
     * @param width  usable width in chat space
     * @param height card block height (rows * lineHeight)
     * @param alpha  the row's fade (0..1)
     * @param scale  the chat scale, for the click rect
     * @param slidePx GUI-pixel slide currently applied to the chat
     */
    public static void draw(final GuiGraphics g, final Attachment att, final GuiMessage message, final int x0, final int yTop,
                            final int width, final int height, final float alpha, final float scale, final int slidePx, final boolean hovered) {
        if (alpha <= 0.02f || width < 20 || height < 8) return;
        final int x1 = x0 + Math.min(width - 2, MAX_WIDTH);
        final int y1 = yTop + height - 2;
        int drawnX1 = x1;
        switch (att.kind()) {
            case VOICE -> drawVoice(g, att, x0, yTop, x1, y1, alpha, hovered);
            case IMAGE_BLOB, IMAGE_URL -> drawnX1 = drawImage(g, att, x0, yTop, x1, y1, alpha, hovered);
            case VIDEO_URL -> drawVideo(g, att, x0, yTop, x1, y1, alpha, hovered);
            case FILE_BLOB -> drawFile(g, att, x0, yTop, x1, y1, alpha, hovered);
        }
        ChatRenderState.clickRects.add(new ChatRenderState.ClickRect(
            Math.round((x0 + 4) * scale), Math.round(yTop * scale) + slidePx,
            Math.round((drawnX1 + 4) * scale), Math.round(y1 * scale) + slidePx, att, message));
    }

    private static void pill(final GuiGraphics g, final int x0, final int y0, final int x1, final int y1, final float alpha, final boolean hovered) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        if (t.isVanilla()) {
            g.fill(x0, y0, x1, y1, Colors.withAlpha(0x000000, Math.round(alpha * 0xA0)));
            SlateDraw.outline(g, x0, y0, x1 - x0, y1 - y0, Colors.withAlpha(hovered ? 0xFFFFFF : 0x606060, Math.round(alpha * 255)), 0);
        } else {
            SlateDraw.pixelRound(g, x0, y0, x1 - x0, y1 - y0, Colors.scaleAlpha(hovered ? p.surfaceHover() : p.surface(), alpha), t.radius());
            SlateDraw.outline(g, x0, y0, x1 - x0, y1 - y0, Colors.scaleAlpha(hovered ? p.borderStrong() : p.border(), alpha), t.radius());
        }
    }

    // ------------------------------------------------------------------ voice

    private static void drawVoice(final GuiGraphics g, final Attachment att, final int x0, final int yTop, final int x1, final int y1, final float alpha, final boolean hovered) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        pill(g, x0, yTop, x1, y1, alpha, hovered);
        final MediaCache.Entry entry = MediaCache.get(att);
        final boolean playing = att.id().equals(MultiplayerBridge.playingId());
        final int cy = (yTop + y1) / 2;
        final int fg = Colors.scaleAlpha(t.isVanilla() ? 0xFFFFFFFF : p.text(), alpha);
        final int muted = Colors.scaleAlpha(t.isVanilla() ? 0xFFB0B0B0 : p.textMuted(), alpha);
        final int accent = Colors.scaleAlpha(p.accent(), alpha);
        // Play / stop button
        final int bx = x0 + 3, bs = y1 - yTop - 6;
        SlateDraw.pixelRound(g, bx, yTop + 3, bs, bs, playing ? accent : Colors.scaleAlpha(t.isVanilla() ? 0xFF303030 : p.surfaceActive(), alpha), t.radius() > 0 ? 2 : 0);
        Icons.draw(g, playing ? Icon.STOP : Icon.PLAY, bx + (bs - 8) / 2, yTop + 3 + (bs - 8) / 2, 8, playing ? Colors.scaleAlpha(p.accentText(), alpha) : fg);
        final int sec = Math.round(att.durationMs() / 1000.0f);
        final String label = "%d:%02d".formatted(sec / 60, sec % 60);
        final int tx = bx + bs + 5;
        g.drawString(SlateDraw.font(), Component.translatable("slate_chat.card.voice"), tx, yTop + 3, fg, t.isVanilla());
        g.drawString(SlateDraw.font(), label, x1 - 4 - SlateDraw.width(label), yTop + 3, muted, t.isVanilla());
        // Progress bar
        final int barX0 = tx, barX1 = x1 - 4, barY = y1 - 5;
        SlateDraw.rect(g, barX0, barY, barX1 - barX0, 2, Colors.scaleAlpha(t.isVanilla() ? 0xFF505050 : p.surfaceActive(), alpha));
        if (playing) SlateDraw.rect(g, barX0, barY, Math.round((barX1 - barX0) * MultiplayerBridge.progress()), 2, accent);
        if (!entry.ready()) {
            final Component status = MediaDraw.statusText(entry);
            g.drawString(SlateDraw.font(), status, barX0, barY - 9, muted, t.isVanilla());
        } else if (!playing && cy > 0) {
            // Static waveform-ish ticks so the card reads as audio at a glance.
            final int n = Math.max(4, (barX1 - barX0) / 3);
            for (int i = 0; i < n; i++) {
                final int h = 1 + (int) ((Math.sin(i * 1.7 + att.id().hashCode()) + 1) * 1.5);
                SlateDraw.rect(g, barX0 + i * 3, barY - h, 2, h, Colors.scaleAlpha(t.isVanilla() ? 0xFF808080 : p.textDim(), alpha * 0.8f));
            }
        }
    }

    // ------------------------------------------------------------------ image

    /** @return the right edge actually used (images narrower than the block shrink the click rect) */
    private static int drawImage(final GuiGraphics g, final Attachment att, final int x0, final int yTop, final int x1, final int y1, final float alpha, final boolean hovered) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final MediaCache.Entry entry = MediaCache.get(att);
        if (!entry.ready() || entry.frames.isEmpty()) {
            final int w = Math.min(x1 - x0, 120);
            pill(g, x0, yTop, x0 + w, y1, alpha, hovered);
            final Component text = MediaDraw.statusText(entry);
            final int muted = Colors.scaleAlpha(t.isVanilla() ? 0xFFB0B0B0 : p.textMuted(), alpha);
            Icons.draw(g, att.isGif() ? Icon.GIF : Icon.IMAGE, x0 + 4, yTop + 3, 8, muted);
            g.drawString(SlateDraw.font(), SlateDraw.truncate(text, w - 18), x0 + 15, yTop + 3, muted, t.isVanilla());
            if (entry.status == MediaCache.Status.LOADING) MediaDraw.drawPlaceholder(g, entry, x0, yTop + 12, w, y1 - yTop - 12, alpha);
            return x0 + w;
        }
        final int maxW = x1 - x0 - 2, maxH = y1 - yTop - 2;
        final int[] s = MediaDraw.fit(entry.width, entry.height, maxW, maxH, true);
        final int w = s[0], h = s[1];
        final int frameColor = Colors.scaleAlpha(t.isVanilla() ? (hovered ? 0xFFFFFFFF : 0xFF000000) : (hovered ? p.borderStrong() : p.border()), alpha);
        if (t.isVanilla()) g.fill(x0, yTop, x0 + w + 2, yTop + h + 2, Colors.withAlpha(0x000000, Math.round(alpha * 0xC0)));
        else SlateDraw.pixelRound(g, x0, yTop, w + 2, h + 2, Colors.scaleAlpha(p.bg(), alpha), t.radius() > 0 ? 2 : 0);
        MediaDraw.drawFrame(g, entry, x0 + 1, yTop + 1, w, h, alpha);
        SlateDraw.outline(g, x0, yTop, w + 2, h + 2, frameColor, t.radius() > 0 ? 2 : 0);
        if (entry.isGif()) {
            // Tiny "GIF" chip like Discord's, bottom-left.
            final String chip = "GIF";
            final int cw = SlateDraw.width(chip) + 4;
            SlateDraw.pixelRound(g, x0 + 3, yTop + h - 8, cw, 8, Colors.withAlpha(0x000000, Math.round(alpha * 0xA0)), 1);
            g.drawString(SlateDraw.font(), chip, x0 + 5, yTop + h - 8, Colors.withAlpha(0xFFFFFF, Math.round(alpha * 0xE0)), false);
        }
        return x0 + w + 2;
    }

    // ------------------------------------------------------------------ video

    private static void drawVideo(final GuiGraphics g, final Attachment att, final int x0, final int yTop, final int x1, final int y1, final float alpha, final boolean hovered) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        pill(g, x0, yTop, x1, y1, alpha, hovered);
        final int fg = Colors.scaleAlpha(t.isVanilla() ? 0xFFFFFFFF : p.text(), alpha);
        final int muted = Colors.scaleAlpha(t.isVanilla() ? 0xFFB0B0B0 : p.textMuted(), alpha);
        final int bs = Math.min(16, y1 - yTop - 6);
        final int bx = x0 + 4, by = yTop + (y1 - yTop - bs) / 2;
        SlateDraw.pixelRound(g, bx, by, bs, bs, Colors.scaleAlpha(p.accent(), alpha), t.radius() > 0 ? 2 : 0);
        Icons.draw(g, Icon.PLAY, bx + (bs - 8) / 2, by + (bs - 8) / 2, 8, Colors.scaleAlpha(p.accentText(), alpha));
        String host = att.url();
        try { host = java.net.URI.create(att.url()).getHost(); } catch (final Exception ignored) {}
        final int tx = bx + bs + 6;
        g.drawString(SlateDraw.font(), Component.translatable("slate_chat.card.video"), tx, yTop + 4, fg, t.isVanilla());
        g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(host == null ? "" : host.toLowerCase(Locale.ROOT)), x1 - tx - 4), tx, yTop + 14, muted, t.isVanilla());
    }

    // ------------------------------------------------------------------ file

    private static void drawFile(final GuiGraphics g, final Attachment att, final int x0, final int yTop, final int x1, final int y1, final float alpha, final boolean hovered) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        pill(g, x0, yTop, x1, y1, alpha, hovered);
        final MediaCache.Entry entry = MediaCache.get(att);
        final int fg = Colors.scaleAlpha(t.isVanilla() ? 0xFFFFFFFF : p.text(), alpha);
        final int muted = Colors.scaleAlpha(t.isVanilla() ? 0xFFB0B0B0 : p.textMuted(), alpha);
        Icons.draw(g, entry.ready() ? Icon.DOWNLOAD : Icon.ATTACH, x0 + 5, yTop + (y1 - yTop - 10) / 2, 10, entry.ready() ? Colors.scaleAlpha(p.accent(), alpha) : muted);
        final String name = att.name().isEmpty() ? entry.name : att.name();
        g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(name), x1 - x0 - 24), x0 + 19, yTop + 3, fg, t.isVanilla());
        final Component sub = entry.ready() && entry.bytes != null ? Component.translatable("slate_chat.card.file_size", entry.bytes.length / 1024) : MediaDraw.statusText(entry);
        g.drawString(SlateDraw.font(), SlateDraw.truncate(sub, x1 - x0 - 24), x0 + 19, y1 - 11, muted, t.isVanilla());
    }

    private AttachmentCards() {}
}
