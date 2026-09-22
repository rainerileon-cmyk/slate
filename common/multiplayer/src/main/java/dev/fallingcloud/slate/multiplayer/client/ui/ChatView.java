package dev.fallingcloud.slate.multiplayer.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateContextMenu;
import dev.fallingcloud.slate.core.widget.SlateSpinner;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import dev.fallingcloud.slate.multiplayer.client.Friend;
import dev.fallingcloud.slate.multiplayer.client.MediaStore;
import dev.fallingcloud.slate.multiplayer.client.SocialClient;
import dev.fallingcloud.slate.multiplayer.client.ThreadModel;
import dev.fallingcloud.slate.multiplayer.social.ChatMessage;
import dev.fallingcloud.slate.multiplayer.voice.clips.VoiceClip;
import dev.fallingcloud.slate.multiplayer.voice.clips.VoicePlayer;
import dev.fallingcloud.slate.multiplayer.voice.clips.VoiceSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * The message list of one thread: grouped messages with heads, timestamps, day separators, an unread
 * marker, image and voice attachments, a typing indicator, smooth scrolling that sticks to the bottom
 * and loads older history when scrolled to the top. Both skins.
 */
public final class ChatView extends SlateWidget {

    private static final int HEAD = 16, INDENT = 24, PAD = 6, LINE = 10, THUMB_W = 200, THUMB_H = 120, VOICE_W = 130, VOICE_H = 18;

    private enum Kind { DAY, HEADER, TEXT, IMAGE, VOICE, UNREAD, TYPING }

    private record Item(Kind kind, int y, int h, @Nullable ChatMessage msg, @Nullable List<FormattedCharSequence> lines, String label) {}

    @Nullable private ThreadModel thread;
    private final List<Item> items = new ArrayList<>();
    private int contentH;
    private String layoutKey = "";
    private final Anim scroll = new Anim(0, 160, Ease.OUT_CUBIC);
    private boolean stickBottom = true;
    private long lastHistoryRequestMs;
    private int unreadAtOpen;

    public ChatView(final int x, final int y, final int w, final int h) {
        super(x, y, w, h, Component.literal("Messages"));
        this.silent();
    }

    public void setThread(@Nullable final ThreadModel t) {
        thread = t;
        unreadAtOpen = t == null ? 0 : t.unread;
        layoutKey = "";
        stickBottom = true;
        scroll.snap(0);
    }

    /** Force a re-layout (new message, history page, typing change). */
    public void invalidate() { layoutKey = ""; }

    private int maxScroll() { return Math.max(0, contentH - getHeight()); }

    // ------------------------------------------------------------------ layout

