import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Pixel-art generator for Slate Building's toolbox items: the Builder's Toolbox, 6 tools x 4 tiers, 7 upgrades and the
 * toolbox GUI's empty-slot ghosts. Run from the repository root with the Gradle JDK:
 *
 * <pre>
 *   java tools/building/Textures.java [previewPng]
 * </pre>
 *
 * Every sprite is built from filled shapes (polygons, thick lines, rects, 8x8 ASCII emblems) on a 16x16 part grid, then
 * painted by ONE set of rules so the whole family reads as a set: each part has a five-step ramp (outline, dark, mid,
 * light, highlight); a 1 px outline in the part's darkest colour is added around every shape; light comes from the top
 * left (pixels with an open edge above/left are lit, open below/right are shaded, a corner open on both lit sides gets
 * the highlight). Tiers only swap the metal ramp, so a Copper and a Netherite Hammer share their silhouette.
 */
public final class Textures {

    // ------------------------------------------------------------------ ramps: outline, dark, mid, light, highlight

    record Ramp(int outline, int dark, int mid, int light, int hi) {
        int shade(final int level) {
            return switch (level) { case 0 -> outline; case 1 -> dark; case 2 -> mid; case 3 -> light; default -> hi; };
        }
    }

    static final Ramp COPPER = new Ramp(0x3D1D12, 0x8F482C, 0xC4683F, 0xE8915F, 0xFBC7A2);
    static final Ramp IRON = new Ramp(0x26272C, 0x6B6D75, 0xA6A8AF, 0xD3D5D9, 0xF6F7F8);
    static final Ramp DIAMOND = new Ramp(0x0B3538, 0x1B8483, 0x35C2B9, 0x86EEE2, 0xDBFFF9);
    static final Ramp NETHERITE = new Ramp(0x140F12, 0x33292E, 0x4F4247, 0x76666C, 0xA59499);
    static final Ramp WOOD = new Ramp(0x2A190D, 0x5A381C, 0x7E532B, 0xA2723C, 0xBE8C52);
    static final Ramp DARK_WOOD = new Ramp(0x1C120A, 0x3E2716, 0x5A3A22, 0x76502F, 0x8E6440);
    static final Ramp BRISTLE = new Ramp(0x4A3F2E, 0xB3A380, 0xD9CCAA, 0xEEE4C8, 0xFFFBEA);
    static final Ramp PAPER = new Ramp(0x0E2446, 0x1F4C8C, 0x2D66B3, 0x4683CF, 0x6FA3E6);
    static final Ramp PAPER_ROLL = new Ramp(0x0E2446, 0x285A9E, 0x3A78C6, 0x5B95DD, 0x8AB8F0);
    static final Ramp INK = new Ramp(0x0E2446, 0xB7D2F2, 0xCFE2F8, 0xE4EFFC, 0xFFFFFF);
    static final Ramp TERRACOTTA = new Ramp(0x3A170D, 0x8E3E24, 0xC25A34, 0xDE7B4E, 0xF3A57C);
    static final Ramp LID = new Ramp(0x301209, 0x74301B, 0xA4492A, 0xC2643C, 0xDB855C);
    static final Ramp HANDLE = new Ramp(0x1D1E22, 0x45474E, 0x686B73, 0x8E9199, 0xB4B7BE);
    static final Ramp BRASS = new Ramp(0x4A2F06, 0xA06E10, 0xD6A424, 0xF2CE55, 0xFFF0A6);
    static final Ramp CARD = new Ramp(0x16181C, 0x2B2E35, 0x3A3E47, 0x4E535E, 0x676D7A);
    static final Ramp WHITE = new Ramp(0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF);

    static Ramp emblem(final int base) {
        return new Ramp(darken(base, 0.30f), darken(base, 0.62f), base, lighten(base, 0.30f), lighten(base, 0.62f));
    }

    static final Map<String, Ramp> TIERS = new LinkedHashMap<>();
    static {
        TIERS.put("copper", COPPER);
        TIERS.put("iron", IRON);
        TIERS.put("diamond", DIAMOND);
        TIERS.put("netherite", NETHERITE);
    }

    // ------------------------------------------------------------------ canvas

    static final int N = 16;

    static final class Canvas {
        final char[][] part = new char[N][N];          // '\0' empty
        final int[][] shade = new int[N][N];           // -1 auto
        final Map<Character, Ramp> ramps = new LinkedHashMap<>();
        boolean outline = true;

        Canvas() {
            for (int[] row : shade) java.util.Arrays.fill(row, -1);
        }

