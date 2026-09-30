package dev.fallingcloud.slate.core.stage.node;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.StageResources;
import dev.fallingcloud.slate.core.stage.mesh.QuadMesh;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A piece of land as a model on a table: a field of columns, each as high as the ground is there, wearing what
 * grows on it, with water standing in the hollows and tree crowns on the forests. Along its rim the land is cut
 * straight down, so the cut shows earth, then stone, then the deep. It is what the world creation screen shows of
 * the world that is about to be made.
 *
 * <p>The columns come from wherever the owner gets them ({@link #put}, from any thread: working a world out takes
 * time, and must not hold up the screen). The model grows as they arrive: the node meshes what it has a few times a
 * second, and once more when the last column is in.</p>
 *
 * <p>The node's origin is the middle of the field, on the table. One cell is one unit.</p>
 */
public final class TerrainNode extends StageNode {

    /**
     * What stands on one cell.
     *
     * @param ground     cells of ground, 1 at least
     * @param top        the block texture of the ground's surface ({@code grass_block_top}), tinted {@code topTint}
     * @param under      the block texture right under the surface
     * @param water      cells of water standing on the ground, 0 for dry land
     * @param frozen     the water's surface is ice
     * @param trunk      the block texture of a tree's trunk standing on the ground, or null for no tree
     * @param canopy     the block texture of the crown on that trunk
     */
    public record Column(int ground, String top, int topTint, String under, int water, int waterTint, boolean frozen,
                         @Nullable String trunk, @Nullable String canopy, int canopyTint) {

        boolean tree() { return trunk != null && canopy != null && water <= 0; }
    }

    private static final int TEXELS = 8;
    /** How often the model is meshed again while columns are still arriving. */
    private static final long REMESH_MS = 280;
    private static final int WHITE = 0xFFFFFFFF;

    private final int width, depth;
    private final AtomicReferenceArray<Column> cells;
    private final AtomicInteger version = new AtomicInteger();
    private final AtomicInteger filled = new AtomicInteger();
    private final Map<String, TextureAtlasSprite> sprites = new HashMap<>();
    @Nullable private QuadMesh mesh;
    private int meshVersion = -1, meshResources = -1;
    private long meshedAt;
    private boolean broken;
    private final Anim rise = new Anim(0, 900, Ease.OUT_CUBIC);
    private final float[] shades = new float[6];
    private final Vector3f normal = new Vector3f();

    public TerrainNode(final int width, final int depth) {
        this.width = Math.max(1, width);
        this.depth = Math.max(1, depth);
        this.cells = new AtomicReferenceArray<>(this.width * this.depth);
        bounds(-this.width / 2f, 0f, -this.depth / 2f, this.width / 2f, 40f, this.depth / 2f);
        hoverFeel(0f, 1f);
        pickable(false);
        named("terrain");
    }

    public int width() { return width; }

    public int depth() { return depth; }

    /** Sets the column at {@code (x, z)}. Any thread. */
    public void put(final int x, final int z, final Column column) {
        if (x < 0 || z < 0 || x >= width || z >= depth) return;
        if (cells.getAndSet(z * width + x, column) == null) filled.incrementAndGet();
        version.incrementAndGet();
    }

    /** Takes every column away: the table is bare again. Any thread. */
    public void clear() {
        for (int i = 0; i < cells.length(); i++) cells.set(i, null);
        filled.set(0);
        version.incrementAndGet();
    }

    /** How many cells have their column. */
    public int filled() { return filled.get(); }

    public boolean complete() { return filled.get() >= width * depth; }

    /** 0..1: how much of the field is there. */
    public float progress() { return filled.get() / (float) (width * depth); }

    // ------------------------------------------------------------------ meshing

    private TextureAtlasSprite sprite(final String texture) {
        return sprites.computeIfAbsent(texture, QuadMesh::sprite);
    }

    @Nullable
    private Column at(final Column[] field, final int x, final int z) {
        return x < 0 || z < 0 || x >= width || z >= depth ? null : field[z * width + x];
    }

    /** What the cut through the ground shows {@code below} cells under the surface. */
    private static String stratum(final Column c, final int below) {
        if (below <= 0) return c.top();
        if (below <= 3) return c.under();
        return below <= 14 ? "stone" : "deepslate";
    }

    private QuadMesh build() {
        final Column[] field = new Column[width * depth];
        for (int i = 0; i < field.length; i++) field[i] = cells.get(i);
        final QuadMesh.Builder b = QuadMesh.builder();
        final int ox = -width / 2, oz = -depth / 2;
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                final Column c = field[z * width + x];
                if (c == null) continue;
                final int g = Math.max(1, c.ground());
                final int wx = ox + x, wz = oz + z;
                b.face(false, Direction.UP, wx, g - 1, wz, sprite(c.top()), TEXELS, c.topTint());
                final int level = g + Math.max(0, c.water());
                if (c.water() > 0) {
                    if (c.frozen()) b.face(false, Direction.UP, wx, level - 1, wz, sprite("ice"), TEXELS, WHITE);
                    else b.face(true, Direction.UP, wx, level - 1, wz, sprite("water_still"), TEXELS, (c.waterTint() & 0x00FFFFFF) | 0xD2000000);
                }
                for (final Direction d : Direction.Plane.HORIZONTAL) {
                    final Column n = at(field, x + d.getStepX(), z + d.getStepZ());
                    final int ng = n == null ? 0 : Math.max(1, n.ground());
                    for (int y = ng; y < g; y++) {
                        final int below = g - 1 - y;
                        b.face(false, d, wx, y, wz, sprite(stratum(c, below)), TEXELS, below == 0 ? c.topTint() : WHITE);
                    }
                    if (c.water() > 0) {
                        // Water shows its side where what stands next to it is lower: the rim, or a fall.
                        final int nl = n == null ? 0 : Math.max(1, n.ground()) + Math.max(0, n.water());
                        for (int y = Math.max(g, nl); y < level; y++) {
                            if (c.frozen() && y == level - 1) b.face(false, d, wx, y, wz, sprite("ice"), TEXELS, WHITE);
                            else b.face(true, d, wx, y, wz, sprite("water_still"), TEXELS, (c.waterTint() & 0x00FFFFFF) | 0xD2000000);
                        }
                    }
                }
                if (c.tree()) {
                    // A tree: a cell of trunk, a cell of crown on it.
                    final TextureAtlasSprite bark = sprite(c.trunk()), leaves = sprite(c.canopy());
                    b.face(false, Direction.UP, wx, g + 1, wz, leaves, TEXELS, c.canopyTint());
                    for (final Direction d : Direction.Plane.HORIZONTAL) {
                        final Column n = at(field, x + d.getStepX(), z + d.getStepZ());
                        final int ng = n == null ? 0 : Math.max(1, n.ground());
                        final boolean beside = n != null && n.tree() && ng == g;
                        // Trees that stand side by side at one height close up into a wood: no wall between them.
                        if (!beside && ng <= g) b.face(false, d, wx, g, wz, bark, 16, WHITE);
                        if (!beside && ng <= g + 1) b.face(false, d, wx, g + 1, wz, leaves, TEXELS, c.canopyTint());
                    }
                }
            }
        }
        return b.build();
    }

    private void ensureMesh() {
        final int gen = StageResources.generation();
        if (gen != meshResources) {
            sprites.clear();
            meshResources = gen;
            meshVersion = -1;
            broken = false;
        }
        final int v = version.get();
        if (v == meshVersion || broken) return;
        final long now = Clock.nowMs();
        // While columns still arrive the model is meshed a few times a second; the last column is never waited on.
        if (mesh != null && !complete() && now - meshedAt < REMESH_MS) return;
        final QuadMesh old = mesh;
        try {
            mesh = build();
            meshVersion = v;
            meshedAt = now;
            if (old == null) { rise.snap(0f); rise.set(1f); }
        } catch (final Exception e) {
            broken = true;
            mesh = null;
            Slate.LOGGER.error("[Slate] stage: terrain could not be built", e);
        }
        if (old != null) old.close();
    }

    // ------------------------------------------------------------------ drawing

    private void shade(final StageRenderContext ctx) {
        final Vector3f sun = ctx.lighting.sun();
        for (final Direction d : Direction.values()) {
            normal.set(d.getStepX(), d.getStepY(), d.getStepZ());
            model.transformDirection(normal);
            if (normal.lengthSquared() > 1e-8f) normal.normalize();
            shades[d.ordinal()] = Mth.clamp(0.5f + 0.5f * Math.max(0f, normal.dot(sun)), 0f, 1f);
        }
    }

    @Override
    protected void draw(final StageRenderContext ctx) {
        ensureMesh();
        if (mesh == null || mesh.quadCount() == 0) return;
        if (alpha() < 1f) return;
        // The land rises out of the table when it first shows.
        final float up = Math.max(0.02f, rise.get());
        ctx.pose.pushPose();
        ctx.pose.scale(1f, up, 1f);
        if (ctx.soft) mesh.drawSoft(false, ctx.pose.last().pose(), ctx.camera.projection(), 1f);
        else {
            shade(ctx);
            mesh.draw(false, ctx.pose.last().pose(), ctx.camera.projection(), d -> shades[d.ordinal()], ctx.lighting.brightness(), 1f);
        }
        ctx.pose.popPose();
    }

    @Override
    public boolean hasTranslucentPass() { return true; }

    @Override
    protected void drawTranslucent(final StageRenderContext ctx) {
        if (mesh == null || mesh.quadCount() == 0) return;
        final float a = alpha();
        final float up = Math.max(0.02f, rise.get());
        ctx.pose.pushPose();
        ctx.pose.scale(1f, up, 1f);
        if (ctx.soft) {
            if (a < 1f) mesh.drawSoft(false, ctx.pose.last().pose(), ctx.camera.projection(), a);
            mesh.drawSoft(true, ctx.pose.last().pose(), ctx.camera.projection(), a);
        } else {
            shade(ctx);
            if (a < 1f) mesh.draw(false, ctx.pose.last().pose(), ctx.camera.projection(), d -> shades[d.ordinal()], ctx.lighting.brightness(), a);
            mesh.draw(true, ctx.pose.last().pose(), ctx.camera.projection(), d -> shades[d.ordinal()], ctx.lighting.brightness(), a);
        }
        ctx.pose.popPose();
    }

    @Override
    public void dispose() {
        if (mesh != null) { mesh.close(); mesh = null; }
    }
}
