package dev.fallingcloud.slate.menu.client.loading.scene;

/**
 * The loading scene's own textures: tiles of 16 x 16, sixteen to a row in the top left corner of the {@link Sheet},
 * painted by {@link Atlas} when the scene starts. The scene runs before the game's assets exist, so it paints its own.
 */
enum Tile {

    WHITE(0, 0),
    /** Stone frame round dark boards: what the machines are housed in. */
    CASING(1, 0),
    CASING_TOP(2, 0),
    /** The same housing framed in brass, for what holds the finished bars. */
    BRASS(3, 0),
    BRASS_TOP(4, 0),
    COPPER(5, 0),
    COPPER_TOP(6, 0),
    GLASS(7, 0),
    BELT(8, 0),
    BELT_SIDE(9, 0),
    SHAFT(10, 0),
    COG(11, 0),
    COG_FACE(12, 0),
    PLINTH_TOP(13, 0),
    PLINTH_SIDE(14, 0),
    IRON(15, 0),
    IRON_DARK(0, 1),
    /** Grey, to be tinted: the lump that comes out of the tunnel. */
    RAW(1, 1),
    /** Grey, to be tinted: the bar the press makes of it. */
    BAR(2, 1),
    BAR_TOP(3, 1),
    GAUGE(4, 1),
    FLAP(5, 1),
    STONE(6, 1),
    STONE_SIDE(7, 1),
    PIPE(8, 1),
    CRATE(9, 1),
    TUNNEL(10, 1),
    BED(11, 1),
    BEAM(12, 1),
    LAMP(13, 1),
    /** Glass seen from inside, through the pane in front of it: a tint and its frame, no light on it. */
    GLASS_BACK(14, 1),
    /** The ore in a lump: specks to be tinted, nothing between them. */
    ORE(15, 1),
    STONE_B(0, 2),
    STONE_C(1, 2),
    /** Grey, to be tinted: sheets of metal lying on one another, seen from the side, eight to the block. */
    PLATES(2, 2),
    PLATE_TOP(3, 2);

    static final int ACROSS = 16, SIZE = 16;
    /** Kept off the edge of a tile by this much of a texel, so a face never takes a texel from the tile beside it. */
    private static final float INSET = 1f / 32f;

    final int column, row;

    Tile(final int column, final int row) {
        this.column = column;
        this.row = row;
    }

    /** {@code t} from 0 to 1 across the tile, as the atlas' coordinate. */
    float u(final float t) {
        return (column * SIZE + INSET + t * (SIZE - 2f * INSET)) / Sheet.SIDE;
    }

    float v(final float t) {
        return (row * SIZE + INSET + t * (SIZE - 2f * INSET)) / Sheet.SIDE;
    }
}
