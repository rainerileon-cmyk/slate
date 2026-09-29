package dev.fallingcloud.slate.core.media;

import dev.fallingcloud.slate.core.Slate;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Clipboard images (Ctrl+V with a screenshot on the clipboard) and the encode-to-fit helpers shared with
 * file uploads.
 *
 * <p>GLFW's clipboard is text-only, so the image side goes through AWT - which works inside the Minecraft
 * process on Windows and Linux (macOS runs headless; there {@link #hasImage()} reports false and a paste
 * falls through to text handling). Clipboard access can block and PNG/JPEG encoding a screenshot is real
 * work, so everything past the quick has-an-image check runs off-thread.</p>
 */
public final class ClipboardImages {

    /** Longest edge kept when an image has to be resampled to fit. */
    private static final int MAX_DIM = 3072;
    /** JPEG qualities tried, best first, before any pixels are given up. */
    private static final float[] JPEG_QUALITIES = { 0.92f, 0.85f, 0.78f, 0.70f };

    /** Quick, non-blocking-ish check. False on headless platforms or when the clipboard holds text. */
    public static boolean hasImage() {
        try {
            return Toolkit.getDefaultToolkit().getSystemClipboard().isDataFlavorAvailable(DataFlavor.imageFlavor);
        } catch (final Throwable t) {
            return false;
        }
    }

    /**
     * Reads the clipboard image and encodes it within {@code maxBytes} (PNG when it has alpha, JPEG
     * otherwise). Blocking: call off the render thread. Null when there is no image or it cannot fit.
     */
    @Nullable
    public static byte[] readImage(final int maxBytes) {
        try {
            final Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.imageFlavor);
            if (!(data instanceof java.awt.Image awt)) return null;
            return encodeWithin(toBuffered(awt), maxBytes);
        } catch (final Throwable t) {
            Slate.LOGGER.debug("[Slate] clipboard image read failed: {}", t.toString());
            return null;
        }
    }

    /**
     * If the clipboard holds an image, reads + encodes it on a worker and hands the bytes to {@code onImage}
     * on the render thread ({@code onFail} when it could not be encoded to fit).
     *
     * @return true when the clipboard held an image (the caller should consume the paste key)
     */
    public static boolean tryPaste(final int maxBytes, final Consumer<byte[]> onImage, @Nullable final Runnable onFail) {
        if (!hasImage()) return false;
        final Thread worker = new Thread(() -> {
            final byte[] bytes = readImage(maxBytes);
            Minecraft.getInstance().execute(() -> {
                if (bytes != null) onImage.accept(bytes);
                else if (onFail != null) onFail.run();
            });
        }, "slate-paste");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    /**
     * Decodes arbitrary PNG/JPEG bytes and re-encodes at shrinking scales until under {@code maxBytes}.
     * Already-small originals are returned untouched (re-encoding a JPEG as PNG would inflate it and then
     * force a needless downscale). GIFs are never re-encoded (they would lose animation): null when over.
     *
     * @return the bytes to send, or null if it cannot be made to fit
     */
    @Nullable
    public static byte[] shrinkToFit(final byte[] raw, final int maxBytes) {
        try {
            if (raw.length <= maxBytes && (isImage(raw) || isGif(raw))) return raw;
            if (isGif(raw)) return null;
            final BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
            if (img == null) return null;
            return encodeWithin(img, maxBytes);
        } catch (final Exception e) {
            return null;
        }
    }

    /** PNG / JPEG magic. */
    public static boolean isImage(final byte[] b) {
        if (b == null || b.length < 4) return false;
        final boolean png = (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47;
        final boolean jpg = (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
        return png || jpg;
    }

    public static boolean isGif(final byte[] b) {
        return b != null && b.length > 3 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F';
    }

    /** File extension matching the bytes' magic ({@code png}, {@code jpg}, {@code gif}, or {@code bin}). */
    public static String extensionFor(final byte[] b) {
        if (isGif(b)) return "gif";
        if (b != null && b.length >= 4 && (b[0] & 0xFF) == 0x89) return "png";
        if (isImage(b)) return "jpg";
        return "bin";
    }

    /**
     * Encodes an image to fit {@code maxBytes} while giving up as little detail as possible: quality first,
     * pixels last. Every scale step is computed from the ORIGINAL image (resampling a resample compounds
     * blur). Images with alpha stay PNG (JPEG would flatten transparency to black).
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
            // Encoded size tracks pixel count, so the square root of the overshoot is the right step.
            final double overshoot = best == null ? 2.0 : best.length / (double) maxBytes;
            factor *= (float) (Math.sqrt(1.0 / overshoot) * 0.95);
        }
        return null;
    }

    private static byte[] encodeJpeg(final BufferedImage img, final float quality) throws IOException {
        final BufferedImage rgb;
        if (img.getType() == BufferedImage.TYPE_INT_RGB) {
            rgb = img;
        } else {
            rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            final java.awt.Graphics2D g = rgb.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
        }
        final javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        final javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        final ByteArrayOutputStream out = new ByteArrayOutputStream(256 * 1024);
        try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static BufferedImage toBuffered(final java.awt.Image awt) {
        if (awt instanceof BufferedImage b) return b;
        final BufferedImage out = new BufferedImage(awt.getWidth(null), awt.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g = out.createGraphics();
        g.drawImage(awt, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage scaled(final BufferedImage in, final float factor) {
        final int w = Math.max(1, Math.round(in.getWidth() * factor));
        final int h = Math.max(1, Math.round(in.getHeight() * factor));
        final BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(in, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static byte[] encodePng(final BufferedImage img) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(256 * 1024);
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /**
     * Puts an image file on the system clipboard as an image (the screenshot gallery's Copy). Minecraft has no image
     * clipboard of its own, so this goes through AWT on a worker thread; where AWT is headless (macOS with LWJGL) or
     * anything throws, {@code onResult} gets false, on the render thread.
     */
    public static void copy(final java.nio.file.Path file, final Consumer<Boolean> onResult) {
        final Thread worker = new Thread(() -> {
            boolean ok = false;
            try {
                if (!java.awt.GraphicsEnvironment.isHeadless()) {
                    final BufferedImage read = javax.imageio.ImageIO.read(file.toFile());
                    if (read != null) {
                        // Clipboards on Windows drop alpha badly; flatten onto opaque RGB first.
                        final BufferedImage rgb = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_RGB);
                        final java.awt.Graphics2D g2 = rgb.createGraphics();
                        g2.drawImage(read, 0, 0, null);
                        g2.dispose();
                        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransferable(rgb), null);
                        ok = true;
                    }
                }
            } catch (final Throwable t) {
                dev.fallingcloud.slate.core.Slate.LOGGER.warn("[Slate] clipboard image copy failed: {}", t.toString());
            }
            final boolean result = ok;
            net.minecraft.client.Minecraft.getInstance().execute(() -> onResult.accept(result));
        }, "slate-clipboard-copy");
        worker.setDaemon(true);
        worker.start();
    }

    private record ImageTransferable(BufferedImage image) implements java.awt.datatransfer.Transferable {
        @Override public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() { return new java.awt.datatransfer.DataFlavor[] { java.awt.datatransfer.DataFlavor.imageFlavor }; }

        @Override public boolean isDataFlavorSupported(final java.awt.datatransfer.DataFlavor flavor) { return java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor); }

        @Override
        public Object getTransferData(final java.awt.datatransfer.DataFlavor flavor) throws java.awt.datatransfer.UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
            return image;
        }
    }

    private ClipboardImages() {}
}
