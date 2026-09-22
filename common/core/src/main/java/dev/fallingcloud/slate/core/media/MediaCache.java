package dev.fallingcloud.slate.core.media;

import com.mojang.blaze3d.platform.NativeImage;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.net.blob.BlobPayloads;
import dev.fallingcloud.slate.core.net.blob.BlobReceiver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Client-side home of every attachment's bytes and textures, keyed by the 8-hex attachment id (lifted from
 * Chatterbox, generalised: kinds are strings, blobs arrive through {@link BlobReceiver}, and a disk cache
 * under {@code config/slate/cache/media/} lets media survive a rejoin).
 *
 * <p>Producers: blob transfers relayed from other players ({@link BlobReceiver} listeners), the local
 * player's own uploads ({@link #putLocal}), URL downloads ({@link #fromUrl}) and the disk cache. One
 * consumer: whoever renders cards asks for an {@link Entry} every frame and gets whatever state the
 * attachment is in.</p>
 *
 * <p>Threading: downloads and decodes run on a small daemon pool; decoded {@link NativeImage}s are handed
 * to the render thread via {@link Minecraft#execute} for GL upload (a texture created off the render thread
 * is a crash). Blob payloads arrive on the client main thread. Voice and file bytes are stored raw: decoding
 * is the caller's job.</p>
 */
public final class MediaCache {

    public enum Status { LOADING, READY, FAILED }

    /** One decoded frame: its registered texture plus how long a GIF shows it (0 for stills). */
    public record Frame(ResourceLocation texture, int delayMs) {}

    public static final class Entry {
        public volatile Status status = Status.LOADING;
        /** Raw bytes: opus clip for voice, file contents for files, the encoded image for images (kept for saving). */
        public volatile byte[] bytes;
        public volatile List<Frame> frames = List.of();
        public volatile int width, height;
        public volatile String sender = "";
        /** {@code image}, {@code voice}, {@code file} - the blob kind, or {@code url} for downloads. */
        public volatile String kind = "";
        public volatile int durationMs;
        public volatile String name = "";
        /** Human-readable reason when {@link #status} is FAILED. */
        public volatile String error = "";
        volatile long lastTouched = System.currentTimeMillis();
        final long created = System.currentTimeMillis();
        /** True once a blob Start arrived (or a download began), i.e. bytes are actually on their way. */
        volatile boolean inbound;

        public boolean ready() { return status == Status.READY; }
        public boolean isGif() { return frames.size() > 1; }
    }

    public static final String KIND_IMAGE = "image";
    public static final String KIND_VOICE = "voice";
    public static final String KIND_FILE = "file";
    public static final String KIND_URL = "url";