        Canvas ramp(final char p, final Ramp r) { ramps.put(p, r); return this; }

        Canvas px(final char p, final int x, final int y) {
            if (x >= 0 && y >= 0 && x < N && y < N) part[y][x] = p;
            return this;
        }

        Canvas clear(final int x, final int y) { return px('\0', x, y); }

        Canvas rect(final char p, final int x0, final int y0, final int x1, final int y1) {
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) px(p, x, y);
            return this;
        }

        /** Pixels whose centre lies inside the polygon (x,y pairs in pixel space, 0..16). */
        Canvas poly(final char p, final double... xy) {
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) if (inside(xy, x + 0.5, y + 0.5)) px(p, x, y);
            }
            return this;
        }

        /** A capsule: pixels whose centre is within {@code r} of the segment. */
        Canvas line(final char p, final double x0, final double y0, final double x1, final double y1, final double r) {
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) if (segDist(x + 0.5, y + 0.5, x0, y0, x1, y1) <= r) px(p, x, y);
            }
            return this;
        }

        /** ASCII stamp: any non-space, non-'.' char is a part id at (ox+col, oy+row). */
        Canvas stamp(final int ox, final int oy, final String... rows) {
            for (int r = 0; r < rows.length; r++) {
                for (int c = 0; c < rows[r].length(); c++) {
                    final char ch = rows[r].charAt(c);
                    if (ch == '.' || ch == ' ') continue;
                    if (ch == '_') clear(ox + c, oy + r); else px(ch, ox + c, oy + r);
                }
            }
            return this;
        }

        /** Forces a shade level (0 outline .. 4 highlight) on an already filled pixel. */
        Canvas shade(final int x, final int y, final int level) {
            shade[y][x] = level;
            return this;
        }

        boolean filled(final int x, final int y) {
            return x >= 0 && y >= 0 && x < N && y < N && part[y][x] != '\0';
        }

        boolean same(final int x, final int y, final char p) {
            return x >= 0 && y >= 0 && x < N && y < N && part[y][x] == p;
        }

        BufferedImage render() {
            final BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    final char p = part[y][x];
                    if (p == '\0') {
                        if (!outline) continue;
                        // Outline: the darkest outline of the 4-neighbouring parts.
                        int best = -1;
                        double bestLum = Double.MAX_VALUE;
                        final int[][] nb = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
                        for (final int[] d : nb) {
                            if (!filled(x + d[0], y + d[1])) continue;
                            final Ramp r = ramps.get(part[y + d[1]][x + d[0]]);
                            if (r == null) continue;
                            final double lum = lum(r.outline);
                            if (lum < bestLum) { bestLum = lum; best = r.outline; }
                        }
                        if (best >= 0) img.setRGB(x, y, 0xFF000000 | best);
                        continue;
                    }
                    final Ramp r = ramps.get(p);
                    if (r == null) throw new IllegalStateException("no ramp for part '" + p + "'");
                    int level = shade[y][x];
                    if (level < 0) {
                        final boolean up = same(x, y - 1, p), left = same(x - 1, y, p);
                        final boolean down = same(x, y + 1, p), right = same(x + 1, y, p);
                        if (!up && !left) level = 4;
                        else if (!up || !left) level = 3;
                        else if (!down || !right) level = 1;
                        else level = 2;
                    }
                    img.setRGB(x, y, 0xFF000000 | r.shade(level));
                }
            }
            return img;
        }
    }

    static boolean inside(final double[] xy, final double px, final double py) {
        boolean in = false;
        for (int i = 0, j = xy.length - 2; i < xy.length; j = i, i += 2) {
            final double xi = xy[i], yi = xy[i + 1], xj = xy[j], yj = xy[j + 1];
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }

    static double segDist(final double px, final double py, final double x0, final double y0, final double x1, final double y1) {
        final double dx = x1 - x0, dy = y1 - y0;
        final double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : ((px - x0) * dx + (py - y0) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        final double cx = x0 + t * dx, cy = y0 + t * dy;
        return Math.hypot(px - cx, py - cy);
    }

    static double lum(final int rgb) {
        return 0.2126 * ((rgb >> 16) & 255) + 0.7152 * ((rgb >> 8) & 255) + 0.0722 * (rgb & 255);
    }

    static int darken(final int rgb, final float f) {
        return mix(rgb, 0x000000, 1 - f);
    }

    static int lighten(final int rgb, final float f) {
        return mix(rgb, 0xFFFFFF, f);
    }

    static int mix(final int a, final int b, final float t) {
        final int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        final int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        final int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ tools (M = tier metal)

    static Canvas trowel(final Ramp metal) {
        final Canvas c = new Canvas().ramp('W', WOOD).ramp('M', metal);
        // Brick trowel seen from above: a broad triangular blade, a short shank, the handle behind it.
        c.line('W', 1.7, 14.3, 4.0, 12.0, 1.3);
        c.line('M', 4.0, 12.0, 6.6, 9.4, 0.6);
        c.poly('M', 4.2, 6.6, 7.0, 5.2, 14.9, 1.1, 10.8, 9.0, 9.4, 11.8);
        return c;
    }

    static Canvas hammer(final Ramp metal) {
        final Canvas c = new Canvas().ramp('W', WOOD).ramp('M', metal);
        c.line('W', 1.6, 14.4, 9.0, 7.0, 1.05);
        // Head: a block across the handle's end; the striking face (lower right) is square, the claw (upper left) splits.
        c.poly('M', 5.9, 5.1, 8.9, 2.1, 14.1, 7.3, 11.1, 10.3);
        c.poly('M', 10.3, 10.9, 14.7, 6.5, 15.2, 7.9, 11.6, 11.5);
        c.clear(6, 3).clear(5, 4).clear(7, 2);
        c.px('M', 6, 4).px('M', 7, 3);
        c.clear(5, 5);
        return c;
    }

    static Canvas brush(final Ramp metal) {
        final Canvas c = new Canvas().ramp('W', WOOD).ramp('M', metal).ramp('B', BRISTLE).ramp('T', metal);
        c.line('W', 1.6, 14.4, 7.0, 9.0, 0.95);
        c.px('W', 1, 13).px('W', 2, 14);
        c.line('M', 6.8, 9.2, 9.0, 7.0, 1.45);
        c.poly('B', 7.6, 6.2, 12.7, 1.1, 14.9, 3.3, 9.8, 8.4);
        c.poly('T', 11.2, 2.6, 12.7, 1.1, 14.9, 3.3, 13.4, 4.8);
        return c;
    }

    static Canvas blueprint(final Ramp metal) {
        final Canvas c = new Canvas().ramp('P', PAPER).ramp('R', PAPER_ROLL).ramp('M', metal).ramp('I', INK);
        c.rect('P', 2, 4, 13, 13);
        c.rect('R', 2, 2, 13, 4);
        c.rect('M', 1, 2, 1, 4).rect('M', 14, 2, 14, 4);
        // A tiny floor plan in ink.
        c.stamp(4, 6,
            "IIIII.II",
            "I......I",
            "I..I...I",
            "I..I....",
            "I..IIIII",
            "IIIII..I");
        for (int y = 6; y <= 11; y++) for (int x = 4; x <= 11; x++) if (c.part[y][x] == 'I') c.shade(x, y, 2);
        c.shade(1, 2, 3).shade(14, 2, 3).shade(1, 4, 1).shade(14, 4, 1);
        // The tier's seal in the corner.
        c.rect('S', 11, 11, 13, 13).clear(13, 13).ramp('S', metal);
        return c;
    }

    static Canvas square(final Ramp metal) {
        final Canvas c = new Canvas().ramp('W', DARK_WOOD).ramp('M', metal);
        // Try square: a thick wooden stock and a thin metal blade with ruler ticks.
        c.rect('W', 2, 3, 4, 13);
        c.rect('M', 5, 3, 14, 5);
        c.rect('M', 2, 1, 4, 2);
        for (int x = 7; x <= 13; x += 2) c.shade(x, 5, 0);
        c.shade(10, 4, 0);
        c.shade(3, 1, 4);
        return c;
    }

    static Canvas chisel(final Ramp metal) {
        final Canvas c = new Canvas().ramp('W', WOOD).ramp('M', metal).ramp('F', metal);
        // Short fat handle, a ferrule ring, a flat blade that widens to its cutting edge.
        c.line('W', 2.0, 14.0, 5.9, 10.1, 1.4);
        c.line('F', 6.1, 9.9, 7.1, 8.9, 1.45);
        c.line('M', 7.1, 8.9, 11.4, 4.6, 0.8);
        c.poly('M', 10.7, 4.1, 12.6, 0.9, 15.1, 3.4, 11.9, 5.3);
        return c;
    }

    // ------------------------------------------------------------------ toolbox

    static Canvas toolbox() {
        final Canvas c = new Canvas().ramp('B', TERRACOTTA).ramp('L', LID).ramp('H', HANDLE).ramp('K', BRASS);
        c.rect('B', 1, 7, 14, 14);
        c.rect('L', 1, 5, 14, 7);
        c.rect('H', 5, 2, 10, 2).rect('H', 4, 3, 4, 4).rect('H', 11, 3, 11, 4);
        c.px('H', 5, 3).px('H', 10, 3);
        c.rect('K', 7, 7, 8, 9);
        // Front seam under the lid and a lighter band.
        for (int x = 1; x <= 14; x++) c.shade(x, 8, x == 1 ? 3 : 1);
        c.shade(7, 8, 3).shade(8, 8, 1);
        for (int x = 2; x <= 13; x++) if (x < 6 || x > 9) c.shade(x, 11, 3);
        c.shade(1, 11, 3);
        return c;
    }

    // ------------------------------------------------------------------ upgrades: card + 8x8 emblem

    static Canvas card(final int color, final String... emblem) {
        final Canvas c = new Canvas().ramp('K', CARD).ramp('S', emblem(color)).ramp('E', emblem(color)).ramp('e', emblem(lighten(color, 0.45f)))
            .ramp('G', BRASS).ramp('n', IRON);
        c.rect('K', 1, 2, 14, 13);
        c.rect('S', 1, 2, 2, 13);
        c.clear(1, 2).clear(14, 2).clear(1, 13).clear(14, 13);
        c.stamp(5, 4, emblem);
        // Contacts on the card's bottom edge.
        c.px('G', 5, 14).px('G', 7, 14).px('G', 9, 14).px('G', 11, 14);
        return c;
    }

    static final Map<String, Canvas> UPGRADES = new LinkedHashMap<>();
    static {
        UPGRADES.put("reach", card(0x5CC46A,
            "...EEEEE",
            "....EEEE",
            ".....EEE",
            "....EE.E",
            "...EE..E",
            "..EE....",
            ".EE.....",
            "EE......"));
        UPGRADES.put("capacity", card(0xE59A45,
            "EEE..EEE",
            "EE....EE",
            "E.eeee.E",
            "..eeee..",
            "..eeee..",
            "E.eeee.E",
            "EE....EE",
            "EEE..EEE"));
        UPGRADES.put("speed", card(0xF2CC3A,
            "....EEE.",
            "...EEE..",
            "..EEE...",
            ".EEEEEE.",
            "...EEE..",
            "..EEE...",
            ".EEE....",
            ".EE....."));
        UPGRADES.put("memory", card(0xA884E8,
            "..EEEE..",
            ".E....E.",
            "E......E",
            "E..e...E",
            "E..ee..E",
            "EE.....E",
            "EEE...E.",
            "EEEEEE.."));
        UPGRADES.put("supply_link", card(0x45C8D6,
            "....EEE.",
            "...E...E",
            "...E...E",
            ".eeeE.E.",
            "e...eE..",
            "e...e...",
            "e...e...",
            ".eee...."));
        UPGRADES.put("magnet", card(0xE0525A,
            ".EE..EE.",
            ".EE..EE.",
            ".EE..EE.",
            ".EE..EE.",
            ".EEEEEE.",
            "..EEEE..",
            "nn....nn",
            "nn....nn"));
        UPGRADES.put("efficiency", card(0x4FD08A,
            "EEEEEEEE",
            "EEEeeEEE",
            "EEEeeEEE",
            "EeeeeeeE",
            "EEEeeEEE",
            ".EEeeEE.",
            "..EEEE..",
            "...EE..."));
        // The magnet's poles: fix the horseshoe feet above the silver tips.
        final Canvas m = UPGRADES.get("magnet");
        m.stamp(5, 10, "nn....nn", "nn....nn");
    }

    // ------------------------------------------------------------------ GUI ghosts (white silhouettes, tinted in code)

    static BufferedImage ghost(final Canvas c) {
        final BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                if (c.filled(x, y)) img.setRGB(x, y, 0xFFFFFFFF);
            }
        }
        return img;
    }

    static Canvas pouchGhost() {
        // An isometric block outline: the pouch takes building blocks.
        final Canvas c = new Canvas().ramp('X', WHITE);
        c.stamp(1, 1,
            "......XXX.....",
            "....XX...XX...",
            "..XX.......XX.",
            "XX...........X",
            "X.XX.......XX.",
            "X...XX...XX..X",
            "X.....XXX....X",
            "X......X.....X",
            "X......X.....X",
            "X......X.....X",
            "X......X.....X",
            ".XX....X...XX.",
            "...XX..X.XX...",
            ".....XXXX.....");
        return c;
    }

    static Canvas upgradeGhost() {
        // A card outline with a plus: "an upgrade goes here".
        final Canvas c = new Canvas().ramp('X', WHITE);
        c.stamp(1, 2,
            ".XXXXXXXXXXXX.",
            "X............X",
            "X.....XX.....X",
            "X.....XX.....X",
            "X...XXXXXX...X",
            "X...XXXXXX...X",
            "X.....XX.....X",
            "X.....XX.....X",
            "X............X",
            ".XXXXXXXXXXXX.",
            "....X.X.X.X...");
        return c;
    }

    // ------------------------------------------------------------------ main

    public static void main(final String[] args) throws IOException {
        final File res = new File("common/building/src/main/resources/assets/slate_building/textures");
        if (!new File("common/building").isDirectory()) throw new IllegalStateException("run from the repository root");
        final File items = new File(res, "item");
        final File gui = new File(res, "gui/toolbox");
        items.mkdirs();
        gui.mkdirs();

        final Map<String, BufferedImage> sheet = new LinkedHashMap<>();
        write(items, "toolbox", toolbox().render(), sheet);
        for (final Map.Entry<String, Ramp> tier : TIERS.entrySet()) {
            final Ramp m = tier.getValue();
            final String t = tier.getKey();
            write(items, t + "_trowel", trowel(m).render(), sheet);
            write(items, t + "_hammer", hammer(m).render(), sheet);
            write(items, t + "_brush", brush(m).render(), sheet);
            write(items, t + "_blueprint", blueprint(m).render(), sheet);
            write(items, t + "_square", square(m).render(), sheet);
            write(items, t + "_chisel", chisel(m).render(), sheet);
        }
        for (final Map.Entry<String, Canvas> u : UPGRADES.entrySet()) write(items, u.getKey() + "_upgrade", u.getValue().render(), sheet);

        write(gui, "slot_trowel", ghost(trowel(IRON)), sheet);
        write(gui, "slot_hammer", ghost(hammer(IRON)), sheet);
        write(gui, "slot_brush", ghost(brush(IRON)), sheet);
        write(gui, "slot_blueprint", ghost(blueprint(IRON)), sheet);
        write(gui, "slot_square", ghost(square(IRON)), sheet);
        write(gui, "slot_chisel", ghost(chisel(IRON)), sheet);
        write(gui, "slot_upgrade", ghost(upgradeGhost()), sheet);
        write(gui, "slot_pouch", ghost(pouchGhost()), sheet);

        if (args.length > 0) preview(new File(args[0]), sheet);
        System.out.println("wrote " + sheet.size() + " textures");
    }

    static void write(final File dir, final String name, final BufferedImage img, final Map<String, BufferedImage> sheet) throws IOException {
        ImageIO.write(img, "png", new File(dir, name + ".png"));
        sheet.put(name, img);
    }

    /** A x8 contact sheet: each sprite on a dark and on a light tile, to judge both skins' slots. */
    static void preview(final File out, final Map<String, BufferedImage> sheet) throws IOException {
        final int s = 8, cell = N * s + 8, cols = 6;
        final List<Map.Entry<String, BufferedImage>> list = new ArrayList<>(sheet.entrySet());
        final int rows = (list.size() + cols - 1) / cols;
        final BufferedImage img = new BufferedImage(cols * cell * 2, rows * cell, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < list.size(); i++) {
            final int cx = (i % cols) * cell * 2, cy = (i / cols) * cell;
            for (int half = 0; half < 2; half++) {
                final int bg = half == 0 ? 0xFF1B1B1A : 0xFF8B8B8B;
                for (int y = 0; y < cell; y++) for (int x = 0; x < cell; x++) img.setRGB(cx + half * cell + x, cy + y, bg);
                final BufferedImage sp = list.get(i).getValue();
                final boolean ghost = list.get(i).getKey().startsWith("slot_");
                for (int y = 0; y < N; y++) {
                    for (int x = 0; x < N; x++) {
                        int argb = sp.getRGB(x, y);
                        if ((argb >>> 24) == 0) continue;
                        if (ghost) argb = half == 0 ? 0xFF4A4945 : 0xFF6F6F6F;
                        for (int yy = 0; yy < s; yy++) for (int xx = 0; xx < s; xx++) img.setRGB(cx + half * cell + 4 + x * s + xx, cy + 4 + y * s + yy, argb);
                    }
                }
            }
        }
        ImageIO.write(img, "png", out);
    }

    private Textures() {}
}
