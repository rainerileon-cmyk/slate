package dev.fallingcloud.slate.menu.client.loading.scene;

/**
 * The factory in the scene's own textures, for where the Create mod is not installed: a copper tank of lava, belts on
 * their beds, a press held from behind with the dial of the task on its housing, and a store of brass and glass the
 * bars are piled in.
 */
final class OwnFactory extends Factory {

    /** The thickness of a belt and half its width. */
    private static final float BELT_T = 0.3125f, BELT_W = 0.4375f, BELT_Y = BASE + 0.8125f;
    private static final float TANK_X = -7.25f, TANK_H = 3f;
    private static final float A_X0 = -5.875f, A_X1 = -0.375f;
    /** The second belt: level, then up, then level again over the store. */
    private static final float B_X0 = -0.125f, B_FLAT = 2.75f, RISE = 2.5f, B_TOP0 = B_FLAT + RISE, B_TOP1 = 6.4f;
    private static final float PRESS_X = 0.875f, HOUSING_Y = BASE + 2.0625f;
    private static final float SILO_X0 = 5.5f, SILO_X1 = 7.5f, SILO_Z = 1f, SILO_H = 3f;
    /** A lump and a bar. */
    private static final float LUMP = 0.44f, BAR_L = 0.5f, BAR_H = 0.19f, BAR_D = 0.28f;
    /** Bars to a layer of the store (along x and along z) and layers to the top. */
    private static final int STORE_X = 3, STORE_Z = 6, STORE_LAYERS = 14;
    private static final int STORE = STORE_X * STORE_Z * STORE_LAYERS;
    private static final float SQRT2 = 1.41421356f;

    private final Mesh fixed = new Mesh(8000), pile = new Mesh(8000);
    private boolean fixedBuilt;
    /** How many bars the pile was built for. */
    private int piled = -1;
    private final float[] at = new float[3];

    OwnFactory() {
        beltY = BELT_Y;
        lumpX0 = A_X0 + 0.5f;
        lumpX1 = A_X1 - 0.35f;
        way = new Path(lumpX1, BELT_Y, B_FLAT, BELT_Y, B_TOP0, BELT_Y + RISE, B_TOP1 - 0.05f, BELT_Y + RISE);
        pressX = PRESS_X;
        pressAt = PRESS_X - lumpX1;
        planned(-8.25f, -1.75f, 8.25f, 1.75f,
            new float[] {TANK_X - 0.5f, BASE, -0.5f, TANK_X + 0.5f, BASE + TANK_H + 0.2f, 0.5f},
            new float[] {PRESS_X - 0.5f, BASE, -1.2f, PRESS_X + 0.5f, HOUSING_Y + 1f, 0.5f},
            new float[] {B_FLAT, BASE, -0.5f, SILO_X1, BELT_Y + RISE + 0.45f, 0.5f},
            new float[] {SILO_X0, BASE, -SILO_Z, SILO_X1, BASE + SILO_H, SILO_Z});
        set(memoryAt, TANK_X, BASE + TANK_H + 0.125f, 0f);
        set(taskFrom, A_X0 + 0.1f, BELT_Y, 0.5f);
        set(taskTo, PRESS_X - 0.5f, HOUSING_Y, -1.125f);
        over = new float[][] {{-2.7f, BASE + 1.95f, -0.78f}, {A_X0 + 0.5f, BELT_Y + 1.25f, -0.4f}};
        set(totalAt, (SILO_X0 + SILO_X1) / 2f, BELT_Y + RISE + 0.75f, 0f);
    }

    @Override
    void free() {
        fixed.free();
        pile.free();
    }

    @Override
    float leaving(final Motion m) {
        final float drop = Math.max(0.15f, BELT_Y + RISE - pileTop(m));
        return (float) Math.sqrt(2f * drop / 12f);
    }

    /** How many bars are in the store. */
    private static int stored(final Motion m) {
        return Math.round(Math.max(0f, Math.min(1f, m.overall)) * STORE);
    }

