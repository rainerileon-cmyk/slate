package dev.fallingcloud.slate.core.gfx;

import com.mojang.blaze3d.platform.NativeImage;
import dev.fallingcloud.slate.core.Slate;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Loads PNG/JPEG files from disk (or bytes) into GPU textures, cached by path. Decoding runs on a
 * worker thread; the upload happens on the render thread. Used by the screenshot gallery, image
 * layout elements, media cards and avatars from URLs.
 */
public final class Textures {

    /** A loaded texture: its registered id and pixel size. */
    public record Loaded(ResourceLocation id, int width, int height) {}

    private static final Map<String, Loaded> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Loaded>> PENDING = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        final Thread t = new Thread(r, "slate-textures");
        t.setDaemon(true);
        return t;
    });
    private static int counter;

    /** Cached result if already loaded. */
    public static Optional<Loaded> cached(final Path file) {
        return Optional.ofNullable(CACHE.get(file.toAbsolutePath().toString()));
    }

    /** Async load; the callback runs on the render thread (once, when ready). Cached results call back immediately. */
    public static void load(final Path file, final Consumer<Loaded> onReady) {
        final String key = file.toAbsolutePath().toString();
        final Loaded c = CACHE.get(key);
        if (c != null) { onReady.accept(c); return; }
        PENDING.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> {
            try (InputStream in = Files.newInputStream(file)) {
                return NativeImage.read(in);
            } catch (final Exception e) {
                Slate.LOGGER.warn("[Slate] cannot read image {}: {}", file, e.toString());
                return null;
            }
        }, POOL).thenApplyAsync(img -> {
            PENDING.remove(k);
            if (img == null) return null;
            final Loaded l = register(img, "file");
            CACHE.put(k, l);
            return l;
        }, Minecraft.getInstance())).thenAccept(l -> { if (l != null) onReady.accept(l); });
    }

    /** Async load with a downscale (thumbnails): the image is resized on the worker before upload. */
    public static void loadThumbnail(final Path file, final int maxSize, final Consumer<Loaded> onReady) {
        final String key = file.toAbsolutePath() + "@" + maxSize;
        final Loaded c = CACHE.get(key);
        if (c != null) { onReady.accept(c); return; }
        PENDING.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> {
            try (InputStream in = Files.newInputStream(file); NativeImage full = NativeImage.read(in)) {
                final int w = full.getWidth(), h = full.getHeight();
                final float s = Math.min(1f, (float) maxSize / Math.max(w, h));
                final int tw = Math.max(1, Math.round(w * s)), th = Math.max(1, Math.round(h * s));
                final NativeImage small = new NativeImage(tw, th, false);
                for (int y = 0; y < th; y++) {
                    for (int x = 0; x < tw; x++) {
                        small.setPixelRGBA(x, y, full.getPixelRGBA(Math.min(w - 1, (int) (x / s)), Math.min(h - 1, (int) (y / s))));
                    }
                }
                return small;
            } catch (final Exception e) {
                Slate.LOGGER.warn("[Slate] cannot thumbnail {}: {}", file, e.toString());
                return null;
            }
        }, POOL).thenApplyAsync(img -> {
            PENDING.remove(k);
            if (img == null) return null;
            final Loaded l = register(img, "thumb");
            CACHE.put(k, l);
            return l;
        }, Minecraft.getInstance())).thenAccept(l -> { if (l != null) onReady.accept(l); });
    }

    /** Decode bytes (PNG/JPEG) synchronously on the render thread. */
    public static Optional<Loaded> fromBytes(final byte[] bytes, final String cacheKey) {
        if (cacheKey != null) {
            final Loaded c = CACHE.get(cacheKey);
            if (c != null) return Optional.of(c);
        }
        try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
            final NativeImage img = NativeImage.read(in);
            final Loaded l = register(img, "bytes");
            if (cacheKey != null) CACHE.put(cacheKey, l);
            return Optional.of(l);
        } catch (final Exception e) {
            return Optional.empty();
        }
    }

    /** Registers an already-decoded image (takes ownership). Render thread only. */
    public static Loaded register(final NativeImage img, final String prefix) {
        final ResourceLocation id = Slate.id("dyn/" + prefix + "/" + (counter++));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
        return new Loaded(id, img.getWidth(), img.getHeight());
    }

    /** Frees a texture (and forgets any cache entry pointing at it). */
    public static void release(final Loaded loaded) {
        if (loaded == null) return;
        Minecraft.getInstance().getTextureManager().release(loaded.id());
        CACHE.values().removeIf(l -> l.id().equals(loaded.id()));
    }

    /** Drops every cached file texture (e.g. when a screenshot was deleted). */
    public static void invalidate(final Path file) {
        final String key = file.toAbsolutePath().toString();
        CACHE.keySet().removeIf(k -> k.equals(key) || k.startsWith(key + "@"));
    }

    private Textures() {}
}
