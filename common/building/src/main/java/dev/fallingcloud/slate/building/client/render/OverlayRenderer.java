package dev.fallingcloud.slate.building.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fallingcloud.slate.building.compat.SubLevels;
import dev.fallingcloud.slate.core.client.render.SlateRenderEvents;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * In-world overlays for selections and modes (design §6): glowing boxes, markers, lines, mirror planes and labels,
 * in world coordinates, colours ARGB (the alpha scales the whole element, so callers fade elements by their alpha;
 * {@link #accent()} is the Slate accent). Immediate mode with the same lifetime rules as {@link GhostRenderer}:
 * submissions made during a client tick stay up until the next tick, all others are drawn by the next world render
 * pass. Render thread only.
 *
 * <p>Look: every edge is a crisp core line over a soft wide glow, and faintly visible through terrain (an x-ray pass)
 * so a selection partly inside a hill still reads. Boxes have faint breathing faces; animated boxes run a light
 * sweep diagonally across their edges. Planes draw a fill, a border and a block grid with a travelling highlight.
 * Labels are camera-facing, keep a readable size at any distance and sit on a skin-matched plate with an accent
 * underline. All motion honours {@code Theme.motion()} (0 = static).
 *
 * <p>With Sable, an element whose coordinates lie in a sub-level's plot (a selection on a ship) is drawn where the
 * sub-level shows that place in this frame, turned with it ({@link SubLevels}): callers pass the coordinates the
 * blocks have, whichever space they are in. An element lies in one space, the one of its middle; a line may run from
 * one space into another (a move off a ship), and has each end where its own space shows it.
 */
public final class OverlayRenderer {

    private record Box(AABB box, int argb, boolean animated, float faces) {}

    private record Marker(BlockPos pos, int argb) {}

    private record Line(Vec3 from, Vec3 to, int argb) {}

    private record Plane(Direction.Axis axis, double coord, AABB bounds, int argb) {}

    private record Label(Vec3 pos, Component text, int argb, boolean throughWalls) {}

    private static final Buckets<Object> ITEMS = new Buckets<>();
    private static final RenderKit.DynamicBuffer FILLS = new RenderKit.DynamicBuffer();
    private static final RenderKit.DynamicBuffer EDGES = new RenderKit.DynamicBuffer();
    private static final RenderKit.DynamicBuffer GRID = new RenderKit.DynamicBuffer();
    private static final RenderKit.DynamicBuffer PLATES = new RenderKit.DynamicBuffer();

    /** The sub-level the element being written lies on, as it is drawn in this frame; null in the world itself. */
    private static @Nullable SubLevels.Pose space;

    /** Lines are grown this much off their box so they never fight the faces of the blocks they frame. */
    private static final double BOX_GROW = 0.004;
    /** Alpha of a box's faces relative to its edges (before {@link #box(AABB, int, boolean, float)}'s {@code faces}). */
    private static final float BOX_FACES = 0.075F;
    /** Sweep: seconds per run across a box and the width of the bright band (blocks). */
    private static final float SWEEP_PERIOD = 2.6F;
    private static final float SWEEP_WIDTH = 1.4F;

    // ======================================================================== API

    /** A box with glowing (optionally animated) edges and faint faces, e.g. an area selection. */
    public static void box(final AABB box, final int argb, final boolean animated) {
        box(box, argb, animated, 1F);
    }

    /**
     * A box with glowing (optionally animated) edges; {@code faces} scales its faint face fill (1 = the default, 0 =
     * edges only), e.g. toned down while ghost blocks inside the box should read as clearly as a placement ghost.
     */
    public static void box(final AABB box, final int argb, final boolean animated, final float faces) {
        if (RenderSystem.isOnRenderThread()) ITEMS.add(new Box(box, argb, animated, Math.max(0F, Math.min(1F, faces))));
    }

    /** A block-sized marker (corner brackets and a faint fill), e.g. a selection anchor. */
    public static void marker(final BlockPos pos, final int argb) {
        if (RenderSystem.isOnRenderThread()) ITEMS.add(new Marker(pos.immutable(), argb));
    }

    /** A line between two points, e.g. the line mode preview or a measure. */
    public static void line(final Vec3 from, final Vec3 to, final int argb) {
        if (RenderSystem.isOnRenderThread()) ITEMS.add(new Line(from, to, argb));
    }

    /** A plane perpendicular to {@code axis} at {@code coord}, clipped to {@code bounds}, drawn with a grid (mirror planes). */
    public static void plane(final Direction.Axis axis, final double coord, final AABB bounds, final int argb) {
        if (RenderSystem.isOnRenderThread()) ITEMS.add(new Plane(axis, coord, bounds, argb));
    }

    /** A camera-facing, readable text label at {@code pos}, faintly visible through walls. */
    public static void label(final Vec3 pos, final Component text, final int argb) {
        label(pos, text, argb, true);
    }

    /** A camera-facing, readable text label at {@code pos}; {@code throughWalls} adds a faint see-through copy. */
    public static void label(final Vec3 pos, final Component text, final int argb, final boolean throughWalls) {
        if (RenderSystem.isOnRenderThread()) ITEMS.add(new Label(pos, text, argb, throughWalls));
    }

    /** The Slate accent colour (opaque), the default for overlays. */
    public static int accent() {
        return Colors.withAlpha(Theme.current().palette().accent(), 0xFF);
    }

    // ======================================================================== lifecycle

    static void beginTick() {
        ITEMS.beginTick();
    }

    static void endPass() {
        ITEMS.endPass();
    }

    static void clearAll() {
        ITEMS.clear();
        FILLS.close();
        EDGES.close();
        GRID.close();
        PLATES.close();
    }

    // ======================================================================== drawing

    static void draw(final SlateRenderEvents.WorldRenderContext ctx) {
        if (ITEMS.isEmpty()) return;
        final List<Object> items = ITEMS.snapshot();
        final Camera camera = ctx.camera();
        final Vec3 cam = camera.getPosition();
        final float t = RenderKit.seconds();
        final boolean animated = RenderKit.animated();
        final Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());

        final boolean subLevels = SubLevels.present();
        final BufferBuilder fills = RenderKit.begin(GhostRenderer.bytes(), VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (final Object item : items) {
            space = subLevels ? spaceOf(item) : null;
            if (item instanceof Box b) {
                if (b.faces() > 0.01F) boxFaces(fills, b.box().inflate(BOX_GROW), b.argb(), t, cam, BOX_FACES * b.faces());
            } else if (item instanceof Marker m) boxFaces(fills, markerBox(m.pos(), t, animated), m.argb(), t, cam, 0.06F);
            else if (item instanceof Plane p) planeFill(fills, p, cam);
        }
        final boolean hasFills = FILLS.fill(fills);

        final BufferBuilder edges = RenderKit.begin(GhostRenderer.bytes(), VertexFormat.Mode.LINES, RenderKit.linesFormat());
        for (final Object item : items) {
            space = subLevels ? spaceOf(item) : null;
            if (item instanceof Box b) boxEdges(edges, b.box().inflate(BOX_GROW), b.argb(), b.animated() && animated, t, cam);
            else if (item instanceof Marker m) markerEdges(edges, markerBox(m.pos(), t, animated), m.argb(), cam);
            else if (item instanceof Line l) line(edges, l, cam, subLevels);
            else if (item instanceof Plane p) planeBorder(edges, p, cam);
        }
        final boolean hasEdges = EDGES.fill(edges);

        final BufferBuilder grid = RenderKit.begin(GhostRenderer.bytes(), VertexFormat.Mode.LINES, RenderKit.linesFormat());
        for (final Object item : items) {
            if (!(item instanceof Plane p)) continue;
            space = subLevels ? spaceOf(item) : null;
            planeGrid(grid, p, t, animated, cam);
        }
        space = null;
        final boolean hasGrid = GRID.fill(grid);

        RenderKit.translucentState();
        if (hasFills) RenderKit.drawFill(FILLS.get(), modelView, projection, 1F, true);
        if (hasGrid) RenderKit.drawLines(GRID.get(), modelView, projection, RenderKit.px(1.25F), 1F, true);
        if (hasEdges) {
            RenderKit.drawLines(EDGES.get(), modelView, projection, RenderKit.px(1.5F), 0.2F, false);   // x-ray
            RenderKit.drawLines(EDGES.get(), modelView, projection, RenderKit.px(6F), 0.2F, true);     // glow
            RenderKit.drawLines(EDGES.get(), modelView, projection, RenderKit.px(2F), 0.95F, true);    // core
        }
        for (final Object item : items) {
            if (!(item instanceof Label l)) continue;
            space = subLevels ? spaceOf(item) : null;
            label(l, camera, modelView, projection);
        }
        space = null;
    }

    /** The sub-level an element lies on, by the place it is at; null in the world itself. */
    private static @Nullable SubLevels.Pose spaceOf(final Object item) {
        final Vec3 at;
        if (item instanceof Box b) at = b.box().getCenter();
        else if (item instanceof Marker m) at = Vec3.atCenterOf(m.pos());
        else if (item instanceof Line l) at = l.from();
        else if (item instanceof Plane p) at = p.bounds().getCenter();
        else if (item instanceof Label l) at = l.pos();
        else return null;
        return SubLevels.renderAt(BlockPos.containing(at));
    }

    /** Where a point of the element being written is in the world: on a sub-level, where the sub-level shows it. */
    private static Vec3 seen(final Vec3 p) {
        return space == null ? p : space.toWorld(p);
    }

    // ---- boxes ----

    private static void boxFaces(final BufferBuilder buf, final AABB box, final int argb, final float t, final Vec3 cam, final float strength) {
        final float breathe = 0.85F + 0.15F * (float) Math.sin(t * Math.PI * 2 / 3.2);
        final int fill = Colors.scaleAlpha(argb, strength * breathe);
        for (final Direction d : Direction.values()) {
            final float[] c = GhostMesher.faceCorners(d, 0F, 1F);
            for (int i = 0; i < 4; i++) {
                final Vec3 corner = seen(new Vec3(c[i * 3] == 0F ? box.minX : box.maxX, c[i * 3 + 1] == 0F ? box.minY : box.maxY,
                    c[i * 3 + 2] == 0F ? box.minZ : box.maxZ));
                c[i * 3] = (float) (corner.x - cam.x);
                c[i * 3 + 1] = (float) (corner.y - cam.y);
                c[i * 3 + 2] = (float) (corner.z - cam.z);
            }
            RenderKit.quad(buf, c, fill);
        }
    }

    /**
     * The 12 edges, split into short pieces whose brightness follows a band sweeping from the box's min corner to its
     * max corner (by x + y + z), so an animated selection shimmers without flashing.
     */
    private static void boxEdges(final BufferBuilder buf, final AABB box, final int argb, final boolean animated, final float t, final Vec3 cam) {
        final double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
        final double span = box.getXsize() + box.getYsize() + box.getZsize();
        final double band = animated ? ((t % SWEEP_PERIOD) / SWEEP_PERIOD) * (span + 2 * SWEEP_WIDTH) - SWEEP_WIDTH : Double.NaN;
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                edge(buf, new Vec3(xs[0], ys[a], zs[b]), new Vec3(xs[1], ys[a], zs[b]), box, argb, band, cam);
                edge(buf, new Vec3(xs[a], ys[0], zs[b]), new Vec3(xs[a], ys[1], zs[b]), box, argb, band, cam);
                edge(buf, new Vec3(xs[a], ys[b], zs[0]), new Vec3(xs[a], ys[b], zs[1]), box, argb, band, cam);
            }
        }
    }

    private static void edge(final BufferBuilder buf, final Vec3 from, final Vec3 to, final AABB box, final int argb, final double band, final Vec3 cam) {
        if (Double.isNaN(band)) {
            segment(buf, from, to, argb, argb, cam);
            return;
        }
        final double len = from.distanceTo(to);
        final int pieces = Math.max(1, Math.min(64, (int) Math.ceil(len / 0.25)));
        Vec3 prev = from;
        int prevColour = sweep(argb, from, box, band);
        for (int i = 1; i <= pieces; i++) {
            final Vec3 p = from.lerp(to, i / (double) pieces);
            final int colour = sweep(argb, p, box, band);
            segment(buf, prev, p, prevColour, colour, cam);
            prev = p;
            prevColour = colour;
        }
    }

    /** {@code argb} dimmed to 70% outside the sweep band and brightened toward white at its centre. */
    private static int sweep(final int argb, final Vec3 p, final AABB box, final double band) {
        final double s = (p.x - box.minX) + (p.y - box.minY) + (p.z - box.minZ);
        final double k = (s - band) / SWEEP_WIDTH;
        final float glow = (float) Math.exp(-k * k * 2.5);
        final int lit = Colors.lerp(argb, Colors.withAlpha(0xFFFFFF, Colors.alpha(argb)), glow * 0.45F);
        return Colors.scaleAlpha(lit, 0.7F + 0.3F * glow);
    }

    // ---- markers ----

    private static AABB markerBox(final BlockPos pos, final float t, final boolean animated) {
        final double grow = 0.03 + (animated ? 0.018 * (0.5 + 0.5 * Math.sin(t * Math.PI * 2 / 1.6)) : 0.0);
        return new AABB(pos).inflate(grow);
    }

    /** Corner brackets: three short arms at each of the eight corners. */
    private static void markerEdges(final BufferBuilder buf, final AABB box, final int argb, final Vec3 cam) {
        final double arm = Math.min(0.32, Math.min(box.getXsize(), Math.min(box.getYsize(), box.getZsize())) / 3.0);
        final double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int k = 0; k < 2; k++) {
                    final Vec3 c = new Vec3(xs[i], ys[j], zs[k]);
                    final int sx = i == 0 ? 1 : -1, sy = j == 0 ? 1 : -1, sz = k == 0 ? 1 : -1;
                    segment(buf, c, c.add(sx * arm, 0, 0), argb, argb, cam);
                    segment(buf, c, c.add(0, sy * arm, 0), argb, argb, cam);
                    segment(buf, c, c.add(0, 0, sz * arm), argb, argb, cam);
                }
            }
        }
    }

    // ---- planes ----

    /** The plane's rectangle: {u0, v0, u1, v1} on its two in-plane axes (XYZ order without the plane's axis). */
    private static double[] planeRect(final Plane p) {
        final Direction.Axis u = p.axis() == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        final Direction.Axis v = p.axis() == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        return new double[] {p.bounds().min(u), p.bounds().min(v), p.bounds().max(u), p.bounds().max(v)};
    }

    private static Vec3 planePoint(final Plane p, final double u, final double v) {
        return switch (p.axis()) {
            case X -> new Vec3(p.coord(), u, v);
            case Y -> new Vec3(u, p.coord(), v);
            case Z -> new Vec3(u, v, p.coord());
        };
    }

    private static void planeFill(final BufferBuilder buf, final Plane p, final Vec3 cam) {
        final double[] r = planeRect(p);
        final int fill = Colors.scaleAlpha(p.argb(), 0.07F);
        final Vec3[] c = {planePoint(p, r[0], r[1]), planePoint(p, r[2], r[1]), planePoint(p, r[2], r[3]), planePoint(p, r[0], r[3])};
        final float[] xyz = new float[12];
        for (int i = 0; i < 4; i++) {
            final Vec3 corner = seen(c[i]);
            xyz[i * 3] = (float) (corner.x - cam.x);
            xyz[i * 3 + 1] = (float) (corner.y - cam.y);
            xyz[i * 3 + 2] = (float) (corner.z - cam.z);
        }
        RenderKit.quad(buf, xyz, fill);
    }

    private static void planeBorder(final BufferBuilder buf, final Plane p, final Vec3 cam) {
        final double[] r = planeRect(p);
        final Vec3 a = planePoint(p, r[0], r[1]), b = planePoint(p, r[2], r[1]), c = planePoint(p, r[2], r[3]), d = planePoint(p, r[0], r[3]);
        final int border = Colors.scaleAlpha(p.argb(), 0.8F);
        segment(buf, a, b, border, border, cam);
        segment(buf, b, c, border, border, cam);
        segment(buf, c, d, border, border, cam);
        segment(buf, d, a, border, border, cam);
    }

    /** Block grid on the plane; a soft highlight travels along the longer side. Capped at 256 lines per direction. */
    private static void planeGrid(final BufferBuilder buf, final Plane p, final float t, final boolean animated, final Vec3 cam) {
        final double[] r = planeRect(p);
        final double w = r[2] - r[0], h = r[3] - r[1];
        final boolean alongU = w >= h;
        final double length = alongU ? w : h;
        final double band = animated ? ((t % 3.4F) / 3.4F) * (length + 6) - 3 : Double.NaN;
        final int step = (int) Math.max(1, Math.ceil(Math.max(w, h) / 256.0));
        for (double u = Math.ceil(r[0]); u < r[2] - 1.0E-3; u += step) {
            if (u <= r[0] + 1.0E-3) continue;
            final int argb = gridColour(p.argb(), alongU ? u - r[0] : Double.NaN, band);
            segment(buf, planePoint(p, u, r[1]), planePoint(p, u, r[3]), argb, argb, cam);
        }
        for (double v = Math.ceil(r[1]); v < r[3] - 1.0E-3; v += step) {
            if (v <= r[1] + 1.0E-3) continue;
            final int argb = gridColour(p.argb(), alongU ? Double.NaN : v - r[1], band);
            segment(buf, planePoint(p, r[0], v), planePoint(p, r[2], v), argb, argb, cam);
        }
    }

    private static int gridColour(final int argb, final double at, final double band) {
        float glow = 0F;
        if (!Double.isNaN(at) && !Double.isNaN(band)) {
            final double k = (at - band) / 2.0;
            glow = (float) Math.exp(-k * k);
        }
        return Colors.scaleAlpha(argb, 0.2F + 0.35F * glow);
    }

    // ---- labels ----

    /**
     * A billboard label on a plate: dark layered surface with a hairline border on the dark skin, the vanilla
     * tooltip's near-black on the vanilla skin, and an accent underline on both. The scale grows with distance so the
     * text stays about the same size on screen (never smaller than vanilla name tags up close).
     */
    private static void label(final Label l, final Camera camera, final Matrix4f modelView, final Matrix4f projection) {
        final Minecraft mc = Minecraft.getInstance();
        final Font font = mc.font;
        final Vec3 cam = camera.getPosition();
        final Vec3 at = seen(l.pos());
        final double dist = at.distanceTo(cam);
        final float alpha = Colors.alpha(l.argb()) / 255F;
        if (alpha <= 0.01F) return;
        final float scale = (float) Math.min(0.2, 0.025 * Math.max(1.0, dist / 6.5));
        final PoseStack pose = new PoseStack();
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        pose.mulPose(camera.rotation());
        pose.scale(scale, -scale, scale);
        final Matrix4f matrix = pose.last().pose();
        final FormattedCharSequence text = l.text().getVisualOrderText();
        final int w = font.width(text);
        final float x = -w / 2F, y = -font.lineHeight / 2F;

        final Palette pal = Theme.current().palette();
        final boolean vanilla = Theme.current().isVanilla();
        final int plate = vanilla ? 0xF0100010 : Colors.withAlpha(pal.bg(), 0xE6);
        final int border = vanilla ? 0x505000FF : Colors.withAlpha(pal.borderStrong(), 0xFF);
        final int underline = Colors.withAlpha(pal.accent(), 0xFF);
        final BufferBuilder plates = RenderKit.begin(GhostRenderer.bytes(), VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        final float px0 = x - 4, px1 = x + w + 4, py0 = y - 3, py1 = y + font.lineHeight + 2;
        rect(plates, matrix, px0 - 1, py0 - 1, px1 + 1, py1 + 1, Colors.scaleAlpha(border, alpha * (vanilla ? 1F : 0.9F)));
        rect(plates, matrix, px0, py0, px1, py1, Colors.scaleAlpha(plate, alpha));
        rect(plates, matrix, px0, py1 - 1, px1, py1, Colors.scaleAlpha(underline, alpha));
        final boolean hasPlate = PLATES.fill(plates);

        // The plate never writes depth, so the text drawn over it always wins; the text itself is depth-tested
        // against the world (and, with throughWalls, repeated faintly without a depth test first).
        final MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        RenderKit.translucentState();
        if (l.throughWalls()) {
            if (hasPlate) RenderKit.drawFill(PLATES.get(), modelView, projection, 0.35F, false);
            font.drawInBatch(text, x, y, Colors.scaleAlpha(l.argb(), 0.45F), false, matrix, source, Font.DisplayMode.SEE_THROUGH, 0, RenderKit.FULL_BRIGHT);
            source.endBatch();
            RenderKit.translucentState();
        }
        if (hasPlate) RenderKit.drawFill(PLATES.get(), modelView, projection, 1F, true);
        font.drawInBatch(text, x, y, l.argb(), false, matrix, source, Font.DisplayMode.NORMAL, 0, RenderKit.FULL_BRIGHT);
        source.endBatch();
        RenderKit.translucentState();
    }

    private static void rect(final BufferBuilder buf, final Matrix4f m, final float x0, final float y0, final float x1, final float y1, final int argb) {
        buf.addVertex(m, x0, y0, 0F).setColor(argb);
        buf.addVertex(m, x0, y1, 0F).setColor(argb);
        buf.addVertex(m, x1, y1, 0F).setColor(argb);
        buf.addVertex(m, x1, y0, 0F).setColor(argb);
    }

    // ---- lines ----

    /** A line, each end where its own space shows it: the two may differ (from a block of a ship to one of the world). */
    private static void line(final BufferBuilder buf, final Line l, final Vec3 cam, final boolean subLevels) {
        final Vec3 from = subLevels ? seenAt(l.from()) : l.from(), to = subLevels ? seenAt(l.to()) : l.to();
        RenderKit.line(buf, (float) (from.x - cam.x), (float) (from.y - cam.y), (float) (from.z - cam.z),
            (float) (to.x - cam.x), (float) (to.y - cam.y), (float) (to.z - cam.z), l.argb(), l.argb());
    }

    private static Vec3 seenAt(final Vec3 p) {
        final SubLevels.Pose at = SubLevels.renderAt(BlockPos.containing(p));
        return at == null ? p : at.toWorld(p);
    }

    // ---- shared ----

    private static void segment(final BufferBuilder buf, final Vec3 a, final Vec3 b, final int argb0, final int argb1, final Vec3 cam) {
        final Vec3 from = seen(a), to = seen(b);
        RenderKit.line(buf, (float) (from.x - cam.x), (float) (from.y - cam.y), (float) (from.z - cam.z),
            (float) (to.x - cam.x), (float) (to.y - cam.y), (float) (to.z - cam.z), argb0, argb1);
    }

    private OverlayRenderer() {}
}
