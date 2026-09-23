package dev.fallingcloud.slate.multiplayer.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Decodes PNG, JPEG and GIF (first frame) bytes into textures. {@code NativeImage.read} only accepts PNG
 * (it validates the PNG signature since 1.20.2), so JPEG - every screen-share frame and most photo
 * attachments - goes through {@link ImageIO} on a worker thread and is copied into a {@link NativeImage}
 * there; only the GPU upload ({@link Textures#register}) happens on the render thread.
 */
public final class ImageDecoding {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        final Thread t = new Thread(r, "slate-image-decode");
        t.setDaemon(true);
        return t;
    });

    /** Decode on the calling thread (no GL). Null when the bytes are not an image we can read. */
    @Nullable
    public static NativeImage decode(final byte[] bytes) {
        if (bytes == null || bytes.length < 8) return null;
        try {
            if (isPng(bytes)) return NativeImage.read(new ByteArrayInputStream(bytes));
            final BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
            return bi == null ? null : toNative(bi);
        } catch (final Exception e) {
            SlateMultiplayer.LOGGER.debug("[Slate Multiplayer] image decode failed: {}", e.toString());
            return null;
        }
    }

    /** Decode synchronously and upload now (render thread only; small images such as thumbnails). */
    public static Optional<Textures.Loaded> decodeNow(final byte[] bytes, final String prefix) {
        final NativeImage img = decode(bytes);
        return img == null ? Optional.empty() : Optional.of(Textures.register(img, prefix));
    }

    /** Decode on a worker; {@code onReady} runs on the render thread with the uploaded texture (or null on failure). */
    public static void decodeAsync(final byte[] bytes, final String prefix, final Consumer<Textures.Loaded> onReady) {
        POOL.execute(() -> {
            final NativeImage img = decode(bytes);
            Minecraft.getInstance().execute(() -> onReady.accept(img == null ? null : Textures.register(img, prefix)));
        });
    }

    public static boolean isPng(final byte[] b) {
        return b.length > 7 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47;
    }

    /** ARGB BufferedImage to NativeImage (which packs pixels as ABGR). */
    public static NativeImage toNative(final BufferedImage bi) {
        final int w = bi.getWidth(), h = bi.getHeight();
        final int[] argb = bi.getRGB(0, 0, w, h, null, 0, w);
        final NativeImage img = new NativeImage(w, h, false);
        for (int y = 0, i = 0; y < h; y++) {
            for (int x = 0; x < w; x++, i++) {
                final int p = argb[i];
                img.setPixelRGBA(x, y, (p & 0xFF00FF00) | ((p & 0xFF) << 16) | ((p >> 16) & 0xFF));
            }
        }
        return img;
    }

    private ImageDecoding() {}
}
