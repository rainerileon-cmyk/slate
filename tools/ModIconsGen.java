import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import javax.imageio.ImageIO;

/**
 * Java port of {@code tools/modicons.py} (the 64x64 mod-list icon of every Slate module) for machines without
 * Python/Pillow. Single-file program (Java 21):
 *
 * <pre>
 *   java tools/ModIconsGen.java            write common/&lt;module&gt;/src/main/resources/assets/&lt;modid&gt;/icon.png
 *   java tools/ModIconsGen.java --verify   render in memory and compare the decoded ARGB with the files on disk
 * </pre>
 *
 * Run it from the repository root (or {@code --root DIR}). {@code modicons.py} stays the source of truth: the
 * motifs, palette, background, border and corner constants are all read from it, and the rendering is the same
 * algorithm (32x32 grid, stepped corners, border, then a 2x nearest-neighbour upscale).
 */
public final class ModIconsGen {

    record Motif(String module, String modid, List<String> rows) {}

    static int GRID, SCALE, CORNER, BORDER;
    static int BG, BORDER_COLOR;
    static final Map<Character, Integer> PALETTE = new LinkedHashMap<>();

    public static void main(final String[] args) throws IOException {
        boolean verify = false;
        Path root = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--verify" -> verify = true;
                case "--root" -> root = Path.of(args[++i]);
                default -> fail("unknown argument " + args[i]);
            }
        }
        if (root == null) root = findRoot();
        final String src = Files.readString(root.resolve("tools/modicons.py"), StandardCharsets.UTF_8).replace("\r\n", "\n");
        GRID = pyInt(src, "GRID");
        SCALE = pyInt(src, "SCALE");
        CORNER = pyInt(src, "CORNER");
        BORDER = pyInt(src, "BORDER");
        BG = pyColor(src, "BG");
        BORDER_COLOR = pyColor(src, "BORDER_COLOR");
        parsePalette(src);
        final List<Motif> motifs = parseMotifs(src);

        boolean ok = true;
        for (final Motif m : motifs) {
            final Path path = root.resolve("common/" + m.module() + "/src/main/resources/assets/" + m.modid() + "/icon.png");
            final BufferedImage im = render(m.rows());
            final String rel = root.relativize(path).toString().replace('\\', '/');
            if (verify) {
                if (!Files.isRegularFile(path)) {
                    System.out.println(rel + ": MISSING");
                    ok = false;
                    continue;
                }
                final int diffs = diff(im, ImageIO.read(path.toFile()));
                System.out.println(rel + (diffs == 0 ? ": identical ARGB" : ": DIFFERS in " + diffs + " pixels"));
                ok &= diffs == 0;
            } else if (Files.isRegularFile(path) && diff(im, ImageIO.read(path.toFile())) == 0) {
                // Leave pixel-identical files alone (no re-encode churn in modules this run did not change).
                System.out.println("unchanged " + rel);
            } else {
                Files.createDirectories(path.getParent());
                Files.write(path, png(im));
                System.out.println("wrote " + rel);
            }
        }
        if (!ok) System.exit(1);
    }

    static Path findRoot() {
        final Path cwd = Path.of("").toAbsolutePath();
        for (Path p = cwd; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("tools/modicons.py"))) return p;
        }
        fail("cannot find tools/modicons.py from " + cwd + " (use --root)");
        return null;
    }

    static int pyInt(final String src, final String name) {
        final Matcher m = Pattern.compile("^" + name + "\\s*=\\s*(\\d+)", Pattern.MULTILINE).matcher(src);
        if (!m.find()) fail("modicons.py: no " + name + " = <int>");
        return Integer.parseInt(m.group(1));
    }

    static final String NUM = "(0x[0-9A-Fa-f]+|\\d+)";
    static final String TUPLE = "\\(\\s*" + NUM + ",\\s*" + NUM + ",\\s*" + NUM + ",\\s*" + NUM + "\\s*\\)";

    static int num(final String s) {
        return s.startsWith("0x") || s.startsWith("0X") ? Integer.parseInt(s.substring(2), 16) : Integer.parseInt(s);
    }

    static int argb(final Matcher m, final int first) {
        return num(m.group(first + 3)) << 24 | num(m.group(first)) << 16 | num(m.group(first + 1)) << 8 | num(m.group(first + 2));
    }

    static int pyColor(final String src, final String name) {
        final Matcher m = Pattern.compile("^" + name + "\\s*=\\s*" + TUPLE, Pattern.MULTILINE).matcher(src);
        if (!m.find()) fail("modicons.py: no " + name + " = (r, g, b, a)");
        return argb(m, 1);
    }

    static void parsePalette(final String src) {
        final Matcher block = Pattern.compile("^PALETTE = \\{(.*?)^\\}", Pattern.MULTILINE | Pattern.DOTALL).matcher(src);
        if (!block.find()) fail("modicons.py: no PALETTE = { ... }");
        final Matcher e = Pattern.compile("\"(.)\":\\s*" + TUPLE).matcher(block.group(1));
        while (e.find()) PALETTE.put(e.group(1).charAt(0), argb(e, 2));
        if (PALETTE.isEmpty()) fail("modicons.py: empty PALETTE");
    }

    static List<Motif> parseMotifs(final String src) {
        final Matcher m = Pattern.compile("^motif\\(\"([^\"]*)\",\\s*\"([^\"]*)\",\\s*\"\"\"(.*?)\"\"\"\\)",
            Pattern.MULTILINE | Pattern.DOTALL).matcher(src);
        final List<Motif> out = new ArrayList<>();
        while (m.find()) {
            final String modid = m.group(2);
            String art = m.group(3);
            int a = 0, b = art.length();
            while (a < b && art.charAt(a) == '\n') a++;
            while (b > a && art.charAt(b - 1) == '\n') b--;
            art = art.substring(a, b);
            final List<String> rows = List.of(art.split("\n", -1));
            if (rows.size() != GRID) fail(modid + ": expected " + GRID + " rows, got " + rows.size());
            for (int i = 0; i < rows.size(); i++) {
                final String row = rows.get(i);
                if (row.length() != GRID) fail(modid + ": row " + i + " has " + row.length() + " chars: '" + row + "'");
                for (final char ch : row.toCharArray()) {
                    if (ch != '.' && !PALETTE.containsKey(ch)) fail(modid + ": row " + i + " has invalid char '" + ch + "'");
                }
            }
            out.add(new Motif(m.group(1), modid, rows));
        }
        final Matcher any = Pattern.compile("^motif\\(", Pattern.MULTILINE).matcher(src);
        int calls = 0;
        while (any.find()) calls++;
        if (calls != out.size()) fail("modicons.py: " + calls + " motif( calls but only " + out.size() + " parsed");
        return out;
    }

    /** Point inside the box [x0,x1]x[y0,y1] whose corners are cut on a diagonal of {@code cut} px. */
    static boolean inside(final int x, final int y, final int x0, final int y0, final int x1, final int y1, final int cut) {
        if (x < x0 || x > x1 || y < y0 || y > y1) return false;
        final int dx = Math.min(x - x0, x1 - x), dy = Math.min(y - y0, y1 - y);
        return dx + dy >= cut;
    }

    static BufferedImage render(final List<String> rows) {
        final BufferedImage out = new BufferedImage(GRID * SCALE, GRID * SCALE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                final int c;
                if (!inside(x, y, 0, 0, GRID - 1, GRID - 1, CORNER)) {
                    c = 0; // transparent rounded corner
                } else if (!inside(x, y, BORDER, BORDER, GRID - 1 - BORDER, GRID - 1 - BORDER, CORNER - 1)) {
                    c = BORDER_COLOR;
                } else {
                    c = PALETTE.getOrDefault(rows.get(y).charAt(x), BG);
                }
                for (int sy = 0; sy < SCALE; sy++) {
                    for (int sx = 0; sx < SCALE; sx++) out.setRGB(x * SCALE + sx, y * SCALE + sy, c);
                }
            }
        }
        return out;
    }

    static int diff(final BufferedImage a, final BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return -1;
        int d = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                final int pa = a.getRGB(x, y), pb = b.getRGB(x, y);
                if (pa != pb && !((pa >>> 24) == 0 && (pb >>> 24) == 0)) d++;
            }
        }
        return d;
    }

    // Minimal deterministic PNG encoder (8-bit RGBA): best of the five filters applied uniformly, deflate 9.
    static byte[] png(final BufferedImage im) {
        final int w = im.getWidth(), h = im.getHeight(), stride = w * 4;
        final byte[][] raw = new byte[h][stride];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = im.getRGB(x, y);
                if ((p >>> 24) == 0) p = 0;
                raw[y][x * 4] = (byte) (p >> 16);
                raw[y][x * 4 + 1] = (byte) (p >> 8);
                raw[y][x * 4 + 2] = (byte) p;
                raw[y][x * 4 + 3] = (byte) (p >>> 24);
            }
        }
        byte[] best = null;
        for (int f = 0; f < 5; f++) {
            final ByteArrayOutputStream filtered = new ByteArrayOutputStream();
            for (int y = 0; y < h; y++) {
                final byte[] prev = y > 0 ? raw[y - 1] : new byte[stride];
                filtered.write(f);
                for (int i = 0; i < stride; i++) {
                    final int x = raw[y][i] & 0xFF, a = i >= 4 ? raw[y][i - 4] & 0xFF : 0, b = prev[i] & 0xFF,
                        c = i >= 4 ? prev[i - 4] & 0xFF : 0;
                    final int pred = switch (f) {
                        case 0 -> 0;
                        case 1 -> a;
                        case 2 -> b;
                        case 3 -> (a + b) >>> 1;
                        default -> {
                            final int p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
                            yield pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
                        }
                    };
                    filtered.write(x - pred);
                }
            }
            final Deflater def = new Deflater(Deflater.BEST_COMPRESSION);
            def.setInput(filtered.toByteArray());
            def.finish();
            final ByteArrayOutputStream z = new ByteArrayOutputStream();
            final byte[] buf = new byte[65536];
            while (!def.finished()) z.write(buf, 0, def.deflate(buf));
            def.end();
            if (best == null || z.size() < best.length) best = z.toByteArray();
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

    static void fail(final String msg) {
        System.err.println(msg);
        System.exit(1);
    }

    private ModIconsGen() {}
}
