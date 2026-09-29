package dev.fallingcloud.slate.multiplayer.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateBadge;
import dev.fallingcloud.slate.core.widget.SlateContextMenu;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateList;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import dev.fallingcloud.slate.core.media.ClipboardImages;
import dev.fallingcloud.slate.multiplayer.client.FilePicker;
import dev.fallingcloud.slate.multiplayer.client.Friend;
import dev.fallingcloud.slate.multiplayer.client.ImageDecoding;
import dev.fallingcloud.slate.multiplayer.client.ImageEncoding;
import dev.fallingcloud.slate.multiplayer.client.Notifications;
import dev.fallingcloud.slate.multiplayer.client.SocialClient;
import dev.fallingcloud.slate.multiplayer.client.ThreadModel;
import dev.fallingcloud.slate.multiplayer.social.ChatMessage;
import dev.fallingcloud.slate.multiplayer.social.GroupInfo;
import dev.fallingcloud.slate.multiplayer.voice.clips.VoiceClip;
import dev.fallingcloud.slate.multiplayer.voice.clips.VoiceSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * Messages: the thread list on the left (heads, last message, unread badge, time), the chat view on the
 * right with the input bar (Enter sends, Shift+Enter newline, Ctrl+V pastes an image, attach button,
 * voice clip button when Simple Voice Chat is present). A pending image (from Menu's gallery or a paste)
 * shows as a chip above the input until sent.
 */
final class MessagesPage extends FriendsHubScreen.HubPage {

    private static final int HEADER_H = 16;

    @Nullable private String selectedKey;
    private SlateList<ThreadModel> list;
    private ChatView view;
    private ChatInput input;
    private SlateIconButton micButton;
    private Rect rightRect;
    @Nullable private byte[] pendingImage;
    private String pendingKind = "image";
    @Nullable private Textures.Loaded pendingThumb;
    private boolean recording;
    private long recordingSinceMs;

    MessagesPage(final FriendsHubScreen screen) {
        super(screen, "messages", UiUtil.t("page.messages"), Icon.CHAT);
    }

    @Override
    public int badge() { return SocialClient.get().unreadTotal(); }

    @Override
    public void build(final SidebarScreen s, final Rect area) {
        final String pending = FriendsHubScreen.takePendingThread();
        if (pending != null) selectedKey = pending;
        final Path share = FriendsHubScreen.takePendingShare();
        if (share != null) loadShare(share);

        final int leftW = Math.max(110, Math.min(190, area.w() * 2 / 5));
        rightRect = new Rect(area.x() + leftW + 8, area.y(), area.w() - leftW - 8, area.h());
        list = s.addPageWidget(new SlateList<ThreadModel>(area.x(), area.y(), leftW, area.h(), 30, new ThreadRow()).gap(2).emptyText(UiUtil.t("messages.empty")));
        list.onSelect(t -> select(t.key));
        list.onRightClick(t -> {
            final List<MenuPopup.Item> items = new ArrayList<>();
            items.add(MenuPopup.Item.of(UiUtil.t("messages.mark_read"), Icon.CHECK, () -> SocialClient.get().markRead(t.key)));
            if (!t.isGroup()) {
                final UUID other = t.other(SocialClient.get().selfUuid());
                final Friend f = other == null ? null : SocialClient.get().friend(other);
                if (f != null) { items.add(MenuPopup.Item.sep()); items.addAll(UiUtil.friendMenu(f)); }
            }
            final Minecraft mc = Minecraft.getInstance();
            SlateContextMenu.open(mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth(),
                mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight(), items);
        });

        final int inputH = ChatInput.LINE + ChatInput.PAD_Y * 2;
        final int inputY = rightRect.bottom() - inputH;
        final boolean voice = VoiceSupport.available();
        final int buttons = voice ? 3 : 2;
        final int inputW = rightRect.w() - buttons * 22;
        view = s.addPageWidget(new ChatView(rightRect.x(), rightRect.y() + HEADER_H, rightRect.w(), inputY - 4 - rightRect.y() - HEADER_H));
        input = s.addPageWidget(new ChatInput(rightRect.x(), inputY, inputW)
            .placeholder(UiUtil.t("chat.placeholder"))
            .onSend(this::send)
            .onPasteImage(this::pasteImage)
            .onChange(v -> { if (selectedKey != null && !v.isEmpty()) SocialClient.get().sendTyping(selectedKey, true); }));
        int bx = rightRect.right() - buttons * 22 + 2;
        s.addPageWidget(new SlateIconButton(bx, inputY, 20, Icon.ATTACH, UiUtil.t("chat.attach"), this::attach));
        bx += 22;
        if (voice) {
            micButton = s.addPageWidget(new SlateIconButton(bx, inputY, 20, Icon.MIC, UiUtil.t("chat.voice"), this::toggleRecording));
            bx += 22;
        }
        s.addPageWidget(new SlateIconButton(bx, inputY, 20, Icon.SEND, UiUtil.t("chat.send"), this::send));
        s.addPageWidget(new StatusStrip());
        refreshList();
        if (selectedKey != null) {
            final ThreadModel t = SocialClient.get().thread(selectedKey);
            view.setThread(t);
            SocialClient.get().setViewing(selectedKey);
            if (t.messages.size() < 20) SocialClient.get().requestHistory(selectedKey, 50);
        }
        input.setFocused(selectedKey != null);
    }

    private void select(final String key) {
        if (key.equals(selectedKey)) return;
        selectedKey = key;
        final ThreadModel t = SocialClient.get().thread(key);
        view.setThread(t);
        SocialClient.get().setViewing(key);
        if (t.messages.size() < 20) SocialClient.get().requestHistory(key, 50);
        input.setFocused(true);
    }

    @Override
    public void onHide() {
        SocialClient.get().setViewing(null);
        if (selectedKey != null) SocialClient.get().sendTyping(selectedKey, false);
        if (recording) { VoiceSupport.stopRecording(); recording = false; }
        dropPending();
    }

    @Override
    void onModelChanged() { refreshList(); }

    @Override
    void onThreadChanged(final String key) {
        if (key.equals(selectedKey)) view.invalidate();
        refreshList();
    }

    private void refreshList() {
        final List<ThreadModel> items = new ArrayList<>();
        for (final ThreadModel t : SocialClient.get().threads()) if (!t.messages.isEmpty() || t.key.equals(selectedKey) || t.isGroup()) items.add(t);
        items.sort(Comparator.comparingLong((ThreadModel t) -> t.last() == null ? 0 : t.last().atMs()).reversed());
        list.items(items);
        if (selectedKey != null) for (int i = 0; i < items.size(); i++) if (items.get(i).key.equals(selectedKey)) { list.select(i); break; }
    }

    // ------------------------------------------------------------------ sending

    private void send() {
        if (selectedKey == null) return;
        final String text = input.value();
        if (pendingImage != null) {
            SocialClient.get().sendAttachment(selectedKey, pendingKind, pendingImage, 0, "image".equals(pendingKind) ? "mime=image/jpeg" : "mime=image/gif", text);
            dropPending();
            input.clear();
            return;
        }
        if (text.isBlank()) return;
        SocialClient.get().sendChat(selectedKey, text);
        input.clear();
    }

    private void attach() {
        FilePicker.pickImage((bytes, kind, name) -> setPending(bytes, kind), err -> Notifications.plain(err, null));
    }

    private void pasteImage() {
        final int max = Math.max(64, MultiplayerConfigs.client().imageMaxKb) * 1024;
        ClipboardImages.tryPaste(max, bytes -> setPending(bytes, "image"), () -> Notifications.plain("Could not read the clipboard image", null));
    }

    private void loadShare(final Path file) {
        try {
            final byte[] raw = Files.readAllBytes(file);
            final int max = Math.max(64, MultiplayerConfigs.client().imageMaxKb) * 1024;
            final byte[] fit = ImageEncoding.shrinkToFit(raw, max);
            if (fit == null) Notifications.plain("That image is too large to send", null); else setPending(fit, "image");
        } catch (final Exception e) {
            Notifications.plain("Could not read " + file.getFileName(), null);
        }
    }

    private void setPending(final byte[] bytes, final String kind) {
        dropPending();
        pendingImage = bytes;
        pendingKind = kind;
        pendingThumb = ImageDecoding.decodeNow(bytes, "chip").orElse(null);
        if (input != null) input.setFocused(true);
    }

    private void dropPending() {
        pendingImage = null;
        if (pendingThumb != null) { Textures.release(pendingThumb); pendingThumb = null; }
    }

    private void toggleRecording() {
        if (selectedKey == null) return;
        if (!recording) {
            recording = VoiceSupport.startRecording(Math.max(5, MultiplayerConfigs.client().voiceClipMaxSeconds));
            recordingSinceMs = System.currentTimeMillis();
            if (!recording) Notifications.plain("Microphone unavailable", VoiceSupport.lastError());
            micButton.setIcon(recording ? Icon.STOP : Icon.MIC).toggled(recording);
            return;
        }
        recording = false;
        micButton.setIcon(Icon.MIC).toggled(false);
        final VoiceClip clip = VoiceSupport.stopRecording();
        if (clip == null) { Notifications.plain("Clip too short", null); return; }
        SocialClient.get().sendAttachment(selectedKey, "voice", clip.serialize(), clip.durationMs(), "ms=" + clip.durationMs(), "");
    }

    @Override
    public void tick() {
        if (recording && System.currentTimeMillis() - recordingSinceMs > Math.max(5, MultiplayerConfigs.client().voiceClipMaxSeconds) * 1000L) toggleRecording();
    }

    // ------------------------------------------------------------------ drawing

    /** The thread header (title + presence line) sits in the strip above the chat view. */
    @Override
    public void render(final SidebarScreen s, final GuiGraphics g, final Rect area, final int mouseX, final int mouseY, final float partialTick) {
        if (rightRect == null || selectedKey == null) return;
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final ThreadModel th = SocialClient.get().thread(selectedKey);
        final String title = SocialClient.get().threadTitle(th);
        String sub;
        if (th.isGroup()) {
            final GroupInfo gi = SocialClient.get().group(th.groupId());
            sub = gi == null ? "" : gi.members().size() + " members";
        } else {
            final UUID other = th.other(SocialClient.get().selfUuid());
            final Friend f = other == null ? null : SocialClient.get().friend(other);
            sub = f == null ? "" : UiUtil.presenceLine(f);
        }
        final int y = rightRect.y() + 2;
        g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(title), rightRect.w() / 2), rightRect.x() + 2, y, p.text(), t.isVanilla());
        if (!sub.isEmpty()) {
            final FormattedCharSequence seq = SlateDraw.truncate(Component.literal(sub), rightRect.w() / 2 - 8);
            g.drawString(SlateDraw.font(), seq, rightRect.right() - 2 - SlateDraw.font().width(seq), y, p.textDim(), t.isVanilla());
        }
    }

    /** Pending attachment chip and recording indicator, drawn over the bottom of the chat view (added last, so on top). */
    private final class StatusStrip extends SlateWidget {

        StatusStrip() {
            super(0, 0, 10, 20, Component.empty());
            this.silent();
        }

        private void sync() {
            this.visible = pendingImage != null || recording;
            setX(rightRect.x());
            setY(input.getY() - 22);
            setWidth(rightRect.w());
        }

        private boolean onClose(final double mx, final double my) {
            return pendingImage != null && mx >= getX() + 134 && mx < getX() + 150 && my >= getY() && my < getY() + 20;
        }

        @Override
        public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
            sync();
            if (!this.visible || button != 0) return false;
            if (onClose(mouseX, mouseY)) { dropPending(); return true; }
            return false;
        }

        @Override
        protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            sync();
            if (!this.visible) return;
            final Theme t = Theme.current();
            final Palette p = t.palette();
            final int x = getX(), y = getY();
            if (pendingImage != null) {
                if (t.isVanilla()) { g.fill(x, y, x + 150, y + 20, 0xE0000000); SlateDraw.outline(g, x, y, 150, 20, 0xFFFFFFFF, 0); }
                else { SlateDraw.pixelRound(g, x, y, 150, 20, p.surface(), t.radius()); SlateDraw.outline(g, x, y, 150, 20, p.accent(), t.radius()); }
                if (pendingThumb != null) {
                    RenderSystem.enableBlend();
                    g.blit(pendingThumb.id(), x + 2, y + 2, 16, 16, 0, 0, pendingThumb.width(), pendingThumb.height(), pendingThumb.width(), pendingThumb.height());
                    RenderSystem.disableBlend();
                }
                final String label = (pendingKind.equals("gif") ? "GIF" : "Image") + " · " + (pendingImage.length / 1024) + " KB · Enter sends";
                g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(label), 112), x + 22, y + 6, p.textMuted(), t.isVanilla());
                Icons.draw(g, Icon.CLOSE, x + 138, y + 6, 8, onClose(mouseX, mouseY) ? p.text() : p.textMuted());
            }
            if (recording) {
                final long el = (System.currentTimeMillis() - recordingSinceMs) / 1000;
                final boolean on = (System.currentTimeMillis() / 500) % 2 == 0;
                final int rx = x + getWidth() - 96;
                g.fill(rx, y + 2, rx + 94, y + 18, t.isVanilla() ? 0xE0000000 : Colors.withAlpha(p.surface(), 0xF0));
                g.fill(rx + 4, y + 7, rx + 10, y + 13, on ? p.danger() : Colors.withAlpha(p.danger(), 0x60));
                g.drawString(SlateDraw.font(), "Rec %d:%02d".formatted(el / 60, el % 60), rx + 14, y + 6, p.text(), t.isVanilla());
            }
        }

        @Override
        protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            renderDark(g, mouseX, mouseY, partialTick);
        }

        @Override
        public net.minecraft.client.gui.ComponentPath nextFocusPath(final net.minecraft.client.gui.navigation.FocusNavigationEvent event) { return null; }
    }

    /** Row renderer for the thread list. */
    private static final class ThreadRow implements SlateList.RowRenderer<ThreadModel> {
        @Override
        public void render(final GuiGraphics g, final ThreadModel t, final int index, final int x, final int y, final int w, final int h, final boolean hovered, final boolean selected, final int mouseX, final int mouseY) {
            final Palette p = Theme.current().palette();
            final SocialClient sc = SocialClient.get();
            final String title = sc.threadTitle(t);
            if (t.isGroup()) {
                SlateDraw.pixelRound(g, x + 6, y + 6, 18, 18, p.surfaceActive(), 2);
                Icons.draw(g, Icon.GROUP, x + 9, y + 9, 12, p.textMuted());
            } else {
                final UUID other = t.other(sc.selfUuid());
                final Friend f = other == null ? null : sc.friend(other);
                if (other != null) UiUtil.drawHead(g, other, f != null ? f.name : "", x + 6, y + 6, 18, f == null ? null : f.presence, 1f);
            }
            final ChatMessage last = t.last();
            final String time = last == null ? "" : UiUtil.sameDay(last.atMs(), System.currentTimeMillis()) ? UiUtil.clock(last.atMs()) : UiUtil.relativeTime(last.atMs()).replace(" ago", "");
            final int timeW = SlateDraw.width(time);
            g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(title), w - 36 - timeW - 4), x + 29, y + 5, t.unread > 0 ? p.text() : Colors.withAlpha(p.text(), 0xE0), Theme.current().isVanilla());
            if (!time.isEmpty()) g.drawString(SlateDraw.font(), time, x + w - timeW - 5, y + 5, p.textDim(), false);
            final String preview = last == null ? "" : (last.from().uuid().equals(sc.selfUuid()) ? "You: " : t.isGroup() ? last.from().display() + ": " : "")
                + (last.text().isEmpty() ? (last.hasAttachment() ? "[" + last.attachKind() + "]" : "") : last.text().replace('\n', ' '));
            final int badgeW = t.unread > 0 ? 18 : 0;
            g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(preview), w - 36 - badgeW), x + 29, y + 16, t.unread > 0 ? p.textMuted() : p.textDim(), false);
            if (t.unread > 0) SlateBadge.drawCount(g, t.unread, x + w - 22, y + 15);
        }
    }
}
