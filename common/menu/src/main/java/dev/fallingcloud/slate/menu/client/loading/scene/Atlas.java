package dev.fallingcloud.slate.menu.client.loading.scene;

/**
 * Paints the {@link Tile}s: pixel art of 16 x 16 each, made here pixel by pixel from a few colours per material, the
 * way the game's own textures are built (a highlight along the top and left, a shadow along the bottom and right, and
 * a little grain that is the same every time). Nothing of it is taken from the game or from a mod.
 */
final class Atlas {

    static final int SIDE = Tile.ACROSS * Tile.SIZE;

    private final int[] px = new int[SIDE * SIDE];
    private int ox, oy, seed;

    /** The whole atlas as ARGB, row by row from the top. */
    static int[] paint() {
        final Atlas a = new Atlas();
        a.all();
        return a.px;
    }

    private void all() {
        at(Tile.WHITE);
        fill(0xFFFFFFFF);
        casing(Tile.CASING, 0xFF3B3B38, 0xFFA3A39C, 0xFF84847D, 0xFF5F5F5A, 0xFF6B4F35);
        plate(Tile.CASING_TOP, 0xFF3B3B38, 0xFFA3A39C, 0xFF84847D, 0xFF5F5F5A);
        casing(Tile.BRASS, 0xFF5A3C14, 0xFFF4CB6A, 0xFFD6A03D, 0xFFA5722A, 0xFF5B422C);
        plate(Tile.BRASS_TOP, 0xFF5A3C14, 0xFFF4CB6A, 0xFFD6A03D, 0xFFA5722A);
        copper();
        plate(Tile.COPPER_TOP, 0xFF5A2E1E, 0xFFEB9C78, 0xFFC8744F, 0xFF98503A);
        glass();
        belt();
        beltSide();
        shaft();
        cog();
        cogFace();
        plinthTop();
        plinthSide();
        metal(Tile.IRON, 0xFF54585F, 0xFFE3E6EA, 0xFFB9BDC4, 0xFF858A93);
        metal(Tile.IRON_DARK, 0xFF24262A, 0xFF7C818A, 0xFF5A5E66, 0xFF3C3F45);
        raw();
        bar();
        barTop();
        gauge();
        flap();
        stone(Tile.STONE, 0xFFECECE6, 0xFFB4B4AE, 0xFF74746F, 0xFF62625E, true);
        stone(Tile.STONE_B, 0xFFECECE6, 0xFFB4B4AE, 0xFF74746F, 0xFF62625E, true);
        stone(Tile.STONE_C, 0xFFECECE6, 0xFFB4B4AE, 0xFF74746F, 0xFF62625E, true);
        stone(Tile.STONE_SIDE, 0xFF8E8E89, 0xFF70706C, 0xFF50504D, 0xFF3E3E3C, false);
        pipe();
        crate();
        tunnel();
        bed();
        beam();
        lamp();
        glassBack();
        ore();
        plates();
    }

    // ------------------------------------------------------------------ materials

