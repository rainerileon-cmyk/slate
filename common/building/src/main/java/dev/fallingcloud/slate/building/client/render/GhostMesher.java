package dev.fallingcloud.slate.building.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.fallingcloud.slate.building.client.model.ShapeGeometry;
import dev.fallingcloud.slate.building.client.model.ShapeModels;
import dev.fallingcloud.slate.building.client.model.ShapeQuadBaker;
import dev.fallingcloud.slate.building.client.render.GhostRenderer.Ghost;
import dev.fallingcloud.slate.building.client.render.GhostRenderer.Style;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Turns ghosts into vertices: textured ghost quads (terrain format, for the ghost shader or the vanilla fallback),
 * outlines (per-ghost shape edges, or the merged hull edges of a large set) and the flat hull of a set too large to
 * texture. Positions are written relative to an origin block; the caller supplies the origin's offset at draw time.
 */
final class GhostMesher {

    /** How far a ghost grows around its block centre: enough to win the depth test against a coplanar face. */
    static final float GROW_PLACE = 0.0025F;
    static final float GROW_EXISTING = 0.006F;
    /**
     * Replacements read amber. A fixed golden amber rather than the palette warning: in the dark skin the warning
     * (E0A458) sits right next to the default terracotta accent that placements use.
     */
    static final int REPLACE_AMBER = 0xFFF2C14E;
    /** Lowest block light a ghost is lit with, so previews stay readable in caves and at night. */
    private static final int MIN_BLOCK_LIGHT = 9 << 4;
    private static final Direction[] DIRECTIONS = Direction.values();

