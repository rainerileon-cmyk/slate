package dev.fallingcloud.slate.menu.client.loading.scene;

/**
 * The factory built of the Create mod's own blocks, as a player would build it in the game: a fluid tank of lava, an
 * andesite tunnel the brass ingots come out of, belts on their pulleys, a mechanical press that makes sheets of them,
 * cogwheels that turn with the belts, and an item vault the sheets go into through a funnel. The vault has a window
 * in its door, which Create's has not: how far the loading is, is how high the sheets are stacked behind it, and they
 * are the very sheets the press makes, the same model that rides the belt. Every block stands on the block grid, and
 * every model and texture is read from the Create the player has installed.
 */
final class CreateFactory extends Factory {

    /** The rows of blocks from the front: the belts' row is 0, behind it -1 and -2. */
    private static final int ROW = 0, BACK = -1;
    /** The blocks along x: the tank, the first belt from A0 to A1, the second from B0, the slope, the top, the vault. */
    private static final int TANK = -7, A0 = -5, A1 = -1, B0 = 0, B1 = 1, D0 = 2, D1 = 3, T0 = 4, T1 = 5, VAULT = 6;
    private static final int PRESS = 1, PRESS_LEVEL = 2, VAULT_SIDE = 3;
    private static final float BELT_Y = BASE + 13f / 16f;
    /** How many blocks the vault is deep, and the plinth: from left to right, from back to front. */
    private static final int VAULT_DEEP = 2;
    private static final float X0 = -8f, X1 = 9.75f, Z0 = -2f, Z1 = 1.5f;
    /**
     * Where in its block a slope of Create's leaves the level: its top lies five texels off the line from the middle
     * of one block to the middle of the next, and meets the level belt's top that much times root two less one
     * before the middle. A way that bent in the middle would run an eighth of a block under the belt all the way up.
     */
    private static final float SLOPE_FOOT = 0.5f - 5f / 16f * (1.41421356f - 1f);
    /** How far the press's head comes down on a belt, as Create has it. */
    private static final float HEAD_TRAVEL = 19f / 16f;
    /** What a pulley's rim is from its axis: a belt that has run a block has turned it by a block over this. */
    private static final float PULLEY = 5f / 16f;

    private static final String BELT = "create:block/belt", BELT_OFFSET = "create:block/belt_offset", BELT_DIAGONAL = "create:block/belt_diagonal";
    private static final String TANK_SIDE = "create:block/fluid_tank";
    private static final String[] VAULT_PARTS = {"front", "side", "top", "bottom"};

    private final Kit kit;
    private final Kit.Model[] belt = new Kit.Model[6], diagonal = new Kit.Model[3];
    private final Kit.Model pulley, shaft, cog, largeCog, pressBlock, pressHead, tankBottom, tankMiddle, tankTop, vault, tunnel, tunnelFlap,
        funnel, casing, ingot, sheet;
    private final Sheet.Region beltScroll, diagonalScroll, tankJoined;
    private final Sheet.Region[] vaultJoined = new Sheet.Region[4];
    /** The vault's door with a window cut into it, and the colour of the sheets behind it. */
    private final Sheet.Region vaultWindow;
    private final int brass;
    /**
     * The stacks in the vault: so many side by side in a row, so many rows from the window back, and so many sheets to
     * a stack when the store is full.
     */
    private static final int STACKS_X = 4, STACKS_Z = 3, STACKS = STACKS_X * STACKS_Z, SHEETS = 19;
    /**
     * From sheet to sheet up a stack, and how thick a sheet is: thicker than on the belt, where it is a hair, and with
     * a little air over it, in which the face of the sheet under it shows. That is what makes a stack of sheets of a
     * column of rims.
     */
    private static final float SHEET_UP = 0.088f, SHEET_THICK = 0.06f;
    /** From stack to stack in a row, from row to row, and where the stacks stand: level with the window's lower edge. */
    private static final float STACK_X = 0.54f, STACK_Z = 0.42f, PILE_Y = BASE + 0.5f;
    /** Seconds a sheet is on its way from the funnel's mouth to its stack. */
    private static final float FLIGHT = 0.4f;
    private final Mesh pile = new Mesh(16000);
    private int piled = -1;
    /** How many sheets are in the vault, the one on its way counted; and how far that one has come, 0 to 1. */
    private int stacked = -1;
    private float flight = -1f;
    private float pileTime;

