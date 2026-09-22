package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * Ctrl+V with an image on the clipboard (lifted from Chatterbox). GLFW's clipboard is text-only, so the
 * image side goes through AWT, which works inside the Minecraft process on Windows (macOS runs headless
 * and quietly reports "no image", so the paste falls through to text). Clipboard access can block and
 * encoding is real work, so everything past the quick has-an-image check runs off-thread.
 */
public final class ClipboardImages {

    /** Quick check without reading the image (may block briefly if another app holds the clipboard). */
    public static boolean hasImage() {
        try {
            return Toolkit.getDefaultToolkit().getSystemClipboard().isDataFlavorAvailable(DataFlavor.imageFlavor);
        } catch (final Throwable t) {
            return false;
        }
    }

    /** @return true when the clipboard held an image and encoding started; the callback runs on the main thread. */
    public static boolean tryPasteImage(final Consumer<byte[]> onImage, final Consumer<String> onError) {
        try {
            if (!Toolkit.getDefaultToolkit().getSystemClipboard().isDataFlavorAvailable(DataFlavor.imageFlavor)) return false;
        } catch (final Throwable t) {
            return false;                                        // headless or clipboard busy - not ours
        }
        final int maxBytes = Math.max(64, MultiplayerConfigs.client().imageMaxKb) * 1024;
        final Thread worker = new Thread(() -> {
            try {
                final Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.imageFlavor);
                if (!(data instanceof java.awt.Image awt)) throw new IllegalStateException("no image data");
                final BufferedImage img = ImageEncoding.toBuffered(awt);
                final byte[] bytes = ImageEncoding.encodeWithin(img, maxBytes);
                Minecraft.getInstance().execute(() -> {
                    if (bytes == null) onError.accept("Image too large to send");
                    else onImage.accept(bytes);
                });
            } catch (final Throwable t) {
                SlateMultiplayer.LOGGER.debug("[Slate Multiplayer] paste failed: {}", t.toString());
                Minecraft.getInstance().execute(() -> onError.accept("Could not read the clipboard image"));
            }
        }, "slate-paste");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    private ClipboardImages() {}
}