    /** Policy: largest linked file downloaded for an inline preview. Modules set it from their config. */
    public static volatile int maxDownloadBytes = 8 * 1024 * 1024;
    /** A blob entry that never received its Start within this long is marked unavailable. */
    private static final long BLOB_WAIT_MS = 60_000;
    private static final int MAX_GIF_FRAMES = 96;
    private static final int MAX_IMAGE_DIM = 4096;

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        final Thread t = new Thread(r, "slate-media");
        t.setDaemon(true);
        return t;
    });
    /** Registered dynamic textures, so eviction can free VRAM. */
    private static final Map<String, List<ResourceLocation>> TEXTURES = new LinkedHashMap<>();
    private static boolean initialised;

    // ------------------------------------------------------------------ bootstrap

    /** Subscribes to the blob channel. Client only; idempotent. Modules call it from {@code initClient()}. */
    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        BlobReceiver.onStart(MediaCache::onBlobStart);
        BlobReceiver.onComplete(MediaCache::onBlobComplete);
        POOL.submit(MediaCache::pruneDisk);
    }

    // ------------------------------------------------------------------ lookup

    /** The renderer's entry point: returns the entry, kicking off a URL download / disk load the first time. */
    public static Entry get(final Attachment att) {
        final Entry e = ENTRIES.computeIfAbsent(att.id(), k -> {
            final Entry fresh = new Entry();
            fresh.durationMs = att.durationMs();
            fresh.name = att.name() == null ? "" : att.name();
            switch (att.kind()) {
                case IMAGE_URL -> { fresh.kind = KIND_URL; fresh.inbound = true; POOL.submit(() -> download(att.id(), att.url(), fresh)); }
                case VIDEO_URL -> { fresh.kind = KIND_URL; fresh.status = Status.READY; }   // video cards render from the URL alone
                case VOICE -> { fresh.kind = KIND_VOICE; POOL.submit(() -> loadFromDisk(att.id(), fresh)); }
                case FILE_BLOB -> { fresh.kind = KIND_FILE; POOL.submit(() -> loadFromDisk(att.id(), fresh)); }
                default -> { fresh.kind = KIND_IMAGE; POOL.submit(() -> loadFromDisk(att.id(), fresh)); }
            }
            return fresh;
        });
        e.lastTouched = System.currentTimeMillis();
        if (e.status == Status.LOADING && !e.inbound && System.currentTimeMillis() - e.created > BLOB_WAIT_MS) {
            e.error = "unavailable";
            e.status = Status.FAILED;
        }
        return e;
    }

    /** The entry for an id if one exists (no loading is started). */
    @Nullable
    public static Entry get(final String id) {
        final Entry e = ENTRIES.get(id);
        if (e != null) e.lastTouched = System.currentTimeMillis();
        return e;
    }

    public static boolean has(final String id) {
        return ENTRIES.containsKey(id);
    }

    /** Starts (or returns) the download of an image link; the entry is keyed by {@link Attachment#idForUrl}. */
    public static Entry fromUrl(final String url) {
        return get(new Attachment(Attachment.Kind.IMAGE_URL, Attachment.idForUrl(url), url, 0));
    }

    /**
     * Stores the local player's own just-uploaded blob so their own chat line renders immediately.
     *
     * @param kind {@link #KIND_IMAGE}, {@link #KIND_VOICE} or {@link #KIND_FILE}
     */
    public static Entry putLocal(final String id, final byte[] bytes, final String kind, final int durationMs) {
        return putLocal(id, bytes, kind, durationMs, "");
    }

    public static Entry putLocal(final String id, final byte[] bytes, final String kind, final int durationMs, final String name) {
        final Entry e = new Entry();
        e.sender = Minecraft.getInstance().getUser().getName();
        e.kind = kind;
        e.durationMs = durationMs;
        e.name = name == null ? "" : name;
        e.inbound = true;
        ENTRIES.put(id, e);
        accept(id, bytes, e);
        evictIfCrowded();
        return e;
    }

    // ------------------------------------------------------------------ blob receive

    private static void onBlobStart(final BlobPayloads.Start start) {
        final String kind = start.kind() == null ? "" : start.kind().toLowerCase(Locale.ROOT);
        if (!KIND_IMAGE.equals(kind) && !KIND_VOICE.equals(kind) && !KIND_FILE.equals(kind)) return;   // not ours (streams etc.)
        final Entry e = ENTRIES.computeIfAbsent(start.id(), k -> new Entry());
        e.sender = start.sender() == null ? "" : start.sender();
        e.kind = kind;
        e.durationMs = start.durationMs();
        if (KIND_FILE.equals(kind)) e.name = Attachment.safeName(start.meta());
        e.inbound = true;
        e.status = Status.LOADING;
        evictIfCrowded();
    }

    private static void onBlobComplete(final BlobReceiver.Received r) {
        final Entry e = ENTRIES.get(r.id());
        if (e == null) return;
        if (!e.kind.equals(KIND_IMAGE) && !e.kind.equals(KIND_VOICE) && !e.kind.equals(KIND_FILE)) return;
        accept(r.id(), r.bytes(), e);
    }

    /** Bytes are complete: store raw kinds, decode images, and write the disk cache. */
    private static void accept(final String id, final byte[] data, final Entry e) {
        e.bytes = data;
        if (KIND_IMAGE.equals(e.kind)) {
            POOL.submit(() -> decodeImage(id, data, e, false));
        } else {
            e.status = Status.READY;
        }
        if (MediaConfig.get().diskCache) POOL.submit(() -> writeDisk(id, data, e));
    }

    // ------------------------------------------------------------------ download + decode

    private static void download(final String id, final String url, final Entry e) {
        try {
            final HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", MediaConfig.get().userAgent);
            final int code = conn.getResponseCode();
            final String type = String.valueOf(conn.getContentType()).toLowerCase(Locale.ROOT);
            if (code != 200 || !type.startsWith("image/")) throw new IOException("HTTP " + code + " " + type);
            final int cap = maxDownloadBytes;
            final ByteArrayOutputStream buf = new ByteArrayOutputStream(64 * 1024);
            try (InputStream in = conn.getInputStream()) {
                final byte[] tmp = new byte[16 * 1024];
                int n;
                while ((n = in.read(tmp)) > 0) {
                    if (buf.size() + n > cap) throw new IOException("over download cap (" + cap / 1024 + " KB)");
                    buf.write(tmp, 0, n);
                }
            }
            final byte[] data = buf.toByteArray();
            e.bytes = data;
            decodeImage(id, data, e, url.toLowerCase(Locale.ROOT).contains(".gif"));
            if (MediaConfig.get().diskCache) writeDisk(id, data, e);
        } catch (final Exception ex) {
            Slate.LOGGER.debug("[Slate] embed failed for {}: {}", url, ex.toString());
            e.error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            e.status = Status.FAILED;
        }
    }

    /** Off-thread decode; GL upload is bounced to the render thread at the end. */
    private static void decodeImage(final String id, final byte[] data, final Entry e, final boolean gifHint) {
        try {
            final boolean gif = gifHint || ClipboardImages.isGif(data);
            final List<NativeImage> images = new ArrayList<>();
            final List<Integer> delays = new ArrayList<>();
            if (gif) {
                try {
                    decodeGif(data, images, delays);
                } catch (final Exception gifFail) {
                    images.clear();
                    delays.clear();
                    images.add(NativeImage.read(new ByteArrayInputStream(data)));   // still frame via stb as a fallback
                    delays.add(0);
                }
            } else {
                images.add(NativeImage.read(new ByteArrayInputStream(data)));
                delays.add(0);
            }
            final NativeImage first = images.get(0);
            if (first.getWidth() > MAX_IMAGE_DIM || first.getHeight() > MAX_IMAGE_DIM) {
                images.forEach(NativeImage::close);
                throw new IOException("image too large: " + first.getWidth() + "x" + first.getHeight());
            }
            e.width = first.getWidth();
            e.height = first.getHeight();
            Minecraft.getInstance().execute(() -> {
                final List<Frame> frames = new ArrayList<>(images.size());
                final List<ResourceLocation> owned = new ArrayList<>(images.size());
                for (int i = 0; i < images.size(); i++) {
                    final ResourceLocation rl = Slate.id("media/" + id + "/" + i);
                    final DynamicTexture tex = new DynamicTexture(images.get(i));
                    // Linear filtering: every attachment is drawn scaled (down to a card, up in the viewer);
                    // point sampling a photo into a 100 px card drops most pixels and reads as aliased junk.
                    tex.setFilter(true, false);
                    Minecraft.getInstance().getTextureManager().register(rl, tex);
                    frames.add(new Frame(rl, delays.get(i)));
                    owned.add(rl);
                }
                synchronized (TEXTURES) {
                    final List<ResourceLocation> old = TEXTURES.put(id, owned);
                    if (old != null) old.forEach(rl -> Minecraft.getInstance().getTextureManager().release(rl));
                }
                e.frames = frames;
                e.status = Status.READY;
            });
        } catch (final Exception ex) {
            Slate.LOGGER.debug("[Slate] decode failed for {}: {}", id, ex.toString());
            e.error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            e.status = Status.FAILED;
        }
    }

    /**
     * GIF frames + per-frame delays via ImageIO, composited manually: ImageIO hands back raw frame rectangles
     * (many GIFs store only the pixels that changed), so each frame is drawn over a persistent canvas at its
     * declared offset, honouring the disposal modes that matter.
     */
    private static void decodeGif(final byte[] data, final List<NativeImage> out, final List<Integer> delays) throws IOException {
        final Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) throw new IOException("no GIF reader");
        final ImageReader reader = readers.next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(in, false);
            final int count = Math.min(reader.getNumImages(true), MAX_GIF_FRAMES);
            int screenW = 0, screenH = 0;
            try {
                final IIOMetadata stream = reader.getStreamMetadata();
                final Node root = stream.getAsTree("javax_imageio_gif_stream_1.0");
                for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
                    if (n.getNodeName().equals("LogicalScreenDescriptor")) {
                        final NamedNodeMap a = n.getAttributes();
                        screenW = Integer.parseInt(a.getNamedItem("logicalScreenWidth").getNodeValue());
                        screenH = Integer.parseInt(a.getNamedItem("logicalScreenHeight").getNodeValue());
                    }
                }
            } catch (final Exception ignored) {}
            java.awt.image.BufferedImage canvas = null;
            for (int i = 0; i < count; i++) {
                final java.awt.image.BufferedImage frame = reader.read(i);
                int delay = 100, x = 0, y = 0;
                String disposal = "none";
                final IIOMetadata meta = reader.getImageMetadata(i);
                final Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
                for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
                    if (n.getNodeName().equals("GraphicControlExtension")) {
                        final NamedNodeMap a = n.getAttributes();
                        delay = Integer.parseInt(a.getNamedItem("delayTime").getNodeValue()) * 10;
                        disposal = a.getNamedItem("disposalMethod").getNodeValue();
                    } else if (n.getNodeName().equals("ImageDescriptor")) {
                        final NamedNodeMap a = n.getAttributes();
                        x = Integer.parseInt(a.getNamedItem("imageLeftPosition").getNodeValue());
                        y = Integer.parseInt(a.getNamedItem("imageTopPosition").getNodeValue());
                    }
                }
                if (canvas == null) {
                    final int cw = Math.max(screenW, frame.getWidth() + x), ch = Math.max(screenH, frame.getHeight() + y);
                    canvas = new java.awt.image.BufferedImage(cw, ch, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                }
                final java.awt.image.BufferedImage previous = disposal.equals("restoreToPrevious") ? copy(canvas) : null;
                final java.awt.Graphics2D g = canvas.createGraphics();
                g.drawImage(frame, x, y, null);
                g.dispose();
                out.add(toNative(canvas));
                delays.add(Math.max(delay, 20));
                if (disposal.equals("restoreToBackgroundColor")) {
                    final java.awt.Graphics2D g2 = canvas.createGraphics();
                    g2.setComposite(java.awt.AlphaComposite.Clear);
                    g2.fillRect(x, y, frame.getWidth(), frame.getHeight());
                    g2.dispose();
                } else if (previous != null) {
                    canvas = previous;
                }
            }
        } finally {
            reader.dispose();
        }
        if (out.isEmpty()) throw new IOException("no GIF frames");
    }

    private static java.awt.image.BufferedImage copy(final java.awt.image.BufferedImage src) {
        final java.awt.image.BufferedImage c = new java.awt.image.BufferedImage(src.getWidth(), src.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g = c.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Src);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return c;
    }

    private static NativeImage toNative(final java.awt.image.BufferedImage img) {
        final NativeImage ni = new NativeImage(img.getWidth(), img.getHeight(), false);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                final int argb = img.getRGB(x, y);
                // BufferedImage is ARGB; NativeImage wants ABGR-packed ints.
                final int abgr = (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >>> 16) | ((argb & 0x000000FF) << 16);
                ni.setPixelRGBA(x, y, abgr);
            }
        }
        return ni;
    }

    /** Which GIF frame is showing right now, from summed per-frame delays on a shared wall clock. */
    public static Frame currentFrame(final List<Frame> frames) {
        if (frames.size() == 1) return frames.get(0);
        int total = 0;
        for (final Frame f : frames) total += Math.max(f.delayMs(), 20);
        long at = System.currentTimeMillis() % Math.max(1, total);
        for (final Frame f : frames) {
            at -= Math.max(f.delayMs(), 20);
            if (at < 0) return f;
        }
        return frames.get(0);
    }

    // ------------------------------------------------------------------ disk cache

    public static Path diskDir() {
        return JsonConfig.dir().resolve("cache").resolve("media");
    }

    private static void writeDisk(final String id, final byte[] data, final Entry e) {
        try {
            final Path dir = diskDir();
            Files.createDirectories(dir);
            final Path tmp = dir.resolve(id + ".tmp");
            Files.write(tmp, data);
            Files.move(tmp, dir.resolve(id + ".bin"), StandardCopyOption.REPLACE_EXISTING);
            final String meta = e.kind + "\n" + e.durationMs + "\n" + e.sender.replace('\n', ' ') + "\n" + e.name.replace('\n', ' ') + "\n";
            Files.writeString(dir.resolve(id + ".meta"), meta, StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            Slate.LOGGER.debug("[Slate] media disk write failed for {}: {}", id, ex.toString());
        }
    }

    /** Fills a blob entry from the disk cache when present; otherwise it stays LOADING until packets land. */
    private static void loadFromDisk(final String id, final Entry e) {
        try {
            final Path bin = diskDir().resolve(id + ".bin");
            if (!Files.isRegularFile(bin)) return;
            final Path meta = diskDir().resolve(id + ".meta");
            if (Files.isRegularFile(meta)) {
                final String[] lines = Files.readString(meta, StandardCharsets.UTF_8).split("\n", -1);
                if (lines.length >= 4) {
                    if (e.kind.isEmpty() || e.kind.equals(KIND_IMAGE)) e.kind = lines[0].isEmpty() ? e.kind : lines[0];
                    try { if (e.durationMs == 0) e.durationMs = Integer.parseInt(lines[1].trim()); } catch (final NumberFormatException ignored) {}
                    if (e.sender.isEmpty()) e.sender = lines[2];
                    if (e.name.isEmpty()) e.name = lines[3];
                }
            }
            if (e.inbound) return;                               // a live transfer beat us to it
            final byte[] data = Files.readAllBytes(bin);
            e.inbound = true;
            e.bytes = data;
            if (KIND_IMAGE.equals(e.kind) || KIND_URL.equals(e.kind)) decodeImage(id, data, e, false);
            else e.status = Status.READY;
            Files.setLastModifiedTime(bin, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
        } catch (final Exception ex) {
            Slate.LOGGER.debug("[Slate] media disk read failed for {}: {}", id, ex.toString());
        }
    }

    /** Keeps the disk cache under budget: oldest files first. Runs once at init on the pool. */
    private static void pruneDisk() {
        try {
            final Path dir = diskDir();
            if (!Files.isDirectory(dir)) return;
            final long budget = Math.max(1L, MediaConfig.get().maxDiskCacheMb) * 1024L * 1024L;
            final List<Path> bins;
            try (Stream<Path> s = Files.list(dir)) {
                bins = new ArrayList<>(s.filter(p -> p.getFileName().toString().endsWith(".bin")).toList());
            }
            long total = 0;
            for (final Path p : bins) total += Files.size(p);
            if (total <= budget) return;
            bins.sort(Comparator.comparingLong(p -> {
                try { return Files.getLastModifiedTime(p).toMillis(); } catch (final IOException e) { return 0L; }
            }));
            for (final Path p : bins) {
                if (total <= budget) break;
                total -= Files.size(p);
                Files.deleteIfExists(p);
                Files.deleteIfExists(dir.resolve(p.getFileName().toString().replace(".bin", ".meta")));
            }
        } catch (final Exception ex) {
            Slate.LOGGER.debug("[Slate] media cache prune failed: {}", ex.toString());
        }
    }

    /** Deletes the whole disk cache (settings "clear cache"). */
    public static void clearDisk() {
        POOL.submit(() -> {
            try {
                final Path dir = diskDir();
                if (!Files.isDirectory(dir)) return;
                try (Stream<Path> s = Files.list(dir)) {
                    for (final Path p : s.toList()) Files.deleteIfExists(p);
                }
            } catch (final Exception ex) {
                Slate.LOGGER.warn("[Slate] could not clear media cache: {}", ex.toString());
            }
        });
    }

    // ------------------------------------------------------------------ eviction

    /** Frees the oldest entries' textures once the cache is over budget. Runs wherever a put happens. */
    private static void evictIfCrowded() {
        final int max = Math.max(8, MediaConfig.get().maxEntries);
        if (ENTRIES.size() <= max) return;
        final List<Map.Entry<String, Entry>> byAge = new ArrayList<>(ENTRIES.entrySet());
        byAge.sort((a, b) -> Long.compare(a.getValue().lastTouched, b.getValue().lastTouched));
        for (final Iterator<Map.Entry<String, Entry>> it = byAge.iterator(); it.hasNext() && ENTRIES.size() > max; ) {
            final String id = it.next().getKey();
            ENTRIES.remove(id);
            final List<ResourceLocation> owned;
            synchronized (TEXTURES) { owned = TEXTURES.remove(id); }
            if (owned != null) {
                Minecraft.getInstance().execute(() -> owned.forEach(rl -> Minecraft.getInstance().getTextureManager().release(rl)));
            }
        }
    }

    /** Drops an entry (e.g. to force a re-download). */
    public static void forget(final String id) {
        ENTRIES.remove(id);
        final List<ResourceLocation> owned;
        synchronized (TEXTURES) { owned = TEXTURES.remove(id); }
        if (owned != null) Minecraft.getInstance().execute(() -> owned.forEach(rl -> Minecraft.getInstance().getTextureManager().release(rl)));
    }

    private MediaCache() {}
}