    private final Mesh fixed = new Mesh(8000), fixedGlass = new Mesh(512);
    private boolean fixedBuilt;
    private final float[] at = new float[3];
    // What the skins of this moment go by.
    private float scrollBy;
    private boolean scrollDiagonal;
    private final int[] joinAt = new int[3], joinSize = new int[3];

    /** The factory of Create's blocks, or null when Create is not there (or not as this knows it). */
    static CreateFactory make(final Assets assets, final Sheet sheet) {
        if (assets == null) return null;
        try {
            if (assets.read("create", "models/block/belt/start.json") == null) return null;
            return new CreateFactory(new Kit(assets, sheet));
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private CreateFactory(final Kit kit) {
        this.kit = kit;
        final String[] parts = {"start", "middle", "end", "start_bottom", "middle_bottom", "end_bottom"};
        for (int i = 0; i < parts.length; i++) belt[i] = kit.model("create:block/belt/" + parts[i]);
        diagonal[0] = kit.model("create:block/belt/diagonal_start");
        diagonal[1] = kit.model("create:block/belt/diagonal_middle");
        diagonal[2] = kit.model("create:block/belt/diagonal_end");
        pulley = kit.model("create:block/belt_pulley");
        shaft = kit.model("create:block/shaft");
        cog = kit.model("create:block/cogwheel");
        largeCog = kit.model("create:block/large_cogwheel");
        pressBlock = kit.model("create:block/mechanical_press/block");
        pressHead = kit.model("create:block/mechanical_press/head");
        tankBottom = kit.model("create:block/fluid_tank/block_bottom_window");
        tankMiddle = kit.model("create:block/fluid_tank/block_middle_window");
        tankTop = kit.model("create:block/fluid_tank/block_top_window");
        vault = kit.model("create:block/item_vault");
        tunnel = kit.model("create:block/tunnel/andesite_tunnel/straight");
        tunnelFlap = kit.model("create:block/belt_tunnel/flap");
        funnel = kit.model("create:block/andesite_belt_funnel_retracted_unpowered");
        casing = kit.model("create:block/andesite_casing");
        ingot = kit.sprite("create:item/brass_ingot");
        sheet = kit.sprite("create:item/brass_sheet");
        for (final String needed : new String[] {BELT, "create:block/belt_scroll", "create:block/belt_diagonal_scroll", TANK_SIDE,
            "create:block/fluid_tank_connected", "create:item/brass_ingot", "create:item/brass_sheet", "create:block/vault/vault_front_large"}) {
            if (!kit.has(needed)) throw new IllegalStateException("no texture " + needed);
        }
        beltScroll = kit.texture("create:block/belt_scroll");
        diagonalScroll = kit.texture("create:block/belt_diagonal_scroll");
        tankJoined = kit.texture("create:block/fluid_tank_connected");
        for (int i = 0; i < VAULT_PARTS.length; i++) vaultJoined[i] = kit.texture("create:block/vault/vault_" + VAULT_PARTS[i] + "_large");
        // The door of a vault three blocks wide is the right three quarters of its sheet, 48 texels square: the window
        // is its middle, two blocks wide and high.
        vaultWindow = kit.windowed("create:block/vault/vault_front_large", 16 + 8, 8, 16 + 40, 40);
        // The colour of Create's brass sheet, taken all in all, and with more colour in it: a pile of metal is not
        // as pale as the light on one sheet.
        brass = Atlas.fuller(kit.tone("create:item/brass_sheet"), 1.7f);

        beltY = BELT_Y;
        lumpX0 = A0 + 0.45f;
        lumpX1 = A1 + 1f - 0.3f;
        // Level to the foot of the slope, up it, and level again to the mouth of the funnel.
        way = new Path(lumpX1, BELT_Y, D0 + SLOPE_FOOT, BELT_Y, D1 + SLOPE_FOOT, BELT_Y + 1f, T1 + 0.75f, BELT_Y + 1f);
        pressX = PRESS + 0.5f;
        pressAt = pressX - lumpX1;
        planned(X0, Z0, X1, Z1,
            new float[] {TANK, BASE, -0.5f, TANK + 1, BASE + 3f, 0.5f},
            new float[] {A0, BASE, -1.5f, A1 + 1, BASE + 2.6f, 0.5f},
            new float[] {PRESS, BASE, -1.5f, PRESS + 2, BASE + PRESS_LEVEL + 1.7f, 0.5f},
            new float[] {VAULT, BASE, 0.5f - VAULT_DEEP, VAULT + 3, BASE + 3f, 0.5f});
        // Things lie flat on Create's belts: seen from a little higher up, so that they are seen at all.
        pitch = 20f;
        set(memoryAt, TANK + 0.5f, BASE + 3.125f, 0f);
        set(taskFrom, A0 + 0.1f, BELT_Y, 0.5f);
        set(taskTo, PRESS - 0.1f, BASE + PRESS_LEVEL, -0.5f);
        over = new float[][] {{A1 - 0.5f, BASE + 2.5f, -1f}, {A0 + 0.5f, BASE + 2f, -0.5f}};
        set(totalAt, VAULT + 1.5f, BASE + 3.4f, -0.5f);
    }

    @Override
    void free() {
        fixed.free();
        fixedGlass.free();
        pile.free();
    }

    @Override
    float leaving(final Motion m) {
        return 0.3f;
    }

    @Override
    void lamps(final Motion m, final SceneGl.Light light, final int accent) {
        final float flicker = 0.92f + 0.08f * (float) Math.sin(m.time * 7.3) * (float) Math.sin(m.time * 3.1 + 1.0);
        final float lava = (0.35f + 0.65f * m.memory) * flicker;
        lamp(light, 0, TANK + 0.7f, BASE + 0.4f + 1.25f * m.memory, 0.9f, 4.6f, 1.0f * lava, 0.42f * lava, 0.10f * lava);
        // The sheets in the vault shine in their own colour, out of the window.
        final float store = 0.16f + 0.3f * m.overall + 0.25f * (1f - m.landed);
        lamp(light, 1, VAULT + 1.5f, pileTop() + 0.45f, 0.1f, 3.4f,
            ((brass >>> 16) & 0xFF) / 255f * store, ((brass >>> 8) & 0xFF) / 255f * store, (brass & 0xFF) / 255f * store);
        final float flash = m.stroke >= LoadingScene.PRESS_DOWN ? Math.max(0f, 1f - (m.stroke - LoadingScene.PRESS_DOWN) / 0.18f) : 0f;
        lamp(light, 2, pressX, BELT_Y + 0.3f, 0.5f, 2.6f, 1.0f * flash, 0.8f * flash, 0.5f * flash);
    }

    // ------------------------------------------------------------------ a frame

    @Override
    void build(final Motion m, final Frame f, final int accent) {
        f.plinth(X0, Z0, X1, Z1, BASE);
        if (!fixedBuilt) {
            fixedBuilt = true;
            fixed.clear();
            fixedGlass.clear();
            standing(fixed, fixedGlass);
        }
        f.solid.add(fixed);
        f.glass.add(fixedGlass);

        final Mesh solid = f.solid;
        plain(solid);
        // The lava, as high as the memory in use, and the glow of it.
        final float level = 1f / 16f + (3f - 0.5f - 1f / 16f) * m.memory;
        f.lava(TANK + 0.08f, BASE + 0.25f, -0.42f, TANK + 0.92f, BASE + 0.25f + level, 0.42f);
        final float heat = 0.25f + 0.75f * m.memory;
        f.halo(TANK + 0.5f, BASE + 0.25f + level * 0.5f, 0.55f, 1.3f + level * 0.55f, 0xFFFF7A1E, 0.30f * heat);
        f.halo(TANK + 0.5f, BASE + 0.25f + level, 0.55f, 0.9f, 0xFFFFB347, 0.18f * heat);

        // The belts and what turns with them. The first runs as the task goes, the others always.
        plain(solid);
        level(f, A0, A1, 0, m.beltA);
        level(f, B0, B1, 0, m.beltB);
        slope(f, m.beltB);
        level(f, T0, T1, 1, m.beltB);
        final float turnA = -m.beltA / PULLEY * 57.29578f, turnB = -m.beltB / PULLEY * 57.29578f;
        wheel(f, cog, A1, 0, BACK, turnA);
        wheel(f, largeCog, A1 - 1, 1, BACK, -turnA / 2f + 11.25f);
        wheel(f, cog, PRESS, PRESS_LEVEL, BACK, turnB);
        wheel(f, largeCog, PRESS + 1, PRESS_LEVEL - 1, BACK, -turnB / 2f + 11.25f);
        wheel(f, shaft, PRESS, PRESS_LEVEL, ROW, turnB);

        // The press's head, and what it strikes.
        block(solid, PRESS, PRESS_LEVEL - HEAD_TRAVEL * m.pressDown, ROW);
        kit.emit(solid, f.glass, pressHead, null);
        solid.pop();
        if (m.stroke >= LoadingScene.PRESS_DOWN) {
            final float flash = Math.max(0f, 1f - (m.stroke - LoadingScene.PRESS_DOWN) / 0.2f);
            f.halo(pressX, BELT_Y + 0.2f, 0.45f, 1.1f, 0xFFFFD28A, 0.5f * flash);
        }

        // What is on the belts.
        for (final Motion.Item it : m.items) {
            float scale = 1f;
            if (it.leaving >= 0f) {
                // Into the funnel: on as the belt runs, and gone behind its flap.
                way.at(way.length(), at);
                at[0] += BELT_SPEED * it.leaving;
                scale = Math.max(0f, 1f - it.leaving / leaving(m));
            } else {
                place(it.s, at);
            }
            lying(f, it.pressed ? sheet : ingot, at[0], at[1], at[2], it.id, scale);
        }
        stored(m, f);
        air(m, f, X0, X1, 1f);
    }

    /** How many sheets are in the vault: all of them when the loading is done. */
    private static int sheets(final Motion m) {
        return Math.round(Math.max(0f, Math.min(1f, m.overall)) * STACKS * SHEETS);
    }

    /** How many sheets a stack holds of so many in all: the rows at the back are filled first, so the last one in is one that is seen. */
    private static int high(final int stack, final int count) {
        final int turn = (STACKS_Z - 1 - stack / STACKS_X) * STACKS_X + stack % STACKS_X;
        return count / STACKS + (turn < count % STACKS ? 1 : 0);
    }

    /** The stack that sheet number {@code count} lies on (the first is number 1). */
    private static int stackOf(final int count) {
        final int turn = (count - 1) % STACKS;
        return (STACKS_Z - 1 - turn / STACKS_X) * STACKS_X + turn % STACKS_X;
    }

    /** How high the stacks stand: at the window's lower edge when nothing is loaded, at its upper when all is. */
    private float pileTop() {
        return PILE_Y + (Math.max(0, stacked) + STACKS - 1) / STACKS * SHEET_UP;
    }

    /** The stacks: the first row behind the window, from left to right, then the rows behind it. */
    private static float stackX(final int stack) {
        return VAULT + 1.5f + (stack % STACKS_X - (STACKS_X - 1) / 2f) * STACK_X;
    }

    private static float stackZ(final int stack) {
        return 0.2f - stack / STACKS_X * STACK_Z;
    }

    /**
     * The sheet as it is on the belt, lying flat with its underside at {@code y}, as a hand lays it on a stack: never
     * quite square on the one under it.
     */
    private void stackedSheet(final Mesh into, final int stack, final int k, final float x, final float y, final float z, final float turned,
                              final Kit.Skin skin) {
        into.push();
        into.translate(x + (Atlas.hash(stack, k, 54) - 0.5f) * 0.03f, y + SHEET_THICK / 2f, z + (Atlas.hash(stack, k, 55) - 0.5f) * 0.03f);
        into.rotateY((Atlas.hash(stack, k, 53) - 0.5f) * 9f + turned);
        into.rotateX(-90f);
        // The sprite is a sixteenth thick.
        into.scale(0.6f, 0.6f, SHEET_THICK * 16f);
        into.translate(-0.5f, -0.5f, -0.5f);
        kit.emit(into, into, sheet, skin);
        into.pop();
    }

    /** Of a sheet on a stack, its underside and the rim at its back are never seen. */
    private static Sheet.Region seen(final Kit.Quad q, final float[] uv) {
        return q.n[2] < -0.5f || q.n[1] > 0.5f ? Kit.SKIP : null;
    }

    /**
     * What is in the vault, seen through the window in its door: the sheets the press has made, the item itself,
     * stacked, every stack as high as the others or a sheet higher, and the one that has just come in through the
     * funnel on its way to its stack. Of the rows behind the first only the top is seen, and only that is built.
     */
    private void stored(final Motion m, final Frame f) {
        final int count = sheets(m);
        final float dt = Math.max(0f, Math.min(0.1f, m.time - pileTime));
        pileTime = m.time;
        if (stacked < 0 || count < stacked) {
            stacked = count;
            flight = -1f;
        } else if (flight >= 0f) {
            flight += dt / FLIGHT;
            if (flight >= 1f) flight = -1f;
        }
        if (flight < 0f && count > stacked) {
            // The next one comes in: all that the loading has run ahead by lies there already, but for the last.
            stacked = count;
            flight = 0f;
        }
        final int lying = flight >= 0f ? stacked - 1 : stacked;
        if (lying != piled) {
            piled = lying;
            pile.clear();
            // What the stacks stand on: from the vault's floor to the window's lower edge, dark.
            pile.tile(Tile.WHITE).colour(0xFF1B1E25).faces(Mesh.ALL & ~Mesh.BOTTOM);
            pile.box(VAULT + 0.4f, BASE + 0.0625f, -1.2f, VAULT + 2.6f, PILE_Y - 0.004f, 0.44f);
            metal(pile);
            for (int i = 0; i < STACKS; i++) {
                final int high = high(i, lying);
                for (int k = i < STACKS_X ? 0 : Math.max(0, high - 3); k < high; k++) {
                    stackedSheet(pile, i, k, stackX(i), PILE_Y + k * SHEET_UP, stackZ(i), 0f, CreateFactory::seen);
                }
            }
            pile.reset();
        }
        f.solid.add(pile);

        if (flight >= 0f) {
            // From the funnel's mouth in a bow, turning as it falls, and flat onto its stack.
            final int stack = stackOf(stacked), k = high(stack, stacked) - 1;
            final float t = flight, left = 1f - t;
            final float fromX = VAULT + 0.2f, fromY = BASE + 2.55f, toY = PILE_Y + k * SHEET_UP;
            final float x = fromX + (stackX(stack) - fromX) * t, z = stackZ(stack) * t;
            final float y = fromY + (toY - fromY) * t * t + 0.3f * (float) Math.sin(Math.PI * t) * left;
            metal(f.solid);
            stackedSheet(f.solid, stack, k, x, y, z, 120f * left, null);
            plain(f.solid);
        }
        final float glow = 0.08f + 0.12f * m.overall + 0.2f * (1f - m.landed);
        f.halo(VAULT + 1.5f, Math.min(BASE + 2.4f, pileTop()), 0.6f, 1.6f, Atlas.mix(brass, 0xFFFFFFFF, 0.25f), glow);
    }

    /** Metal in the dark of a vault: it has a little light of its own, or the window would show none of it. */
    private static void metal(final Mesh m) {
        m.reset();
        m.gloss(0.5f).emissive(0.22f);
    }

    /** What never moves: the tank, the tunnel, the press, the vault with its funnel, what the raised belt stands on. */
    private void standing(final Mesh solid, final Mesh glass) {
        plain(solid);
        plain(glass);
        // The tank, three blocks of it: each takes the picture of its walls that joins it to the ones over and under it.
        final Kit.Skin walls = (q, uv) -> {
            if (!TANK_SIDE.equals(q.texture)) return null;
            Kit.joined(q, uv, joinAt, joinSize);
            return tankJoined;
        };
        size(1, 3, 1);
        final Kit.Model[] tank = {tankBottom, tankMiddle, tankTop};
        for (int y = 0; y < 3; y++) {
            joinAt[0] = joinAt[2] = 0;
            joinAt[1] = y;
            block(solid, TANK, y, ROW);
            kit.emit(solid, glass, tank[y], walls);
            solid.pop();
        }

        block(solid, A0, 1, ROW);
        kit.emit(solid, glass, tunnel, null);
        // The strips in its mouth, four side by side, where the ingots come out.
        turn(solid, 0f, -90f, 0f);
        for (int i = 0; i < 4; i++) {
            solid.push();
            solid.translate(-i * 3f / 16f, 0f, 0f);
            kit.emit(solid, glass, tunnelFlap, null);
            solid.pop();
        }
        solid.pop();

        block(solid, PRESS, PRESS_LEVEL, ROW);
        kit.emit(solid, glass, pressBlock, null);
        solid.pop();

        // The vault: three by three and two deep, every block with the picture of its place in the whole.
        size(3, 3, VAULT_DEEP);
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < VAULT_DEEP; z++) {
                    final int bx = x, by = y, bz = z;
                    block(solid, VAULT + x, y, z - VAULT_DEEP + 1);
                    kit.emit(solid, glass, vault, (q, uv) -> {
                        // Only what is on the outside of the whole.
                        final int nx = Math.round(q.n[0]), ny = Math.round(q.n[1]), nz = Math.round(q.n[2]);
                        if (bx + nx >= 0 && bx + nx < 3 && by + ny >= 0 && by + ny < 3 && bz + nz >= 0 && bz + nz < VAULT_DEEP) return Kit.SKIP;
                        joinAt[0] = bx;
                        joinAt[1] = by;
                        joinAt[2] = bz;
                        Kit.joined(q, uv, joinAt, joinSize);
                        return nz > 0 ? vaultWindow : vaultJoined[nz != 0 ? 0 : ny > 0 ? 2 : ny < 0 ? 3 : 1];
                    });
                    solid.pop();
                }
            }
        }

