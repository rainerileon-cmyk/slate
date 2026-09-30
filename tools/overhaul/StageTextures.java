import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Paints the textures of the Overhaul layout's stylized props: the chest and its sign. Pixel art by code, so it can
 * be tuned and regenerated: flat, warm colours, every plank a highlight, a body and a shade, no noise. The look of a
 * Minecraft trailer, which the soft stage shader then lights.
 *
 * <p>Run from the repository root: {@code java tools/overhaul/StageTextures.java}. Writes
 * {@code common/core/src/main/resources/assets/slate/textures/stage/chest.png} and {@code sign.png}; with
 * {@code --preview DIR} also enlarged copies to look at. The layout of chest.png is the one
 * {@code core.stage.node.ChestNode} reads (its UV table names the same rectangles).</p>
 */
public final class StageTextures {

    // Wood
    static final int PLANK = 0xFFBE8236, PLANK_HI = 0xFFD2984A, PLANK_LO = 0xFFA36A29, SEAM = 0xFF83531F;
    static final int FRAME = 0xFF5E3C1D, FRAME_HI = 0xFF7A4F27, FRAME_LO = 0xFF432A13;
    // Inside: dark as the menus are, a little warm as the wood round it. What is shown on the lid's panel stands out.
    static final int IN_TOP = 0xFF322A22, IN_LOW = 0xFF151210, IN_FLOOR = 0xFF1A1714, IN_FLOOR_LO = 0xFF12100E;
    static final int PANEL = 0xFF191816, PANEL_HI = 0xFF262421, PANEL_LO = 0xFF100F0E;
    static final int HOLLOW_HI = 0xFF2E2A25, HOLLOW_LO = 0xFF141312;
    // Metal
    static final int METAL = 0xFFAEB5BD, METAL_HI = 0xFFD3D9DF, METAL_LO = 0xFF7B838B, KEYHOLE = 0xFF2C3136;
    // Sign
    static final int BOARD = 0xFFD8B87C, BOARD_HI = 0xFFE6CB94, BOARD_LO = 0xFFBF9C60, BOARD_EDGE = 0xFF8F6E3C, BOARD_SEAM = 0xFFAB8850;

    private static BufferedImage img;

    public static void main(final String[] args) throws IOException {
        final File out = new File("common/core/src/main/resources/assets/slate/textures/stage");
        if (!out.isDirectory() && !out.mkdirs()) throw new IOException("cannot create " + out);
        File preview = null;
        for (int i = 0; i + 1 < args.length; i++) if (args[i].equals("--preview")) preview = new File(args[i + 1]);

        img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        chest();
        write(out, preview, "chest.png");

        img = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
        sign();
        write(out, preview, "sign.png");
    }

    // ------------------------------------------------------------------ chest

    private static void chest() {
        planksFramed(0, 0, 14, 14);                 // lid, top
        lidInside(14, 0);                           // lid, underside: the rim and the panel the button sits on
        baseInside(28, 0);                          // base, from above: the rim and the floor
        fill(42, 0, 14, 14, FRAME_LO);              // base, underside
        frame(42, 0, 14, 14, FRAME);
        planksFramed(0, 14, 14, 5);                 // lid, front
        planksFramed(14, 14, 14, 5);                // lid, the other sides
        planksFramed(0, 19, 14, 9);                 // base, front
        planksFramed(14, 19, 14, 9);                // base, the other sides
        // Where the lid meets the base: a line of shade under the lid.
        hline(0, 19, 14, FRAME_LO);
        hline(14, 19, 14, FRAME_LO);
        wall(28, 14, 12, 8, IN_TOP, IN_LOW);        // base, inner walls
        wall(40, 14, 12, 4, HOLLOW_HI, HOLLOW_LO);  // lid, inner walls
        latch(28, 22);
    }

    /** Planks running across, three pixels each (light, body, shade), inside a dark frame one pixel wide. */
    private static void planksFramed(final int x, final int y, final int w, final int h) {
        fill(x, y, w, h, FRAME);
        hline(x, y, w, FRAME_HI);                   // the frame catches light on its upper edge
        hline(x, y + h - 1, w, FRAME_LO);
        final int ix = x + 1, iy = y + 1, iw = w - 2, ih = h - 2;
        for (int row = 0; row < ih; row++) {
            final int inPlank = row % 3;
            // The last row of a face is never a highlight left hanging: it closes as body.
            final int c = inPlank == 0 ? (row == ih - 1 ? PLANK : PLANK_HI) : inPlank == 1 ? PLANK : PLANK_LO;
            hline(ix, iy + row, iw, c);
        }
        // Butt joints, staggered from plank to plank like laid boards.
        for (int plank = 0; plank * 3 < ih; plank++) {
            final int jx = ix + 3 + (plank * 5) % Math.max(1, iw - 5);
            for (int r = 0; r < 3 && plank * 3 + r < ih; r++) set(jx, iy + plank * 3 + r, r == 0 ? PLANK_LO : SEAM);
        }
    }

