package dev.fallingcloud.slate.core.stage.node;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.StageResources;
import dev.fallingcloud.slate.core.stage.mesh.QuadMesh;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A little planet in the shape of a cube: land, seas, forests, mountains and ice caps on all six faces, in small
 * voxels wearing Minecraft's own textures, with flat white clouds drifting around it. It is what the Overhaul
 * layout's Play button shows, and what every world and server on the Play ring looks like.
 *
 * <p>Everything comes from a seed: the kind of planet, where its continents lie, where its forests grow. The same
 * seed always gives the same planet, so a world keeps its own, and no two look quite alike. A server's planet wears
 * a {@link #ring ring} of small lights, whose colour says whether the server answers.</p>
 *
 * <p>The terrain is a function of the direction from the planet's middle, so it runs over edges and corners without
 * a seam; "up" is outwards on every face, which is why this is drawn as its own mesh ({@link QuadMesh}) and not out
 * of block states, whose grass only ever grows on the side that faces the sky. The planet is lit for the way it is
 * turned right now, so a spinning one has a day side that stays put.</p>
 *
 * <p>The node's origin is the planet's middle. One voxel is one unit: {@link #size} scales the node so the cube
 * measures that many blocks a side.</p>
 */
public final class CubePlanetNode extends StageNode {

    /** What sort of planet. The first five come up from seeds; the last two only when asked for. */
    public enum Kind {
        TEMPERATE(10), OCEAN(3), FOREST(3), DESERT(2), ICE(2), NETHER(0), END(0);

        private final int weight;

        Kind(final int weight) { this.weight = weight; }

        /** The kind a seed stands for: mostly temperate, so the odd desert or ice planet stands out. */
        public static Kind of(final long seed) {
            int total = 0;
            for (final Kind k : values()) total += k.weight;
            int pick = (int) Math.floorMod(mix(seed ^ 0x9E3779B97F4A7C15L), (long) total);
            for (final Kind k : values()) {
                if (pick < k.weight) return k;
                pick -= k.weight;
            }
            return TEMPERATE;
        }
    }

    private enum Tint { NONE, GRASS, FOLIAGE, WATER }

    /** What a voxel is made of: a texture of the block atlas, how it is tinted, whether light passes. */
    private enum Mat {
        WATER("water_still", Tint.WATER, true), LAVA("lava_still"), SAND("sand"), RED_SAND("red_sand"), GRAVEL("gravel"),
        STONE("stone"), DEEPSLATE("deepslate"), DIRT("dirt"), CLAY("clay"),
        GRASS("grass_block_top", Tint.GRASS, false), PODZOL("podzol_top"), MYCELIUM("mycelium_top"), MOSS("moss_block"),
        SNOW("snow"), ICE("packed_ice"),
        LEAVES("oak_leaves", Tint.FOLIAGE, false), SPRUCE("spruce_leaves", 0xFF619961), BIRCH("birch_leaves", 0xFF80A755),
        JUNGLE("jungle_leaves", Tint.FOLIAGE, false), CHERRY("cherry_leaves"),
        TERRACOTTA("terracotta"), ORANGE("orange_terracotta"), YELLOW("yellow_terracotta"), RED("red_terracotta"), WHITE("white_terracotta"),
        NETHERRACK("netherrack"), CRIMSON("crimson_nylium"), WARPED("warped_nylium"), WART("nether_wart_block"), WARPED_WART("warped_wart_block"),
        BASALT("smooth_basalt"), BLACKSTONE("blackstone"), MAGMA("magma"), GLOWSTONE("glowstone"),
        END_STONE("end_stone"), OBSIDIAN("obsidian"), PURPUR("purpur_block");

        final String texture;
        final Tint tint;
        final int fixed;
        final boolean translucent;
        /** Leaves let light through their gaps: what lies behind them is drawn. */
        final boolean gaps;

        Mat(final String texture) { this(texture, Tint.NONE, false, 0xFFFFFFFF); }

        Mat(final String texture, final int fixed) { this(texture, Tint.NONE, false, fixed); }

        Mat(final String texture, final Tint tint, final boolean translucent) { this(texture, tint, translucent, 0xFFFFFFFF); }

        Mat(final String texture, final Tint tint, final boolean translucent, final int fixed) {
            this.texture = texture;
            this.tint = tint;
            this.translucent = translucent;
            this.fixed = fixed;
            this.gaps = texture.endsWith("_leaves");
        }

        boolean seeThrough() { return translucent || gaps; }
    }

    private static final Mat[] MATS = Mat.values();

    /** Voxels a side, and how many layers of hills may stand on the surface. */
    public static final int SIDE = 24;
    private static final int RELIEF = 3;
    /** Room above the hills for a canopy. */
    private static final int GRID = SIDE + 2 * (RELIEF + 1);
    /** Texels a voxel shows: a texture runs over four voxels, so it stays as crisp as the game's own blocks. */
    private static final int TEXELS = 4;
    /** Where the surface lies, as a Chebyshev distance from the middle (cell centres sit on half units). */
    private static final float SURFACE = SIDE / 2f - 0.5f;

    private record Terrain(Mat top, Mat under, int height, @Nullable Mat canopy, boolean sea, Mat bed) {}

    /**
     * One slab of cloud. On the belt it rides round the four sides at height {@code y}, flat against whichever side it
     * is passing; over the cap it circles above the top face at {@code radius} from the axis, lying flat.
     */
    private record Cloud(boolean belt, float radius, float y, float angle, float halfW, float halfH, float halfD) {}

    /** How far above the sea the clouds ride: clear of the highest hills. */
    private static final float CLOUD_LIFT = RELIEF + 1.7f;
    /** How square the belt's path is: 2 would be a circle, more hugs the sides and swings wide round the edges. */
    private static final float BELT_SQUARE = 5f;

    private final long seed;
    private final Kind kind;
    private final VoxelNode.Noise elevation, moisture, detail;
    private final int grass, foliage, water;
    private final List<Cloud> clouds = new ArrayList<>();
    @Nullable private QuadMesh mesh;
    private int meshResources = -1;
    private boolean broken;

    private boolean showClouds = true;
    private float cloudDegPerSec = -7f;
    private float cloudAngle;
    private int ringColor;
    private boolean ring;
    private float ringAngle;

    private final Vector3f normal = new Vector3f();
    private final Vector3f corner = new Vector3f();
    private final Matrix4f rotated = new Matrix4f();
    private final float[] shades = new float[6];

    private CubePlanetNode(final long seed, final Kind kind) {
        this.seed = seed;
        this.kind = kind;
        this.elevation = new VoxelNode.Noise(mix(seed));
        this.moisture = new VoxelNode.Noise(mix(seed + 101L));
        this.detail = new VoxelNode.Noise(mix(seed + 977L));
        switch (kind) {
            case FOREST -> { grass = 0xFF59C93C; foliage = 0xFF30BB0B; water = 0xFF3F76E4; }
            case DESERT -> { grass = 0xFFBFB755; foliage = 0xFFAEA42A; water = 0xFF32A598; }
            case ICE -> { grass = 0xFF80B497; foliage = 0xFF60A17B; water = 0xFF3938C9; }
            case OCEAN -> { grass = 0xFF91BD59; foliage = 0xFF77AB2F; water = 0xFF43D5EE; }
            default -> { grass = 0xFF91BD59; foliage = 0xFF77AB2F; water = 0xFF3F76E4; }
        }
        final float reach = SIDE / 2f + RELIEF + 1f;
        bounds(-reach, -reach, -reach, reach, reach, reach);
        hoverFeel(0f, 1f);
        buildClouds();
        named("planet");
    }

    /** A planet whose kind comes from the seed. */
    public static CubePlanetNode create(final long seed) {
        return new CubePlanetNode(seed, Kind.of(seed));
    }

    public static CubePlanetNode create(final long seed, final Kind kind) {
        return new CubePlanetNode(seed, kind);
    }

    /** A server's planet: the same, wearing a ring (grey until {@link #ring} says how the server is doing). */
    public static CubePlanetNode server(final long seed) {
        final CubePlanetNode p = new CubePlanetNode(seed, Kind.of(seed));
        p.ring(0xFF8A8880);
        return p;
    }

    /** A stable seed for a name (a world's folder, a server's address): the same name always grows the same planet. */
    public static long seedOf(final String name) {
        long h = 1125899906842597L;
        for (int i = 0; i < name.length(); i++) h = 31 * h + name.charAt(i);
        return h;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // ------------------------------------------------------------------ configuration

    public Kind kind() { return kind; }

    /** Scales the node so the cube measures {@code blocks} a side. */
    public CubePlanetNode size(final float blocks) {
        scale(blocks / SIDE);
        return this;
    }

    public CubePlanetNode clouds(final boolean on) { this.showClouds = on; return this; }

    /** How fast the clouds circle the planet, in degrees per second against the stage (the planet turns by itself). */
    public CubePlanetNode cloudSpeed(final float degPerSec) { this.cloudDegPerSec = degPerSec; return this; }

    /** A ring of small lights around the planet, in this colour (ARGB). The mark of a server. */
    public CubePlanetNode ring(final int argb) {
        this.ring = true;
        this.ringColor = argb;
        return this;
    }

    public CubePlanetNode noRing() { this.ring = false; return this; }

    // ------------------------------------------------------------------ terrain

    /** Height and moisture of the land in a direction from the middle, and what that makes of it. */
    private Terrain terrain(final float dx, final float dy, final float dz) {
        final float e = elevation.fbm(dx * 1.9f, dy * 1.9f, dz * 1.9f) * 0.66f + detail.fbm(dx * 4.4f + 7f, dy * 4.4f, dz * 4.4f - 3f) * 0.34f;
        final float m = moisture.fbm(dx * 2.6f + 13f, dy * 2.6f - 5f, dz * 2.6f);
        final float lat = Math.abs(dy);
        final float fringe = detail.value(dx * 6f, dy * 6f + 20f, dz * 6f) * 0.07f;
        return switch (kind) {
            case NETHER -> nether(e, m);
            case END -> end(e, m);
            case DESERT -> desert(e, m, lat + fringe);
            case ICE -> ice(e, m, lat + fringe);
            default -> temperate(e, m, lat + fringe);
        };
    }

    private Terrain temperate(final float e, final float m, final float lat) {
        final float sea = kind == Kind.OCEAN ? 0.2f : kind == Kind.FOREST ? -0.1f : 0f;
        final boolean polar = lat > 0.9f;
        final float h = e - sea;
        if (h < 0f) {
            final Mat bed = h < -0.26f ? Mat.GRAVEL : h < -0.12f ? Mat.CLAY : Mat.SAND;
            return polar ? new Terrain(Mat.ICE, Mat.ICE, 0, null, false, bed) : new Terrain(Mat.WATER, bed, 0, null, true, bed);
        }
        final int height = h < 0.13f ? 0 : h < 0.29f ? 1 : h < 0.43f ? 2 : 3;
        if (polar) return new Terrain(Mat.SNOW, height >= 2 ? Mat.STONE : Mat.DIRT, height, null, false, Mat.STONE);
        if (h < 0.045f) return new Terrain(Mat.SAND, Mat.SAND, 0, null, false, Mat.SAND);
        if (height == 3) return new Terrain(Mat.SNOW, Mat.STONE, 3, null, false, Mat.STONE);
        if (height == 2) return new Terrain(Mat.STONE, Mat.STONE, 2, null, false, Mat.STONE);
        final float wet = m + (kind == Kind.FOREST ? 0.32f : 0f);
        if (lat < 0.34f && wet < -0.3f) return new Terrain(Mat.SAND, Mat.SAND, height, null, false, Mat.SAND);
        if (lat > 0.74f) return new Terrain(wet > 0.05f ? Mat.PODZOL : Mat.GRASS, Mat.DIRT, height, wet > -0.05f ? Mat.SPRUCE : null, false, Mat.DIRT);
        if (wet > 0.16f) {
            final Mat leaves = kind == Kind.FOREST && lat < 0.3f ? Mat.JUNGLE : wet > 0.42f ? Mat.BIRCH : Mat.LEAVES;
            return new Terrain(Mat.GRASS, Mat.DIRT, height, leaves, false, Mat.DIRT);
        }
        return new Terrain(Mat.GRASS, Mat.DIRT, height, null, false, Mat.DIRT);
    }

    private Terrain desert(final float e, final float m, final float lat) {
        final float h = e + 0.3f;
        if (h < 0f) return new Terrain(Mat.WATER, Mat.SAND, 0, null, true, Mat.SAND);
        if (lat > 0.93f) return new Terrain(Mat.SNOW, Mat.STONE, h > 0.5f ? 1 : 0, null, false, Mat.STONE);
        final int height = h < 0.42f ? 0 : h < 0.58f ? 1 : h < 0.72f ? 2 : 3;
        if (h < 0.07f) return new Terrain(Mat.GRASS, Mat.DIRT, 0, m > 0f ? Mat.LEAVES : null, false, Mat.DIRT);   // an oasis round the water
        if (height >= 1) {
            final Mat band = switch (height) { case 1 -> Mat.ORANGE; case 2 -> Mat.TERRACOTTA; default -> Mat.WHITE; };
            return new Terrain(m > 0.25f ? Mat.RED : band, band, height, null, false, Mat.TERRACOTTA);
        }
        return new Terrain(m > 0.3f ? Mat.RED_SAND : Mat.SAND, Mat.SAND, 0, null, false, Mat.SAND);
    }

    private Terrain ice(final float e, final float m, final float lat) {
        final float h = e + 0.04f;
        if (h < 0f) {
            final boolean open = lat < 0.3f && m > 0.05f;
            return open ? new Terrain(Mat.WATER, Mat.GRAVEL, 0, null, true, Mat.GRAVEL) : new Terrain(Mat.ICE, Mat.ICE, 0, null, false, Mat.GRAVEL);
        }
        final int height = h < 0.14f ? 0 : h < 0.3f ? 1 : h < 0.44f ? 2 : 3;
        if (height == 2) return new Terrain(Mat.STONE, Mat.STONE, 2, null, false, Mat.STONE);
        return new Terrain(Mat.SNOW, height == 3 ? Mat.STONE : Mat.DIRT, height, m > 0.22f && height < 2 ? Mat.SPRUCE : null, false, Mat.STONE);
    }

    private Terrain nether(final float e, final float m) {
        if (e < -0.08f) return new Terrain(Mat.LAVA, Mat.MAGMA, 0, null, false, Mat.MAGMA);
        final int height = e < 0.1f ? 0 : e < 0.26f ? 1 : e < 0.4f ? 2 : 3;
        if (e < -0.03f) return new Terrain(Mat.MAGMA, Mat.NETHERRACK, 0, null, false, Mat.NETHERRACK);
        if (height >= 2) return new Terrain(height == 3 ? Mat.BLACKSTONE : Mat.BASALT, Mat.BASALT, height, null, false, Mat.BASALT);
        if (m > 0.2f) return new Terrain(Mat.CRIMSON, Mat.NETHERRACK, height, m > 0.34f ? Mat.WART : null, false, Mat.NETHERRACK);
        if (m < -0.22f) return new Terrain(Mat.WARPED, Mat.NETHERRACK, height, m < -0.36f ? Mat.WARPED_WART : null, false, Mat.NETHERRACK);
        return new Terrain(Mat.NETHERRACK, Mat.NETHERRACK, height, null, false, Mat.NETHERRACK);
    }

    private Terrain end(final float e, final float m) {
        final int height = e < 0.18f ? 0 : e < 0.34f ? 1 : e < 0.46f ? 2 : 3;
        if (height == 3) return new Terrain(Mat.OBSIDIAN, Mat.OBSIDIAN, 3, null, false, Mat.END_STONE);
        return new Terrain(Mat.END_STONE, Mat.END_STONE, height, m > 0.36f && height < 2 ? Mat.PURPUR : null, false, Mat.END_STONE);
    }

    // ------------------------------------------------------------------ meshing

    private static int index(final int x, final int y, final int z) {
        return (x * GRID + y) * GRID + z;
    }

    /** Fills the grid: 0 is air, anything else a material's ordinal plus one. */
    private byte[] voxels() {
        final byte[] cells = new byte[GRID * GRID * GRID];
        final Map<Integer, Terrain> cache = new HashMap<>();
        final float c = (GRID - 1) / 2f;
        final Random rnd = new Random(mix(seed + 31L));
        for (int x = 0; x < GRID; x++) {
            for (int y = 0; y < GRID; y++) {
                for (int z = 0; z < GRID; z++) {
                    final float cx = x - c, cy = y - c, cz = z - c;
                    final float r = Math.max(Math.abs(cx), Math.max(Math.abs(cy), Math.abs(cz)));
                    final int shell = Math.round(r - SURFACE);
                    if (shell < -1) {
                        cells[index(x, y, z)] = (byte) (Mat.STONE.ordinal() + 1);      // the inside, never seen
                        continue;
                    }
                    // The cell of the surface this one stands on (or lies under): every cell of a column shares it,
                    // so nothing ever floats, and the three columns that meet on an edge agree on their height.
                    final float bx = base(cx, r), by = base(cy, r), bz = base(cz, r);
                    final int key = index(Math.round(bx + c), Math.round(by + c), Math.round(bz + c));
                    Terrain t = cache.get(key);
                    if (t == null) {
                        final float len = (float) Math.sqrt(bx * bx + by * by + bz * bz);
                        t = terrain(bx / len, by / len, bz / len);
                        // A canopy is a scatter of crowns, not a lid: some of a forest's cells stay bare.
                        if (t.canopy() != null && rnd.nextFloat() > 0.62f) t = new Terrain(t.top(), t.under(), t.height(), null, t.sea(), t.bed());
                        cache.put(key, t);
                    }
                    final Mat mat;
                    if (shell == -1) mat = t.sea() ? t.bed() : t.under();
                    else if (shell == 0) mat = t.height() == 0 ? t.top() : t.under();
                    else if (shell <= t.height()) mat = shell == t.height() ? t.top() : t.under();
                    else if (shell == t.height() + 1 && t.canopy() != null) mat = t.canopy();
                    else mat = null;
                    if (mat != null) cells[index(x, y, z)] = (byte) (mat.ordinal() + 1);
                }
            }
        }
        return cells;
    }

    private static float base(final float v, final float r) {
        final float a = Math.abs(v);
        return a >= r - 0.01f || a > SURFACE ? Math.copySign(SURFACE, v) : v;
    }

    private QuadMesh build() {
        final byte[] cells = voxels();
        final TextureAtlasSprite[] sprites = new TextureAtlasSprite[MATS.length];
        final QuadMesh.Builder b = QuadMesh.builder();
        final int half = GRID / 2;
        for (int x = 0; x < GRID; x++) {
            for (int y = 0; y < GRID; y++) {
                for (int z = 0; z < GRID; z++) {
                    final int id = cells[index(x, y, z)];
                    if (id == 0) continue;
                    final Mat mat = MATS[id - 1];
                    for (final Direction d : Direction.values()) {
                        final int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
                        final boolean outside = nx < 0 || ny < 0 || nz < 0 || nx >= GRID || ny >= GRID || nz >= GRID;
                        final int nid = outside ? 0 : cells[index(nx, ny, nz)];
                        if (nid != 0) {
                            final Mat other = MATS[nid - 1];
                            // Hidden behind something solid, or the same see-through stuff going on.
                            if (!other.seeThrough() || other == mat) continue;
                            // Leaves resting on the ground: their underside is never seen.
                            if (mat.gaps && !other.translucent) continue;
                        }
                        if (sprites[id - 1] == null) sprites[id - 1] = QuadMesh.sprite(mat.texture);
                        b.face(mat.translucent, d, x - half, y - half, z - half, sprites[id - 1], TEXELS, colour(mat));
                    }
                }
            }
        }
        return b.build();
    }

    private int colour(final Mat mat) {
        return switch (mat.tint) {
            case GRASS -> grass;
            case FOLIAGE -> foliage;
            case WATER -> (water & 0x00FFFFFF) | 0xD8000000;
            default -> mat.fixed;
        };
    }

    private void ensureMesh() {
        final int gen = StageResources.generation();
        if (mesh != null && gen == meshResources) return;
        if (broken && gen == meshResources) return;
        if (mesh != null) { mesh.close(); mesh = null; }
        meshResources = gen;
        try {
            mesh = build();
            broken = false;
        } catch (final Exception e) {
            broken = true;
            Slate.LOGGER.error("[Slate] stage: planet {} could not be built", seed, e);
        }
    }

    // ------------------------------------------------------------------ clouds and ring

    private void buildClouds() {
        final Random rnd = new Random(mix(seed + 7L));
        final float out = SIDE / 2f + CLOUD_LIFT;
        final int count = 7 + rnd.nextInt(3);
        for (int i = 0; i < count; i++) {
            final float angle = (i + rnd.nextFloat() * 0.7f) / count * Mth.TWO_PI;
            final float y = (rnd.nextFloat() - 0.5f) * (SIDE - 6f);
            final float w = 1.5f + rnd.nextFloat() * 1.7f, h = 0.8f + rnd.nextFloat() * 0.9f;
            clouds.add(new Cloud(true, out + rnd.nextFloat() * 0.8f, y, angle, w, h, 0.42f));
            // A second slab, set off, turns a box into a cloud.
            if (rnd.nextInt(3) != 0) {
                clouds.add(new Cloud(true, out + 0.5f + rnd.nextFloat() * 0.6f, y + (rnd.nextBoolean() ? 0.9f : -0.9f) * h,
                    angle + (rnd.nextBoolean() ? 0.09f : -0.09f), w * 0.6f, h * 0.62f, 0.38f));
            }
        }
        // And a few over the top face, which is the one looked down on.
        final int caps = 3 + rnd.nextInt(2);
        for (int i = 0; i < caps; i++) {
            final float angle = (i + rnd.nextFloat() * 0.6f) / caps * Mth.TWO_PI;
            final float r = 3f + rnd.nextFloat() * (SIDE / 2f - 5.5f);
            final float w = 1.5f + rnd.nextFloat() * 1.5f, d = 0.9f + rnd.nextFloat() * 0.8f;
            clouds.add(new Cloud(false, r, out + rnd.nextFloat() * 0.8f, angle, w, 0.4f, d));
            if (rnd.nextBoolean()) clouds.add(new Cloud(false, r + d * 0.9f, out + 0.45f + rnd.nextFloat() * 0.4f, angle + 0.12f, w * 0.6f, 0.36f, d * 0.65f));
        }
    }

    /** How far from the axis the belt's path runs in direction {@code angle}: a square with its corners taken off. */
    private static float beltRadius(final float radius, final float angle) {
        final double c = Math.abs(Math.cos(angle)), s = Math.abs(Math.sin(angle));
        return (float) (radius / Math.pow(Math.pow(c, BELT_SQUARE) + Math.pow(s, BELT_SQUARE), 1.0 / BELT_SQUARE));
    }

    @Override
    public void update(final StageRenderContext ctx) {
        if (Theme.current().motion() <= 0f) return;
        cloudAngle = (cloudAngle + cloudDegPerSec * ctx.deltaMs / 1000f) % 360f;
        ringAngle = (ringAngle + 11f * ctx.deltaMs / 1000f) % 360f;
    }

    // ------------------------------------------------------------------ drawing

    /** How bright each of the six sides is, turned as the planet is right now. */
    private void shade(final StageRenderContext ctx, final Matrix4f local) {
        final Vector3f sun = ctx.lighting.sun();
        final float ambient = 0.5f;
        for (final Direction d : Direction.values()) {
            normal.set(d.getStepX(), d.getStepY(), d.getStepZ());
            local.transformDirection(normal);
            if (normal.lengthSquared() > 1e-8f) normal.normalize();
            final float lit = Math.max(0f, normal.dot(sun));
            shades[d.ordinal()] = Mth.clamp(ambient + (1f - ambient) * lit, 0f, 1f);
        }
    }

    @Override
    protected void draw(final StageRenderContext ctx) {
        ensureMesh();
        if (mesh == null) return;
        shade(ctx, model);
        final float a = alpha();
        if (a < 1f) return;                          // fading: everything goes in the translucent pass, in order
        if (ctx.soft) mesh.drawSoft(false, ctx.pose.last().pose(), ctx.camera.projection(), 1f);
        else mesh.draw(false, ctx.pose.last().pose(), ctx.camera.projection(), d -> shades[d.ordinal()], ctx.lighting.brightness(), 1f);
    }

    @Override
    public boolean hasTranslucentPass() { return true; }

    @Override
    protected void drawTranslucent(final StageRenderContext ctx) {
        if (mesh == null) return;
        final float a = alpha();
        final Matrix4f pose = ctx.pose.last().pose();
        shade(ctx, model);
        if (ctx.soft) {
            if (a < 1f) mesh.drawSoft(false, pose, ctx.camera.projection(), a);
            mesh.drawSoft(true, pose, ctx.camera.projection(), a);
        } else {
            if (a < 1f) {
                RenderSystem.depthMask(true);
                mesh.draw(false, pose, ctx.camera.projection(), d -> shades[d.ordinal()], ctx.lighting.brightness(), a);
                RenderSystem.depthMask(false);
            }
            mesh.draw(true, pose, ctx.camera.projection(), d -> shades[d.ordinal()], ctx.lighting.brightness(), a);
        }

        final boolean wantClouds = showClouds && !clouds.isEmpty();
        if (!wantClouds && !ring) return;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        dev.fallingcloud.slate.core.stage.StageBlend.over();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        if (ring) {
            // The ring is solid light: it writes depth, so clouds passing behind it stay behind.
            RenderSystem.depthMask(true);
            final BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            ring(bb, pose, a);
            final var data = bb.build();
            if (data != null) BufferUploader.drawWithShader(data);
        }
        if (wantClouds) {
            RenderSystem.depthMask(false);
            final BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (final Cloud c : clouds) cloud(bb, pose, c, a);
            final var data = bb.build();
            if (data != null) BufferUploader.drawWithShader(data);
        }
        RenderSystem.depthMask(true);
    }

    /** One slab of cloud: flat, its long side along the circle it rides on. */
    private void cloud(final BufferBuilder bb, final Matrix4f pose, final Cloud c, final float alpha) {
        final float angle = c.angle() + (float) Math.toRadians(cloudAngle);
        final float turn;
        if (c.belt()) {
            // Where it is on the path, and which way the path runs there: the slab lies along it.
            final float r = beltRadius(c.radius(), angle);
            final float x = Mth.sin(angle) * r, z = Mth.cos(angle) * r;
            final float ahead = angle + 0.02f, ra = beltRadius(c.radius(), ahead);
            final float behind = angle - 0.02f, rb = beltRadius(c.radius(), behind);
            final float tx = Mth.sin(ahead) * ra - Mth.sin(behind) * rb, tz = Mth.cos(ahead) * ra - Mth.cos(behind) * rb;
            turn = (float) Math.atan2(-tz, tx);
            rotated.set(pose).translate(x, c.y(), z).rotateY(turn);
        } else {
            turn = angle;
            rotated.set(pose).rotateY(angle).translate(0f, c.y(), c.radius());
        }
        // Lit like the planet: by the way this slab is turned in the world.
        local.set(model).rotateY(turn);
        box(bb, rotated, local, c.halfW(), c.halfH(), c.halfD(), 255, 255, 255, Math.round(alpha * 222f), 0.66f);
    }

    private final Matrix4f local = new Matrix4f();

    private void ring(final BufferBuilder bb, final Matrix4f pose, final float alpha) {
        final float radius = (SIDE / 2f + CLOUD_LIFT) * 1.4142f + 3.2f;
        final int beads = 30;
        final int r = (ringColor >> 16) & 0xFF, g = (ringColor >> 8) & 0xFF, b = ringColor & 0xFF;
        final int a = Math.round(((ringColor >>> 24) & 0xFF) * alpha);
        for (int i = 0; i < beads; i++) {
            final float angle = i / (float) beads * Mth.TWO_PI + (float) Math.toRadians(ringAngle);
            // Tilted a little against the planet's own turn, like a ring that has its own mind.
            rotated.set(pose).rotateZ(0.2f).rotateX(0.12f).rotateY(angle).translate(0f, 0f, radius);
            local.set(model).rotateZ(0.2f).rotateX(0.12f).rotateY(angle);
            final float s = i % 3 == 0 ? 0.78f : 0.52f;
            box(bb, rotated, local, s, s, s, r, g, b, a, 0.78f);
        }
    }

    /** A box around the origin of {@code at}, each side shaded for the way {@code turned} points it (floor: {@code ambient}). */
    private void box(final BufferBuilder bb, final Matrix4f at, final Matrix4f turned, final float hx, final float hy, final float hz,
                     final int r, final int g, final int b, final int a, final float ambient) {
        final Vector3f sun = sunOr();
        for (final Direction d : Direction.values()) {
            normal.set(d.getStepX(), d.getStepY(), d.getStepZ());
            turned.transformDirection(normal);
            if (normal.lengthSquared() > 1e-8f) normal.normalize();
            final float s = Mth.clamp(ambient + (1f - ambient) * Math.max(0f, normal.dot(sun)), 0f, 1f);
            final int cr = Math.round(r * s), cg = Math.round(g * s), cb = Math.round(b * s);
            final float x0 = -hx, x1 = hx, y0 = -hy, y1 = hy, z0 = -hz, z1 = hz;
            switch (d) {
                case UP -> quad(bb, at, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, cr, cg, cb, a);
                case DOWN -> quad(bb, at, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, cr, cg, cb, a);
                case NORTH -> quad(bb, at, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, cr, cg, cb, a);
                case SOUTH -> quad(bb, at, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, cr, cg, cb, a);
                case WEST -> quad(bb, at, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, cr, cg, cb, a);
                default -> quad(bb, at, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, cr, cg, cb, a);
            }
        }
    }

    private Vector3f sun = new Vector3f(0.35f, 1f, 0.55f).normalize();

    private Vector3f sunOr() { return sun; }

    @Override
    public void prepare(final StageRenderContext ctx) {
        super.prepare(ctx);
        sun = ctx.lighting.sun();
    }

    private void quad(final BufferBuilder bb, final Matrix4f m,
                      final float ax, final float ay, final float az, final float bx, final float by, final float bz,
                      final float cx, final float cy, final float cz, final float dx, final float dy, final float dz,
                      final int r, final int g, final int b, final int a) {
        bb.addVertex(m, ax, ay, az).setColor(r, g, b, a);
        bb.addVertex(m, bx, by, bz).setColor(r, g, b, a);
        bb.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
        bb.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
    }

    @Override
    public void dispose() {
        if (mesh != null) { mesh.close(); mesh = null; }
    }
}