        // Its inside, dark, as seen through the window: the floor, the back, the walls, the ceiling; and the pane.
        final float ix0 = VAULT + 0.03f, ix1 = VAULT + 2.97f, iy0 = BASE + 0.03f, iy1 = BASE + 2.97f, iz0 = 0.5f - VAULT_DEEP + 0.03f, iz1 = 0.47f;
        solid.reset();
        solid.tile(Tile.WHITE).colour(0xFF14161B);
        solid.quad(ix0, iy1, iz0, ix0, iy0, iz0, ix1, iy0, iz0, ix1, iy1, iz0, 0f, 0f, 1f, 1f);
        solid.colour(0xFF1B1E25);
        solid.quad(ix0, iy0, iz0, ix0, iy0, iz1, ix1, iy0, iz1, ix1, iy0, iz0, 0f, 0f, 1f, 1f);
        solid.colour(0xFF101216);
        solid.quad(ix0, iy1, iz1, ix0, iy1, iz0, ix1, iy1, iz0, ix1, iy1, iz1, 0f, 0f, 1f, 1f);
        solid.colour(0xFF171A20);
        solid.quad(ix0, iy1, iz1, ix0, iy0, iz1, ix0, iy0, iz0, ix0, iy1, iz0, 0f, 0f, 1f, 1f);
        solid.quad(ix1, iy1, iz0, ix1, iy0, iz0, ix1, iy0, iz1, ix1, iy1, iz1, 0f, 0f, 1f, 1f);
        glass.reset();
        glass.tile(Tile.GLASS_BACK).emissive(0.5f);
        glass.quad(VAULT + 0.5f, BASE + 2.5f, 0.46f, VAULT + 0.5f, BASE + 0.5f, 0.46f, VAULT + 2.5f, BASE + 0.5f, 0.46f, VAULT + 2.5f, BASE + 2.5f, 0.46f, 0f, 0f, 1f, 1f);
        plain(solid);
        plain(glass);

