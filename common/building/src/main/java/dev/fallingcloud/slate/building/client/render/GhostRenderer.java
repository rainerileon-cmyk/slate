package dev.fallingcloud.slate.building.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.compat.SubLevels;
import dev.fallingcloud.slate.building.config.PreviewSettings;
import dev.fallingcloud.slate.core.client.render.SlateRenderEvents;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.Sheets;
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
 * block sheet is used instead (alpha and style tint only, since a custom core shader would bypass the pack); cached
 * plans then replay a mesh recorded when they changed, and keep their outlines and hulls. Packs composite translucent
 * geometry their own way and wash a ghost out, so that path lifts the opacity ({@link #fallbackOpacity}) and always
 * draws a bright outline, whatever {@code preview.outline} says.
 *
 * <p><b>Depth.</b> Ghosts are depth-tested against the world (hidden behind walls, never drawn through them) and drawn
 * in two passes: depth only, then colour where the depth matches. Only the ghost surface nearest to the camera
 * shows, so a translucent stair or a whole planned wall reads as one clean glassy volume instead of a pile of
 * overlapping faces. Ghosts are grown by a few thousandths around their block centre, so a ghost replacing a block
 * of the same size (or sitting in a flower's cell) never z-fights with it.
 *
 * <p><b>Sub-levels.</b> With Sable, a ghost on a ship has a position in the ship's plot, far from where the ship is
 * seen. Ghosts are therefore drawn a space at a time: those of the world as always, those of a sub-level as one mesh
 * built round one of its blocks and drawn with the sub-level's pose of this frame ({@link SubLevels}), so they move
 * and turn with it. A cached plan is cut up the same way when it is built: one that reaches from a ship into the
 * world (a move off the deck) is two meshes, each drawn where its own space is.
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

    /**
     * This frame's immediate ghosts of one space (the world, or one sub-level), with the buffers they are drawn from.
     * Groups are kept from frame to frame, so a frame allocates nothing; there is one in all but the rarest frames.
     */
    private static final class Group {
        final List<Ghost> ghosts = new ArrayList<>();
        float[] alphas = new float[8];
        @Nullable UUID space;
        @Nullable SubLevels.Pose pose;
        BlockPos origin = BlockPos.ZERO;
        boolean hullOnly, hasQuads, hasLines, hasHull;
        final RenderKit.DynamicBuffer quads = new RenderKit.DynamicBuffer();
        final RenderKit.DynamicBuffer lines = new RenderKit.DynamicBuffer();
        final RenderKit.DynamicBuffer hull = new RenderKit.DynamicBuffer();
        /** Scratch mesh of these ghosts on the shader-pack fallback path. */
        final FallbackMesh fallback = new FallbackMesh();

        void begin(final @Nullable SubLevels.Pose in, final BlockPos at) {
            ghosts.clear();
            pose = in;
            space = in == null ? null : in.id();
            origin = at;
            hasQuads = hasLines = hasHull = false;
        }

        void add(final Submitted s) {
            if (ghosts.size() == alphas.length) alphas = java.util.Arrays.copyOf(alphas, alphas.length * 2);
            alphas[ghosts.size()] = s.alpha();
            ghosts.add(s.ghost());
        }

        void close() {
            quads.close();
            lines.close();
            hull.close();
            fallback.trim(0);
        }
    }

    private static final Buckets<Submitted> IMMEDIATE = new Buckets<>();
    private static final Map<Object, CachedPlan> CACHED = new HashMap<>();
    private static final List<Group> GROUPS = new ArrayList<>();
    private static @Nullable ByteBufferBuilder bytes;
    /** Dev harness only: draw with the shader-pack fallback path, to check it without Iris installed. */
    static boolean forceVanillaPath;
    /** Dev harness only: how many cached fallback meshes were recorded (once per plan change, never per frame). */
    static int fallbackBuilds;

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

    /** Dev harness: draws with the shader-pack fallback path from now on (or stops), to check it without Iris installed. */
    public static void debugFallbackPath(final boolean on) {
        forceVanillaPath = on;
    }

    /** Dev harness: whether {@code key} is cached with its outline buffer built. */
    static boolean hasOutlines(final Object key) {
        final CachedPlan p = CACHED.get(key);
        if (p == null) return false;
        for (final Part part : p.parts) if (part.lines != null) return true;
        return false;
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
        for (final Group g : GROUPS) g.close();
        GROUPS.clear();
    }

    // ======================================================================== drawing

    static void draw(final SlateRenderEvents.WorldRenderContext ctx, final Level level) {
        final List<Submitted> immediate = IMMEDIATE.snapshot();
        final List<CachedPlan> shown = new ArrayList<>();
        for (final CachedPlan p : CACHED.values()) if (p.visible() && !p.ghosts.isEmpty()) shown.add(p);
        if (immediate.isEmpty() && shown.isEmpty()) return;

        final PreviewSettings settings = PreviewSettings.current();
        final Vec3 cam = ctx.camera().getPosition();
        final Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        final float time = RenderKit.seconds();
        final float pulse = settings.pulse && RenderKit.animated() ? 0.9F + 0.1F * (float) Math.sin(time * Math.PI * 2 / 2.2) : 1F;
        final ShaderInstance shader = GhostShader.get();
        final boolean vanillaPath = forceVanillaPath || shader == null || RenderCompat.shaderPackInUse();

        final List<Group> groups = group(immediate, cam, settings.maxBlocks());
        // Cached plans are brought up to date for the path drawing them (outlines and hulls on both paths; the
        // textured mesh as a GPU buffer for the ghost shader, or recorded for the fallback path). Each plan is
        // measured against maxBlocks on its own, like the immediate ghosts. What is drawn is its parts, a space each.
        final boolean subLevels = SubLevels.present();
        final List<Part> plans = new ArrayList<>(shown.size());
        for (final CachedPlan p : shown) {
            p.ensureBuilt(level, settings, !vanillaPath);
            for (final Part part : p.parts) {
                part.pose = subLevels ? SubLevels.renderAt(part.origin) : null;
                plans.add(part);
            }
        }

        if (vanillaPath) {
            drawVanilla(level, cam, settings, pulse, groups, plans);
        } else {
            drawShaded(level, cam, modelView, projection, shader, settings, time, pulse, groups, plans);
        }
        drawHulls(cam, modelView, projection, pulse, groups, plans);
        drawOutlines(level, cam, modelView, projection, settings, vanillaPath, pulse, groups, plans);
    }

    /**
     * This frame's immediate ghosts, a group to a space: the world's round the camera's block (float precision far
     * from the world's origin), a sub-level's round the first of its ghosts.
     */
    private static List<Group> group(final List<Submitted> immediate, final Vec3 cam, final int maxBlocks) {
        int used = 0;
        final boolean subLevels = SubLevels.present();
        for (final Submitted s : immediate) {
            final SubLevels.Pose pose = subLevels ? SubLevels.renderAt(s.ghost().pos()) : null;
            final UUID space = pose == null ? null : pose.id();
            Group g = null;
            for (int i = 0; i < used; i++) {
                if (Objects.equals(GROUPS.get(i).space, space)) {
                    g = GROUPS.get(i);
                    break;
                }
            }
            if (g == null) {
                if (used == GROUPS.size()) GROUPS.add(new Group());
                g = GROUPS.get(used++);
                g.begin(pose, pose == null ? BlockPos.containing(cam) : s.ghost().pos());
            }
            g.add(s);
        }
        for (int i = 0; i < used; i++) GROUPS.get(i).hullOnly = GROUPS.get(i).ghosts.size() > maxBlocks;
        return GROUPS.subList(0, used);
    }

    /** Textured ghosts through the ghost shader: depth pre-pass, then colour where the depth matches. */
    private static void drawShaded(final Level level, final Vec3 cam, final Matrix4f modelView, final Matrix4f projection,
                                   final ShaderInstance shader, final PreviewSettings settings, final float time, final float pulse,
                                   final List<Group> groups, final List<Part> plans) {
        // Build this frame's immediate meshes first (no GL state changes in between).
        for (final Group g : groups) {
            if (g.hullOnly) continue;
            final BufferBuilder buf = RenderKit.begin(bytes(), VertexFormat.Mode.QUADS, GhostShader.FORMAT);
            GhostMesher.quads(GhostMesher.shaderSink(buf), g.ghosts, g.alphas, g.origin, level);
            g.hasQuads = g.quads.fill(buf);
        }

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
            for (final Group g : groups) if (g.hasQuads) drawGhostBuffer(g.quads.get(), shader, modelView, projection, g.origin, cam, g.pose);
            for (final Part p : plans) if (p.quads != null) drawGhostBuffer(p.quads, shader, modelView, projection, p.origin, cam, p.pose);
        }
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
    }

    /** Sets above the limit: their flat hull (translucent, no depth writes), on both paths. */
    private static void drawHulls(final Vec3 cam, final Matrix4f modelView, final Matrix4f projection, final float pulse,
                                  final List<Group> groups, final List<Part> plans) {
        RenderKit.translucentState();
        for (final Group g : groups) {
            if (!g.hullOnly) continue;
            final BufferBuilder fill = RenderKit.begin(bytes(), VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            GhostMesher.hullFaces(fill, g.ghosts, g.origin);
            g.hasHull = g.hull.fill(fill);
            if (g.hasHull) RenderKit.drawFill(g.hull.get(), placed(modelView, g.origin, cam, g.pose), projection, pulse, true);
        }
        for (final Part p : plans) {
            if (p.hull != null) RenderKit.drawFill(p.hull, placed(modelView, p.origin, cam, p.pose), projection, pulse, true);
        }
    }

    private static void drawGhostBuffer(final VertexBuffer vb, final ShaderInstance shader, final Matrix4f modelView, final Matrix4f projection,
                                        final BlockPos origin, final Vec3 cam, final @Nullable SubLevels.Pose pose) {
        if (pose == null) {
            uniform(shader, "GhostOffset", (float) (origin.getX() - cam.x), (float) (origin.getY() - cam.y), (float) (origin.getZ() - cam.z));
            vb.bind();
            vb.drawWithShader(modelView, projection, shader);
        } else {
            // On a sub-level the mesh is turned as well as moved: all of it is in the matrix, none in the offset.
            uniform(shader, "GhostOffset", 0F, 0F, 0F);
            vb.bind();
            vb.drawWithShader(placed(modelView, origin, cam, pose), projection, shader);
        }
        VertexBuffer.unbind();
    }

    /**
     * Shader-pack fallback: the ghosts' quads through the vanilla translucent block sheet (the pack's own pipeline
     * handles it), alpha and style tint only. Cached plans replay the mesh recorded when they changed
     * ({@link FallbackMesh}); only the few immediate ghosts are meshed per frame. Sets above {@code maxBlocks} show
     * their hull instead ({@link #drawHulls}), exactly as on the shaded path.
     */
    private static void drawVanilla(final Level level, final Vec3 cam, final PreviewSettings settings, final float pulse,
                                    final List<Group> groups, final List<Part> plans) {
        boolean any = false;
        for (final Group g : groups) any |= !g.hullOnly;
        for (final Part p : plans) any |= p.fallback != null && p.fallback.vertices() > 0;
        if (!any) return;
        final MultiBufferSource.BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
        final RenderType type = Sheets.translucentCullBlockSheet();
        final VertexConsumer consumer = source.getBuffer(type);
        final float opacity = fallbackOpacity(settings.opacity()) * pulse;
        final int danger = Theme.current().palette().danger();
        for (final Group g : groups) {
            if (g.hullOnly) continue;
            // Positions relative to the group's origin (float precision far from the world origin), then camera-relative.
            g.fallback.clear();
            GhostMesher.quads(g.fallback, g.ghosts, g.alphas, g.origin, level);
            g.fallback.replay(consumer, (float) (g.origin.getX() - cam.x), (float) (g.origin.getY() - cam.y), (float) (g.origin.getZ() - cam.z),
                g.pose == null ? null : g.pose.place(new Matrix4f(), g.origin, cam), opacity, danger);
        }
        for (final Part p : plans) {
            if (p.fallback == null) continue;
            p.fallback.replay(consumer, (float) (p.origin.getX() - cam.x), (float) (p.origin.getY() - cam.y), (float) (p.origin.getZ() - cam.z),
                p.pose == null ? null : p.pose.place(new Matrix4f(), p.origin, cam), opacity, danger);
        }
        source.endBatch(type);
    }

    /** Outlines (and, above the limit, merged hull edges): a soft wide glow under a crisp core line. */
    private static void drawOutlines(final Level level, final Vec3 cam, final Matrix4f modelView, final Matrix4f projection,
                                     final PreviewSettings settings, final boolean fallback, final float pulse, final List<Group> groups,
                                     final List<Part> plans) {
        final boolean outline = settings.outline || fallback;
        for (final Group g : groups) {
            final List<Ghost> invalid = new ArrayList<>();
            for (final Ghost ghost : g.ghosts) if (ghost.style() == Style.INVALID) invalid.add(ghost);
            if (outline || g.hullOnly || !invalid.isEmpty()) {
                final BufferBuilder lines = RenderKit.begin(bytes(), VertexFormat.Mode.LINES, RenderKit.linesFormat());
                final List<Ghost> outlined = outline || g.hullOnly ? g.ghosts : invalid;
                GhostMesher.outlines(lines, outlined, outlined == g.ghosts ? g.alphas : null, g.origin, level, g.hullOnly);
                g.hasLines = g.lines.fill(lines);
            }
        }
        RenderKit.translucentState();
        // Brighter on the shader-pack path: the outline is what keeps a washed-out ghost readable there.
        final float glow = (fallback ? 0.4F : 0.22F) * pulse, core = (fallback ? 1F : 0.85F) * pulse;
        for (final Group g : groups) {
            if (!g.hasLines) continue;
            final Matrix4f mv = placed(modelView, g.origin, cam, g.pose);
            RenderKit.drawLines(g.lines.get(), mv, projection, RenderKit.px(5F), glow, true);
            RenderKit.drawLines(g.lines.get(), mv, projection, RenderKit.px(1.75F), core, true);
        }
        for (final Part p : plans) {
            if (p.lines == null) continue;
            final Matrix4f mv = placed(modelView, p.origin, cam, p.pose);
            RenderKit.drawLines(p.lines, mv, projection, RenderKit.px(5F), glow, true);
            RenderKit.drawLines(p.lines, mv, projection, RenderKit.px(1.75F), core, true);
        }
    }

    // ======================================================================== helpers

    /**
     * Opacity on the shader-pack path: packs composite translucent geometry through their own lighting, fog and
     * blending, which left a ghost at the default 0.45 barely visible (MakeUp in the DF pack). Lifted into 0.35..1, so
     * the setting still orders faint to solid but a ghost never disappears.
     */
    static float fallbackOpacity(final float opacity) {
        return Math.min(1F, 0.35F + 0.65F * Math.max(0F, opacity));
    }

    static Matrix4f translated(final Matrix4f modelView, final BlockPos origin, final Vec3 cam) {
        return new Matrix4f(modelView).translate((float) (origin.getX() - cam.x), (float) (origin.getY() - cam.y), (float) (origin.getZ() - cam.z));
    }

    /**
     * The model-view a mesh built round {@code origin} is drawn with: moved to where the origin is seen from the
     * camera, and, for a mesh on a sub-level, turned and scaled as the sub-level is in this frame.
     */
    static Matrix4f placed(final Matrix4f modelView, final BlockPos origin, final Vec3 cam, final @Nullable SubLevels.Pose pose) {
        return pose == null ? translated(modelView, origin, cam) : pose.place(new Matrix4f(modelView), origin, cam);
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

    /**
     * One {@link #submitCached} key: the ghost list, and the GPU buffers it is drawn from, built lazily on the render
     * thread. The buffers are those of its {@link Part}s: one, unless the plan reaches from one space into another.
     */
    private static final class CachedPlan {
        @Nullable List<Ghost> ghosts;
        int version;
        boolean dirty = true;
        boolean usedTick;
        boolean usedFrame;
        long lastUsedMs;
        final List<Part> parts = new ArrayList<>(1);
        private int builtMax = -1;
        private boolean builtOutline;
        private boolean builtShaded;

        void setGhosts(final List<Ghost> list, final int newVersion) {
            ghosts = list;
            version = newVersion;
            dirty = true;
        }

        boolean visible() {
            return ghosts != null && (usedTick || usedFrame);
        }

        /**
         * Builds what the path drawing this frame needs, once per change of the ghosts, the relevant settings or the
         * path ({@code shaded}: the ghost shader; else the shader-pack fallback): the textured mesh (or the hull
         * above {@code maxBlocks}) and the outlines, for every space the plan has ghosts in.
         */
        void ensureBuilt(final Level level, final PreviewSettings settings, final boolean shaded) {
            if (ghosts == null) return;
            final boolean outline = settings.outline || !shaded;   // the fallback path always outlines
            if (!dirty && builtMax == settings.maxBlocks() && builtOutline == outline && builtShaded == shaded) return;
            dirty = false;
            builtMax = settings.maxBlocks();
            builtOutline = outline;
            builtShaded = shaded;
            closeBuffers();
            if (ghosts.isEmpty()) return;
            // The plan as a whole is what is measured against the limit, whatever it is cut into.
            final boolean hullOnly = ghosts.size() > builtMax;
            split();
            for (final Part part : parts) part.build(level, hullOnly, shaded, outline);
        }

        /** Cuts the ghosts up by the space they lie in; all of them are in one in all but the rarest plans. */
        private void split() {
            final List<Ghost> all = Objects.requireNonNull(ghosts);
            if (!SubLevels.present()) {
                parts.add(new Part(null, all));
                return;
            }
            final UUID first = SubLevels.renderIdAt(all.get(0).pos());
            boolean one = true;
            for (int i = 1; i < all.size() && one; i++) one = Objects.equals(first, SubLevels.renderIdAt(all.get(i).pos()));
            if (one) {
                parts.add(new Part(first, all));
                return;
            }
            for (final Ghost g : all) {
                final UUID space = SubLevels.renderIdAt(g.pos());
                Part part = null;
                for (final Part p : parts) {
                    if (Objects.equals(p.space, space)) {
                        part = p;
                        break;
                    }
                }
                if (part == null) {
                    part = new Part(space, new ArrayList<>());
                    parts.add(part);
                }
                part.ghosts.add(g);
            }
        }

        private void closeBuffers() {
            for (final Part part : parts) part.close();
            parts.clear();
        }

        void close() {
            closeBuffers();
            ghosts = null;
        }
    }

    /** The ghosts of a cached plan that lie in one space (the world, or one sub-level), and their GPU buffers. */
    private static final class Part {
        final @Nullable UUID space;
        final List<Ghost> ghosts;
        /** The block the meshes are built round: the first ghost's. */
        BlockPos origin = BlockPos.ZERO;
        /** The pose of the sub-level the part lies on, in this frame; null in the world itself. */
        @Nullable SubLevels.Pose pose;
        /** The ghost-shader mesh (shaded path). */
        @Nullable VertexBuffer quads;
        /** The recorded mesh of the shader-pack fallback path. */
        @Nullable FallbackMesh fallback;
        @Nullable VertexBuffer hull;
        @Nullable VertexBuffer lines;

        Part(final @Nullable UUID space, final List<Ghost> ghosts) {
            this.space = space;
            this.ghosts = ghosts;
        }

        void build(final Level level, final boolean hullOnly, final boolean shaded, final boolean outline) {
            if (ghosts.isEmpty()) return;
            origin = ghosts.get(0).pos();
            try {
                if (!hullOnly && shaded) {
                    final BufferBuilder buf = RenderKit.begin(bytes(), VertexFormat.Mode.QUADS, GhostShader.FORMAT);
                    GhostMesher.quads(GhostMesher.shaderSink(buf), ghosts, null, origin, level);
                    quads = uploadStatic(buf);
                } else if (!hullOnly) {
                    final FallbackMesh mesh = new FallbackMesh();
                    GhostMesher.quads(mesh, ghosts, null, origin, level);
                    fallback = mesh;
                    fallbackBuilds++;
                } else {
                    final BufferBuilder fill = RenderKit.begin(bytes(), VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                    GhostMesher.hullFaces(fill, ghosts, origin);
                    hull = uploadStatic(fill);
                }
                final List<Ghost> invalid = new ArrayList<>();
                for (final Ghost g : ghosts) if (g.style() == Style.INVALID) invalid.add(g);
                final List<Ghost> outlined = hullOnly || outline ? ghosts : invalid;
                if (!outlined.isEmpty()) {
                    final BufferBuilder lineBuf = RenderKit.begin(bytes(), VertexFormat.Mode.LINES, RenderKit.linesFormat());
                    GhostMesher.outlines(lineBuf, outlined, null, origin, level, hullOnly);
                    lines = uploadStatic(lineBuf);
                }
            } catch (final RuntimeException e) {
                SlateBuilding.LOGGER.error("[Slate Building] could not build a ghost preview of {} blocks", ghosts.size(), e);
                close();
            }
        }

        private static @Nullable VertexBuffer uploadStatic(final BufferBuilder buf) {
            final VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            if (RenderKit.upload(buf, vb)) return vb;
            vb.close();
            return null;
        }

        void close() {
            if (quads != null) quads.close();
            if (hull != null) hull.close();
            if (lines != null) lines.close();
            quads = null;
            fallback = null;
            hull = null;
            lines = null;
        }
    }

    private GhostRenderer() {}
}