    private static float pileTop(final Motion m) {
        final int layers = (stored(m) + STORE_X * STORE_Z - 1) / (STORE_X * STORE_Z);
        return BASE + 0.125f + layers * (BAR_H + 0.01f);
    }

    @Override
    void lamps(final Motion m, final SceneGl.Light light, final int accent) {
        // The lava lights what is near it, the more the higher it stands; it flickers a little.
        final float flicker = 0.92f + 0.08f * (float) Math.sin(m.time * 7.3) * (float) Math.sin(m.time * 3.1 + 1.0);
        final float lava = (0.35f + 0.65f * m.memory) * flicker;
        lamp(light, 0, TANK_X + 0.2f, BASE + 0.4f + 1.25f * m.memory, 0.9f, 4.6f, 1.0f * lava, 0.42f * lava, 0.10f * lava);
        // The bars in the store glow in their own colour.
        final float store = 0.10f + 0.34f * m.overall;
        lamp(light, 1, (SILO_X0 + SILO_X1) / 2f, pileTop(m) + 0.5f, 0.6f, 3.6f,
            ((accent >>> 16) & 0xFF) / 255f * store, ((accent >>> 8) & 0xFF) / 255f * store, (accent & 0xFF) / 255f * store);
        // The press flashes when it strikes.
        final float flash = m.stroke >= LoadingScene.PRESS_DOWN ? Math.max(0f, 1f - (m.stroke - LoadingScene.PRESS_DOWN) / 0.18f) : 0f;
        lamp(light, 2, PRESS_X, BELT_Y + 0.3f, 0.5f, 2.6f, 1.0f * flash, 0.8f * flash, 0.5f * flash);
    }

    @Override
    void build(final Motion m, final Frame f, final int accent) {
        f.plinth(-8.25f, -1.75f, 8.25f, 1.75f, BASE);
        // What never moves is built once and copied, the pile when a bar is added to it.
        if (!fixedBuilt) {
            fixedBuilt = true;
            fixed.clear();
            tankFrame(fixed);
            tunnel(fixed);
            beds(fixed);
            pressFrame(fixed);
            storeFrame(fixed);
            things(fixed, accent);
        }
        f.solid.add(fixed);
        tank(m, f);
        firstBelt(m, f, accent);
        secondBelt(m, f, accent);
        press(m, f, accent);
        store(m, f, accent);
        wheels(m, f.solid);
        air(m, f, -8f, 8f, 1f);
    }

    /** The tank: copper top and bottom, a post in every corner, a band round every block of its height. */
    private static void tankFrame(final Mesh m) {
        final float x0 = TANK_X - 0.5f, x1 = TANK_X + 0.5f, y0 = BASE, y1 = BASE + TANK_H;
        m.reset();
        m.tiles(Tile.COPPER, Tile.COPPER_TOP, Tile.COPPER_TOP).gloss(0.5f).ground(0.8f);
        m.box(x0, y0, -0.5f, x1, y0 + 0.25f, 0.5f);
        m.ground(1f);
        m.box(x0, y1 - 0.25f, -0.5f, x1, y1, 0.5f);
        m.tile(Tile.COPPER).texel(1, 0);
        for (int i = 0; i < 4; i++) {
            final float px = (i & 1) == 0 ? x0 : x1 - 0.0625f, pz = (i & 2) == 0 ? -0.5f : 0.4375f;
            m.box(px, y0 + 0.25f, pz, px + 0.0625f, y1 - 0.25f, pz + 0.0625f);
        }
        m.texel(0, 7);
        for (int i = 1; i < 3; i++) {
            final float by = y0 + i - 0.03125f;
            m.box(x0, by, 0.4375f, x1, by + 0.0625f, 0.5f);
            m.box(x0, by, -0.5f, x1, by + 0.0625f, -0.4375f);
            m.box(x0, by, -0.4375f, x0 + 0.0625f, by + 0.0625f, 0.4375f);
            m.box(x1 - 0.0625f, by, -0.4375f, x1, by + 0.0625f, 0.4375f);
        }
        // A cap with a valve on top.
        m.reset();
        m.tile(Tile.COPPER_TOP).gloss(0.5f);
        m.box(TANK_X - 0.1875f, y1, -0.1875f, TANK_X + 0.1875f, y1 + 0.125f, 0.1875f);
        m.reset();
    }