    /** A frame of stone or metal round sunken boards. */
    private void casing(final Tile tile, final int outline, final int light, final int mid, final int dark, final int wood) {
        at(tile);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final int e = edge(x, y);
                int c;
                if (e == 0) {
                    c = outline;
                } else if (e == 1) {
                    final boolean lit = x == 1 || y == 1, shaded = x == 14 || y == 14;
                    c = lit && shaded ? mid : lit ? light : dark;
                } else if (e == 2) {
                    c = shade(mid, 0.96f + rnd(x, y) * 0.08f);
                } else if (e == 3) {
                    // The boards lie deeper than the frame: its shadow falls on their top and left.
                    c = x == 3 || y == 3 ? shade(wood, 0.52f) : shade(wood, 0.78f);
                } else {
                    final int board = (y - 4) / 4;
                    final float tone = 0.94f + hash(board, 3, seed) * 0.12f;
                    final float grain = 0.95f + hash(x / 2, y, seed) * 0.10f;
                    c = shade(wood, tone * grain);
                    if ((y - 4) % 4 == 0 && y > 4) c = shade(wood, 0.66f);
                    if ((y - 4) % 4 == 1) c = shade(c, 1.08f);
                }
                set(x, y, c);
            }
        }
        // A bolt in each corner of the frame.
        for (final int[] at : new int[][] {{2, 2}, {13, 2}, {2, 13}, {13, 13}}) {
            set(at[0], at[1], light);
            set(at[0] + (at[0] < 8 ? 1 : -1), at[1] + (at[1] < 8 ? 1 : -1), dark);
        }
    }

    /** The same frame round a plate of its own material, for tops and bottoms. */
    private void plate(final Tile tile, final int outline, final int light, final int mid, final int dark) {
        at(tile);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final int e = edge(x, y);
                int c;
                if (e == 0) {
                    c = outline;
                } else if (e == 1) {
                    final boolean lit = x == 1 || y == 1, shaded = x == 14 || y == 14;
                    c = lit && shaded ? mid : lit ? light : dark;
                } else if (e == 3) {
                    c = x == 3 || y == 3 ? dark : light;
                } else {
                    c = shade(mid, 0.95f + rnd(x, y) * 0.10f);
                    if (e >= 4 && (x == 7 || x == 8 || y == 7 || y == 8)) c = shade(mid, 0.84f);
                    if (e >= 6) c = x + y <= 15 ? light : dark;
                }
                set(x, y, c);
            }
        }
    }

    private void copper() {
        at(Tile.COPPER);
        final int outline = 0xFF5A2E1E, light = 0xFFEB9C78, mid = 0xFFC8744F, dark = 0xFF98503A;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final int e = edge(x, y);
                int c;
                if (e == 0) {
                    c = outline;
                } else if (e == 1) {
                    final boolean lit = x == 1 || y == 1, shaded = x == 14 || y == 14;
                    c = lit && shaded ? mid : lit ? light : dark;
                } else {
                    // Brushed: every row its own tone, a few brighter flecks.
                    c = shade(mid, 0.93f + hash(0, y, seed) * 0.14f);
                    if (rnd(x, y) > 0.9f) c = shade(c, 1.12f);
                    if (y == 7) c = dark;
                    if (y == 8) c = light;
                }
                set(x, y, c);
            }
        }
        for (final int[] at : new int[][] {{3, 3}, {12, 3}, {3, 12}, {12, 12}}) {
            set(at[0], at[1], light);
            set(at[0] + 1, at[1] + 1, outline);
        }
    }

    /** Hardly there: a frame, a breath of colour, one streak of light. */
    private void glass() {
        at(Tile.GLASS);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = 0x12D4F1F7;
                final int e = edge(x, y);
                if (e == 0) c = 0x80E4F8FC;
                if (e == 1 && (x == 1 || y == 1)) c = 0x30FFFFFF;
                final int d = x + y;
                if ((d == 10 || d == 11) && x >= 3 && x <= 8) c = 0x5CFFFFFF;
                if (d == 13 && x >= 6 && x <= 8) c = 0x34FFFFFF;
                set(x, y, c);
            }
        }
    }

    private void glassBack() {
        at(Tile.GLASS_BACK);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) set(x, y, edge(x, y) == 0 ? 0x50C4DCE2 : 0x0CD4F1F7);
        }
    }

    /** Specks of ore, white to be tinted, on nothing: laid over a lump of stone. */
    private void ore() {
        at(Tile.ORE);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final float blotch = hash(x / 3, y / 3, seed) * 0.55f + hash((x + 1) / 2, (y + 2) / 2, seed + 5) * 0.45f;
                int c = 0;
                if (blotch > 0.5f && edge(x, y) > 0) {
                    final float tone = hash(x, y, seed + 9);
                    c = tone > 0.75f ? 0xFFFFFFFF : tone > 0.3f ? 0xFFD2D2D2 : 0xFF9A9A9A;
                }
                set(x, y, c);
            }
        }
    }

    /** Dark rubber with arrowheads that point the way it runs (towards greater u). */
    private void belt() {
        at(Tile.BELT);
        final int base = 0xFF38383B;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c;
                if (y == 0 || y == 15) {
                    c = 0xFF232325;
                } else if (y == 1 || y == 14) {
                    c = 0xFF55555B;
                } else {
                    final int across = y <= 7 ? 7 - y : y - 8;
                    final int d = Math.floorMod(x + across, 8);
                    c = d == 0 ? 0xFF64646B : d == 7 ? 0xFF2A2A2D : shade(base, 0.95f + rnd(x, y) * 0.10f);
                    if (y == 2 || y == 13) c = shade(c, 0.85f);
                }
                set(x, y, c);
            }
        }
    }

    /** The edge of the belt as seen from beside it: five texels of rubber, then the dark under it. */
    private void beltSide() {
        at(Tile.BELT_SIDE);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c;
                if (y == 0) c = 0xFF5B5B61;
                else if (y < 4) c = x % 4 == 1 && y == 2 ? 0xFF6E6E75 : 0xFF2D2D30;
                else if (y == 4) c = 0xFF1B1B1D;
                else c = 0xFF232325;
                set(x, y, c);
            }
        }
    }

    /** A rod of stone-grey metal. Round it goes from light to dark and has one seam, so its turning shows. */
    private void shaft() {
        at(Tile.SHAFT);
        final int[] round = {0xFFA9A9A2, 0xFFA9A9A2, 0xFF94948D, 0xFF94948D, 0xFF85857F, 0xFF85857F, 0xFF74746F, 0xFF4E4E4A,
            0xFF74746F, 0xFF85857F, 0xFF85857F, 0xFF94948D, 0xFF94948D, 0xFF9E9E97, 0xFF9E9E97, 0xFF5F5F5A};
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = shade(round[y], 0.97f + rnd(x, y) * 0.06f);
                if (x == 0 || x == 8) c = shade(c, 0.78f);
                if (x == 1 || x == 9) c = shade(c, 1.08f);
                set(x, y, c);
            }
        }
    }

    /** The wood of a cogwheel's teeth. */
    private void cog() {
        at(Tile.COG);
        final int wood = 0xFF7D5C3B;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = shade(wood, (0.92f + hash(x / 3, y / 8, seed) * 0.10f) * (0.96f + rnd(x, y) * 0.08f));
                if (y % 4 == 3) c = shade(wood, 0.72f);
                final int e = edge(x, y);
                if (e == 0) c = shade(wood, x == 0 || y == 0 ? 1.18f : 0.6f);
                set(x, y, c);
            }
        }
    }

    /** A cogwheel from its side: a hub of metal, boards round it like the slices of a cake, a darker rim. */
    private void cogFace() {
        at(Tile.COG_FACE);
        final int wood = 0xFF7D5C3B, rim = 0xFF59402A, hub = 0xFF8C8C85;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final float dx = x - 7.5f, dy = y - 7.5f;
                final float r = (float) Math.sqrt(dx * dx + dy * dy);
                final double angle = Math.atan2(dy, dx);
                int c;
                if (r < 1.2f) c = 0xFF3A3A37;
                else if (r < 2.6f) c = dx + dy < 0 ? shade(hub, 1.14f) : shade(hub, 0.86f);
                else if (r < 3.6f) c = 0xFF3C2C1D;
                else if (r < 6.4f) {
                    final double slice = Math.abs(((angle / (Math.PI / 4)) % 1 + 1) % 1 - 0.5);
                    c = slice > 0.4 ? shade(wood, 0.7f) : shade(wood, 0.94f + rnd(x, y) * 0.12f);
                } else {
                    c = shade(rim, 0.94f + rnd(x, y) * 0.12f);
                }
                set(x, y, c);
            }
        }
    }

    /** One polished dark slab to the block. */
    private void plinthTop() {
        at(Tile.PLINTH_TOP);
        final int base = 0xFF303034;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = shade(base, 0.94f + rnd(x, y) * 0.12f);
                if (x == 0 || y == 0) c = 0xFF45454A;
                if (x == 15 || y == 15) c = 0xFF1C1C1F;
                if ((x == 0 && y == 15) || (x == 15 && y == 0)) c = base;
                // A long soft reflection across the slab.
                final int d = x - y;
                if (edge(x, y) > 1 && (d == 3 || d == 4)) c = shade(c, 1.12f);
                set(x, y, c);
            }
        }
    }

    /** The plinth from its side: a brass rail along the top, dark courses under it. */
    private void plinthSide() {
        at(Tile.PLINTH_SIDE);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c;
                if (y == 0) c = 0xFFF4CB6A;
                else if (y == 1) c = 0xFFB6842E;
                else if (y == 2) c = 0xFF121213;
                else {
                    final int course = (y - 3) / 3;
                    final boolean joint = (y - 3) % 3 == 2 || Math.floorMod(x + course * 8, 16) == 0;
                    c = joint ? 0xFF18181A : shade(0xFF2B2B2F, 0.92f + rnd(x, y) * 0.14f);
                    if (!joint && (y - 3) % 3 == 0) c = shade(c, 1.2f);
                }
                set(x, y, c);
            }
        }
    }

    /** A plate of steel with a bolt in each corner. */
    private void metal(final Tile tile, final int outline, final int light, final int mid, final int dark) {
        at(tile);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final int e = edge(x, y);
                int c;
                if (e == 0) {
                    c = outline;
                } else if (e == 1) {
                    final boolean lit = x == 1 || y == 1, shaded = x == 14 || y == 14;
                    c = lit && shaded ? mid : lit ? light : dark;
                } else {
                    c = shade(mid, 0.95f + hash(x, 0, seed) * 0.05f + rnd(x, y) * 0.05f);
                    final int d = x + y;
                    if (d == 13 || d == 14) c = shade(c, 1.1f);
                }
                set(x, y, c);
            }
        }
        for (final int[] at : new int[][] {{3, 3}, {12, 3}, {3, 12}, {12, 12}}) {
            set(at[0], at[1], light);
            set(at[0] + 1, at[1] + 1, outline);
        }
    }

    /** A rough lump of stone. */
    private void raw() {
        at(Tile.RAW);
        final int stone = 0xFF716B63;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final float blotch = hash(x / 4, y / 4, seed) * 0.5f + hash((x + 2) / 3, (y + 1) / 3, seed + 3) * 0.5f;
                int c = shade(stone, 0.82f + blotch * 0.3f + rnd(x, y) * 0.06f);
                final int e = edge(x, y);
                if (e == 0) c = shade(stone, x == 0 || y == 0 ? 1.22f : 0.58f);
                if (hash(x / 2, y / 2, seed + 11) > 0.92f) c = shade(stone, 0.6f);
                set(x, y, c);
            }
        }
    }

    /** A cast bar from its side, in greys to be tinted. */
    private void bar() {
        at(Tile.BAR);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int v = 0xC8 - y * 4;
                if (y == 0) v = 0xFF;
                if (x == 0) v = Math.min(255, v + 24);
                if (x == 15 || y == 15) v = 0x70;
                v = Math.round(v * (0.97f + rnd(x, y) * 0.06f));
                set(x, y, grey(Math.min(255, v)));
            }
        }
    }

    /** The bar's top: brighter, a streak of light across it, a stamped mark. */
    private void barTop() {
        at(Tile.BAR_TOP);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int v = 0xE2;
                final int e = edge(x, y);
                if (e == 0) v = x == 0 || y == 0 ? 0xFF : 0x9A;
                final int d = x + y;
                if (e > 0 && (d == 11 || d == 12)) v = 0xFF;
                if (e > 0 && d == 20) v = 0xF4;
                v = Math.round(v * (0.97f + rnd(x, y) * 0.06f));
                set(x, y, grey(Math.min(255, v)));
            }
        }
    }

    /** A round dial: a brass ring, a pale face, marks from the lower left over the top to the lower right. */
    private void gauge() {
        at(Tile.GAUGE);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final float dx = x - 7.5f, dy = y - 7.5f;
                final float r = (float) Math.sqrt(dx * dx + dy * dy);
                int c;
                if (r > 7.9f) c = 0;
                else if (r > 6.4f) c = dx + dy < 0 ? 0xFFF4CB6A : 0xFFA5722A;
                else if (r > 5.6f) c = 0xFF4A3A22;
                else {
                    c = shade(0xFFF0E7D2, 0.96f + rnd(x, y) * 0.05f);
                    if (dx + dy > 5) c = shade(c, 0.9f);
                }
                set(x, y, c);
            }
        }
        // Marks every 60 degrees of the 240 the needle sweeps.
        for (int i = 0; i <= 4; i++) {
            final double a = Math.toRadians(210 - i * 60);
            final int x = (int) Math.round(7.5 + Math.cos(a) * 4.4), y = (int) Math.round(7.5 - Math.sin(a) * 4.4);
            set(x, y, 0xFF3A2E1E);
        }
    }

    /** The strips that hang in the mouth of a tunnel. */
    private void flap() {
        at(Tile.FLAP);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final int strip = x % 4;
                int c = strip == 0 ? 0xFF4A4A4F : strip == 3 ? 0xFF1A1A1C : shade(0xFF2E2E32, 0.94f + rnd(x, y) * 0.12f);
                if (y == 15) c = strip == 0 || strip == 3 ? 0 : 0xFF1A1A1C;
                if (y == 0) c = 0xFF5F5F5A;
                set(x, y, c);
            }
        }
    }

    /** A block of hewn stone: what the letters of the title are built of. */
    private void stone(final Tile tile, final int light, final int mid, final int dark, final int crack, final boolean bevel) {
        at(tile);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final float blotch = hash(x / 4, y / 4, seed) * 0.5f + hash((x + 2) / 3, (y + 1) / 3, seed + 3) * 0.5f;
                int c = shade(mid, 0.86f + blotch * 0.28f + rnd(x, y) * 0.05f);
                final int e = edge(x, y);
                if (bevel) {
                    if (e == 0) c = x == 0 || y == 0 ? light : dark;
                    if (e == 1) c = x == 1 || y == 1 ? shade(light, 0.86f) : shade(dark, 1.22f);
                    if ((x == 0 && y == 15) || (x == 15 && y == 0)) c = mid;
                    if ((x == 1 && y == 14) || (x == 14 && y == 1)) c = mid;
                } else if (e == 0) {
                    c = shade(c, x == 0 || y == 0 ? 1.12f : 0.78f);
                }
                set(x, y, c);
            }
        }
    }

    /** A pile of sheets from its side: a light edge and a dark gap to every sheet, and the sheet on top of the pile. */
    private void plates() {
        at(Tile.PLATES);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                // A sheet is two texels: its edge in the light, and the gap to the one under it.
                int v = y % 2 == 0 ? 0xFF : 0xA2;
                v = Math.round(v * (0.93f + hash(x / 3, y / 2, seed) * 0.14f));
                if (x == 0) v = Math.min(255, v + 18);
                if (x == 15) v = Math.round(v * 0.72f);
                set(x, y, grey(Math.min(255, v)));
            }
        }
        at(Tile.PLATE_TOP);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int v = 0xDC;
                final int e = edge(x, y);
                if (e == 0) v = x == 0 || y == 0 ? 0xFF : 0x96;
                final int d = x + y;
                if (e > 0 && (d == 12 || d == 13)) v = 0xFF;
                if (e > 0 && d == 21) v = 0xF0;
                v = Math.round(v * (0.97f + rnd(x, y) * 0.06f));
                set(x, y, grey(Math.min(255, v)));
            }
        }
    }

    /** A copper pipe along u: round across, a band every half block. */
    private void pipe() {
        at(Tile.PIPE);
        final int[] round = {0xFF8A4A33, 0xFFC8744F, 0xFFF0A582, 0xFFE08C66, 0xFFC8744F, 0xFFB86946, 0xFFA85C3E, 0xFF8A4A33};
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = shade(round[y / 2], 0.96f + rnd(x, y) * 0.08f);
                if (x % 8 == 0) c = shade(c, 0.7f);
                if (x % 8 == 1) c = shade(c, 1.12f);
                set(x, y, c);
            }
        }
    }

    /** A crate of boards, braced corner to corner. */
    private void crate() {
        at(Tile.CRATE);
        final int frame = 0xFF8C6A42, board = 0xFFA9855A;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final int e = edge(x, y);
                int c;
                if (e == 0) c = shade(frame, x == 0 || y == 0 ? 1.16f : 0.62f);
                else if (e == 1) c = shade(frame, 0.95f + rnd(x, y) * 0.1f);
                else if (e == 2) c = shade(board, x == 2 || y == 2 ? 0.6f : 0.85f);
                else {
                    c = shade(board, (0.94f + hash(x / 3, 0, seed) * 0.1f) * (0.96f + rnd(x, y) * 0.08f));
                    if (x % 3 == 2) c = shade(board, 0.74f);
                    if (Math.abs(x - y) <= 1) c = shade(frame, x - y == 1 ? 0.7f : 1f);
                }
                set(x, y, c);
            }
        }
    }

    /** The housing of a tunnel: the casing's frame round a dark pane. */
    private void tunnel() {
        at(Tile.TUNNEL);
        final int outline = 0xFF3B3B38, light = 0xFFA3A39C, mid = 0xFF84847D, dark = 0xFF5F5F5A;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final int e = edge(x, y);
                int c;
                if (e == 0) {
                    c = outline;
                } else if (e == 1) {
                    final boolean lit = x == 1 || y == 1, shaded = x == 14 || y == 14;
                    c = lit && shaded ? mid : lit ? light : dark;
                } else if (e == 2) {
                    c = shade(mid, 0.96f + rnd(x, y) * 0.08f);
                } else if (e == 3) {
                    c = x == 3 || y == 3 ? 0xFF0E1214 : 0xFF2A363C;
                } else {
                    c = 0xFF1A2328;
                    final int d = x + y;
                    if (d == 12 || d == 13) c = 0xFF3A4C55;
                    if (d == 18) c = 0xFF2A3940;
                }
                set(x, y, c);
            }
        }
    }

    /** The bed a belt runs on, from its side: a dark panel with slits. */
    private void bed() {
        at(Tile.BED);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = shade(0xFF3D3D41, 0.94f + rnd(x, y) * 0.1f);
                if (y == 0) c = 0xFF5A5A60;
                if (y == 15) c = 0xFF1E1E20;
                if (x == 0) c = 0xFF55555A;
                if (x == 15) c = 0xFF242426;
                if (x >= 4 && x <= 11 && (y == 3 || y == 5 || y == 7)) c = 0xFF1B1B1D;
                if (x >= 4 && x <= 11 && (y == 4 || y == 6 || y == 8)) c = 0xFF4C4C52;
                if ((x == 2 || x == 13) && (y == 2 || y == 12)) c = 0xFF8A8A90;
                set(x, y, c);
            }
        }
    }

    /** A beam of grey alloy: what holds the machines up. */
    private void beam() {
        at(Tile.BEAM);
        final int mid = 0xFF7E7E78;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = shade(mid, 0.94f + rnd(x, y) * 0.1f);
                if (x == 0) c = 0xFFA6A69F;
                if (x == 3) c = 0xFF53534F;
                if (x > 3) c = shade(mid, (x % 4 == 0 ? 1.22f : x % 4 == 3 ? 0.68f : 1f) * (0.95f + rnd(x, y) * 0.1f));
                if (y % 8 == 7) c = shade(c, 0.7f);
                if (y % 8 == 0) c = shade(c, 1.15f);
                set(x, y, c);
            }
        }
    }

    /** A lamp: warm light behind a brass grille. */
    private void lamp() {
        at(Tile.LAMP);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final int e = edge(x, y);
                int c;
                if (e == 0) c = 0xFF5A3C14;
                else if (e == 1) c = x == 1 || y == 1 ? 0xFFF4CB6A : 0xFFA5722A;
                else {
                    final float dx = x - 7.5f, dy = y - 7.5f;
                    final float r = (float) Math.sqrt(dx * dx + dy * dy) / 8f;
                    c = mix(0xFFFFF3C4, 0xFFFFB347, Math.min(1f, r * 1.2f));
                    if (x % 4 == 0 || y % 4 == 0) c = mix(c, 0xFFA5722A, 0.55f);
                }
                set(x, y, c);
            }
        }
    }

    // ------------------------------------------------------------------ the brush

    private void at(final Tile tile) {
        ox = tile.column * Tile.SIZE;
        oy = tile.row * Tile.SIZE;
        seed = tile.ordinal() * 7919 + 17;
    }

    private void fill(final int argb) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) set(x, y, argb);
    }

    private void set(final int x, final int y, final int argb) {
        if (x < 0 || y < 0 || x > 15 || y > 15) return;
        px[(oy + y) * SIDE + ox + x] = argb;
    }

    /** How many texels in from the nearest edge of the tile. */
    private static int edge(final int x, final int y) {
        return Math.min(Math.min(x, 15 - x), Math.min(y, 15 - y));
    }

    private float rnd(final int x, final int y) {
        return hash(x, y, seed);
    }

    /** The same number from 0 to 1 for the same place, every time. */
    static float hash(final int x, final int y, final int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static int grey(final int v) {
        final int c = Math.max(0, Math.min(255, v));
        return 0xFF000000 | c << 16 | c << 8 | c;
    }

    static int shade(final int argb, final float f) {
        final int r = Math.min(255, Math.round(((argb >>> 16) & 0xFF) * f));
        final int g = Math.min(255, Math.round(((argb >>> 8) & 0xFF) * f));
        final int b = Math.min(255, Math.round((argb & 0xFF) * f));
        return argb & 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The colour with {@code by} times as much colour in it: what is grey stays grey, what is pale becomes full. */
    static int fuller(final int argb, final float by) {
        final int r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        final float grey = 0.299f * r + 0.587f * g + 0.114f * b;
        final int nr = Math.max(0, Math.min(255, Math.round(grey + (r - grey) * by)));
        final int ng = Math.max(0, Math.min(255, Math.round(grey + (g - grey) * by)));
        final int nb = Math.max(0, Math.min(255, Math.round(grey + (b - grey) * by)));
        return argb & 0xFF000000 | nr << 16 | ng << 8 | nb;
    }

    static int mix(final int a, final int b, final float t) {
        final float u = Math.max(0f, Math.min(1f, t));
        final int al = Math.round((a >>> 24) + ((b >>> 24) - (a >>> 24)) * u);
        final int r = Math.round(((a >>> 16) & 0xFF) + (((b >>> 16) & 0xFF) - ((a >>> 16) & 0xFF)) * u);
        final int g = Math.round(((a >>> 8) & 0xFF) + (((b >>> 8) & 0xFF) - ((a >>> 8) & 0xFF)) * u);
        final int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * u);
        return al << 24 | r << 16 | g << 8 | bl;
    }
}