        // The funnel into it, over the end of the top belt, and the casing the raised belt stands on.
        block(solid, T1, 2, ROW);
        turn(solid, 0f, 90f, 0f);
        kit.emit(solid, glass, funnel, null);
        solid.pop();
        for (int x = D1; x <= T1; x++) {
            block(solid, x, 0, ROW);
            kit.emit(solid, glass, casing, null);
            solid.pop();
        }

        // A few ingots that fell off the belt.
        flat(solid, glass, ingot, -3.6f, BASE, 1.15f, 0f, 35f, 1f);
        flat(solid, glass, ingot, -3.2f, BASE, 1.3f, 0f, -20f, 1f);
        flat(solid, glass, sheet, 3.3f, BASE, 1.2f, 0f, 15f, 1f);
        plain(solid);
        plain(glass);
    }

    /** A belt lying level, from block {@code from} to block {@code to}, with a pulley in its first and its last block. */
    private void level(final Frame f, final int from, final int to, final int level, final float travel) {
        final Mesh solid = f.solid;
        for (int b = from; b <= to; b++) {
            final int part = b == from ? 0 : b == to ? 2 : 1;
            block(solid, b, level, ROW);
            turn(solid, 0f, 90f, 0f);
            run(travel - (b - from), false);
            kit.emit(solid, f.glass, belt[part], this::running);
            run(travel - (b - from) + 0.5f, false);
            kit.emit(solid, f.glass, belt[part + 3], this::running);
            solid.pop();
        }
        final float angle = -travel / PULLEY * 57.29578f;
        wheel(f, pulley, from, level, ROW, angle);
        wheel(f, pulley, to, level, ROW, angle);
    }

    /** The belt that goes up: a block at the foot, a block at the head, a level higher. */
    private void slope(final Frame f, final float travel) {
        final Mesh solid = f.solid;
        for (int i = 0; i < 2; i++) {
            block(solid, D0 + i, i, ROW);
            turn(solid, 0f, -90f, 0f);
            run(travel * 1.41421356f - i, true);
            kit.emit(solid, f.glass, diagonal[i == 0 ? 0 : 2], this::running);
            solid.pop();
        }
        final float angle = -travel / PULLEY * 57.29578f;
        wheel(f, pulley, D0, 0, ROW, angle);
        wheel(f, pulley, D1, 1, ROW, angle);
    }

    /** What turns about an axis from front to back: a pulley, a cogwheel, a shaft, in the block named. */
    private void wheel(final Frame f, final Kit.Model model, final float bx, final float by, final int row, final float angle) {
        final Mesh solid = f.solid;
        block(solid, bx, by, row);
        solid.translate(0.5f, 0.5f, 0.5f);
        solid.rotateX(90f);
        solid.rotateY(angle);
        solid.translate(-0.5f, -0.5f, -0.5f);
        kit.emit(solid, f.glass, model, null);
        solid.pop();
    }

    /** A thing lying on a belt as Create lays it: flat, half its size, turned a little, each its own way. */
    private void lying(final Frame f, final Kit.Model thing, final float x, final float y, final float slope, final int id, final float scale) {
        if (scale <= 0.01f) return;
        flat(f.solid, f.glass, thing, x, y, 0f, slope, (Atlas.hash(id, 3, 19) - 0.5f) * 50f, scale);
    }

    private void flat(final Mesh solid, final Mesh glass, final Kit.Model thing, final float x, final float y, final float z,
                      final float slope, final float yaw, final float scale) {
        plain(solid);
        solid.gloss(0.45f);
        solid.push();
        solid.translate(x, y, z);
        solid.rotateZ(slope);
        solid.translate(0f, 1f / 64f, 0f);
        solid.rotateY(yaw);
        solid.rotateX(-90f);
        solid.scale(0.6f * scale);
        solid.translate(-0.5f, -0.5f, -0.5f);
        kit.emit(solid, glass, thing, null);
        solid.pop();
        plain(solid);
    }

    // ------------------------------------------------------------------ skins

    /** The belt has run this far, in blocks, where the faces to come begin. */
    private void run(final float blocks, final boolean slope) {
        scrollBy = blocks;
        scrollDiagonal = slope;
    }

    /**
     * A belt's faces take the picture that is twice as long as theirs and the part of it that the belt's running has
     * brought up: Create moves its belts the same way.
     */
    private Sheet.Region running(final Kit.Quad q, final float[] uv) {
        final boolean level = BELT.equals(q.texture) || BELT_OFFSET.equals(q.texture);
        if (!level && !BELT_DIAGONAL.equals(q.texture)) return null;
        final float period = scrollDiagonal ? 12f : 16f;
        final float texels = scrollBy * 16f;
        final float by = texels - (float) Math.floor(texels / period) * period;
        for (int c = 0; c < 4; c++) uv[c * 2 + 1] = (uv[c * 2 + 1] + by) / 2f;
        return scrollDiagonal ? diagonalScroll : beltScroll;
    }

    private void size(final int x, final int y, final int z) {
        joinSize[0] = x;
        joinSize[1] = y;
        joinSize[2] = z;
    }

    // ------------------------------------------------------------------ placing

    /** The matrix to a block's lower corner: so many blocks along, so many up from the plinth, in this row. */
    private static void block(final Mesh m, final float bx, final float by, final int row) {
        m.push();
        m.translate(bx, BASE + by, row - 0.5f);
    }

    /** Turned about the middle of the block, as a block state turns a model. */
    private static void turn(final Mesh m, final float x, final float y, final float z) {
        m.translate(0.5f, 0.5f, 0.5f);
        if (y != 0f) m.rotateY(y);
        if (x != 0f) m.rotateX(x);
        if (z != 0f) m.rotateZ(z);
        m.translate(-0.5f, -0.5f, -0.5f);
    }

    private static void plain(final Mesh m) {
        m.reset();
        m.gloss(0.2f);
    }
}
