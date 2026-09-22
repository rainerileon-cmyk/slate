package dev.fallingcloud.slate.multiplayer.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.jetbrains.annotations.Nullable;

/**
 * JPEG/PNG encoding on worker threads: screen-share frames (downscale + JPEG at a fixed quality) and
 * attachments (fit under a byte budget giving up quality before pixels, lifted from Chatterbox). Never
 * touches GL; a {@link NativeImage} handed in is read through its pixel array and closed.
 */
public final class ImageEncoding {

    /** Longest edge kept for attachments. */
    private static final int MAX_DIM = 3072;
    private static final float[] JPEG_QUALITIES = { 0.92f, 0.85f, 0.78f, 0.70f };

    /** Downscale a captured frame to {@code maxWidth} and JPEG it. Closes the image. Returns {jpeg, width, height}. */
    public static Encoded encodeFrame(final NativeImage img, final int maxWidth, final float quality) throws IOException {
        final int w = img.getWidth(), h = img.getHeight();
        final int[] src;
        try {
            src = img.makePixelArray();          // ARGB
        } finally {
            img.close();
        }
        final float scale = Math.min(1f, (float) Math.max(64, maxWidth) / Math.max(1, w));
        final int tw = Math.max(1, Math.round(w * scale)), th = Math.max(1, Math.round(h * scale));
        final int[] dst = scale >= 1f ? src : boxDownsample(src, w, h, tw, th);
        final BufferedImage bi = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        bi.setRGB(0, 0, tw, th, dst, 0, tw);
        return new Encoded(encodeJpeg(bi, Math.max(0.15f, Math.min(0.95f, quality))), tw, th);
    }

    public record Encoded(byte[] bytes, int width, int height) {}

    /** Area-averaging downsample of an ARGB array (alpha dropped). */
    static int[] boxDownsample(final int[] src, final int sw, final int sh, final int tw, final int th) {
        final int[] out = new int[tw * th];
        for (int ty = 0; ty < th; ty++) {
            final int y0 = ty * sh / th, y1 = Math.max(y0 + 1, (ty + 1) * sh / th);
            for (int tx = 0; tx < tw; tx++) {
                final int x0 = tx * sw / tw, x1 = Math.max(x0 + 1, (tx + 1) * sw / tw);
                long r = 0, g = 0, b = 0;
                int n = 0;
                for (int y = y0; y < y1; y++) {
                    int i = y * sw + x0;
                    for (int x = x0; x < x1; x++, i++) {
                        final int p = src[i];
                        r += (p >> 16) & 0xFF;
                        g += (p >> 8) & 0xFF;
                        b += p & 0xFF;
                        n++;
                    }
                }
                out[ty * tw + tx] = 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ attachments

    /** PNG / JPEG magic. GIFs never reach here - callers keep those animated and untouched. */
    public static boolean isPngOrJpeg(final byte[] b) {
        if (b == null || b.length < 4) return false;
        final boolean png = (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47;
        final boolean jpg = (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
        return png || jpg;
    }

    public static boolean isGif(final byte[] b) {
        return b != null && b.length > 3 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F';
    }

    /**
     * Decodes PNG/JPEG bytes and re-encodes at shrinking scales until under {@code maxBytes}. Bytes that
     * already fit are returned untouched (re-encoding a fitting JPEG only softens it).
     * @return null if it cannot be made to fit
     */
    @Nullable
    public static byte[] shrinkToFit(final byte[] raw, final int maxBytes) {
        try {
            if (raw.length <= maxBytes && isPngOrJpeg(raw)) return raw;
            final BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
            if (img == null) return null;
            return encodeWithin(img, maxBytes);
        } catch (final Exception e) {
            return null;
        }
    }

    /**
     * Encodes an image to fit {@code maxBytes} while giving up as little detail as possible: quality first,
     * pixels last, and every scale step is computed from the ORIGINAL image so blur never compounds.
     * Images with alpha stay PNG (JPEG would flatten them to black).
     */
    @Nullable
    public static byte[] encodeWithin(final BufferedImage source, final int maxBytes) throws IOException {
        final boolean hasAlpha = source.getColorModel().hasAlpha();
        float factor = Math.min(1f, Math.min((float) MAX_DIM / source.getWidth(), (float) MAX_DIM / source.getHeight()));
        for (int attempt = 0; attempt < 8; attempt++) {
            final BufferedImage img = factor >= 1f ? source : scaled(source, factor);
            byte[] best;
            if (hasAlpha) {
                best = encodePng(img);
            } else {
                best = null;
                for (final float q : JPEG_QUALITIES) {
                    best = encodeJpeg(img, q);
                    if (best.length <= maxBytes) break;
                }
            }
            if (best != null && best.length <= maxBytes) return best;
            if (img.getWidth() <= 96 || img.getHeight() <= 96) return null;
            final double overshoot = best == null ? 2.0 : best.length / (double) maxBytes;
            factor *= (float) (Math.sqrt(1.0 / overshoot) * 0.95);
        }
        return null;
    }

    public static byte[] encodeJpeg(final BufferedImage img, final float quality) throws IOException {
        final BufferedImage rgb;
        if (img.getType() == BufferedImage.TYPE_INT_RGB) {
            rgb = img;
        } else {
            rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            final Graphics2D g = rgb.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
        }
        final ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        final ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        final ByteArrayOutputStream out = new ByteArrayOutputStream(128 * 1024);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    public static byte[] encodePng(final BufferedImage img) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(256 * 1024);
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    public static BufferedImage toBuffered(final java.awt.Image awt) {
        if (awt instanceof BufferedImage b) return b;
        final BufferedImage out = new BufferedImage(awt.getWidth(null), awt.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = out.createGraphics();
        g.drawImage(awt, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage scaled(final BufferedImage in, final float factor) {
        final int w = Math.max(1, Math.round(in.getWidth() * factor));
        final int h = Math.max(1, Math.round(in.getHeight() * factor));
        final BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(in, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private ImageEncoding() {}
}
