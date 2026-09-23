import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import javax.imageio.ImageIO;

/**
 * Java port of {@code tools/icons.py} for machines without Python/Pillow. Single-file program (Java 21):
 *
 * <pre>
 *   java tools/IconsGen.java                 regenerate icons.png, Icon.java and tools/icons-preview.png
 *   java tools/IconsGen.java --check         validate the glyph list only (no files written)
 *   java tools/IconsGen.java --verify        render in memory and compare with the files on disk
 *                                            (atlas by decoded ARGB, Icon.java byte for byte); exit 1 on drift
 *   java tools/IconsGen.java --review DIR    also write per-glyph review sheets (16/12/10/8 px) to DIR
 *   java tools/IconsGen.java --review DIR --from N   ... only glyphs from atlas index N on
 * </pre>
 *
 * Run it from the repository root (or from {@code tools/}); {@code --root DIR} overrides. On this machine:
 * {@code "$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2/bin/java.exe" tools/IconsGen.java}.
 *
 * {@code icons.py} stays the single source of truth: this program reads its {@code icon("NAME", """...""")}
 * blocks, {@code CELL}, {@code COLUMNS}, {@code JAVA_HEAD} and {@code JAVA_TAIL} and produces exactly what the
 * Python generator produces: the same atlas pixels ('#' = white a255, '+' = white a128, '.' = 0x00000000) and
 * a byte-identical {@code Icon.java}. The preview sheet has the same layout, but its text is drawn with Java2D
 * fonts rather than Pillow's, so its pixels differ. PNGs are written by a small deterministic encoder (8-bit
 * RGBA, best of several filter strategies, deflate level 9) so reruns do not churn git.
 */
public final class IconsGen {

    // Palette used only by the preview/review sheets (the dark skin defaults in DESIGN.md), as in icons.py.
    static final Color PREVIEW_BG = new Color(0x16, 0x16, 0x15);
    static final Color PREVIEW_CELL = new Color(0x22, 0x22, 0x21);
    static final Color PREVIEW_BORDER = new Color(0x33, 0x33, 0x2F);
    static final Color PREVIEW_TEXT = new Color(0xEC, 0xEA, 0xE4);
    static final Color PREVIEW_MUTED = new Color(0xA1, 0x9F, 0x97);

    record Glyph(String name, List<String> rows) {}

    static int CELL;
    static int COLUMNS;