    /** What is in the tank: lava as high as the memory in use, behind glass, and the glow of it. */
    private static void tank(final Motion m, final Frame f) {
        final float x0 = TANK_X - 0.5f, x1 = TANK_X + 0.5f, y0 = BASE, y1 = BASE + TANK_H;
        final float level = 0.04f + 2.46f * m.memory;
        f.lava(x0 + 0.04f, y0 + 0.25f, -0.46f, x1 - 0.04f, y0 + 0.25f + level, 0.46f);
        panes(f.glass, x0 + 0.02f, y0 + 0.25f, -0.48f, x1 - 0.02f, y1 - 0.25f, 0.48f);
        final float heat = 0.25f + 0.75f * m.memory;
        f.halo(TANK_X, y0 + 0.25f + level * 0.5f, 0.55f, 1.3f + level * 0.55f, 0xFFFF7A1E, 0.34f * heat);
        f.halo(TANK_X, y0 + 0.25f + level, 0.55f, 0.9f, 0xFFFFB347, 0.22f * heat);
    }

    /** Walls of glass: the two the camera looks at with the light on them, the two behind only a tint. */
    private static void panes(final Mesh glass, final float x0, final float y0, final float z0, final float x1, final float y1, final float z1) {
        glass.reset();
        glass.tile(Tile.GLASS_BACK).faces(Mesh.BACK | Mesh.RIGHT).emissive(0.5f);
        glass.box(x0, y0, z0, x1, y1, z1);
        glass.tile(Tile.GLASS).faces(Mesh.FRONT | Mesh.LEFT).emissive(0.5f);
        glass.box(x0, y0, z0, x1, y1, z1);
        glass.reset();
    }

    /** Where the lumps come from: a housing over the start of the first belt, strips hanging in its mouth. */
    private static void tunnel(final Mesh m) {
        final float x0 = A_X0 + 0.0625f, x1 = x0 + 1f, y0 = BELT_Y - BELT_T, y1 = BELT_Y + 0.8125f;
        m.reset();
        m.tiles(Tile.TUNNEL, Tile.CASING_TOP, Tile.CASING_TOP).ground(0.85f);
        m.texel(0, 16f - (y1 - y0) * 16f % 16f);
        m.box(x0, y0, 0.4375f, x1, y1, 0.5f);
        m.box(x0, y0, -0.5f, x1, y1, -0.4375f);
        m.texel(0, 0);
        m.tiles(Tile.CASING, Tile.CASING_TOP, Tile.CASING_TOP);
        m.box(x0, y0, -0.4375f, x0 + 0.0625f, y1, 0.4375f);
        m.box(x0, y1 - 0.125f, -0.4375f, x1, y1, 0.4375f);
        // A hopper on its roof.
        m.tiles(Tile.CASING, Tile.IRON_DARK, Tile.CASING_TOP).ground(0.85f);
        m.box(x0 + 0.1875f, y1, -0.3125f, x1 - 0.1875f, y1 + 0.3125f, 0.3125f);
        m.tiles(Tile.BEAM, Tile.IRON_DARK, Tile.BEAM).ground(1f);
        m.box(x0 + 0.0625f, y1 + 0.3125f, -0.4375f, x1 - 0.0625f, y1 + 0.4375f, 0.4375f);
        // The strips, seen from both sides.
        m.reset();
        m.tile(Tile.FLAP);
        final float fy0 = BELT_Y + 0.03f, fy1 = y1 - 0.125f;
        final float v1 = Math.min(1f, fy1 - fy0);
        m.quad(x1, fy1, 0.4375f, x1, fy0, 0.4375f, x1, fy0, -0.4375f, x1, fy1, -0.4375f, 0f, 1f - v1, 0.875f, 1f);
        m.quad(x1, fy1, -0.4375f, x1, fy0, -0.4375f, x1, fy0, 0.4375f, x1, fy1, 0.4375f, 0f, 1f - v1, 0.875f, 1f);
        m.reset();
    }

