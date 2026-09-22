package dev.fallingcloud.slate.chat;

import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.net.blob.BlobRelay;

/**
 * {@code config/slate/chat-server.json} - the relay's hard limits, mirrored into {@link BlobRelay} when a
 * server (integrated or dedicated) starts. Enforced against what actually arrives on the wire, never
 * against anything a client claims.
 */
public final class ChatServerConfig {

    /** Largest attachment the server relays, in KB. Anything over is dropped mid-transfer. */
    public int maxBlobKb = 3072;
    /** Per-player rate limit on attachment chunks (24 KB each). 80/s is ~2 MB/s; clients pace at 60/s. */
    public int maxChunksPerSecond = 80;
    /** How many attachments one player may be uploading at once. */
    public int maxConcurrentTransfers = 3;
    /** Relay "X is typing" between players. */
    public boolean typingRelay = true;

    private static JsonConfig<ChatServerConfig> file;

    public static synchronized JsonConfig<ChatServerConfig> file() {
        if (file == null) file = JsonConfig.of("chat-server", ChatServerConfig.class, ChatServerConfig::new);
        return file;
    }

    public static ChatServerConfig get() {
        return file().get();
    }

    /** Pushes the limits into the relay. Called at server start (and again if the file is reloaded). */
    public static void apply() {
        file().load();
        final ChatServerConfig c = get();
        BlobRelay.maxBlobBytes = Math.max(64, c.maxBlobKb) * 1024;
        BlobRelay.maxChunksPerSecond = Math.max(4, c.maxChunksPerSecond);
        BlobRelay.maxConcurrentTransfers = Math.max(1, c.maxConcurrentTransfers);
        SlateChat.LOGGER.info("[Slate Chat] relay limits: {} KB per blob, {} chunks/s, {} concurrent", c.maxBlobKb, c.maxChunksPerSecond, c.maxConcurrentTransfers);
    }
}
