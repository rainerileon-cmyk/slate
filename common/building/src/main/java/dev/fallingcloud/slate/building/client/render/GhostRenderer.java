package dev.fallingcloud.slate.building.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.config.PreviewSettings;
import dev.fallingcloud.slate.core.client.render.SlateRenderEvents;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Translucent "ghost" blocks: the placement preview and the planned result of a building mode (design §6).
 *
 * <p><b>Submitting.</b> Immediate mode: {@link #submit} what should be visible. Submissions made during a client
 * tick stay up until the next tick starts; submissions made anywhere else (your own
 * {@code SlateRenderEvents.AFTER_TRANSLUCENT} listener registered during client init, {@link BuildingRender#FRAME},
 * a HUD callback) are drawn by the next world render pass, then dropped. Large plans use {@link #submitCached},
 * which keeps GPU buffers until {@code version} changes; a key that is not submitted for a frame is not drawn, and
 * its buffers are freed after a short grace period (a plan that blinks out for a frame does not rebuild). Render
 * thread only; calls from other threads are ignored.
 *
 * <p><b>Look.</b> A ghost is the block's real model (for our shape blocks: the material cut to the shape) drawn with
 * the {@code slate_building:ghost} core shader: desaturated toward grey by {@code preview.saturation}, faded to
 * {@code preview.opacity}, with a soft accent rim. {@link Style#REPLACE} tints amber, {@link Style#REMOVE} red with
 * drifting hatching, {@link Style#INVALID} draws only a red outline. Optional outline in the style's colour (the
 * Slate accent for placements) and a gentle alpha pulse. Above {@code preview.maxBlocks} a set is drawn as its flat
 * hull with merged hull edges instead of textured blocks. With an Iris shader pack active the vanilla translucent
 * entity path is used instead (alpha only, since a custom core shader would bypass the pack).
 *
 * <p><b>Depth.</b> Ghosts are depth-tested against the world (hidden behind walls, never drawn through them) and drawn
 * in two passes: depth only, then colour where the depth matches. Only the ghost surface nearest to the camera
 * shows, so a translucent stair or a whole planned wall reads as one clean glassy volume instead of a pile of
 * overlapping faces. Ghosts are grown by a few thousandths around their block centre, so a ghost replacing a block
 * of the same size (or sitting in a flower's cell) never z-fights with it.
 */
public final class GhostRenderer {

    /** How a ghost reads. */
    public enum Style {
        /** Normal placement. */
        PLACE,
        /** Replaces an existing block (amber tint). */
        REPLACE,
        /** Will be removed (red, cross-hatched, outlined). */
        REMOVE,
        /** Cannot be placed here (red outline only). */
        INVALID
    }

    /**
     * One ghost block.
     *
     * @param pos      where
     * @param state    the block state to show (for our shape blocks: the shape's state; for a removal: what is there now)
     * @param material the material rendered through the shape (our shape blocks only), else null
     * @param style    how it reads
     */
    public record Ghost(BlockPos pos, BlockState state, @Nullable BlockState material, Style style) {
        public Ghost {
            pos = pos.immutable();
        }

        public static Ghost of(final BlockPos pos, final BlockState state, final Style style) {
            return new Ghost(pos, state, null, style);
        }
    }

    /** Unused cached plans keep their GPU buffers this long before they are freed. */
    private static final long CACHE_GRACE_MS = 1500;

    private record Submitted(Ghost ghost, float alpha) {}

    private static final Buckets<Submitted> IMMEDIATE = new Buckets<>();
    private static final Map<Object, CachedPlan> CACHED = new HashMap<>();
    private static final RenderKit.DynamicBuffer IMMEDIATE_QUADS = new RenderKit.DynamicBuffer();
    private static final RenderKit.DynamicBuffer IMMEDIATE_LINES = new RenderKit.DynamicBuffer();
    private static final RenderKit.DynamicBuffer IMMEDIATE_HULL = new RenderKit.DynamicBuffer();
    private static @Nullable ByteBufferBuilder bytes;
    /** Dev harness only: draw with the shader-pack fallback path, to check it without Iris installed. */
    static boolean forceVanillaPath;

    // ======================================================================== API

    /** Shows {@code ghost} (see the class doc for how long). */
    public static void submit(final Ghost ghost) {
        submit(ghost, 1F);
    }

    /** Shows {@code ghost} with an extra fade (0..1), e.g. while it animates in. */
    public static void submit(final Ghost ghost, final float alpha) {
        if (!RenderSystem.isOnRenderThread() || !(alpha > 0F)) return;
        IMMEDIATE.add(new Submitted(ghost, Math.min(1F, alpha)));
    }

    /**
     * Shows a (large) set of ghosts, rebuilding the cached vertex buffers for {@code key} only when {@code version}
     * differs from the cached one ({@code supplier} is not called otherwise). A key that is not submitted for a frame
     * is not drawn; its buffers are released shortly after.
     */
    public static void submitCached(final Object key, final int version, final Supplier<List<Ghost>> supplier) {
        if (!RenderSystem.isOnRenderThread()) return;
        CachedPlan plan = CACHED.get(key);
        if (plan == null) {
            plan = new CachedPlan();
            CACHED.put(key, plan);
        }
        if (plan.ghosts == null || plan.version != version) {
            final List<Ghost> ghosts = supplier.get();
            plan.setGhosts(ghosts == null ? List.of() : List.copyOf(ghosts), version);
        }
        if (BuildingRender.inTick()) plan.usedTick = true;
        else plan.usedFrame = true;
        plan.lastUsedMs = Clock.nowMs();
    }

    /** Frees the buffers of {@code key} now (e.g. its mode was closed). */
    public static void release(final Object key) {
        final CachedPlan plan = CACHED.remove(key);
        if (plan != null) plan.close();
    }

    // ======================================================================== lifecycle (BuildingRender)

    static void beginTick() {
        IMMEDIATE.beginTick();
        for (final CachedPlan p : CACHED.values()) p.usedTick = false;
    }

    static void endPass() {
        IMMEDIATE.endPass();
        final long now = Clock.nowMs();
        final Iterator<CachedPlan> it = CACHED.values().iterator();
        while (it.hasNext()) {
            final CachedPlan p = it.next();
            p.usedFrame = false;
            if (!p.usedTick && now - p.lastUsedMs > CACHE_GRACE_MS) {
                p.close();
                it.remove();
            }
        }
    }

    /** Resources reloaded: sprites moved in the atlas, so every cached mesh is rebuilt on next use. */
    static void invalidate() {
        for (final CachedPlan p : CACHED.values()) p.dirty = true;
        GhostMesher.reset();
    }

    /** Leaving the world: drop everything. */
    static void clearAll() {
        IMMEDIATE.clear();
        for (final CachedPlan p : CACHED.values()) p.close();
        CACHED.clear();
        IMMEDIATE_QUADS.close();
        IMMEDIATE_LINES.close();
        IMMEDIATE_HULL.close();
    }

    // ======================================================================== drawing

    static void draw(final SlateRenderEvents.WorldRenderContext ctx, final Level level) {
        final List<Submitted> immediate = IMMEDIATE.snapshot();
        final List<CachedPlan> plans = new ArrayList<>();
        for (final CachedPlan p : CACHED.values()) if (p.visible() && !p.ghosts.isEmpty()) plans.add(p);
        if (immediate.isEmpty() && plans.isEmpty()) return;

        final PreviewSettings settings = PreviewSettings.current();
        final Vec3 cam = ctx.camera().getPosition();
        final Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        final float time = RenderKit.seconds();
        final float pulse = settings.pulse && RenderKit.animated() ? 0.9F + 0.1F * (float) Math.sin(time * Math.PI * 2 / 2.2) : 1F;
        final ShaderInstance shader = GhostShader.get();
        final boolean vanillaPath = forceVanillaPath || shader == null || RenderCompat.shaderPackInUse();

        final List<Ghost> ghosts = new ArrayList<>(immediate.size());
        final float[] alphas = new float[immediate.size()];
        for (int i = 0; i < immediate.size(); i++) {
            ghosts.add(immediate.get(i).ghost());
            alphas[i] = immediate.get(i).alpha();
        }
        final BlockPos origin = BlockPos.containing(cam);
        final boolean immediateHull = ghosts.size() > settings.maxBlocks();

        if (vanillaPath) {
            drawVanilla(level, cam, settings, pulse, ghosts, alphas, immediateHull, plans);
        } else {
            drawShaded(level, cam, modelView, projection, shader, settings, time, pulse, ghosts, alphas, origin, immediateHull, plans);
        }
        drawOutlines(level, cam, modelView, projection, settings, pulse, ghosts, alphas, origin, immediateHull, plans);
    }

    /** Textured ghosts through the ghost shader: depth pre-pass, then colour where the depth matches. */
    private static void drawShaded(final Level level, final Vec3 cam, final Matrix4f modelView, final Matrix4f projection,
                                   final ShaderInstance shader, final PreviewSettings settings, final float time, final float pulse,
                                   final List<Ghost> ghosts, final float[] alphas, final BlockPos origin, final boolean immediateHull,
                                   final List<CachedPlan> plans) {
        // Build this frame's immediate mesh and bring cached plans up to date first (no GL state changes in between).
        boolean immediateQuads = false;
        if (!ghosts.isEmpty() && !immediateHull) {
            final BufferBuilder buf = RenderKit.begin(bytes(), VertexFormat.Mode.QUADS, GhostShader.FORMAT);
            GhostMesher.quads(GhostMesher.shaderSink(buf), ghosts, alphas, origin, level);
            immediateQuads = IMMEDIATE_QUADS.fill(buf);
        }
        for (final CachedPlan p : plans) p.ensureBuilt(level, settings);

        final Palette pal = Theme.current().palette();
        uniform(shader, "GhostParams", settings.opacity() * pulse, settings.saturation(), time, 0.45F);
        uniform(shader, "GhostAccent", pal.accent(), 0F);
        uniform(shader, "GhostReplace", GhostMesher.REPLACE_AMBER, 0.42F);
        uniform(shader, "GhostRemove", pal.danger(), 0.45F);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
        RenderKit.translucentState();
        RenderSystem.enableCull();

        for (int pass = 0; pass < 2; pass++) {
            final boolean depthPass = pass == 0;
            RenderSystem.colorMask(!depthPass, !depthPass, !depthPass, !depthPass);
            RenderSystem.depthMask(depthPass);
            if (immediateQuads) drawGhostBuffer(IMMEDIATE_QUADS.get(), shader, modelView, projection, origin, cam);
            for (final CachedPlan p : plans) if (p.quads != null) drawGhostBuffer(p.quads, shader, modelView, projection, p.origin, cam);
        }
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();

        // Sets above the limit: their flat hull (translucent, no depth writes).
        RenderKit.translucentState();
        if (!ghosts.isEmpty() && immediateHull) {
            final BufferBuilder fill = RenderKit.begin(bytes(), VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            GhostMesher.hullFaces(fill, ghosts, origin);
            if (IMMEDIATE_HULL.fill(fill)) RenderKit.drawFill(IMMEDIATE_HULL.get(), translated(modelView, origin, cam), projection, pulse, true);
        }
        for (final CachedPlan p : plans) {
            if (p.hull != null) RenderKit.drawFill(p.hull, translated(modelView, p.origin, cam), projection, pulse, true);
        }
    }

    private static void drawGhostBuffer(final VertexBuffer vb, final ShaderInstance shader, final Matrix4f modelView, final Matrix4f projection,
                                        final BlockPos origin, final Vec3 cam) {
        uniform(shader, "GhostOffset", (float) (origin.getX() - cam.x), (float) (origin.getY() - cam.y), (float) (origin.getZ() - cam.z));
        vb.bind();
        vb.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();
    }

    /**
     * Shader-pack fallback: the ghosts' quads through the vanilla translucent entity render type (the pack's own
     * pipeline handles it), alpha and style tint only. Capped at {@code maxBlocks} ghosts in total.
     */
    private static void drawVanilla(final Level level, final Vec3 cam, final PreviewSettings settings, final float pulse,
                                    final List<Ghost> ghosts, final float[] alphas, final boolean immediateHull, final List<CachedPlan> plans) {
        final MultiBufferSource.BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
        final RenderType type = Sheets.translucentCullBlockSheet();
        final VertexConsumer consumer = source.getBuffer(type);
        final float opacity = settings.opacity() * pulse;
        // Positions come relative to the camera block (float precision far from the world origin), then camera-relative.
        final BlockPos origin = BlockPos.containing(cam);
        final float ox = (float) (origin.getX() - cam.x), oy = (float) (origin.getY() - cam.y), oz = (float) (origin.getZ() - cam.z);
        final Palette pal = Theme.current().palette();
        final GhostMesher.Sink sink = (x, y, z, r, g, b, a, u, v, bl, sl, style, nx, ny, nz) -> {
            final int tint = style == 1 ? GhostMesher.REPLACE_AMBER : style == 2 ? pal.danger() : 0;
            float rr = r, gg = g, bb = b;
            if (style != 0) {
                rr = r * 0.55F + ((tint >> 16) & 0xFF) / 255F * 0.45F;
                gg = g * 0.55F + ((tint >> 8) & 0xFF) / 255F * 0.45F;
                bb = b * 0.55F + (tint & 0xFF) / 255F * 0.45F;
            }
            consumer.addVertex(x + ox, y + oy, z + oz,
                Colors.argb(Math.round(Math.min(1F, a * opacity) * 255), Math.round(Math.min(1F, rr) * 255),
                    Math.round(Math.min(1F, gg) * 255), Math.round(Math.min(1F, bb) * 255)),
                u, v, OverlayTexture.NO_OVERLAY, bl | (sl << 16), nx, ny, nz);
        };
        int budget = settings.maxBlocks();
        if (!ghosts.isEmpty() && !immediateHull) {
            GhostMesher.quads(sink, ghosts, alphas, origin, level);
            budget -= ghosts.size();
        }
        for (final CachedPlan p : plans) {
            if (p.ghosts.size() > budget) continue;
            GhostMesher.quads(sink, p.ghosts, null, origin, level);
            budget -= p.ghosts.size();
        }
        source.endBatch(type);
    }

    /** Outlines (and, above the limit, merged hull edges): a soft wide glow under a crisp core line. */
    private static void drawOutlines(final Level level, final Vec3 cam, final Matrix4f modelView, final Matrix4f projection,
                                     final PreviewSettings settings, final float pulse, final List<Ghost> ghosts, final float[] alphas,
                                     final BlockPos origin, final boolean immediateHull, final List<CachedPlan> plans) {
        boolean immediateLines = false;
        if (!ghosts.isEmpty()) {
            final List<Ghost> invalid = new ArrayList<>();
            for (final Ghost g : ghosts) if (g.style() == Style.INVALID) invalid.add(g);
            if (settings.outline || immediateHull || !invalid.isEmpty()) {
                final BufferBuilder lines = RenderKit.begin(bytes(), VertexFormat.Mode.LINES, RenderKit.linesFormat());
                final List<Ghost> outlined = settings.outline || immediateHull ? ghosts : invalid;
                GhostMesher.outlines(lines, outlined, outlined == ghosts ? alphas : null, origin, level, immediateHull);
                immediateLines = IMMEDIATE_LINES.fill(lines);
            }
        }
        RenderKit.translucentState();
        final float glow = 0.22F * pulse, core = 0.85F * pulse;
        if (immediateLines) {
            final Matrix4f mv = translated(modelView, origin, cam);
            RenderKit.drawLines(IMMEDIATE_LINES.get(), mv, projection, RenderKit.px(5F), glow, true);
            RenderKit.drawLines(IMMEDIATE_LINES.get(), mv, projection, RenderKit.px(1.75F), core, true);
        }
        for (final CachedPlan p : plans) {
            if (p.lines == null) continue;
            final Matrix4f mv = translated(modelView, p.origin, cam);
            RenderKit.drawLines(p.lines, mv, projection, RenderKit.px(5F), glow, true);
            RenderKit.drawLines(p.lines, mv, projection, RenderKit.px(1.75F), core, true);
        }
    }

    // ======================================================================== helpers

    static Matrix4f translated(final Matrix4f modelView, final BlockPos origin, final Vec3 cam) {
        return new Matrix4f(modelView).translate((float) (origin.getX() - cam.x), (float) (origin.getY() - cam.y), (float) (origin.getZ() - cam.z));
    }

    private static void uniform(final ShaderInstance shader, final String name, final float a, final float b, final float c, final float d) {
        shader.safeGetUniform(name).set(a, b, c, d);
    }

    private static void uniform(final ShaderInstance shader, final String name, final float a, final float b, final float c) {
        shader.safeGetUniform(name).set(a, b, c);
    }

    private static void uniform(final ShaderInstance shader, final String name, final int rgb, final float w) {
        shader.safeGetUniform(name).set(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F, w);
    }

    static ByteBufferBuilder bytes() {
        if (bytes == null) bytes = new ByteBufferBuilder(1 << 18);
        return bytes;
    }

    /** One {@link #submitCached} key: the ghost list plus its GPU buffers, built lazily on the render thread. */
    private static final class CachedPlan {
        @Nullable List<Ghost> ghosts;
        int version;
        boolean dirty = true;
        boolean usedTick;
        boolean usedFrame;
        long lastUsedMs;
        BlockPos origin = BlockPos.ZERO;
        @Nullable VertexBuffer quads;
        @Nullable VertexBuffer hull;
        @Nullable VertexBuffer lines;
        private int builtMax = -1;
        private boolean builtOutline;

        void setGhosts(final List<Ghost> list, final int newVersion) {
            ghosts = list;
            version = newVersion;
            dirty = true;
        }

        boolean visible() {
            return ghosts != null && (usedTick || usedFrame);
        }

        void ensureBuilt(final Level level, final PreviewSettings settings) {
            if (ghosts == null) return;
            if (!dirty && builtMax == settings.maxBlocks() && builtOutline == settings.outline) return;
            dirty = false;
            builtMax = settings.maxBlocks();
            builtOutline = settings.outline;
            closeBuffers();
            if (ghosts.isEmpty()) return;
            origin = ghosts.get(0).pos();
            final boolean hullOnly = ghosts.size() > builtMax;
            try {
                if (!hullOnly) {
                    final BufferBuilder buf = RenderKit.begin(bytes(), VertexFormat.Mode.QUADS, GhostShader.FORMAT);
                    GhostMesher.quads(GhostMesher.shaderSink(buf), ghosts, null, origin, level);
                    quads = uploadStatic(buf);
                } else {
                    final BufferBuilder fill = RenderKit.begin(bytes(), VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                    GhostMesher.hullFaces(fill, ghosts, origin);
                    hull = uploadStatic(fill);
                }
                final List<Ghost> invalid = new ArrayList<>();
                for (final Ghost g : ghosts) if (g.style() == Style.INVALID) invalid.add(g);
                final List<Ghost> outlined = hullOnly || builtOutline ? ghosts : invalid;
                if (!outlined.isEmpty()) {
                    final BufferBuilder lineBuf = RenderKit.begin(bytes(), VertexFormat.Mode.LINES, RenderKit.linesFormat());
                    GhostMesher.outlines(lineBuf, outlined, null, origin, level, hullOnly);
                    lines = uploadStatic(lineBuf);
                }
            } catch (final RuntimeException e) {
                SlateBuilding.LOGGER.error("[Slate Building] could not build a ghost preview of {} blocks", ghosts.size(), e);
                closeBuffers();
            }
        }

        private static @Nullable VertexBuffer uploadStatic(final BufferBuilder buf) {
            final VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            if (RenderKit.upload(buf, vb)) return vb;
            vb.close();
            return null;
        }

        private void closeBuffers() {
            if (quads != null) quads.close();
            if (hull != null) hull.close();
            if (lines != null) lines.close();
            quads = null;
            hull = null;
            lines = null;
        }

        void close() {
            closeBuffers();
            ghosts = null;
        }
    }

    private GhostRenderer() {}
}