    /** What the belts lie on: a bed under each level stretch, two posts and a pair of beams under the slope. */
    private static void beds(final Mesh m) {
        m.reset();
        m.tiles(Tile.BED, Tile.IRON_DARK, Tile.IRON_DARK).ground(0.78f);
        m.box(A_X0 + 0.125f, BASE, -0.375f, A_X1 - 0.125f, BELT_Y - BELT_T, 0.375f);
        m.box(B_X0 + 0.125f, BASE, -0.375f, B_FLAT - 0.25f, BELT_Y - BELT_T, 0.375f);

        final float slope = RISE * SQRT2;
        m.reset();
        m.tile(Tile.BEAM).ground(0.8f);
        for (final float px : new float[] {3.55f, 4.85f}) {
            final float under = BELT_Y + (px - B_FLAT) - BELT_T * SQRT2;
            m.box(px - 0.125f, BASE, -0.125f, px + 0.125f, under - 0.05f, 0.125f);
            m.box(px - 0.25f, BASE, -0.25f, px + 0.25f, BASE + 0.125f, 0.25f);
        }
        m.ground(1f);
        m.push();
        m.translate(B_FLAT, BELT_Y, 0);
        m.rotateZ(45f);
        m.box(0.35f, -BELT_T - 0.14f, -0.3125f, slope - 0.1f, -BELT_T, -0.1875f);
        m.box(0.35f, -BELT_T - 0.14f, 0.1875f, slope - 0.1f, -BELT_T, 0.3125f);
        m.pop();
        m.reset();
    }

    private void firstBelt(final Motion m, final Frame f, final int accent) {
        f.solid.push();
        f.solid.translate(A_X0, BELT_Y, 0);
        belt(f.solid, A_X1 - A_X0, m.beltA, m.beltA);
        f.solid.pop();
    }

    private void secondBelt(final Motion m, final Frame f, final int accent) {
        final Mesh solid = f.solid;
        final float flat = B_FLAT - B_X0, slope = RISE * SQRT2, top = B_TOP1 - B_TOP0;
        solid.push();
        solid.translate(B_X0, BELT_Y, 0);
        belt(solid, flat, m.beltB, m.beltB);
        solid.pop();
        solid.push();
        solid.translate(B_FLAT, BELT_Y, 0);
        solid.rotateZ(45f);
        belt(solid, slope, m.beltB - flat, m.beltB);
        solid.pop();
        solid.push();
        solid.translate(B_TOP0, BELT_Y + RISE, 0);
        belt(solid, top, m.beltB - flat - slope, m.beltB);
        solid.pop();
        for (final Motion.Item it : m.items) item(m, solid, accent, it);
    }

