package dev.fallingcloud.slate.menu.client.screenshots;

import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.MenuIo;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * Puts a PNG on the system clipboard as an image. Minecraft has no image clipboard of its own, so this
 * goes through AWT on a worker thread; where AWT is headless (macOS with LWJGL) or anything throws, the
 * callback gets {@code false} and the gallery shows a "not supported" toast.
 */
public final class ClipboardImages {

    public static void copy(final Path file, final Consumer<Boolean> onResult) {
        MenuIo.POOL.submit(() -> {
            boolean ok = false;
            try {
                if (!GraphicsEnvironment.isHeadless()) {
                    final BufferedImage read = ImageIO.read(file.toFile());
                    if (read != null) {
                        // Clipboards on Windows drop alpha badly; flatten onto opaque RGB first.
                        final BufferedImage rgb = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_RGB);
                        final java.awt.Graphics2D g2 = rgb.createGraphics();
                        g2.drawImage(read, 0, 0, null);
                        g2.dispose();
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransferable(rgb), null);
                        ok = true;
                    }
                }
            } catch (final Throwable t) {
                SlateMenu.LOGGER.warn("[Slate Menu] clipboard image copy failed: {}", t.toString());
            }
            final boolean result = ok;
            MenuIo.onClient(() -> onResult.accept(result));
        });
    }

    private record ImageTransferable(BufferedImage image) implements Transferable {
        @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] { DataFlavor.imageFlavor }; }

        @Override public boolean isDataFlavorSupported(final DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }

        @Override
        public Object getTransferData(final DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return image;
        }
    }

    private ClipboardImages() {}
}