    /** Receives ghost vertices. {@code style} is 0 place, 1 replace, 2 remove (the shader's hatching / tints). */
    interface Sink {
        void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int blockLight, int skyLight,
                    int style, float nx, float ny, float nz);
    }

    /** A sink writing the ghost shader's format ({@code DefaultVertexFormat.BLOCK}; style in the high byte of UV2.x). */
    static Sink shaderSink(final BufferBuilder buf) {
        return (x, y, z, r, g, b, a, u, v, bl, sl, style, nx, ny, nz) ->
            buf.addVertex(x, y, z).setColor(r, g, b, a).setUv(u, v).setUv2(bl | (style << 8), sl).setNormal(nx, ny, nz);
    }

    private GhostMesher() {}

    // ======================================================================== textured ghosts

    /**
     * Writes the textured quads of {@code ghosts} (INVALID ghosts have none: they are outline-only). Faces hidden by
     * a neighbouring full ghost or by the world are skipped, so a large fill is a shell, not thousands of cubes.
     */
    static void quads(final Sink sink, final List<Ghost> ghosts, final float @Nullable [] alphas, final BlockPos origin, final Level level) {
        final Long2ObjectOpenHashMap<Ghost> byPos = new Long2ObjectOpenHashMap<>(ghosts.size());
        for (final Ghost g : ghosts) byPos.put(g.pos().asLong(), g);
        final LongSet solid = new LongOpenHashSet();
        for (final Ghost g : ghosts) if (occludes(g, level)) solid.add(g.pos().asLong());
        final BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        for (int i = 0; i < ghosts.size(); i++) {
            final Ghost g = ghosts.get(i);
            if (g.style() == Style.INVALID) continue;
            final float alpha = alphas == null ? 1F : alphas[i];
            if (alpha <= 0.004F) continue;
            final BlockState state = g.state();
            final BlockPos pos = g.pos();
            final float ox = pos.getX() - origin.getX(), oy = pos.getY() - origin.getY(), oz = pos.getZ() - origin.getZ();
            final int style = styleIndex(g.style());
            final float grow = g.style() == Style.PLACE ? GROW_PLACE : GROW_EXISTING;
            final int light = light(level, pos, g.style() != Style.PLACE);
            if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
                // Nothing to texture (air, fluids, block-entity-rendered blocks): a hatched box stands in.
                box(sink, whiteSprite(), ox, oy, oz, grow, tintOf(g.style()), alpha, light, style);
                continue;
            }
            final boolean shape = state.getBlock() instanceof ShapeBlock;
            final BlockState material = shape ? g.material() : null;
            final BlockState sourceState = material != null ? material : state;
            final BakedModel model = ShapeModels.modelOf(sourceState);
            final long seed = sourceState.getSeed(pos);
            final ShapeQuadBaker.QuadSource source = d -> model.getQuads(sourceState, d, ShapeModels.random(seed));
            for (int bucket = 0; bucket < 7; bucket++) {
                final Direction side = bucket < 6 ? DIRECTIONS[bucket] : null;
                if (side != null && hidden(g, side, byPos, solid, level, neighbour)) continue;
                final List<BakedQuad> list = shape
                    ? ShapeQuadBaker.quads(state, side, material != null ? material : ShapeModels.UNSET, null, source)
                    : source.quads(side);
                for (final BakedQuad q : list) {
                    int rgb = 0xFFFFFF;
                    if (q.isTinted()) {
                        final int c = Minecraft.getInstance().getBlockColors().getColor(sourceState, level, pos, q.getTintIndex());
                        if (c != -1) rgb = c;
                    }
                    final float shade = level.getShade(q.getDirection(), q.isShade());
                    quad(sink, q, ox, oy, oz, grow, ((rgb >> 16) & 0xFF) / 255F * shade, ((rgb >> 8) & 0xFF) / 255F * shade,
                        (rgb & 0xFF) / 255F * shade, alpha, light, style);
                }
            }
        }
    }

    /** Whether a ghost hides its neighbours' faces completely (an opaque full block that will be there). */
    private static boolean occludes(final Ghost g, final Level level) {
        if (g.style() == Style.INVALID || g.style() == Style.REMOVE) return false;
        final BlockState state = g.state();
        if (state.getBlock() instanceof ShapeBlock) {
            final BlockState material = g.material();
            if (material == null || ShapeModels.translucent(material) || !material.canOcclude()) return false;
            for (final Direction d : DIRECTIONS) if (!ShapeQuadBaker.coversFace(state, d)) return false;
            return true;
        }
        return state.isSolidRender(level, g.pos());
    }

    private static boolean hidden(final Ghost g, final Direction side, final Long2ObjectOpenHashMap<Ghost> byPos, final LongSet solid,
                                  final Level level, final BlockPos.MutableBlockPos neighbour) {
        neighbour.setWithOffset(g.pos(), side);
        final long key = neighbour.asLong();
        if (solid.contains(key)) return true;
        if (byPos.containsKey(key)) return false;   // a ghost that is not solid, or one that is going away: show the face
        try {
            return !Block.shouldRenderFace(g.state(), level, g.pos(), side, neighbour);
        } catch (final RuntimeException e) {
            return false;
        }
    }

    private static void quad(final Sink sink, final BakedQuad q, final float ox, final float oy, final float oz, final float grow,
                             final float r, final float g, final float b, final float a, final int light, final int style) {
        final int[] v = q.getVertices();
        if (v.length < 16) return;
        final int stride = v.length / 4;
        final float scale = 1F + 2F * grow;
        final var n = q.getDirection().getNormal();
        final int bl = light & 0xFFFF, sl = (light >> 16) & 0xFFFF;
        for (int i = 0; i < 4; i++) {
            final int base = i * stride;
            final float x = (Float.intBitsToFloat(v[base]) - 0.5F) * scale + 0.5F + ox;
            final float y = (Float.intBitsToFloat(v[base + 1]) - 0.5F) * scale + 0.5F + oy;
            final float z = (Float.intBitsToFloat(v[base + 2]) - 0.5F) * scale + 0.5F + oz;
            sink.vertex(x, y, z, r, g, b, a, Float.intBitsToFloat(v[base + 4]), Float.intBitsToFloat(v[base + 5]), bl, sl, style,
                n.getX(), n.getY(), n.getZ());
        }
    }

    /** A textured unit cube (the white sprite) for ghosts without a model. */
    private static void box(final Sink sink, final TextureAtlasSprite sprite, final float ox, final float oy, final float oz, final float grow,
                            final int rgb, final float a, final int light, final int style) {
        final float lo = -grow, hi = 1F + grow;
        final float r = ((rgb >> 16) & 0xFF) / 255F, g = ((rgb >> 8) & 0xFF) / 255F, b = (rgb & 0xFF) / 255F;
        final int bl = light & 0xFFFF, sl = (light >> 16) & 0xFFFF;
        final float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        for (final Direction d : DIRECTIONS) {
            final float[] c = faceCorners(d, lo, hi);
            final float shade = Minecraft.getInstance().level == null ? 1F : Minecraft.getInstance().level.getShade(d, true);
            final float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
            for (int i = 0; i < 4; i++) {
                sink.vertex(c[i * 3] + ox, c[i * 3 + 1] + oy, c[i * 3 + 2] + oz, r * shade, g * shade, b * shade, a, uv[i][0], uv[i][1],
                    bl, sl, style, d.getStepX(), d.getStepY(), d.getStepZ());
            }
        }
    }

    /** The four corners of a cube face, counter-clockwise seen from outside (front-facing with back-face culling). */
    static float[] faceCorners(final Direction d, final float lo, final float hi) {
        return switch (d) {
            case DOWN -> new float[] {lo, lo, lo, hi, lo, lo, hi, lo, hi, lo, lo, hi};
            case UP -> new float[] {lo, hi, lo, lo, hi, hi, hi, hi, hi, hi, hi, lo};
            case NORTH -> new float[] {lo, lo, lo, lo, hi, lo, hi, hi, lo, hi, lo, lo};
            case SOUTH -> new float[] {lo, lo, hi, hi, lo, hi, hi, hi, hi, lo, hi, hi};
            case WEST -> new float[] {lo, lo, lo, lo, lo, hi, lo, hi, hi, lo, hi, lo};
            case EAST -> new float[] {hi, lo, lo, hi, hi, lo, hi, hi, hi, hi, lo, hi};
        };
    }

    // ======================================================================== outlines

    /**
     * The outlines of a set: full-block ghosts are merged per style into the edges of their hull (where the surface
     * folds), so a 20×10×20 fill reads as one clean box and an L-shaped plan shows its real silhouette; ghosts with a
     * partial shape (stairs, slabs, fences) keep their own profile. {@code cellsOnly} treats every ghost as a full
     * cell (the hull mode of sets above {@code maxBlocks}).
     */
    static void outlines(final BufferBuilder lines, final List<Ghost> ghosts, final float @Nullable [] alphas, final BlockPos origin,
                         final Level level, final boolean cellsOnly) {
        if (cellsOnly) {
            hullOutlines(lines, ghosts, alphas, origin);
            return;
        }
        final List<Ghost> full = new ArrayList<>(ghosts.size());
        final List<Ghost> partial = new ArrayList<>();
        final float[] fullAlphas = new float[ghosts.size()];
        final float[] partialAlphas = new float[ghosts.size()];
        for (int i = 0; i < ghosts.size(); i++) {
            final Ghost g = ghosts.get(i);
            final float a = alphas == null ? 1F : alphas[i];
            if (Block.isShapeFullBlock(outlineShape(g, level))) {
                fullAlphas[full.size()] = a;
                full.add(g);
            } else {
                partialAlphas[partial.size()] = a;
                partial.add(g);
            }
        }
        if (!full.isEmpty()) hullOutlines(lines, full, fullAlphas, origin);
        if (!partial.isEmpty()) shapeOutlines(lines, partial, partialAlphas, origin, level);
    }

    /** Per-ghost outlines: the edges of each ghost's own shape (a stair's profile, a slab's half box). */
    private static void shapeOutlines(final BufferBuilder lines, final List<Ghost> ghosts, final float @Nullable [] alphas, final BlockPos origin,
                                      final Level level) {
        final Map<Style, Integer> colours = colours();
        for (int i = 0; i < ghosts.size(); i++) {
            final Ghost g = ghosts.get(i);
            final float alpha = alphas == null ? 1F : alphas[i];
            if (alpha <= 0.004F) continue;
            final int argb = Colors.scaleAlpha(colours.get(g.style()), alpha);
            final float grow = g.style() == Style.PLACE ? GROW_PLACE * 2 : GROW_EXISTING * 1.5F;
            final float scale = 1F + 2F * grow;
            final float ox = g.pos().getX() - origin.getX(), oy = g.pos().getY() - origin.getY(), oz = g.pos().getZ() - origin.getZ();
            outlineShape(g, level).forAllEdges((x0, y0, z0, x1, y1, z1) -> RenderKit.line(lines,
                (float) ((x0 - 0.5) * scale + 0.5 + ox), (float) ((y0 - 0.5) * scale + 0.5 + oy), (float) ((z0 - 0.5) * scale + 0.5 + oz),
                (float) ((x1 - 0.5) * scale + 0.5 + ox), (float) ((y1 - 0.5) * scale + 0.5 + oy), (float) ((z1 - 0.5) * scale + 0.5 + oz),
                argb, argb));
        }
    }

    private static VoxelShape outlineShape(final Ghost g, final Level level) {
        final BlockState state = g.state();
        if (state.isAir()) return Shapes.block();
        if (state.getBlock() instanceof ShapeBlock) return ShapeGeometry.shape(state);
        try {
            final VoxelShape s = state.getShape(level, g.pos(), CollisionContext.empty());
            return s.isEmpty() ? Shapes.block() : s;
        } catch (final RuntimeException e) {
            return Shapes.block();
        }
    }

    /** Hull edges of unit cells, grouped by style; each edge takes the fade of the cell that draws it. */
    private static void hullOutlines(final BufferBuilder lines, final List<Ghost> ghosts, final float @Nullable [] alphas, final BlockPos origin) {
        final Map<Style, Integer> colours = colours();
        final Map<Style, Long2FloatOpenHashMap> groups = new EnumMap<>(Style.class);
        for (int i = 0; i < ghosts.size(); i++) {
            final Ghost g = ghosts.get(i);
            final float a = alphas == null ? 1F : alphas[i];
            final Long2FloatOpenHashMap group = groups.computeIfAbsent(g.style(), s -> new Long2FloatOpenHashMap());
            group.put(g.pos().asLong(), Math.max(a, group.get(g.pos().asLong())));
        }
        for (final Map.Entry<Style, Long2FloatOpenHashMap> e : groups.entrySet()) {
            final int argb = colours.get(e.getKey());
            final Long2FloatOpenHashMap cells = e.getValue();
            final Set<Edge> done = new HashSet<>();
            final BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
            for (final Long2FloatMap.Entry cellEntry : cells.long2FloatEntrySet()) {
                final long cell = cellEntry.getLongKey();
                final float alpha = cellEntry.getFloatValue();
                if (alpha <= 0.004F) continue;
                c.set(cell);
                for (final Direction d : DIRECTIONS) {
                    if (cells.containsKey(BlockPos.offset(cell, d))) continue;   // not an exposed face
                    for (final Direction side : DIRECTIONS) {
                        if (side.getAxis() == d.getAxis()) continue;
                        final long next = BlockPos.offset(cell, side);
                        final boolean continues = cells.containsKey(next) && !cells.containsKey(BlockPos.offset(next, d));
                        if (continues) continue;
                        edge(lines, c, d, side, origin, Colors.scaleAlpha(argb, alpha), done);
                    }
                }
            }
        }
    }

    private record Edge(long corner, int axis) {}

    /** The edge shared by face {@code d} of cell {@code c} and its {@code side}, once per group. */
    private static void edge(final BufferBuilder lines, final BlockPos c, final Direction d, final Direction side, final BlockPos origin,
                             final int argb, final Set<Edge> done) {
        final Direction.Axis along = thirdAxis(d.getAxis(), side.getAxis());
        final int x = c.getX() + (offsetFor(d, Direction.Axis.X) + offsetFor(side, Direction.Axis.X));
        final int y = c.getY() + (offsetFor(d, Direction.Axis.Y) + offsetFor(side, Direction.Axis.Y));
        final int z = c.getZ() + (offsetFor(d, Direction.Axis.Z) + offsetFor(side, Direction.Axis.Z));
        if (!done.add(new Edge(BlockPos.asLong(x, y, z), along.ordinal()))) return;
        final float g = GROW_PLACE * 2;
        final float x0 = x - origin.getX() + grow(d, side, Direction.Axis.X, g);
        final float y0 = y - origin.getY() + grow(d, side, Direction.Axis.Y, g);
        final float z0 = z - origin.getZ() + grow(d, side, Direction.Axis.Z, g);
        final float x1 = x0 + (along == Direction.Axis.X ? 1 : 0);
        final float y1 = y0 + (along == Direction.Axis.Y ? 1 : 0);
        final float z1 = z0 + (along == Direction.Axis.Z ? 1 : 0);
        RenderKit.line(lines, x0, y0, z0, x1, y1, z1, argb, argb);
    }

    /** 1 when {@code d} points along {@code axis} positively (the face sits on the cell's far side), else 0. */
    private static int offsetFor(final Direction d, final Direction.Axis axis) {
        return d.getAxis() == axis && d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
    }

    /** Pushes an edge a hair outward along the two faces it joins, so it never z-fights with the cells. */
    private static float grow(final Direction d, final Direction side, final Direction.Axis axis, final float g) {
        float out = 0;
        if (d.getAxis() == axis) out += d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? g : -g;
        if (side.getAxis() == axis) out += side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? g : -g;
        return out;
    }

    private static Direction.Axis thirdAxis(final Direction.Axis a, final Direction.Axis b) {
        for (final Direction.Axis x : Direction.Axis.values()) if (x != a && x != b) return x;
        return Direction.Axis.Y;
    }

    // ======================================================================== hull (above maxBlocks)

    /** Flat, style-coloured exposed faces of a set too large to texture (drawn with the position-colour shader). */
    static void hullFaces(final BufferBuilder fill, final List<Ghost> ghosts, final BlockPos origin) {
        final Map<Style, Integer> colours = colours();
        final LongSet cells = new LongOpenHashSet(ghosts.size());
        for (final Ghost g : ghosts) cells.add(g.pos().asLong());
        final float lo = -GROW_PLACE, hi = 1F + GROW_PLACE;
        for (final Ghost g : ghosts) {
            final long cell = g.pos().asLong();
            final int argb = Colors.withAlpha(colours.get(g.style()), g.style() == Style.INVALID ? 0x30 : 0x48);
            final float ox = g.pos().getX() - origin.getX(), oy = g.pos().getY() - origin.getY(), oz = g.pos().getZ() - origin.getZ();
            for (final Direction d : DIRECTIONS) {
                if (cells.contains(BlockPos.offset(cell, d))) continue;
                final float[] c = faceCorners(d, lo, hi);
                for (int i = 0; i < 4; i++) {
                    c[i * 3] += ox;
                    c[i * 3 + 1] += oy;
                    c[i * 3 + 2] += oz;
                }
                RenderKit.quad(fill, c, argb);
            }
        }
    }

    // ======================================================================== helpers

    /** Outline / hull colours per style: the theme accent (place), amber (replace), the theme danger red (remove, blocked). */
    static Map<Style, Integer> colours() {
        final Palette p = Theme.current().palette();
        final Map<Style, Integer> out = new EnumMap<>(Style.class);
        out.put(Style.PLACE, Colors.withAlpha(p.accent(), 0xFF));
        out.put(Style.REPLACE, REPLACE_AMBER);
        out.put(Style.REMOVE, Colors.withAlpha(p.danger(), 0xFF));
        out.put(Style.INVALID, Colors.withAlpha(p.danger(), 0xFF));
        return out;
    }

    /** Vertex tint of a model-less ghost (the hatched box). */
    private static int tintOf(final Style style) {
        final Palette p = Theme.current().palette();
        return switch (style) {
            case REPLACE -> REPLACE_AMBER;
            case REMOVE, INVALID -> p.danger();
            case PLACE -> p.accent();
        } & 0xFFFFFF;
    }

    static int styleIndex(final Style style) {
        return switch (style) {
            case REPLACE -> 1;
            case REMOVE -> 2;
            default -> 0;
        };
    }

    /**
     * Light for a ghost: the light at its position (for a block that replaces or removes an existing one, the
     * brightest neighbour, since the inside of a solid block is dark), with a floor so it stays readable.
     */
    private static int light(final Level level, final BlockPos pos, final boolean existing) {
        int packed = LevelRenderer.getLightColor(level, pos);
        if (existing) {
            final BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
            for (final Direction d : DIRECTIONS) {
                final int other = LevelRenderer.getLightColor(level, n.setWithOffset(pos, d));
                packed = Math.max(packed & 0xFFFF, other & 0xFFFF) | (Math.max(packed >>> 16, other >>> 16) << 16);
            }
        }
        final int block = Math.max(packed & 0xFFFF, MIN_BLOCK_LIGHT);
        return block | (packed & 0xFFFF0000);
    }

    private static @Nullable TextureAtlasSprite white;

    static TextureAtlasSprite whiteSprite() {
        if (white == null) {
            white = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(dev.fallingcloud.slate.building.SlateBuilding.id("block/ghost_white"));
        }
        return white;
    }

    /** Forget cached sprites (resource reload). */
    static void reset() {
        white = null;
    }
}