    private void layout() {
        if (thread == null) { items.clear(); contentH = 0; return; }
        final long now = System.currentTimeMillis();
        final List<UUID> typing = thread.typingNow(now);
        final String key = thread.key + "|" + thread.messages.size() + "|" + (thread.messages.isEmpty() ? "" : thread.last().id()) + "|" + getWidth() + "|" + typing.size() + "|" + unreadAtOpen
            + "|" + (thread.messages.isEmpty() ? "" : thread.messages.get(0).id());
        if (key.equals(layoutKey)) return;
        layoutKey = key;
        items.clear();
        final int textW = getWidth() - INDENT - PAD * 2 - 6;
        int y = PAD;
        ChatMessage prev = null;
        final int firstUnread = thread.messages.size() - Math.min(unreadAtOpen, thread.messages.size());
        for (int i = 0; i < thread.messages.size(); i++) {
            final ChatMessage m = thread.messages.get(i);
            if (prev == null || !UiUtil.sameDay(prev.atMs(), m.atMs())) {
                items.add(new Item(Kind.DAY, y, 14, null, null, UiUtil.dayLabel(m.atMs())));
                y += 14;
            }
            if (unreadAtOpen > 0 && i == firstUnread && !m.from().uuid().equals(SocialClient.get().selfUuid())) {
                items.add(new Item(Kind.UNREAD, y, 10, null, null, "New"));
                y += 10;
            }
            final boolean newGroup = prev == null || !prev.from().uuid().equals(m.from().uuid()) || m.atMs() - prev.atMs() > 5 * 60_000 || !UiUtil.sameDay(prev.atMs(), m.atMs());
            if (newGroup) {
                items.add(new Item(Kind.HEADER, y, HEAD + 2, m, null, ""));
                y += HEAD + 2;
            }
            if (!m.text().isEmpty()) {
                final List<FormattedCharSequence> lines = new ArrayList<>();
                for (final String para : m.text().split("\n")) lines.addAll(SlateDraw.font().split(Component.literal(para), textW));
                final int h = lines.size() * LINE + 2;
                items.add(new Item(Kind.TEXT, y, h, m, lines, ""));
                y += h;
            }
            if (m.hasAttachment()) {
                if ("voice".equals(m.attachKind())) { items.add(new Item(Kind.VOICE, y, VOICE_H + 4, m, null, "")); y += VOICE_H + 4; }
                else { items.add(new Item(Kind.IMAGE, y, THUMB_H + 4, m, null, "")); y += THUMB_H + 4; }
            }
            prev = m;
        }
        if (!typing.isEmpty()) {
            final List<String> names = new ArrayList<>();
            for (final UUID u : typing) { final Friend f = SocialClient.get().friend(u); names.add(f != null ? f.display() : "Someone"); }
            items.add(new Item(Kind.TYPING, y, 12, null, null, names.size() == 1 ? names.get(0) + " is typing" : String.join(", ", names) + " are typing"));
            y += 12;
        }
        contentH = y + PAD;
        if (stickBottom) scroll.snap(maxScroll());
        else scroll.snap((float) Math.min(scroll.target(), maxScroll()));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (!contains(mouseX, mouseY)) return false;
        layout();
        final float target = (float) Math.max(0, Math.min(maxScroll(), scroll.target() - scrollY * 24));
        scroll.set(target);
        stickBottom = target >= maxScroll() - 1;
        if (target <= 0 && thread != null && thread.more && System.currentTimeMillis() - lastHistoryRequestMs > 800) {
            lastHistoryRequestMs = System.currentTimeMillis();
            SocialClient.get().requestHistory(thread.key, 50);
        }
        return true;
    }