    /**
     * A belt from the origin along +x with its top at y = 0: the rubber running over a roller at each end. The
     * pattern on it has moved on by {@code travel}; its rollers have turned as far as {@code turned} blocks of belt.
     */
    private static void belt(final Mesh solid, final float length, final float travel, final float turned) {
        final float r = BELT_T / 2f;
        solid.reset();
        // The sides and the underside.
        solid.tiles(Tile.BELT_SIDE, Tile.BELT, Tile.BELT_SIDE).faces(Mesh.FRONT | Mesh.BACK | Mesh.BOTTOM);
        solid.box(r, -BELT_T, -BELT_W, length - r, 0f, BELT_W);
        // The top, its arrows running.
        solid.reset();
        solid.tile(Tile.BELT);
        float x = r;
        final float end = length - r;
        while (x < end - 1e-4f) {
            final float phase = x - travel;
            float into = phase - (float) Math.floor(phase);
            if (into > 1f - 1e-4f) into = 0f;
            final float span = Math.min(end - x, 1f - into);
            solid.quad(x, 0f, -BELT_W, x, 0f, BELT_W, x + span, 0f, BELT_W, x + span, 0f, -BELT_W, into, 0f, into + span, 1f);
            x += span;
        }
        // The rollers, and the shafts they turn on.
        final float angle = -turned / r * 57.29578f;
        for (final float cx : new float[] {r, length - r}) {
            solid.reset();
            solid.tiles(Tile.IRON_DARK, Tile.SHAFT, Tile.SHAFT).colour(0xFF8A8A90);
            solid.push();
            solid.translate(cx, -r, 0f);
            solid.rotateZ(angle);
            solid.cylinderZ(0f, 0f, -BELT_W, BELT_W, r, 10);
            solid.reset();
            solid.tiles(Tile.SHAFT, Tile.SHAFT, Tile.SHAFT);
            solid.cylinderZ(0f, 0f, -BELT_W - 0.1f, BELT_W + 0.1f, 0.07f, 8);
            solid.pop();
        }
        solid.reset();
    }

    /** A lump of ore standing on a belt at {@code x}, turned by {@code slope} degrees with the belt under it. */
    private static void lump(final Mesh solid, final int accent, final float x, final float y, final float slope, final float squash) {
        solid.push();
        solid.translate(x, y, 0f);
        solid.rotateZ(slope);
        final float h = LUMP * (1f - squash) + BAR_H * squash, half = LUMP / 2f * (1f + 0.25f * squash);
        solid.reset();
        solid.tile(Tile.RAW).ground(0.8f);
        solid.box(-half, 0f, -half, half, h, half);
        solid.reset();
        solid.tile(Tile.ORE).colour(Atlas.mix(accent, 0xFFFFFFFF, 0.12f)).emissive(0.25f + 0.6f * squash).gloss(0.5f);
        final float o = 0.004f;
        solid.box(-half - o, -o, -half - o, half + o, h + o, half + o);
        solid.reset();
        solid.pop();
    }

    /** A bar lying somewhere: along x, or across it. */
    private static void bar(final Mesh m, final int colour, final float x, final float y, final float z, final float slope,
                            final boolean along, final float shine) {
        m.push();
        m.translate(x, y, z);
        m.rotateZ(slope);
        if (!along) m.rotateY(90f);
        castBar(m, colour, shine);
        m.pop();
    }

    /** A cast bar at the origin, its long side along x, standing on y = 0: wider at the bottom than at the top. */
    private static void castBar(final Mesh m, final int colour, final float shine) {
        final float hx = BAR_L / 2f, hz = BAR_D / 2f, in = 0.045f, h = BAR_H;
        m.reset();
        m.colour(colour).gloss(0.55f).emissive(0.10f + 0.5f * shine);
        m.tile(Tile.BAR_TOP);
        m.quad(-hx + in, h, -hz + in, -hx + in, h, hz - in, hx - in, h, hz - in, hx - in, h, -hz + in, 0f, 0f, 1f, 1f);
        m.tile(Tile.BAR);
        m.quad(-hx + in, h, hz - in, -hx, 0f, hz, hx, 0f, hz, hx - in, h, hz - in, 0f, 0f, 1f, 0.4f);
        m.quad(hx - in, h, -hz + in, hx, 0f, -hz, -hx, 0f, -hz, -hx + in, h, -hz + in, 0f, 0f, 1f, 0.4f);
        m.quad(hx - in, h, hz - in, hx, 0f, hz, hx, 0f, -hz, hx - in, h, -hz + in, 0f, 0f, 0.6f, 0.4f);
        m.quad(-hx + in, h, -hz + in, -hx, 0f, -hz, -hx, 0f, hz, -hx + in, h, hz - in, 0f, 0f, 0.6f, 0.4f);
        m.reset();
    }