    private static void lidInside(final int x, final int y) {
        fill(x, y, 14, 14, FRAME);                  // the rim
        frame(x, y, 14, 14, FRAME_HI);
        fill(x + 1, y + 1, 12, 12, PANEL);
        // A quiet panel: a shade round its edge, a lighter line inside that, nothing to fight the button.
        frame(x + 1, y + 1, 12, 12, PANEL_LO);
        frame(x + 2, y + 2, 10, 10, PANEL_HI);
        fill(x + 3, y + 3, 8, 8, PANEL);
    }

    private static void baseInside(final int x, final int y) {
        fill(x, y, 14, 14, FRAME);
        frame(x, y, 14, 14, FRAME_HI);
        fill(x + 1, y + 1, 12, 12, IN_FLOOR);
        for (int row = 3; row < 12; row += 3) hline(x + 1, y + 1 + row, 12, IN_FLOOR_LO);
        frame(x + 1, y + 1, 12, 12, IN_FLOOR_LO);
    }

    /** An inner wall: light where the opening is (the top), dark at the bottom. */
    private static void wall(final int x, final int y, final int w, final int h, final int top, final int bottom) {
        for (int row = 0; row < h; row++) hline(x, y + row, w, lerp(top, bottom, h <= 1 ? 0f : row / (float) (h - 1)));
    }

    private static void latch(final int x, final int y) {
        fill(x, y, 2, 4, METAL);                    // front
        set(x, y, METAL_HI);
        set(x + 1, y, METAL_HI);
        set(x, y + 2, KEYHOLE);
        set(x + 1, y + 2, KEYHOLE);
        hline(x, y + 3, 2, METAL_LO);
        fill(x + 2, y, 1, 4, METAL_LO);             // sides
        set(x + 2, y, METAL);
        fill(x + 3, y, 2, 1, METAL_HI);             // top and underside
    }

    // ------------------------------------------------------------------ sign

    private static void sign() {
        // The face of the board, 16 by 8: two pale planks in a darker edge.
        fill(0, 0, 16, 8, BOARD);
        hline(1, 1, 14, BOARD_HI);
        hline(1, 3, 14, BOARD_LO);
        hline(1, 4, 14, BOARD_HI);
        hline(1, 6, 14, BOARD_LO);
        frame(0, 0, 16, 8, BOARD_EDGE);
        set(5, 2, BOARD_SEAM);
        set(11, 5, BOARD_SEAM);
        // Its back and its edges.
        fill(16, 0, 16, 8, BOARD_LO);
        frame(16, 0, 16, 8, BOARD_EDGE);
        fill(0, 8, 16, 2, BOARD_EDGE);
        hline(0, 8, 16, BOARD_SEAM);
    }

    // ------------------------------------------------------------------ pixels

    private static void set(final int x, final int y, final int argb) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, argb);
    }

    private static void fill(final int x, final int y, final int w, final int h, final int argb) {
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) set(x + i, y + j, argb);
    }

    private static void hline(final int x, final int y, final int w, final int argb) {
        fill(x, y, w, 1, argb);
    }

    private static void frame(final int x, final int y, final int w, final int h, final int argb) {
        fill(x, y, w, 1, argb);
        fill(x, y + h - 1, w, 1, argb);
        fill(x, y, 1, h, argb);
        fill(x + w - 1, y, 1, h, argb);
    }

    private static int lerp(final int a, final int b, final float t) {
        final int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        final int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    private static void write(final File dir, final File preview, final String name) throws IOException {
        ImageIO.write(img, "png", new File(dir, name));
        System.out.println("wrote " + new File(dir, name));
        if (preview == null) return;
        final int s = 10;
        final BufferedImage big = new BufferedImage(img.getWidth() * s, img.getHeight() * s, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < big.getHeight(); y++) {
            for (int x = 0; x < big.getWidth(); x++) {
                final int c = img.getRGB(x / s, y / s);
                final int a = c >>> 24, bg = (((x / s) + (y / s)) & 1) == 0 ? 0x30 : 0x40;
                final int r = (((c >> 16) & 255) * a + bg * (255 - a)) / 255, g = (((c >> 8) & 255) * a + bg * (255 - a)) / 255, b = ((c & 255) * a + bg * (255 - a)) / 255;
                big.setRGB(x, y, 0xFF000000 | r << 16 | g << 8 | b);
            }
        }
        if (!preview.isDirectory() && !preview.mkdirs()) throw new IOException("cannot create " + preview);
        ImageIO.write(big, "png", new File(preview, name));
    }

    private StageTextures() {}
}
