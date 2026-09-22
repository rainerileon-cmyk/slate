package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.chat.SlateChat;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.media.Attachment;
import dev.fallingcloud.slate.core.media.ClipboardImages;
import dev.fallingcloud.slate.core.media.MediaUpload;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Where outgoing lines go: the server's chat for Global/System, the Multiplayer thread for a DM/group tab.
 * Media follows the same routing - the blob target is {@code ""} (everyone) for server chat and the
 * thread's own target otherwise - and the fallback token is posted once the upload has left the queue.
 */
public final class ChatSend {

    /** Sends a line into the current channel. Commands always go to the server. */
    public static void text(final String text) {
        final Minecraft mc = Minecraft.getInstance();
        if (text == null || text.isBlank()) return;
        final String line = text.trim();
        if (line.startsWith("/")) {
            if (mc.getConnection() != null) mc.getConnection().sendCommand(line.substring(1));
            if (mc.gui != null) mc.gui.getChat().addRecentChat(line);
            return;
        }
        final ChatChannels.Channel c = ChatChannels.current();
        if (c.isThread() && c.thread() != null) {
            if (!MultiplayerBridge.send(c.thread(), line)) {
                SlateToasts.show(Component.translatable("slate_chat.dm.unavailable"), null, Icon.WARNING);
                return;
            }
            if (mc.gui != null) mc.gui.getChat().addRecentChat(line);
            ChatChannels.refresh();
            return;
        }
        if (mc.getConnection() == null) return;
        mc.getConnection().sendChat(line);
        if (mc.gui != null) mc.gui.getChat().addRecentChat(line);
    }

    /** The blob routing target for the current channel. */
    public static String blobTarget() {
        final ChatChannels.Channel c = ChatChannels.current();
        return c.isThread() && c.thread() != null ? MultiplayerBridge.blobTarget(c.thread()) : "";
    }

    public static boolean canSendMedia() {
        return MediaUpload.ready();
    }

    private static boolean guardMedia() {
        if (canSendMedia()) return true;
        SlateToasts.show(Component.translatable("slate_chat.media.no_server"), Component.translatable("slate_chat.media.no_server.body"), Icon.WARNING);
        return false;
    }

    /** Uploads image bytes and posts the token when done. */
    public static void image(final byte[] bytes) {
        if (!guardMedia()) return;
        final boolean gif = ClipboardImages.isGif(bytes);
        MediaUpload.sendImage(bytes, blobTarget(), id -> {
            text(Attachment.token(Attachment.Kind.IMAGE_BLOB, id, 0));
            SlateToasts.show(Component.translatable(gif ? "slate_chat.media.sent_gif" : "slate_chat.media.sent_image", bytes.length / 1024), null, Icon.IMAGE);
        });
    }

    public static void imageFile(final Path path) {
        if (!guardMedia()) return;
        MediaUpload.sendImageFile(path, blobTarget(),
            id -> text(Attachment.token(Attachment.Kind.IMAGE_BLOB, id, 0)),
            err -> SlateToasts.show(Component.translatable("slate_chat.media.failed"), Component.literal(err), Icon.WARNING));
    }

    public static void anyFile(final Path path) {
        if (!guardMedia()) return;
        final String name = path.getFileName() == null ? "file" : path.getFileName().toString();
        final boolean image = name.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(png|jpe?g|gif|webp)");
        if (image) { imageFile(path); return; }
        MediaUpload.sendAnyFile(path, blobTarget(),
            id -> text(Attachment.token(Attachment.Kind.FILE_BLOB, id, 0, name)),
            err -> SlateToasts.show(Component.translatable("slate_chat.media.failed"), Component.literal(err), Icon.WARNING));
    }

    public static void voice(final byte[] bytes, final int durationMs) {
        if (!guardMedia()) return;
        MediaUpload.sendVoice(bytes, durationMs, blobTarget(), id -> text(Attachment.token(Attachment.Kind.VOICE, id, durationMs)));
    }

    /** Pastes a clipboard image if there is one; true when the key press was consumed. */
    public static boolean pasteImage() {
        return ClipboardImages.tryPaste(MediaUpload.MAX_BYTES, ChatSend::image,
            () -> SlateToasts.show(Component.translatable("slate_chat.media.failed"), Component.translatable("slate_chat.media.paste_too_large"), Icon.WARNING));
    }

    static void log(final String what) {
        SlateChat.LOGGER.debug("[Slate Chat] {}", what);
    }

    private ChatSend() {}
}