    /** What is on the belts: where its way along them puts it. */
    private void item(final Motion m, final Mesh solid, final int accent, final Motion.Item it) {
        float x, y, slope;
        if (it.leaving >= 0f) {
            // Off the end: on with the speed it had, and down.
            way.at(way.length(), at);
            x = at[0] + BELT_SPEED * 0.55f * it.leaving;
            y = Math.max(pileTop(m), at[1] - 6f * it.leaving * it.leaving);
            slope = -Math.min(1f, it.leaving / leaving(m)) * 18f;
        } else {
            place(it.s, at);
            x = at[0];
            y = at[1];
            slope = at[2];
        }
        if (it.pressed) {
            // Still hot from the press for a little way.
            bar(solid, accent, x, y, 0f, slope, false, it.s < pressAt + 1.2f ? 1f - (it.s - pressAt) / 1.2f : 0f);
        } else {
            final float under = Math.abs(it.s - pressAt) < 0.05f ? m.pressDown : 0f;
            lump(solid, accent, x, y, slope, under * under);
        }
    }

    /** The press's stand and housing, with the face of the dial on it. */
    private static void pressFrame(final Mesh m) {
        final float y0 = HOUSING_Y, y1 = y0 + 1f;
        m.reset();
        m.tile(Tile.BEAM).ground(0.8f);
        for (final float px : new float[] {PRESS_X - 0.5f, PRESS_X + 0.25f}) {
            m.box(px, BASE, -1.125f, px + 0.25f, y1, -0.875f);
            m.box(px - 0.0625f, BASE, -1.1875f, px + 0.3125f, BASE + 0.125f, -0.8125f);
        }
        m.ground(1f);
        m.box(PRESS_X - 0.5f, y1 - 0.25f, -0.875f, PRESS_X + 0.5f, y1, -0.5f);
        m.reset();
        m.tiles(Tile.CASING, Tile.CASING_TOP, Tile.CASING_TOP);
        m.box(PRESS_X - 0.5f, y0, -0.5f, PRESS_X + 0.5f, y1, 0.5f);
        m.reset();
        m.tile(Tile.GAUGE).emissive(0.25f);
        final float gy = y0 + 0.5f, g = 0.36f, gz = 0.505f;
        m.quad(PRESS_X - g, gy + g, gz, PRESS_X - g, gy - g, gz, PRESS_X + g, gy - g, gz, PRESS_X + g, gy + g, gz, 0f, 0f, 1f, 1f);
        m.reset();
        m.colour(0xFF3A2E1E);
        m.box(PRESS_X - 0.04f, gy - 0.04f, gz + 0.02f, PRESS_X + 0.04f, gy + 0.04f, gz + 0.035f);
        m.reset();
    }

    /** What moves of the press: the shaft through it, the needle of the dial, the ram. */
    private static void press(final Motion m, final Frame f, final int accent) {
        final Mesh solid = f.solid;
        final float y0 = HOUSING_Y;
        solid.reset();
        solid.tile(Tile.SHAFT);
        solid.push();
        solid.translate(PRESS_X, y0 + 0.5f, 0f);
        solid.rotateY(90f);
        solid.rotateZ(m.beltB * 120f);
        solid.cylinderZ(0f, 0f, -0.75f, 0.75f, 0.09f, 8);
        solid.pop();

        // The needle: from the lower left over the top to the lower right.
        solid.reset();
        solid.colour(Atlas.shade(accent, 0.8f)).emissive(0.5f);
        solid.push();
        solid.translate(PRESS_X, y0 + 0.5f, 0.515f);
        solid.rotateZ(210f - 240f * Math.max(0f, Math.min(1f, m.needle)));
        solid.box(-0.05f, -0.022f, 0f, 0.27f, 0.022f, 0.012f);
        solid.pop();

        // The ram: up under the housing, or down on what lies on the belt.
        final float top = y0 - 0.25f, bottom = BELT_Y + BAR_H + 0.002f;
        final float head = top + (bottom - top) * m.pressDown;
        solid.reset();
        solid.tile(Tile.IRON_DARK).gloss(0.4f);
        solid.box(PRESS_X - 0.125f, head + 0.1875f, -0.125f, PRESS_X + 0.125f, y0, 0.125f);
        solid.tile(Tile.IRON).gloss(0.7f);
        solid.box(PRESS_X - 0.4375f, head + 0.0625f, -0.4375f, PRESS_X + 0.4375f, head + 0.1875f, 0.4375f);
        solid.tile(Tile.IRON_DARK);
        solid.box(PRESS_X - 0.3125f, head, -0.3125f, PRESS_X + 0.3125f, head + 0.0625f, 0.3125f);
        solid.reset();

        if (m.stroke >= LoadingScene.PRESS_DOWN) {
            final float flash = Math.max(0f, 1f - (m.stroke - LoadingScene.PRESS_DOWN) / 0.2f);
            f.halo(PRESS_X, BELT_Y + 0.2f, 0.45f, 1.1f, 0xFFFFD28A, 0.5f * flash);
        }
    }

