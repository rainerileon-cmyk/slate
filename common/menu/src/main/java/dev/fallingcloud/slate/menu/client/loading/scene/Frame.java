package dev.fallingcloud.slate.menu.client.loading.scene;

/**
 * The meshes of a frame of the factory, one for each way of drawing: the floor, the plinth's sides and its polished
 * top, all that is solid, all that lets light through, and light itself.
 */
final class Frame {

    final Mesh ground = new Mesh(64), stage = new Mesh(2048), polish = new Mesh(2048), solid = new Mesh(24000), glass = new Mesh(4096),
        glow = new Mesh(2048);
    /** The camera the frame is seen by: lights in the air are turned to it. */
    Camera camera;

    void clear() {
        ground.clear();
        stage.clear();
        polish.clear();
        solid.clear();
        glass.clear();
        glow.clear();
    }

    void free() {
        ground.free();
        stage.free();
        polish.free();
        solid.free();
        glass.free();
        glow.free();
    }

    /** A soft light in the air, turned to the camera. */
    void halo(final float x, final float y, final float z, final float radius, final int colour, final float strength) {
        glow.reset();
        glow.kind(Mesh.GLOW).colour(Math.round(Math.max(0f, Math.min(1f, strength)) * 255f) << 24 | colour & 0xFFFFFF);
        final float rx = camera.right[0] * radius, ry = camera.right[1] * radius, rz = camera.right[2] * radius;
        final float ux = camera.up[0] * radius, uy = camera.up[1] * radius, uz = camera.up[2] * radius;
        glow.rawQuad(x - rx + ux, y - ry + uy, z - rz + uz, x - rx - ux, y - ry - uy, z - rz - uz,
            x + rx - ux, y + ry - uy, z + rz - uz, x + rx + ux, y + ry + uy, z + rz + uz,
            0, 0, 1, 0, 0, 0, 1, 1, 1, 1, 0);
    }

    /** A body of lava: texel by texel from the shader, so it flows. */
    void lava(final float x0, final float y0, final float z0, final float x1, final float y1, final float z1) {
        final Mesh m = solid;
        m.reset();
        m.kind(Mesh.LAVA);
        final float s = 16f;
        m.rawQuad(x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, 0, 0, 1, x0 * s, -y1 * s, x0 * s, -y0 * s, x1 * s, -y0 * s, x1 * s, -y1 * s);
        m.rawQuad(x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 0, 0, -1, -x1 * s, -y1 * s, -x1 * s, -y0 * s, -x0 * s, -y0 * s, -x0 * s, -y1 * s);
        m.rawQuad(x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 1, 0, 0, -z1 * s, -y1 * s, -z1 * s, -y0 * s, -z0 * s, -y0 * s, -z0 * s, -y1 * s);
        m.rawQuad(x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, -1, 0, 0, z0 * s, -y1 * s, z0 * s, -y0 * s, z1 * s, -y0 * s, z1 * s, -y1 * s);
        m.rawQuad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, x0 * s, z0 * s + 40f, x0 * s, z1 * s + 40f, x1 * s, z1 * s + 40f, x1 * s, z0 * s + 40f);
        m.reset();
    }

    /** The plinth everything stands on: dark courses with a brass rail, a polished top that mirrors what stands on it. */
    void plinth(final float x0, final float z0, final float x1, final float z1, final float top) {
        stage.reset();
        stage.tiles(Tile.PLINTH_SIDE, Tile.PLINTH_TOP, Tile.PLINTH_TOP).faces(Mesh.SIDES);
        stage.box(x0, 0f, z0, x1, top, z1);
        polish.reset();
        polish.tile(Tile.PLINTH_TOP).faces(Mesh.TOP).gloss(0.3f).colour(0xD8FFFFFF);
        polish.box(x0, 0f, z0, x1, top, z1);
    }
}