    public static void main(final String[] args) throws IOException {
        boolean check = false, verify = false;
        Path review = null, root = null;
        int from = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--check" -> check = true;
                case "--verify" -> verify = true;
                case "--review" -> review = Path.of(args[++i]);
                case "--from" -> from = Integer.parseInt(args[++i]);
                case "--root" -> root = Path.of(args[++i]);
                default -> fail("unknown argument " + args[i]);
            }
        }
        if (root == null) root = findRoot();
        final Path py = root.resolve("tools/icons.py");
        final String src = Files.readString(py, StandardCharsets.UTF_8).replace("\r\n", "\n");

        CELL = pyInt(src, "CELL");
        COLUMNS = pyInt(src, "COLUMNS");
        final String javaHead = pyString(src, "JAVA_HEAD");
        final String javaTail = pyString(src, "JAVA_TAIL");
        final List<Glyph> glyphs = parseGlyphs(src);

        System.out.println(glyphs.size() + " glyphs -> " + COLUMNS + " x " + atlasRows(glyphs) + " cells");
        if (check) return;

        final Path atlasPath = root.resolve("common/core/src/main/resources/assets/slate/textures/gui/icons.png");
        final Path javaPath = root.resolve("common/core/src/main/java/dev/fallingcloud/slate/core/gfx/Icon.java");
        final Path previewPath = root.resolve("tools/icons-preview.png");

        final BufferedImage atlas = renderAtlas(glyphs);
        final String java = javaSource(glyphs, javaHead, javaTail);

        if (verify) {
            boolean ok = true;
            final BufferedImage onDisk = ImageIO.read(atlasPath.toFile());
            final String atlasDiff = diffArgb(atlas, onDisk, glyphs);
            if (atlasDiff == null) {
                System.out.println("icons.png: identical ARGB (" + atlas.getWidth() + "x" + atlas.getHeight() + ")");
            } else {
                System.out.println("icons.png: DIFFERS - " + atlasDiff);
                ok = false;
            }
            final byte[] javaDisk = Files.readAllBytes(javaPath);
            if (java.equals(new String(javaDisk, StandardCharsets.UTF_8))) {
                System.out.println("Icon.java: byte-identical (" + javaDisk.length + " bytes)");
            } else {
                System.out.println("Icon.java: DIFFERS");
                ok = false;
            }
            if (!ok) System.exit(1);
            return;
        }

        Files.createDirectories(atlasPath.getParent());
        Files.write(atlasPath, png(atlas, true));
        System.out.println("wrote " + rel(root, atlasPath));
        Files.createDirectories(javaPath.getParent());
        Files.writeString(javaPath, java, StandardCharsets.UTF_8);
        System.out.println("wrote " + rel(root, javaPath));
        Files.write(previewPath, png(renderPreview(glyphs, 4), false));
        System.out.println("wrote " + rel(root, previewPath));
        if (review != null) {
            renderReview(glyphs, review, 8, from);
            System.out.println("wrote review sheets to " + review);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Parsing icons.py
    // ------------------------------------------------------------------------------------------

    static Path findRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        for (Path p = cwd; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("tools/icons.py"))) return p;
        }
        fail("cannot find tools/icons.py from " + cwd + " (use --root)");
        return null;
    }

    static int pyInt(final String src, final String name) {
        final Matcher m = Pattern.compile("^" + name + "\\s*=\\s*(\\d+)\\s*$", Pattern.MULTILINE).matcher(src);
        if (!m.find()) fail("icons.py: no " + name + " = <int>");
        return Integer.parseInt(m.group(1));
    }

    static String pyString(final String src, final String name) {
        final Matcher m = Pattern.compile("^" + name + " = \"\"\"(.*?)\"\"\"", Pattern.MULTILINE | Pattern.DOTALL)
            .matcher(src);
        if (!m.find()) fail("icons.py: no " + name + " = \"\"\"...\"\"\"");
        final String s = m.group(1);
        if (s.indexOf('\\') >= 0) fail("icons.py: " + name + " contains a backslash escape; teach IconsGen.java to decode it");
        return s;
    }

    static List<Glyph> parseGlyphs(final String src) {
        final Pattern call = Pattern.compile("^icon\\(\"([^\"]*)\",\\s*\"\"\"(.*?)\"\"\"\\)", Pattern.MULTILINE | Pattern.DOTALL);
        final Matcher m = call.matcher(src);
        final List<Glyph> out = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        while (m.find()) {
            final String name = m.group(1);
            // Python: art.strip("\n").splitlines()
            final String art = stripNewlines(m.group(2));
            final List<String> rows = art.isEmpty() ? List.of() : List.of(art.split("\n", -1));
            if (rows.size() != CELL) fail(name + ": expected " + CELL + " rows, got " + rows.size());
            for (int i = 0; i < rows.size(); i++) {
                final String row = rows.get(i);
                if (row.length() != CELL) {
                    fail(name + ": row " + i + " has " + row.length() + " chars, expected " + CELL + ": '" + row + "'");
                }
                for (final char ch : row.toCharArray()) {
                    if (ch != '.' && ch != '#' && ch != '+') fail(name + ": row " + i + " has invalid char '" + ch + "'");
                }
            }
            if (!names.add(name)) fail(name + ": duplicate glyph name");
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*") || !name.equals(name.toUpperCase(Locale.ROOT))) {
                fail(name + ": must be an UPPER_CASE Java identifier");
            }
            out.add(new Glyph(name, rows));
        }
        // Every top-level icon( call must have been understood, or the atlas would silently shift.
        final Matcher any = Pattern.compile("^icon\\(", Pattern.MULTILINE).matcher(src);
        int calls = 0;
        while (any.find()) calls++;
        if (calls != out.size()) fail("icons.py: " + calls + " icon( calls but only " + out.size() + " parsed");
        return out;
    }

    static String stripNewlines(final String s) {
        int a = 0, b = s.length();
        while (a < b && s.charAt(a) == '\n') a++;
        while (b > a && s.charAt(b - 1) == '\n') b--;
        return s.substring(a, b);
    }

    // ------------------------------------------------------------------------------------------
    // Outputs
    // ------------------------------------------------------------------------------------------

    static int atlasRows(final List<Glyph> glyphs) {
        return (glyphs.size() + COLUMNS - 1) / COLUMNS;
    }

    static int argb(final char ch) {
        return switch (ch) {
            case '#' -> 0xFFFFFFFF;
            case '+' -> 0x80FFFFFF;
            default -> 0x00000000;
        };
    }

    static BufferedImage glyphImage(final Glyph g) {
        final BufferedImage im = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < CELL; y++) {
            for (int x = 0; x < CELL; x++) im.setRGB(x, y, argb(g.rows().get(y).charAt(x)));
        }
        return im;
    }

    static BufferedImage renderAtlas(final List<Glyph> glyphs) {
        final BufferedImage sheet = new BufferedImage(COLUMNS * CELL, atlasRows(glyphs) * CELL, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < glyphs.size(); i++) {
            final int ox = (i % COLUMNS) * CELL, oy = (i / COLUMNS) * CELL;
            final Glyph g = glyphs.get(i);
            for (int y = 0; y < CELL; y++) {
                for (int x = 0; x < CELL; x++) sheet.setRGB(ox + x, oy + y, argb(g.rows().get(y).charAt(x)));
            }
        }
        return sheet;
    }

    static String javaSource(final List<Glyph> glyphs, final String head, final String tail) {
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < glyphs.size(); i += 8) {
            final List<String> chunk = new ArrayList<>();
            for (int j = i; j < Math.min(i + 8, glyphs.size()); j++) chunk.add(glyphs.get(j).name());
            final boolean last = i + 8 >= glyphs.size();
            lines.add("    " + String.join(", ", chunk) + (last ? ";" : ","));
        }
        return head + String.join("\n", lines) + "\n" + tail;
    }

    /** Null when both images have the same size and ARGB everywhere, else a description of the first drift. */
    static String diffArgb(final BufferedImage a, final BufferedImage b, final List<Glyph> glyphs) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return "size " + a.getWidth() + "x" + a.getHeight() + " vs " + b.getWidth() + "x" + b.getHeight();
        }
        int diffs = 0;
        String first = null;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                final int pa = a.getRGB(x, y), pb = b.getRGB(x, y);
                // Fully transparent pixels compare equal whatever their RGB.
                if (pa != pb && !((pa >>> 24) == 0 && (pb >>> 24) == 0)) {
                    if (first == null) {
                        final int idx = (y / CELL) * COLUMNS + x / CELL;
                        first = String.format("(%d,%d) in %s: generated %08X, on disk %08X", x, y,
                            idx < glyphs.size() ? glyphs.get(idx).name() : "cell " + idx, pa, pb);
                    }
                    diffs++;
                }
            }
        }
        return diffs == 0 ? null : diffs + " pixels, first " + first;
    }

    static Graphics2D graphics(final BufferedImage im) {
        final Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        return g;
    }

    /** Text with its top-left at (x, y), like Pillow's ImageDraw.text. */
    static void text(final Graphics2D g, final String s, final float x, final float y, final Font font, final Color c) {
        g.setFont(font);
        g.setColor(c);
        g.drawString(s, x, y + g.getFontMetrics().getAscent() - 1);
    }

    /** Pillow-style nearest-neighbour resize: output pixel x samples source floor((x + 0.5) * in / out). */
    static BufferedImage resizeNearest(final BufferedImage src, final int w, final int h) {
        final BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            final int sy = (int) Math.floor((y + 0.5) * src.getHeight() / h);
            for (int x = 0; x < w; x++) {
                final int sx = (int) Math.floor((x + 0.5) * src.getWidth() / w);
                out.setRGB(x, y, src.getRGB(sx, sy));
            }
        }
        return out;
    }

    static void rect(final Graphics2D g, final int x0, final int y0, final int x1, final int y1, final Color fill, final Color outline) {
        // Pillow rectangles are inclusive of (x1, y1).
        if (fill != null) {
            g.setColor(fill);
            g.fillRect(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
        }
        if (outline != null) {
            g.setColor(outline);
            g.drawRect(x0, y0, x1 - x0, y1 - y0);
        }
    }

    /** The whole sheet, scaled, on the dark skin background, one labelled cell per glyph. */
    static BufferedImage renderPreview(final List<Glyph> glyphs, final int scale) {
        final int cellW = 96, cellH = 100, pad = 12, titleH = 36, cols = COLUMNS, rows = atlasRows(glyphs);
        final BufferedImage im = new BufferedImage(pad * 2 + cols * cellW, titleH + pad * 2 + rows * cellH, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D d = graphics(im);
        d.setColor(PREVIEW_BG);
        d.fillRect(0, 0, im.getWidth(), im.getHeight());
        final Font big = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
        final Font small = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        final Font tiny = new Font(Font.SANS_SERIF, Font.PLAIN, 9);
        text(d, "Slate icon atlas - " + glyphs.size() + " glyphs, " + cols + " x " + rows + " cells of " + CELL
            + " px, shown at " + scale + "x", pad, 10, big, PREVIEW_TEXT);
        for (int i = 0; i < glyphs.size(); i++) {
            final int cx = pad + (i % cols) * cellW;
            final int cy = titleH + pad + (i / cols) * cellH;
            rect(d, cx + 2, cy + 2, cx + cellW - 3, cy + cellH - 3, PREVIEW_CELL, PREVIEW_BORDER);
            final BufferedImage g = resizeNearest(glyphImage(glyphs.get(i)), CELL * scale, CELL * scale);
            final int gx = cx + (cellW - CELL * scale) / 2;
            final int gy = cy + 8;
            rect(d, gx - 1, gy - 1, gx + CELL * scale, gy + CELL * scale, null, PREVIEW_BORDER);
            d.drawImage(g, gx, gy, null);
            text(d, String.valueOf(i), cx + 6, cy + 4, tiny, PREVIEW_MUTED);
            final String label = glyphs.get(i).name().toLowerCase(Locale.ROOT);
            d.setFont(small);
            final FontMetrics fm = d.getFontMetrics();
            text(d, label, cx + (cellW - fm.stringWidth(label)) / 2f, gy + CELL * scale + 6, small, PREVIEW_MUTED);
        }
        d.dispose();
        return im;
    }

    /**
     * Per-glyph sheets: each glyph at 16 px (8x zoom) next to nearest-neighbour downscales to 12, 10 and 8 px
     * (also zoomed) - what Slate actually shows at the small sizes.
     */
    static void renderReview(final List<Glyph> glyphs, final Path outDir, final int perSheet, final int from) throws IOException {
        Files.createDirectories(outDir);
        final int zoom = 8;
        final int[] sizes = {16, 12, 10, 8};
        final int colW = 16 * zoom + 20;
        final int rowH = 16 * zoom + 28;
        final Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        for (int s = from; s < glyphs.size(); s += perSheet) {
            final List<Glyph> chunk = glyphs.subList(s, Math.min(s + perSheet, glyphs.size()));
            final BufferedImage im = new BufferedImage(20 + chunk.size() * colW, 20 + sizes.length * rowH, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D d = graphics(im);
            d.setColor(PREVIEW_BG);
            d.fillRect(0, 0, im.getWidth(), im.getHeight());
            for (int c = 0; c < chunk.size(); c++) {
                final BufferedImage base = glyphImage(chunk.get(c));
                final int x0 = 20 + c * colW;
                text(d, (s + c) + " " + chunk.get(c).name().toLowerCase(Locale.ROOT), x0, 4, font, PREVIEW_TEXT);
                for (int r = 0; r < sizes.length; r++) {
                    final int size = sizes[r];
                    final int y0 = 20 + r * rowH + 14;
                    final BufferedImage small = size == 16 ? base : resizeNearest(base, size, size);
                    final BufferedImage zoomed = resizeNearest(small, size * zoom, size * zoom);
                    rect(d, x0 - 1, y0 - 1, x0 + 16 * zoom, y0 + 16 * zoom, PREVIEW_CELL, PREVIEW_BORDER);
                    d.drawImage(zoomed, x0, y0, null);
                    text(d, size + "px", x0, y0 - 13, font, PREVIEW_MUTED);
                }
            }
            d.dispose();
            Files.write(outDir.resolve(String.format("review_%02d.png", (s - from) / perSheet)), png(im, false));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Minimal deterministic PNG encoder (8-bit RGBA, non-interlaced)
    // ------------------------------------------------------------------------------------------

    static byte[] png(final BufferedImage im, final boolean tryAllFilters) {
        final int w = im.getWidth(), h = im.getHeight(), stride = w * 4;
        final byte[][] raw = new byte[h][stride];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = im.getRGB(x, y);
                if ((p >>> 24) == 0) p = 0; // canonical fully transparent pixel
                raw[y][x * 4] = (byte) (p >> 16);
                raw[y][x * 4 + 1] = (byte) (p >> 8);
                raw[y][x * 4 + 2] = (byte) p;
                raw[y][x * 4 + 3] = (byte) (p >>> 24);
            }
        }
        // Strategies: 0..4 = that filter on every row, 5 = per-row minimum-sum-of-absolute-differences.
        final int[] strategies = tryAllFilters ? new int[] {5, 0, 1, 2, 3, 4} : new int[] {5};
        byte[] best = null;
        for (final int strategy : strategies) {
            final ByteArrayOutputStream filtered = new ByteArrayOutputStream(h * (stride + 1));
            for (int y = 0; y < h; y++) {
                final byte[] prev = y > 0 ? raw[y - 1] : new byte[stride];
                byte[] row = null;
                int type = strategy;
                if (strategy == 5) {
                    long bestSum = Long.MAX_VALUE;
                    for (int f = 0; f < 5; f++) {
                        final byte[] cand = filter(f, raw[y], prev);
                        long sum = 0;
                        for (final byte b : cand) sum += Math.abs((int) b);
                        if (sum < bestSum) {
                            bestSum = sum;
                            row = cand;
                            type = f;
                        }
                    }
                } else {
                    row = filter(strategy, raw[y], prev);
                }
                filtered.write(type);
                filtered.write(row, 0, row.length);
            }
            final byte[] z = deflate(filtered.toByteArray());
            if (best == null || z.length < best.length) best = z;
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        final ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        writeInt(ihdr, w);
        writeInt(ihdr, h);
        ihdr.writeBytes(new byte[] {8, 6, 0, 0, 0});
        chunk(out, "IHDR", ihdr.toByteArray());
        chunk(out, "IDAT", best);
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    static byte[] filter(final int type, final byte[] row, final byte[] prev) {
        final byte[] out = new byte[row.length];
        for (int i = 0; i < row.length; i++) {
            final int x = row[i] & 0xFF;
            final int a = i >= 4 ? row[i - 4] & 0xFF : 0;
            final int b = prev[i] & 0xFF;
            final int c = i >= 4 ? prev[i - 4] & 0xFF : 0;
            final int pred = switch (type) {
                case 0 -> 0;
                case 1 -> a;
                case 2 -> b;
                case 3 -> (a + b) >>> 1;
                default -> paeth(a, b, c);
            };
            out[i] = (byte) (x - pred);
        }
        return out;
    }

    static int paeth(final int a, final int b, final int c) {
        final int p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) return a;
        return pb <= pc ? b : c;
    }

    static byte[] deflate(final byte[] data) {
        final Deflater def = new Deflater(Deflater.BEST_COMPRESSION);
        def.setInput(data);
        def.finish();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buf = new byte[65536];
        while (!def.finished()) out.write(buf, 0, def.deflate(buf));
        def.end();
        return out.toByteArray();
    }

    static void chunk(final ByteArrayOutputStream out, final String type, final byte[] data) {
        writeInt(out, data.length);
        final byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(t);
        out.writeBytes(data);
        final CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    static void writeInt(final ByteArrayOutputStream out, final int v) {
        out.write(v >>> 24);
        out.write(v >>> 16);
        out.write(v >>> 8);
        out.write(v);
    }

    static String rel(final Path root, final Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }

    static void fail(final String msg) {
        System.err.println(msg);
        System.exit(1);
    }

    private IconsGen() {}
}