    /** The store: a floor and four posts of brass, a band round every block of its height. */
    private static void storeFrame(final Mesh m) {
        final float y0 = BASE, y1 = BASE + SILO_H;
        m.reset();
        m.tiles(Tile.BRASS, Tile.BRASS_TOP, Tile.BRASS_TOP).gloss(0.5f).ground(0.85f);
        m.box(SILO_X0, y0, -SILO_Z, SILO_X1, y0 + 0.125f, SILO_Z);
        m.ground(1f);
        m.tile(Tile.BRASS).texel(1, 0);
        for (int i = 0; i < 4; i++) {
            final float px = (i & 1) == 0 ? SILO_X0 : SILO_X1 - 0.125f, pz = (i & 2) == 0 ? -SILO_Z : SILO_Z - 0.125f;
            m.box(px, y0 + 0.125f, pz, px + 0.125f, y1, pz + 0.125f);
        }
        m.texel(0, 1);
        for (int i = 1; i <= 3; i++) {
            final float by = y0 + i - (i == 3 ? 0.125f : 0.0625f), bh = i == 3 ? 0.125f : 0.0625f;
            m.box(SILO_X0 + 0.125f, by, SILO_Z - 0.125f, SILO_X1 - 0.125f, by + bh, SILO_Z);
            m.box(SILO_X0 + 0.125f, by, -SILO_Z, SILO_X1 - 0.125f, by + bh, -SILO_Z + 0.125f);
            m.box(SILO_X0, by, -SILO_Z + 0.125f, SILO_X0 + 0.125f, by + bh, SILO_Z - 0.125f);
            m.box(SILO_X1 - 0.125f, by, -SILO_Z + 0.125f, SILO_X1, by + bh, SILO_Z - 0.125f);
        }
        m.reset();
    }

    /** What is in the store: the bars piled layer on layer, behind glass, and the glow of them. */
    private void store(final Motion m, final Frame f, final int accent) {
        final float y0 = BASE, y1 = BASE + SILO_H;
        panes(f.glass, SILO_X0 + 0.04f, y0 + 0.125f, -SILO_Z + 0.04f, SILO_X1 - 0.04f, y1 - 0.125f, SILO_Z - 0.04f);

        final int count = stored(m), layer = STORE_X * STORE_Z;
        final float cx = (SILO_X0 + SILO_X1) / 2f;
        if (count != piled) {
            // Every other layer lies across the one under it, as bars are stacked. All but the last bar: that one
            // is still settling.
            piled = count;
            pile.clear();
            final int top = (count - 1) / layer;
            for (int i = 0; i < count - 1; i++) {
                final int l = i / layer, in = i % layer;
                final int a = in % STORE_X, b = in / STORE_X;
                // Only what can be seen: the outside of the pile and its two top layers.
                if (l < top - 1 && a > 0 && a < STORE_X - 1 && b > 0 && b < STORE_Z - 1) continue;
                place(pile, accent, i, 0f, 0f);
            }
        }
        f.solid.add(pile);
        if (count > 0) {
            final float settle = 1f - m.landed;
            place(f.solid, accent, count - 1, 0.06f * settle * settle, settle);
            f.halo(cx, pileTop(m) + 0.1f, 0.2f, 1.5f, Atlas.mix(accent, 0xFFFFFFFF, 0.2f), 0.10f + 0.16f * m.overall + 0.2f * settle);
        }
    }

