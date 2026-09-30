package dev.fallingcloud.slate.menu.client.loading.journey;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.StageResources;
import dev.fallingcloud.slate.core.stage.StageSoft;
import dev.fallingcloud.slate.core.stage.mesh.QuadMesh;
import dev.fallingcloud.slate.core.stage.node.StageNode;
import dev.fallingcloud.slate.menu.SlateMenu;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A {@link Land} as a model that builds itself: its tiles come up out of the ground in rings around the middle, each
 * with a little bounce, the way chunks appear at the edge of sight. How far the rings have got is the owner's to say
 * ({@link #reach}): the loading screen ties it to how far the loading is. {@link #leave} takes them down again, the
 * outermost first.
 *
 * <p>The tiles of a ring are dealt into a few groups that come up a moment after one another, so a ring does not
 * rise as one slab. A group is one mesh, built when it is its turn and every cell of it has been looked at, and
 * moved as a whole while it rises. Where a group borders on another its cut goes all the way down, in a few tall
 * pieces: that is what shows while the neighbour is not up yet, and is hidden for good once it is.</p>
 *
 * <p>The node's origin is the middle of the land, on the ground everything stands on. One cell is one unit.</p>
 */
final class LandNode extends StageNode {

    /** How many groups the tiles of a ring are dealt into, and how much later (in rings) each comes up. */
    private static final int GROUPS = 3;
    private static final float LATER = 0.34f;
    /** Cells a tile comes up from, and how long it takes. */
    private static final float DROP = 15f;
    private static final float RISE_MS = 760f, SINK_MS = 620f;
    private static final int TEXELS = 8;
    /** Cells of a cut laid cell by cell under a surface; below them the cut is laid in pieces this tall. */
    private static final int FINE = 5, PIECE = 8;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int MAX_BUILDS = 2;

    private static final class Group {
        final int index;
        final float from;
        final List<int[]> tiles = new ArrayList<>();
        @Nullable VertexBuffer solid, clear;
        boolean built;
        float upAt = -1f, downAt = -1f;

        Group(final int index, final float from) {
            this.index = index;
            this.from = from;
        }
    }

    private final Land land;
    private final int[] groupOf = new int[Land.TILES_X * Land.TILES_Z];
    private final Group[] groups;
    private final float rings;
    private final Map<String, TextureAtlasSprite> sprites = new HashMap<>();
    private final float[] shades = new float[6];
    private final Vector3f normal = new Vector3f();
    private float reach, leaving;
    private int resources = -1;
    private boolean soft, broken;
    private int up;

    LandNode(final Land land) {
        this.land = land;
        final float mx = Land.TILES_X / 2f, mz = Land.TILES_Z / 2f;
        final int count = (int) Math.floor(Math.sqrt(mx * mx + mz * mz)) + 1;
        rings = count + LATER * (GROUPS - 1);
        final Group[] all = new Group[count * GROUPS];
        for (int r = 0; r < count; r++) for (int g = 0; g < GROUPS; g++) all[r * GROUPS + g] = new Group(r * GROUPS + g, r + g * LATER);
        for (int tz = 0; tz < Land.TILES_Z; tz++) {
            for (int tx = 0; tx < Land.TILES_X; tx++) {
                final float dx = tx + 0.5f - mx, dz = tz + 0.5f - mz;
                final int ring = Math.min(count - 1, (int) Math.floor(Math.sqrt(dx * dx + dz * dz)));
                final int group = ring * GROUPS + Math.floorMod(tx * 7 + tz * 13 + (tx ^ tz) * 3, GROUPS);
                groupOf[tz * Land.TILES_X + tx] = group;
                all[group].tiles.add(new int[] {tx, tz});
            }
        }
        groups = Arrays.stream(all).filter(g -> !g.tiles.isEmpty()).sorted(Comparator.comparingDouble(g -> g.from)).toArray(Group[]::new);
        bounds(-Land.WIDTH / 2f, 0f, -Land.DEPTH / 2f, Land.WIDTH / 2f, 80f, Land.DEPTH / 2f);
        hoverFeel(0f, 1f);
        pickable(false);
        named("land");
    }

    /** How far from the middle the land is up, 0..1 of the way to its furthest corner. */
    LandNode reach(final float part) {
        this.reach = Mth.clamp(part, 0f, 1f);
        return this;
    }

    /** How much of the land has gone down again, from the rim inwards: 0 = none of it, 1 = all. */
    LandNode leave(final float part) {
        this.leaving = Mth.clamp(part, 0f, 1f);
        return this;
    }

    /** Puts up at once whatever is within reach: a land that is shown as it was left, not as it comes. */
    void settle(final float nowMs) {
        for (final Group g : groups) if (g.upAt < 0f && g.from <= reach * rings) g.upAt = nowMs - RISE_MS;
    }

    /** How many groups are up or on their way. */
    int up() { return up; }

    // ------------------------------------------------------------------ building

    private TextureAtlasSprite sprite(final String texture) {
        return sprites.computeIfAbsent(texture, t -> t.indexOf(':') >= 0 ? QuadMesh.sprite(ResourceLocation.parse(t)) : QuadMesh.sprite(t));
    }

    /**
     * What a cut shows so many cells under the surface. Under the earth it is stone all the way: a cut here is as
     * often the face of a cliff as the rim of the land, and a cliff of the deep's dark slate is not what a hill is made of.
     */
    private static String stratum(final Land.Cell c, final int below) {
        if (below <= 0) return c.top();
        if (below <= c.strata()) return c.under();
        return "stone";
    }

    private static int tint(final Land.Cell c, final int below) {
        return below <= 0 ? c.topTint() : below <= c.strata() ? c.underTint() : WHITE;
    }

    private boolean ready(final Group g) {
        for (final int[] t : g.tiles) if (!land.ready(t[0], t[1])) return false;
        return true;
    }

    private int groupAt(final int x, final int z) {
        return x < 0 || z < 0 || x >= Land.WIDTH || z >= Land.DEPTH ? -1 : groupOf[z / Land.TILE * Land.TILES_X + x / Land.TILE];
    }

    /** One layer of a group's mesh while it is put together. */
    private final class Sheet {
        private final ByteBufferBuilder memory = new ByteBufferBuilder(1 << 15);
        private final BufferBuilder buffer = new BufferBuilder(memory, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

        /** The {@code face} side of the cell at {@code (x, y, z)}, the texture laid over the cells in windows. */
        void face(final Direction face, final int x, final int y, final int z, final TextureAtlasSprite sprite, final int texels, final int argb) {
            final int a = face.getAxis() == Direction.Axis.X ? z : x, b = face.getAxis() == Direction.Axis.Y ? z : y;
            final int t = Math.max(1, Math.min(16, texels)), span = Math.max(1, 16 / t);
            final float u0 = Math.floorMod(a, span) * t / 16f;
            final int row = face.getAxis() == Direction.Axis.Y ? Math.floorMod(b, span) : span - 1 - Math.floorMod(b, span);
            final float v0 = row * t / 16f;
            quad(face, x, y, y + 1f, z, sprite, u0, u0 + t / 16f, v0, v0 + t / 16f, argb);
        }

        /** The {@code face} side of the cells from {@code y0} up to {@code y1}, as one piece: the texture is drawn out over it. */
        void piece(final Direction face, final int x, final int y0, final int y1, final int z, final TextureAtlasSprite sprite, final int argb) {
            final int a = face.getAxis() == Direction.Axis.X ? z : x;
            final float u0 = Math.floorMod(a, 2) * 0.5f;
            quad(face, x, y0, y1, z, sprite, u0, u0 + 0.5f, 0f, 1f, argb);
        }

        private void quad(final Direction face, final float x, final float y, final float y1, final float z, final TextureAtlasSprite sprite,
                          final float u0, final float u1, final float v0, final float v1, final int tint) {
            final float x1 = x + 1f, z1 = z + 1f;
            final float nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();
            // Without the soft light every side is as bright as the way it faces makes it, once and for all.
            final int argb = soft ? tint : shaded(tint, shades[face.ordinal()]);
            switch (face) {
                case UP -> {
                    vertex(sprite, x, y1, z, u0, v0, argb, nx, ny, nz);
                    vertex(sprite, x, y1, z1, u0, v1, argb, nx, ny, nz);
                    vertex(sprite, x1, y1, z1, u1, v1, argb, nx, ny, nz);
                    vertex(sprite, x1, y1, z, u1, v0, argb, nx, ny, nz);
                }
                case DOWN -> {
                    vertex(sprite, x, y, z1, u0, v1, argb, nx, ny, nz);
                    vertex(sprite, x, y, z, u0, v0, argb, nx, ny, nz);
                    vertex(sprite, x1, y, z, u1, v0, argb, nx, ny, nz);
                    vertex(sprite, x1, y, z1, u1, v1, argb, nx, ny, nz);
                }
                case NORTH -> {
                    vertex(sprite, x, y, z, u0, v1, argb, nx, ny, nz);
                    vertex(sprite, x, y1, z, u0, v0, argb, nx, ny, nz);
                    vertex(sprite, x1, y1, z, u1, v0, argb, nx, ny, nz);
                    vertex(sprite, x1, y, z, u1, v1, argb, nx, ny, nz);
                }
                case SOUTH -> {
                    vertex(sprite, x1, y, z1, u1, v1, argb, nx, ny, nz);
                    vertex(sprite, x1, y1, z1, u1, v0, argb, nx, ny, nz);
                    vertex(sprite, x, y1, z1, u0, v0, argb, nx, ny, nz);
                    vertex(sprite, x, y, z1, u0, v1, argb, nx, ny, nz);
                }
                case WEST -> {
                    vertex(sprite, x, y, z1, u1, v1, argb, nx, ny, nz);
                    vertex(sprite, x, y1, z1, u1, v0, argb, nx, ny, nz);
                    vertex(sprite, x, y1, z, u0, v0, argb, nx, ny, nz);
                    vertex(sprite, x, y, z, u0, v1, argb, nx, ny, nz);
                }
                default -> {
                    vertex(sprite, x1, y, z, u0, v1, argb, nx, ny, nz);
                    vertex(sprite, x1, y1, z, u0, v0, argb, nx, ny, nz);
                    vertex(sprite, x1, y1, z1, u1, v0, argb, nx, ny, nz);
                    vertex(sprite, x1, y, z1, u1, v1, argb, nx, ny, nz);
                }
            }
        }

        private void vertex(final TextureAtlasSprite sprite, final float x, final float y, final float z, final float u, final float v,
                            final int argb, final float nx, final float ny, final float nz) {
            buffer.addVertex(x, y, z).setColor(argb).setUv(sprite.getU(u), sprite.getV(v)).setLight(LightTexture.FULL_BRIGHT).setNormal(nx, ny, nz);
        }

        @Nullable
        VertexBuffer upload() {
            try {
                final MeshData data = buffer.build();
                if (data == null) return null;
                final VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vbo.bind();
                vbo.upload(data);
                VertexBuffer.unbind();
                return vbo;
            } finally {
                memory.close();
            }
        }
    }

    private static int shaded(final int argb, final float shade) {
        final int r = Math.round((argb >> 16 & 0xFF) * shade), g = Math.round((argb >> 8 & 0xFF) * shade), b = Math.round((argb & 0xFF) * shade);
        return argb & 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** A cut from {@code from} up to {@code to}, in tall pieces, as the ground shows it that far under {@code c}'s surface. */
    private void pieces(final Sheet sheet, final Direction d, final int wx, final int wz, final int from, final int to, final Land.Cell c) {
        for (int y1 = to; y1 > from; y1 -= PIECE) {
            final int y0 = Math.max(from, y1 - PIECE);
            final int below = c.ground() - y1;
            sheet.piece(d, wx, y0, y1, wz, sprite(stratum(c, Math.max(1, below))), tint(c, Math.max(1, below)));
        }
    }

    private void build(final Group group) {
        final Sheet solid = new Sheet(), clear = new Sheet();
        final int ox = -Land.WIDTH / 2, oz = -Land.DEPTH / 2;
        for (final int[] tile : group.tiles) {
            for (int z = tile[1] * Land.TILE; z < (tile[1] + 1) * Land.TILE; z++) {
                for (int x = tile[0] * Land.TILE; x < (tile[0] + 1) * Land.TILE; x++) {
                    final Land.Cell c = land.at(x, z);
                    if (c == null) continue;
                    final int g = c.ground(), level = c.level();
                    final int wx = ox + x, wz = oz + z;
                    final int water = c.waterTint() & 0x00FFFFFF | 0xD2000000;
                    solid.face(Direction.UP, wx, g - 1, wz, sprite(c.top()), TEXELS, c.topTint());
                    if (c.water() > 0) {
                        if (c.frozen()) solid.face(Direction.UP, wx, level - 1, wz, sprite("ice"), TEXELS, WHITE);
                        else clear.face(Direction.UP, wx, level - 1, wz, sprite("water_still"), TEXELS, water);
                    }
                    for (final Direction d : Direction.Plane.HORIZONTAL) {
                        final int nx = x + d.getStepX(), nz = z + d.getStepZ();
                        final Land.Cell n = land.at(nx, nz);
                        final boolean together = n != null && groupAt(nx, nz) == group.index;
                        final int ng = n == null ? 0 : n.ground();
                        // The cut that stays: the neighbour stands lower.
                        final int fine = Math.max(ng, g - FINE);
                        for (int y = fine; y < g; y++) solid.face(d, wx, y, wz, sprite(stratum(c, g - 1 - y)), TEXELS, tint(c, g - 1 - y));
                        if (ng < fine) pieces(solid, d, wx, wz, ng, fine, c);
                        // The cut that shows only until the neighbour is up.
                        if (!together && n != null) pieces(solid, d, wx, wz, 0, Math.min(ng, g), c);
                        if (c.water() > 0) {
                            final int nl = n == null ? 0 : n.level();
                            for (int y = Math.max(g, nl); y < level; y++) {
                                if (c.frozen() && y == level - 1) solid.face(d, wx, y, wz, sprite("ice"), TEXELS, WHITE);
                                else clear.face(d, wx, y, wz, sprite("water_still"), TEXELS, water);
                            }
                        }
                    }
                    if (c.tree()) {
                        final TextureAtlasSprite bark = sprite(c.trunk()), leaves = sprite(c.canopy());
                        solid.face(Direction.UP, wx, g + 1, wz, leaves, TEXELS, c.canopyTint());
                        for (final Direction d : Direction.Plane.HORIZONTAL) {
                            final Land.Cell n = land.at(x + d.getStepX(), z + d.getStepZ());
                            final int ng = n == null ? 0 : n.ground();
                            // Trees side by side at one height close up into a wood: no wall between them.
                            final boolean beside = n != null && n.tree() && ng == g && groupAt(x + d.getStepX(), z + d.getStepZ()) == group.index;
                            if (!beside && ng <= g) solid.face(d, wx, g, wz, bark, 16, WHITE);
                            if (!beside && ng <= g + 1) solid.face(d, wx, g + 1, wz, leaves, TEXELS, c.canopyTint());
                        }
                    }
                }
            }
        }
        group.solid = solid.upload();
        group.clear = clear.upload();
        group.built = true;
    }

    private static void close(final Group g) {
        if (g.solid != null) { g.solid.close(); g.solid = null; }
        if (g.clear != null) { g.clear.close(); g.clear = null; }
        g.built = false;
    }

    private void shade(final StageRenderContext ctx) {
        final Vector3f sun = ctx.lighting.sun();
        for (final Direction d : Direction.values()) {
            normal.set(d.getStepX(), d.getStepY(), d.getStepZ());
            model.transformDirection(normal);
            if (normal.lengthSquared() > 1e-8f) normal.normalize();
            shades[d.ordinal()] = Mth.clamp(0.5f + 0.5f * Math.max(0f, normal.dot(sun)), 0f, 1f);
        }
    }

    // ------------------------------------------------------------------ living

    @Override
    public void update(final StageRenderContext ctx) {
        if (broken) return;
        final int gen = StageResources.generation();
        if (gen != resources || soft != ctx.soft) {
            // Other textures, or another light: what is up is put together again where it stands.
            sprites.clear();
            for (final Group g : groups) if (g.built) close(g);
            resources = gen;
            soft = ctx.soft;
            shade(ctx);
        }
        final float within = reach * rings;
        int builds = 0, count = 0;
        try {
            for (final Group g : groups) {
                if (g.upAt >= 0f) {
                    count++;
                    // Put up at once (a land shown as it was left, animations off): still built only once its cells are there.
                    if (!g.built && ready(g)) build(g);
                    continue;
                }
                // In their order: a ring never comes up before the one inside it.
                if (g.from > within || builds >= MAX_BUILDS || !ready(g)) break;
                build(g);
                g.upAt = ctx.timeMs;
                builds++;
                count++;
            }
        } catch (final Exception e) {
            broken = true;
            SlateMenu.LOGGER.error("[Slate Menu] the land could not be built", e);
        }
        up = count;
        // Going: from the rim inwards, and never before a group has come up. The rim is where the land ends, which
        // may be well inside the field: what was seen of a world is as wide as the player could see.
        float rim = 0f;
        for (final Group g : groups) if (g.upAt >= 0f && (g.solid != null || g.clear != null)) rim = Math.max(rim, g.from);
        final float gone = leaving * (rim + 1f);
        for (final Group g : groups) {
            if (g.upAt < 0f) continue;
            final boolean goes = leaving > 0f && rim - g.from < gone;
            if (goes && g.downAt < 0f) g.downAt = ctx.timeMs;
            else if (!goes && g.downAt >= 0f) g.downAt = -1f;
        }
    }

    /** How far under its place a group stands now; NaN once it has gone. */
    private static float under(final Group g, final float nowMs) {
        float y = 0f;
        if (g.upAt >= 0f) y = -DROP * (1f - Ease.OUT_BACK.apply((nowMs - g.upAt) / RISE_MS));
        if (g.downAt >= 0f) {
            final float t = (nowMs - g.downAt) / SINK_MS;
            if (t >= 1f) return Float.NaN;
            y -= DROP * 1.6f * t * t * t;
        }
        return y;
    }

    private void draw(final StageRenderContext ctx, final boolean translucent) {
        if (broken) return;
        final float a = alpha();
        final RenderType type = translucent ? RenderType.translucent() : RenderType.cutoutMipped();
        final float[] was = RenderSystem.getShaderColor().clone();
        boolean any = false;
        for (final Group g : groups) {
            final VertexBuffer vbo = translucent ? g.clear : g.solid;
            if (vbo == null || g.upAt < 0f) continue;
            final float y = under(g, ctx.timeMs);
            if (Float.isNaN(y)) continue;
            ctx.pose.pushPose();
            ctx.pose.translate(0f, y, 0f);
            if (soft) {
                StageSoft.draw(vbo, ctx.pose.last().pose(), ctx.camera.projection(), TextureAtlas.LOCATION_BLOCKS, a, translucent);
            } else {
                if (!any) {
                    type.setupRenderState();
                    any = true;
                }
                final float b = ctx.lighting.brightness();
                RenderSystem.setShaderColor(b, b, b, a);
                vbo.bind();
                vbo.drawWithShader(ctx.pose.last().pose(), ctx.camera.projection(), RenderSystem.getShader());
            }
            ctx.pose.popPose();
        }
        if (any) {
            VertexBuffer.unbind();
            type.clearRenderState();
        }
        RenderSystem.setShaderColor(was[0], was[1], was[2], was[3]);
    }

    @Override
    protected void draw(final StageRenderContext ctx) {
        if (alpha() >= 1f) draw(ctx, false);
    }

    @Override
    public boolean hasTranslucentPass() { return true; }

    @Override
    protected void drawTranslucent(final StageRenderContext ctx) {
        if (alpha() < 1f) draw(ctx, false);
        draw(ctx, true);
    }

    @Override
    public void dispose() {
        for (final Group g : groups) close(g);
    }
}
