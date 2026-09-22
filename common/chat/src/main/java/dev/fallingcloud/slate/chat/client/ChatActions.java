package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.core.client.media.ImageViewerScreen;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.media.Attachment;
import dev.fallingcloud.slate.core.media.MediaCache;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * What happens when a card or a hover-action button is clicked: play/stop a voice clip, open the image
 * viewer (with the visible images as a gallery), open a video link, copy a message, prefill a reply,
 * open the first link. Everything here runs on the render thread from the ChatScreen mixin.
 */
public final class ChatActions {

    /** A card was clicked. */
    public static void activate(final Attachment att, @Nullable final GuiMessage message) {
        final Minecraft mc = Minecraft.getInstance();
        switch (att.kind()) {
            case VOICE -> toggleVoice(att);
            case IMAGE_BLOB, IMAGE_URL -> {
                final MediaCache.Entry entry = MediaCache.get(att);
                if (!entry.ready() || entry.frames.isEmpty()) return;
                final List<Attachment> gallery = new ArrayList<>();
                int index = 0;
                for (final ChatRenderState.ClickRect r : ChatRenderState.clickRects) {
                    if (!r.attachment().isImage() || gallery.stream().anyMatch(a -> a.id().equals(r.attachment().id()))) continue;
                    if (r.attachment().id().equals(att.id())) index = gallery.size();
                    gallery.add(r.attachment());
                }
                if (gallery.isEmpty()) gallery.add(att);
                // Visible order is bottom-up (newest first); show the gallery oldest -> newest.
                java.util.Collections.reverse(gallery);
                index = gallery.size() - 1 - index;
                mc.setScreen(new ImageViewerScreen(mc.screen, gallery, index));
            }
            case VIDEO_URL -> openLink(att.url());
            case FILE_BLOB -> saveFile(att);
        }
    }

    private static void toggleVoice(final Attachment att) {
        final MediaCache.Entry entry = MediaCache.get(att);
        if (!entry.ready() || entry.bytes == null) return;
        if (!MultiplayerBridge.playerAvailable()) {
            SlateToasts.show(Component.translatable("slate_chat.voice.unavailable"), Component.translatable("slate_chat.voice.unavailable.body"), Icon.MIC_OFF);
            return;
        }
        if (att.id().equals(MultiplayerBridge.playingId())) {
            MultiplayerBridge.stopPlay();
            return;
        }
        final Object clip = MultiplayerBridge.deserialize(entry.bytes);
        final short[] pcm = MultiplayerBridge.decode(clip);
        if (pcm == null) {
            SlateToasts.show(Component.translatable("slate_chat.voice.decode_failed"), null, Icon.WARNING);
            return;
        }
        MultiplayerBridge.togglePlay(att.id(), pcm);
    }

    /** Generic file cards: write the bytes next to the screenshots folder and open it. */
    private static void saveFile(final Attachment att) {
        final MediaCache.Entry entry = MediaCache.get(att);
        if (!entry.ready() || entry.bytes == null) return;
        final java.nio.file.Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("downloads");
        final java.nio.file.Path target = dir.resolve(Attachment.safeName(att.name().isEmpty() ? entry.name : att.name()));
        final byte[] bytes = entry.bytes;
        final Thread t = new Thread(() -> {
            try {
                java.nio.file.Files.createDirectories(dir);
                java.nio.file.Files.write(target, bytes);
                Minecraft.getInstance().execute(() -> SlateToasts.show(Component.translatable("slate_chat.file.saved"),
                    Component.literal(target.getFileName().toString()), Icon.DOWNLOAD, () -> net.minecraft.Util.getPlatform().openPath(dir)));
            } catch (final Exception e) {
                Minecraft.getInstance().execute(() -> SlateToasts.show(Component.translatable("slate_chat.media.failed"), Component.literal(String.valueOf(e.getMessage())), Icon.WARNING));
            }
        }, "slate-save-file");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ hover actions

    public static void run(final ChatRenderState.Action action, final GuiMessage message, @Nullable final EditBox input) {
        final ChatMeta.Meta meta = ChatMeta.of(message);
        switch (action) {
            case COPY -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(meta.plain);
                SlateToasts.show(Component.translatable("slate.copied"), null, Icon.COPY);
            }
            case REPLY -> {
                if (input == null || !meta.hasSender()) return;
                final String prefix = "@" + meta.sender + " ";
                final String cur = input.getValue();
                if (!cur.startsWith(prefix)) {
                    input.setValue(prefix + cur);
                }
                input.moveCursorToEnd(false);
            }
            case LINK -> {
                final List<String> links = Attachment.links(meta.plain);
                if (!links.isEmpty()) openLink(links.get(0));
            }
        }
    }

    /** Opens a link after vanilla's confirmation, coming back to the current screen. */
    public static void openLink(@Nullable final String url) {
        if (url == null || url.isBlank()) return;
        final Minecraft mc = Minecraft.getInstance();
        final Screen prev = mc.screen;
        mc.setScreen(new ConfirmLinkScreen(ok -> {
            if (ok) {
                try { SlatePlatform.get().openUri(URI.create(url)); }
                catch (final Exception e) { SlateToasts.show(Component.translatable("slate_chat.media.failed"), Component.literal(String.valueOf(e.getMessage())), Icon.WARNING); }
            }
            mc.setScreen(prev);
        }, url, true));
    }

    private ChatActions() {}
}