    /** The bar that is number {@code i} in the store, where it lies. */
    private static void place(final Mesh m, final int accent, final int i, final float lift, final float shine) {
        final int layer = STORE_X * STORE_Z;
        final int l = i / layer, in = i % layer;
        final int a = in % STORE_X, b = in / STORE_X;
        final boolean along = (l & 1) == 0;
        final float cx = (SILO_X0 + SILO_X1) / 2f;
        final float pitchLong = (SILO_X1 - SILO_X0 - 0.3f) / STORE_X, pitchShort = (2f * SILO_Z - 0.3f) / STORE_Z;
        final float u = (a - (STORE_X - 1) / 2f) * pitchLong, v = (b - (STORE_Z - 1) / 2f) * pitchShort;
        final float y = BASE + 0.125f + l * (BAR_H + 0.01f) + lift;
        bar(m, accent, along ? cx + u : cx + v, y, along ? v : u, 0f, along, shine);
    }

    /** Cogwheels behind the first belt: a large one and the small one that drives it, turning as the belt moves. */
    private static void wheels(final Motion m, final Mesh solid) {
        final float turn = m.beltA * 70f;
        wheel(solid, -2.7f, BASE + 1.0f, -0.78f, 0.95f, 16, turn);
        wheel(solid, -1.37f, BASE + 1.0f - 0.18f, -0.78f, 0.45f, 8, -turn * 2f + 22.5f);
    }

    private static void wheel(final Mesh solid, final float x, final float y, final float z, final float radius, final int teeth, final float angle) {
        solid.push();
        solid.translate(x, y, z);
        solid.rotateZ(angle);
        solid.reset();
        solid.tiles(Tile.COG, Tile.COG_FACE, Tile.COG_FACE);
        solid.cylinderZ(0f, 0f, -0.11f, 0.11f, radius * 0.84f, teeth * 2);
        solid.tile(Tile.COG);
        final float w = (float) (Math.PI * radius / teeth) * 0.52f;
        for (int i = 0; i < teeth; i++) {
            solid.push();
            solid.rotateZ(i * 360f / teeth);
            solid.box(radius * 0.8f, -w, -0.09f, radius, w, 0.09f);
            solid.pop();
        }
        solid.tile(Tile.SHAFT);
        solid.cylinderZ(0f, 0f, -0.35f, 0.3f, 0.075f, 8);
        solid.reset();
        solid.pop();
    }

    /** What stands about: crates by the tank and before the belt, two bars that never made it to the store. */
    private static void things(final Mesh m, final int accent) {
        m.reset();
        m.tile(Tile.CRATE).ground(0.78f);
        m.push();
        m.translate(-6.3f, BASE, -1.05f);
        m.rotateY(12f);
        m.box(-0.375f, 0f, -0.375f, 0.375f, 0.75f, 0.375f);
        m.pop();
        m.push();
        m.translate(-4.1f, BASE, 1.2f);
        m.rotateY(31f);
        m.box(-0.25f, 0f, -0.25f, 0.25f, 0.5f, 0.25f);
        m.pop();
        bar(m, accent, 3.4f, BASE, 1.2f, 0f, true, 0f);
        m.push();
        m.translate(3.95f, BASE, 1.05f);
        m.rotateY(40f);
        castBar(m, accent, 0f);
        m.pop();
        m.reset();
    }
}