    @Nullable
    private Item itemAt(final double my) {
        final int rel = (int) (my - getY() + scroll.get());
        for (final Item it : items) if (rel >= it.y && rel < it.y + it.h) return it;
        return null;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.visible || !this.active || !contains(mouseX, mouseY)) return false;
        layout();
        final Item it = itemAt(mouseY);
        if (it == null || it.msg == null) return false;
        if (button == 1) {
            final List<MenuPopup.Item> menu = new ArrayList<>();
            if (!it.msg.text().isEmpty()) menu.add(MenuPopup.Item.of(Component.translatable("slate_multiplayer.chat.copy"), Icon.COPY, () -> Minecraft.getInstance().keyboardHandler.setClipboard(it.msg.text())));
            menu.add(MenuPopup.Item.of(Component.literal(it.msg.from().display()), Icon.USER, () -> PlayerCardPopup.open(it.msg.from().uuid(), it.msg.from().name(), (int) mouseX, (int) mouseY)));
            SlateContextMenu.open(mouseX, mouseY, menu);
            return true;
        }
        if (button != 0) return false;
        switch (it.kind) {
            case HEADER -> { PlayerCardPopup.open(it.msg.from().uuid(), it.msg.from().name(), (int) mouseX, (int) mouseY); return true; }
            case IMAGE -> {
                final Optional<Textures.Loaded> tex = MediaStore.texture(it.msg.attachId());
                tex.ifPresent(t -> ImageViewPopup.open(t, it.msg.from().display() + "  ·  " + UiUtil.clock(it.msg.atMs())));
                return true;
            }
            case VOICE -> { toggleVoice(it.msg); return true; }
            default -> { return false; }
        }
    }

    private void toggleVoice(final ChatMessage m) {
        final byte[] bytes = MediaStore.get(m.attachId());
        if (bytes == null) { MediaStore.request(m.attachId()); return; }
        if (m.attachId().equals(VoicePlayer.playingId())) { VoicePlayer.stop(); return; }
        final VoiceClip clip = VoiceClip.deserialize(bytes);
        final short[] pcm = clip == null ? null : VoiceSupport.decode(clip);
        if (pcm == null) { dev.fallingcloud.slate.multiplayer.client.Notifications.plain("Cannot play voice clip", VoiceSupport.available() ? "Bad clip" : "Simple Voice Chat is not installed"); return; }
        VoicePlayer.toggle(m.attachId(), pcm);
    }

    // ------------------------------------------------------------------ render

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
        final int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        if (vanilla) SlateDraw.vanillaListBackground(g, x, y, w, h, Minecraft.getInstance().level != null);
        else SlateDraw.panel(g, x, y, w, h, p.bg2(), p.border());
        if (thread == null) {
            SlateDraw.textCentered(g, Component.translatable("slate_multiplayer.chat.select"), x + w / 2, y + h / 2 - 4, p.textDim());
            return;
        }
        layout();
        if (items.isEmpty()) {
            SlateDraw.textCentered(g, Component.translatable("slate_multiplayer.chat.empty"), x + w / 2, y + h / 2 - 4, p.textDim());
            return;
        }
        final int s = Math.round(scroll.get());
        final Item hover = this.isHovered() ? itemAt(mouseY) : null;
        SlateDraw.scissor(g, x, y, w, h);
        for (final Item it : items) {
            final int iy = y + it.y - s;
            if (iy + it.h < y || iy > y + h) continue;
            switch (it.kind) {
                case DAY -> {
                    final int tw = SlateDraw.width(it.label);
                    SlateDraw.hline(g, x + PAD, iy + 7, w - PAD * 2, p.border());
                    g.fill(x + (w - tw) / 2 - 4, iy + 2, x + (w + tw) / 2 + 4, iy + 12, vanilla ? 0xFF000000 : p.bg2());
                    g.drawString(SlateDraw.font(), it.label, x + (w - tw) / 2, iy + 3, p.textDim(), vanilla);
                }
                case UNREAD -> {
                    SlateDraw.hline(g, x + PAD, iy + 5, w - PAD * 2 - 24, p.accent());
                    g.drawString(SlateDraw.font(), it.label, x + w - PAD - 20, iy + 1, p.accent(), vanilla);
                }
                case HEADER -> {
                    final ChatMessage m = it.msg;
                    final Friend f = SocialClient.get().friend(m.from().uuid());
                    final boolean own = m.from().uuid().equals(SocialClient.get().selfUuid());
                    UiUtil.drawHead(g, m.from().uuid(), m.from().name(), x + PAD, iy + 1, HEAD, f == null ? null : f.presence, 1f);
                    final String name = own ? "You" : f != null ? f.display() : m.from().display();
                    final int nameColor = own ? p.accent() : p.text();
                    g.drawString(SlateDraw.font(), name, x + PAD + INDENT, iy + 4, hover == it ? Colors.brighten(nameColor, 0.2f) : nameColor, vanilla);
                    g.drawString(SlateDraw.font(), UiUtil.clock(m.atMs()), x + PAD + INDENT + SlateDraw.width(name) + 6, iy + 4, p.textDim(), vanilla);
                }
                case TEXT -> {
                    int ly = iy + 1;
                    for (final FormattedCharSequence line : it.lines) { g.drawString(SlateDraw.font(), line, x + PAD + INDENT, ly, p.text(), vanilla); ly += LINE; }
                }
                case IMAGE -> drawImage(g, it, x + PAD + INDENT, iy + 2, hover == it);
                case VOICE -> drawVoice(g, it, x + PAD + INDENT, iy + 2, hover == it);
                case TYPING -> {
                    final int dots = (int) ((Clock.nowMs() / 400) % 4);
                    g.drawString(SlateDraw.font(), it.label + ".".repeat(dots), x + PAD + INDENT, iy + 2, p.textMuted(), vanilla);
                }
            }
        }
        SlateDraw.unscissor(g);
        if (maxScroll() > 0) {
            final int bx = x + w - 5;
            final int barH = Math.max(12, (int) ((long) h * h / Math.max(1, contentH)));
            final int barY = y + (int) ((h - barH) * (scroll.get() / Math.max(1, maxScroll())));
            SlateDraw.pixelRound(g, bx, barY, 3, barH, vanilla ? 0xFFC0C0C0 : p.borderStrong(), 1);
        }
        if (thread.historyPending) SlateSpinner.draw(g, x + w / 2 - 5, y + 4, 10, p.accent());
    }

    private void drawImage(final GuiGraphics g, final Item it, final int x, final int y, final boolean hover) {
        final Palette p = Theme.current().palette();
        final Optional<Textures.Loaded> tex = MediaStore.texture(it.msg.attachId());
        if (tex.isEmpty()) {
            SlateDraw.panel(g, x, y, THUMB_W, THUMB_H, p.surface(), p.border());
            SlateSpinner.draw(g, x + THUMB_W / 2 - 6, y + THUMB_H / 2 - 6, 12, p.textMuted());
            g.drawString(SlateDraw.font(), it.msg.attachKind() + "...", x + 6, y + THUMB_H - 12, p.textDim(), false);
            return;
        }
        final Textures.Loaded l = tex.get();
        final float s = Math.min((float) THUMB_W / l.width(), (float) THUMB_H / l.height());
        final int dw = Math.max(1, Math.round(l.width() * Math.min(1f, s))), dh = Math.max(1, Math.round(l.height() * Math.min(1f, s)));
        SlateDraw.outline(g, x - 1, y - 1, dw + 2, dh + 2, hover ? p.borderStrong() : p.border(), 0);
        RenderSystem.enableBlend();
        g.blit(l.id(), x, y, dw, dh, 0, 0, l.width(), l.height(), l.width(), l.height());
        RenderSystem.disableBlend();
        if (hover) Icons.draw(g, Icon.FULLSCREEN, x + dw - 12, y + 2, 10, 0xFFFFFFFF);
    }

    private void drawVoice(final GuiGraphics g, final Item it, final int x, final int y, final boolean hover) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean playing = it.msg.attachId().equals(VoicePlayer.playingId());
        final boolean have = MediaStore.has(it.msg.attachId());
        SlateDraw.pixelRound(g, x, y, VOICE_W, VOICE_H, hover ? p.surfaceHover() : p.surface(), t.radius());
        SlateDraw.outline(g, x, y, VOICE_W, VOICE_H, playing ? p.accent() : p.border(), t.radius());
        Icons.draw(g, playing ? Icon.PAUSE : Icon.PLAY, x + 4, y + 3, 12, have ? p.text() : p.textDim());
        final int barX = x + 20, barW = VOICE_W - 24 - 30;
        SlateDraw.rect(g, barX, y + 8, barW, 2, p.surfaceActive());
        if (playing) SlateDraw.rect(g, barX, y + 8, Math.round(barW * VoicePlayer.progress()), 2, p.accent());
        long dur = 0;
        for (final String part : it.msg.attachMeta().split(";")) if (part.startsWith("ms=")) { try { dur = Long.parseLong(part.substring(3)); } catch (final NumberFormatException ignored) {} }
        final String d = dur > 0 ? "%d:%02d".formatted(dur / 60000, (dur / 1000) % 60) : "voice";
        g.drawString(SlateDraw.font(), d, x + VOICE_W - 4 - SlateDraw.width(d), y + 5, p.textMuted(), false);
    }
}
