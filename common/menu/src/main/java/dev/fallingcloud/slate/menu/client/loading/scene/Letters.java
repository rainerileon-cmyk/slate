package dev.fallingcloud.slate.menu.client.loading.scene;

/**
 * The title, built of blocks of stone: every letter five blocks high and a block deep, its strokes a block wide. The
 * game's own logo is a picture in its assets, which do not exist yet where the loading scene first runs, so the
 * scene builds the word the way the game builds everything.
 */
final class Letters {

    private static final String[][] WORD = {
        {"#...#", "##.##", "#.#.#", "#...#", "#...#"},   // M
        {"#", "#", "#", "#", "#"},                       // I
        {"#..#", "##.#", "#.##", "#..#", "#..#"},        // N
        {"###", "#..", "##.", "#..", "###"},             // E
        {"###", "#..", "#..", "#..", "###"},             // C
        {"###.", "#..#", "###.", "#.#.", "#..#"},        // R
        {"###", "#.#", "###", "#.#", "#.#"},             // A
        {"###", "#..", "##.", "#..", "#.."},             // F
        {"###", ".#.", ".#.", ".#.", ".#."},             // T
    };

    static final int HEIGHT = 5;
    static final float WIDTH;
    /** Half of how deep a letter is, and the room between two letters. */
    private static final float DEPTH = 0.9f, GAP = 0.8f;
    private static final Tile[] STONES = {Tile.STONE, Tile.STONE_B, Tile.STONE_C};

    static {
        float w = (WORD.length - 1) * GAP;
        for (final String[] letter : WORD) w += letter[0].length();
        WIDTH = w;
    }

    /** The word with its middle over the origin, standing on y = 0, a block deep round z = 0. */
    static void build(final Mesh m) {
        float x = -WIDTH / 2f;
        for (final String[] letter : WORD) {
            final int w = letter[0].length();
            for (int row = 0; row < HEIGHT; row++) {
                for (int col = 0; col < w; col++) {
                    if (!filled(letter, col, row)) continue;
                    final float x0 = x + col, y0 = HEIGHT - 1 - row;
                    // Only the faces nothing stands against.
                    int sides = 0;
                    if (!filled(letter, col, row - 1)) sides |= Mesh.TOP;
                    if (!filled(letter, col, row + 1)) sides |= Mesh.BOTTOM;
                    if (!filled(letter, col - 1, row)) sides |= Mesh.LEFT;
                    if (!filled(letter, col + 1, row)) sides |= Mesh.RIGHT;
                    // No two blocks of quite the same stone.
                    final int n = Math.round(x0 * 2f) + 128;
                    final int tone = Atlas.shade(0xFFFFFFFF, (0.88f + 0.12f * Atlas.hash(n, row, 5)) * (1f - 0.035f * row));
                    m.reset();
                    m.tile(STONES[(int) (Atlas.hash(n, row, 9) * 2.999f)]).faces(Mesh.FRONT).gloss(0.12f).colour(tone);
                    m.box(x0, y0, -DEPTH, x0 + 1, y0 + 1, DEPTH);
                    if (sides != 0) {
                        m.tile(Tile.STONE_SIDE).faces(sides);
                        m.box(x0, y0, -DEPTH, x0 + 1, y0 + 1, DEPTH);
                    }
                }
            }
            x += w + GAP;
        }
        m.reset();
    }

    private static boolean filled(final String[] letter, final int col, final int row) {
        return row >= 0 && row < HEIGHT && col >= 0 && col < letter[0].length() && letter[row].charAt(col) == '#';
    }

    private Letters() {}
}
