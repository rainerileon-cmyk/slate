package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.net.blob.BlobReceiver;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side attachment bytes (images, GIFs, voice clips) keyed by blob id: a memory LRU in front of
 * {@code config/slate/multiplayer/media/}. Blobs that complete on the receiver land here; a message
 * whose attachment is missing asks the hub once ({@link SocialMessage.MediaRequest}). Textures are
 * decoded through Core's {@link Textures} cache. Client main thread only.
 */
public final class MediaStore {

    private static final long MEMORY_CAP = 64L * 1024 * 1024;
    private static final Map<String, byte[]> MEMORY = new LinkedHashMap<>(64, 0.75f, true);
    private static final Map<String, Long> REQUESTED = new HashMap<>();
    private static long memoryBytes;
    private static boolean initialised;

    static synchronized void init() {
        if (initialised) return;
        initialised = true;
        BlobReceiver.onComplete(r -> {
            final String kind = r.kind();
            if ("image".equals(kind) || "gif".equals(kind) || "voice".equals(kind) || "video".equals(kind)) put(r.id(), r.bytes());
        });
    }

    private static Path dir() { return MultiplayerConfigs.clientDataDir().resolve("media"); }

    public static boolean has(final String id) {
        return MEMORY.containsKey(id) || Files.isRegularFile(dir().resolve(id));
    }

    @Nullable
    public static byte[] get(final String id) {
        if (id == null || id.isEmpty()) return null;
        final byte[] mem = MEMORY.get(id);
        if (mem != null) return mem;
        final Path f = dir().resolve(id);
        if (!Files.isRegularFile(f)) return null;
        try {
            final byte[] bytes = Files.readAllBytes(f);
            remember(id, bytes);
            return bytes;
        } catch (final IOException e) {
            return null;
        }
    }

    public static void put(final String id, final byte[] bytes) {
        if (id == null || id.isEmpty() || bytes == null) return;
        remember(id, bytes);
        REQUESTED.remove(id);
        final Path f = dir().resolve(id);
        Util.ioPool().execute(() -> {
            try {
                Files.createDirectories(f.getParent());
                Files.write(f, bytes);
            } catch (final IOException e) {
                SlateMultiplayer.LOGGER.debug("[Slate Multiplayer] cannot cache media {}: {}", id, e.toString());
            }
        });
    }

    private static void remember(final String id, final byte[] bytes) {
        final byte[] old = MEMORY.put(id, bytes);
        if (old != null) memoryBytes -= old.length;
        memoryBytes += bytes.length;
        final Iterator<Map.Entry<String, byte[]>> it = MEMORY.entrySet().iterator();
        while (memoryBytes > MEMORY_CAP && it.hasNext()) {
            final Map.Entry<String, byte[]> e = it.next();
            if (e.getKey().equals(id)) continue;
            memoryBytes -= e.getValue().length;
            it.remove();
        }
    }

    /** Ask the hub for a missing attachment (at most once a minute per id). */
    public static void request(final String id) {
        if (id == null || id.isEmpty() || has(id) || BlobReceiver.isPending(id)) return;
        final long now = System.currentTimeMillis();
        final Long last = REQUESTED.get(id);
        if (last != null && now - last < 60_000) return;
        REQUESTED.put(id, now);
        SocialClient.get().send(new SocialMessage.MediaRequest(id));
    }

    private static final Map<String, Textures.Loaded> TEXTURES = new HashMap<>();
    private static final java.util.Set<String> DECODING = new java.util.HashSet<>();
    private static final java.util.Set<String> UNDECODABLE = new java.util.HashSet<>();

    /**
     * The decoded texture for an image attachment. Missing bytes are requested from the hub; present
     * bytes are decoded once on a worker (JPEG/GIF go through ImageIO), so the first calls return empty.
     */
    public static Optional<Textures.Loaded> texture(final String id) {
        final Textures.Loaded t = TEXTURES.get(id);
        if (t != null) return Optional.of(t);
        if (UNDECODABLE.contains(id) || DECODING.contains(id)) return Optional.empty();
        final byte[] bytes = get(id);
        if (bytes == null) { request(id); return Optional.empty(); }
        DECODING.add(id);
        ImageDecoding.decodeAsync(bytes, "media", loaded -> {
            DECODING.remove(id);
            if (loaded == null) UNDECODABLE.add(id); else TEXTURES.put(id, loaded);
        });
        return Optional.empty();
    }

    private MediaStore() {}
}
