package dev.fallingcloud.slate.core.stage;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.stage.node.StageNode;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateSounds;
import dev.fallingcloud.slate.core.widget.SlateTooltips;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

/**
 * Real 3D Minecraft content inside a GUI rectangle. A stage owns a {@link StageLevel}, a {@link StageCamera}, a
 * {@link StageLighting} and a list of {@link StageNode}s. Every frame {@link #render} draws the nodes into the
 * stage's own framebuffer (sized to the rectangle × GUI scale × {@link #resolutionScale}, capped) with its own
 * projection, depth buffer and fog, then blits that texture into the GUI with premultiplied alpha. All render state
 * the pass touches (projection and model-view matrices, viewport, framebuffer, scissor, depth, blend, cull, shader
 * colour, fog, lightmap binding, shader lights) is restored before returning, so the surrounding screen is untouched.
 *
 * <p>Picking casts the mouse through the camera against node bounds and drives hover, press and click; Tab/arrows
 * cycle keyboard focus and Enter/Space activate. A stage only advances (ticks, animates, renders) while something
 * calls {@code render}, so a hidden screen costs nothing. Call {@link #close} when done (a {@link #bind bound} stage
 * closes itself when its screen is replaced).</p>
 */
public final class Stage implements AutoCloseable {

    public static final int MAX_TEXTURE = 4096;
    private static final float TICK_MS = 50f;
    private static final List<Stage> LIVE = new ArrayList<>();
    /** Diagnostics (GL errors, FBO readback) for the first frames when {@code config/slate/stage-debug} exists. */
    private static final boolean DEBUG = java.nio.file.Files.exists(
        dev.fallingcloud.slate.core.platform.SlatePlatform.get().configDir().resolve("slate").resolve("stage-debug"));

    private void debugReadback(final Minecraft mc, final int pw, final int ph) {
        final java.nio.ByteBuffer px = org.lwjgl.BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(pw / 2, ph / 2, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
        final int err = GL11.glGetError();
        final int bound = GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        Slate.LOGGER.info("[Slate] stage debug: frame {} fbo {} (bound {}) tex {} size {}x{} centre rgba=({},{},{},{}) glError={} shader={} nodes={} gradient={}",
            frames, target == null ? -1 : target.frameBufferId, bound, target == null ? -1 : target.getColorTextureId(), pw, ph,
            px.get(0) & 0xFF, px.get(1) & 0xFF, px.get(2) & 0xFF, px.get(3) & 0xFF, err,
            RenderSystem.getShader() == null ? "null" : RenderSystem.getShader().getName(), nodes.size(), gradient);
    }

    private final StageLighting lighting = new StageLighting();
    private final StageLevel level = new StageLevel(lighting);
    private final StageCamera camera = new StageCamera();
    private final List<StageNode> nodes = new ArrayList<>();
    private final StageRenderContext ctx = new StageRenderContext();

    @Nullable private TextureTarget target;
    private int targetW, targetH;
    private float resolutionScale = 1f;
    private int background;
    private int gradientTop, gradientBottom;
    private boolean gradient;
    private boolean fog;
    private float fogStart = 16f, fogEnd = 48f;
    private int fogColor = 0xFF161615;
    private float alpha = 1f;
    private boolean showFocusOutline = true;
    private int outlineColor;

    private long lastRenderMs;
    private float tickAccMs;
    private float timeMs;
    private long lastFrameNanos;
    private float frameMsAvg;
    private long frames;

    @Nullable private StageNode hovered, pressed, focused;
    private boolean focusVisible;
    private long hoverSinceMs;
    @Nullable private Screen owner;
    private boolean closed;

    @Nullable private Camera farCamera;
    @Nullable private Entity cameraAnchor;
    private final int[] scissorBox = new int[4];
    private final float[] savedFogColor = new float[4];
    private final float[] savedShaderColor = new float[4];
    private final Matrix4f savedProjection = new Matrix4f();
    private final Matrix4f identity = new Matrix4f();
    @Nullable private VertexSorting savedSorting;

    public Stage() {
        StageResources.ensureRegistered();
        synchronized (LIVE) { LIVE.add(this); }
    }

    // ------------------------------------------------------------------ configuration

    public StageLevel level() { return level; }

    public StageCamera camera() { return camera; }

    public StageLighting lighting() { return lighting; }

    public List<StageNode> nodes() { return nodes; }

    public <T extends StageNode> T add(final T node) {
        nodes.add(node);
        return node;
    }

    public void remove(final StageNode node) {
        if (nodes.remove(node)) {
            if (hovered == node) hovered = null;
            if (pressed == node) pressed = null;
            if (focused == node) focused = null;
            node.dispose();
        }
    }

    /** A solid background colour (ARGB; 0 = transparent, the GUI shows through). */
    public Stage background(final int argb) { this.background = argb; this.gradient = false; return this; }

    /** A vertical gradient background. */
    public Stage gradient(final int top, final int bottom) { this.gradientTop = top; this.gradientBottom = bottom; this.gradient = true; return this; }

    /** Render at a fraction of the GUI pixel density (0.25..2); below 1 the result is filtered bilinearly. */
    public Stage resolutionScale(final float s) { this.resolutionScale = Mth.clamp(s, 0.25f, 2f); return this; }

    public float resolutionScale() { return resolutionScale; }

    /**
     * Distance fog towards {@code color} (only sensible with an opaque background: vanilla shaders fade colour, not
     * alpha). {@code start >= end} or {@code fog(false)} disables it.
     */
    public Stage fog(final boolean on, final float start, final float end, final int color) {
        this.fog = on && end > start;
        this.fogStart = start;
        this.fogEnd = end;
        this.fogColor = color;
        return this;
    }

    public Stage fog(final boolean on) { this.fog = on && fogEnd > fogStart; return this; }

    /** Whole-stage opacity applied at the blit (fade in/out). */
    public Stage alpha(final float a) { this.alpha = Mth.clamp(a, 0f, 1f); return this; }

    public float alpha() { return alpha; }

    public Stage focusOutline(final boolean on) { this.showFocusOutline = on; return this; }

    /** Outline colour for the focused node (ARGB; 0 = the theme accent). */
    public Stage outlineColor(final int argb) { this.outlineColor = argb; return this; }

    /** Ties the stage to a screen: it closes itself when another screen replaces that one. */
    public Stage bind(@Nullable final Screen screen) { this.owner = screen; return this; }

    // ------------------------------------------------------------------ state

    @Nullable public StageNode hovered() { return hovered; }

    @Nullable public StageNode focused() { return focused; }

    public boolean isClosed() { return closed; }

    /** Stage time in ms (advances only while rendering). */
    public float timeMs() { return timeMs; }

    /** Smoothed render cost of the last frames in ms (FBO pass plus blit). */
    public float frameMs() { return frameMsAvg; }

    /** Render cost of the last frame in ms. */
    public float lastFrameMs() { return lastFrameNanos / 1_000_000f; }

    public int targetWidth() { return targetW; }

    public int targetHeight() { return targetH; }

    public long frames() { return frames; }

    // ------------------------------------------------------------------ focus and input

    /** Whether the keyboard focus outline is drawn (the owning widget has focus). */
    public void focusVisible(final boolean visible) { this.focusVisible = visible; }

    public void focus(@Nullable final StageNode node) {
        if (focused == node) return;
        if (focused != null) focused.setFocused(false);
        focused = node;
        if (focused != null) focused.setFocused(true);
    }

    /**
     * Moves keyboard focus to the next ({@code +1}) or previous ({@code -1}) pickable node. Returns false when the
     * end is reached (focus cleared), so a widget can pass Tab on to the rest of its screen.
     */
    public boolean focusNext(final int direction) {
        StageNode first = null, last = null, next = null;
        boolean passed = false;
        if (direction >= 0) {
            for (final StageNode n : nodes) {
                if (!n.pickable() || !n.visible()) continue;
                if (first == null) first = n;
                if (passed && next == null) next = n;
                if (n == focused) passed = true;
            }
        } else {
            for (int i = nodes.size() - 1; i >= 0; i--) {
                final StageNode n = nodes.get(i);
                if (!n.pickable() || !n.visible()) continue;
                if (first == null) first = n;
                if (passed && next == null) next = n;
                if (n == focused) passed = true;
            }
        }
        if (focused == null) { focus(first); return first != null; }
        if (next == null) { focus(null); return false; }
        focus(next);
        return true;
    }

    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button != 0 || hovered == null) return false;
        pressed = hovered;
        pressed.setPressed(true);
        return true;
    }

    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        if (button != 0 || pressed == null) return false;
        final StageNode p = pressed;
        pressed = null;
        p.setPressed(false);
        if (p == hovered) {
            focus(p);
            SlateSounds.click();
            p.activate();
        }
        return true;
    }

    /** Tab / Shift+Tab / arrows cycle focus; Enter, Space activate. Returns true when consumed. */
    public boolean keyPressed(final int key, final int scan, final int modifiers) {
        final boolean shift = (modifiers & 1) != 0;
        switch (key) {
            case 258 -> { return focusNext(shift ? -1 : 1); }
            case 262, 264 -> { if (!focusNext(1)) focusNext(1); return true; }
            case 263, 265 -> { if (!focusNext(-1)) focusNext(-1); return true; }
            case 257, 335, 32 -> {
                if (focused == null) return false;
                SlateSounds.click();
                focused.activate();
                return true;
            }
            default -> { return false; }
        }
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Draws the stage into the GUI rectangle. {@code mouseX/Y} are GUI coordinates (anything outside the rectangle
     * means "no mouse"). Safe to call from any {@code render} of a screen or widget.
     */
    public void render(final GuiGraphics g, final int x, final int y, final int w, final int h, final double mouseX, final double mouseY, final float partialTick) {
        if (closed || w <= 0 || h <= 0) return;
        final long t0 = System.nanoTime();
        final Minecraft mc = Minecraft.getInstance();

        // ---- time: measured between renders and clamped, so a hidden stage pauses instead of catching up
        final long now = Clock.nowMs();
        final float delta = lastRenderMs == 0 ? 16f : Math.min(250f, now - lastRenderMs);
        lastRenderMs = now;
        timeMs += delta;
        tickAccMs += delta;
        int ticks = 0;
        while (tickAccMs >= TICK_MS && ticks < 4) { tickAccMs -= TICK_MS; ticks++; }
        if (tickAccMs > TICK_MS) tickAccMs = 0f;

        // ---- target
        final double guiScale = mc.getWindow().getGuiScale();
        final int pw = Mth.clamp((int) Math.round(w * guiScale * resolutionScale), 1, MAX_TEXTURE);
        final int ph = Mth.clamp((int) Math.round(h * guiScale * resolutionScale), 1, MAX_TEXTURE);
        ensureTarget(pw, ph, pw != Math.round(w * guiScale) || ph != Math.round(h * guiScale));

        // ---- context
        camera.update(delta, (float) w / (float) h);
        ctx.buffers = mc.renderBuffers().bufferSource();
        ctx.stage = this;
        ctx.camera = camera;
        ctx.lighting = lighting;
        ctx.level = level;
        ctx.light = lighting.packedLight();
        ctx.partial = tickAccMs / TICK_MS;
        ctx.deltaMs = delta;
        ctx.timeMs = timeMs;
        ctx.nowMs = now;
        ctx.hasMouse = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        if (ctx.hasMouse) {
            ctx.mouseNdcX = (float) ((mouseX - x) / w * 2.0 - 1.0);
            ctx.mouseNdcY = (float) (1.0 - (mouseY - y) / h * 2.0);
            camera.ray(ctx.mouseNdcX, ctx.mouseNdcY, ctx.rayOrigin, ctx.rayDir);
        }
        for (int i = 0; i < ticks; i++) tick();
        for (final StageNode n : nodes) {
            n.update(ctx);
            n.prepare(ctx);
        }
        updateHover(now);

        // ---- enter: remember everything the pass touches
        g.flush();
        final boolean scissorWas = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (scissorWas) {
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
            RenderSystem.disableScissor();
        }
        final float fogStartWas = RenderSystem.getShaderFogStart();
        final float fogEndWas = RenderSystem.getShaderFogEnd();
        final FogShape fogShapeWas = RenderSystem.getShaderFogShape();
        System.arraycopy(RenderSystem.getShaderFogColor(), 0, savedFogColor, 0, 4);
        System.arraycopy(RenderSystem.getShaderColor(), 0, savedShaderColor, 0, 4);
        final int lightmapWas = RenderSystem.getShaderTexture(2);
        // By value, not backupProjectionMatrix(): that is a single slot other code may also be using.
        savedProjection.set(RenderSystem.getProjectionMatrix());
        savedSorting = RenderSystem.getVertexSorting();
        final Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();

        try {
            renderPass(mc);
            if (DEBUG && frames < 3) debugReadback(mc, pw, ph);
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] stage render failed", e);
        } finally {
            // ---- leave: put the GUI back exactly as it was
            VertexBuffer.unbind();
            RenderSystem.setShaderTexture(2, lightmapWas);
            RenderSystem.setShaderFogStart(fogStartWas);
            RenderSystem.setShaderFogEnd(fogEndWas);
            RenderSystem.setShaderFogShape(fogShapeWas);
            RenderSystem.setShaderFogColor(savedFogColor[0], savedFogColor[1], savedFogColor[2], savedFogColor[3]);
            RenderSystem.setShaderColor(savedShaderColor[0], savedShaderColor[1], savedShaderColor[2], savedShaderColor[3]);
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting != null ? savedSorting : VertexSorting.ORTHOGRAPHIC_Z);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();
            restoreDispatchers(mc);
            mc.getMainRenderTarget().bindWrite(true);
            if (scissorWas) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
        }

        blit(g, x, y, w, h);

        lastFrameNanos = System.nanoTime() - t0;
        final float ms = lastFrameNanos / 1_000_000f;
        frameMsAvg = frames == 0 ? ms : frameMsAvg * 0.9f + ms * 0.1f;
        frames++;
    }

    private void renderPass(final Minecraft mc) {
        final TextureTarget fbo = target;
        if (fbo == null) return;
        final float bgA = background == 0 ? 0f : ((background >>> 24) & 0xFF) / 255f;
        // Premultiplied clear: the blit treats the texture as premultiplied, so a transparent clear is plain zeros.
        fbo.setClearColor(((background >> 16) & 0xFF) / 255f * bgA, ((background >> 8) & 0xFF) / 255f * bgA, (background & 0xFF) / 255f * bgA, bgA);
        fbo.clear(Minecraft.ON_OSX);      // clears... and unbinds again (RenderTarget.clear ends with unbindWrite)
        fbo.bindWrite(true);              // so bind for writing here; this also sets the viewport

        RenderSystem.setProjectionMatrix(camera.projection(), VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        final float b = lighting.brightness();
        RenderSystem.setShaderColor(b, b, b, 1f);
        if (fog) {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogColor(((fogColor >> 16) & 0xFF) / 255f, ((fogColor >> 8) & 0xFF) / 255f, (fogColor & 0xFF) / 255f, 1f);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
        } else {
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
            RenderSystem.setShaderFogColor(0f, 0f, 0f, 0f);
        }
        if (gradient) drawGradient();

        mc.gameRenderer.lightTexture().turnOnLightLayer();
        lighting.applyShaderLights(camera.view());
        prepareDispatchers(mc);

        ctx.pose.setIdentity();
        ctx.pose.mulPose(camera.view());
        for (final StageNode n : nodes) {
            if (!n.visible()) continue;
            if (n.alpha() < 1f) {
                // Per-node opacity rides on the shader colour, which applies at draw time: flush around the node.
                ctx.buffers.endBatch();
                RenderSystem.setShaderColor(b, b, b, n.alpha());
                n.render(ctx);
                ctx.buffers.endBatch();
                RenderSystem.setShaderColor(b, b, b, 1f);
            } else {
                n.render(ctx);
            }
        }
        ctx.buffers.endBatch();
        // Translucent pass: water layers, clouds, sparkles; over the opaque depth, no depth writes.
        for (final StageNode n : nodes) {
            if (!n.visible() || !n.hasTranslucentPass()) continue;
            if (n.alpha() < 1f) RenderSystem.setShaderColor(b, b, b, n.alpha());
            n.renderTranslucent(ctx);
            if (n.alpha() < 1f) RenderSystem.setShaderColor(b, b, b, 1f);
        }
        ctx.buffers.endBatch();
        RenderSystem.depthMask(true);
        drawFocusOutline();
    }

    private void drawGradient() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        // Clip-space quad: identity projection, then the camera projection comes back (no nested backup: that slot is single).
        RenderSystem.setProjectionMatrix(identity, VertexSorting.ORTHOGRAPHIC_Z);
        final BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bb.addVertex(-1f, 1f, 0f).setColor(gradientTop);
        bb.addVertex(-1f, -1f, 0f).setColor(gradientBottom);
        bb.addVertex(1f, -1f, 0f).setColor(gradientBottom);
        bb.addVertex(1f, 1f, 0f).setColor(gradientTop);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        RenderSystem.setProjectionMatrix(camera.projection(), VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private void drawFocusOutline() {
        final StageNode f = focused;
        if (f == null || !focusVisible || !showFocusOutline || !f.visible()) return;
        final float a = f.focus() * (0.75f + 0.25f * Mth.sin(timeMs / 1000f * 4f));
        if (a <= 0.01f) return;
        final int color = outlineColor != 0 ? outlineColor : Theme.current().accent();
        final float r = ((color >> 16) & 0xFF) / 255f, gr = ((color >> 8) & 0xFF) / 255f, bl = (color & 0xFF) / 255f;
        final VertexConsumer lines = ctx.buffers.getBuffer(RenderType.lines());
        ctx.pose.pushPose();
        ctx.pose.mulPose(f.model);
        final float pad = 0.04f + 0.02f * (1f - f.focus());
        LevelRenderer.renderLineBox(ctx.pose, lines,
            f.boundsMin().x - pad, f.boundsMin().y - pad, f.boundsMin().z - pad,
            f.boundsMax().x + pad, f.boundsMax().y + pad, f.boundsMax().z + pad, r, gr, bl, a);
        ctx.pose.popPose();
        ctx.buffers.endBatch(RenderType.lines());
    }

    private void blit(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        final TextureTarget fbo = target;
        if (fbo == null) return;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, fbo.getColorTextureId());
        RenderSystem.enableBlend();
        // The texture holds premultiplied colour (transparent clear, translucent layers blended over it).
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        final Matrix4f m = g.pose().last().pose();
        final int a = Math.round(alpha * 255f);
        final BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.addVertex(m, x, y, 0f).setUv(0f, 1f).setColor(a, a, a, a);
        bb.addVertex(m, x, y + h, 0f).setUv(0f, 0f).setColor(a, a, a, a);
        bb.addVertex(m, x + w, y + h, 0f).setUv(1f, 0f).setColor(a, a, a, a);
        bb.addVertex(m, x + w, y, 0f).setUv(1f, 1f).setColor(a, a, a, a);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        RenderSystem.defaultBlendFunc();
    }

    private void ensureTarget(final int w, final int h, final boolean scaled) {
        if (target == null) {
            target = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            targetW = w;
            targetH = h;
            target.setFilterMode(scaled ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        } else if (targetW != w || targetH != h) {
            target.resize(w, h, Minecraft.ON_OSX);
            targetW = w;
            targetH = h;
            target.setFilterMode(scaled ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        }
    }

    /**
     * Entity and block-entity renderers read the dispatchers' level and camera. The camera sits far away on
     * purpose: {@code LivingEntityRenderer.shouldShowName} dereferences {@code Minecraft.player} for anything within
     * 64 blocks of it, and there is no player on the title screen.
     */
    private void prepareDispatchers(final Minecraft mc) {
        if (farCamera == null) {
            farCamera = new Camera();
            cameraAnchor = level.spawn(EntityType.MARKER, 0.0, -10000.0, 0.0, 0f);
        }
        if (cameraAnchor != null) farCamera.setup(level, cameraAnchor, false, false, 1f);
        final EntityRenderDispatcher erd = mc.getEntityRenderDispatcher();
        erd.prepare(level, farCamera, null);
        erd.setRenderShadow(false);
        final BlockEntityRenderDispatcher berd = mc.getBlockEntityRenderDispatcher();
        berd.prepare(level, farCamera, null);
    }

    private void restoreDispatchers(final Minecraft mc) {
        final EntityRenderDispatcher erd = mc.getEntityRenderDispatcher();
        erd.setRenderShadow(true);
        if (mc.level != null) {
            erd.prepare(mc.level, mc.gameRenderer.getMainCamera(), mc.crosshairPickEntity);
            mc.getBlockEntityRenderDispatcher().prepare(mc.level, mc.gameRenderer.getMainCamera(), mc.hitResult);
        }
    }

    private void tick() {
        for (final StageNode n : nodes) {
            try {
                n.tick(ctx);
            } catch (final Exception e) {
                Slate.LOGGER.warn("[Slate] stage: node tick failed: {}", e.toString());
            }
        }
    }

    private void updateHover(final long now) {
        StageNode best = null;
        double bestT = Double.POSITIVE_INFINITY;
        if (ctx.hasMouse) {
            for (final StageNode n : nodes) {
                if (!n.pickable() || !n.visible()) continue;
                final double t = n.pick(ctx.rayOrigin, ctx.rayDir);
                if (t < bestT) { bestT = t; best = n; }
            }
        }
        if (best != hovered) {
            if (hovered != null) hovered.setHovered(false);
            hovered = best;
            hoverSinceMs = now;
            if (hovered != null) hovered.setHovered(true);
        }
        if (hovered != null && hovered.tooltip() != null && now - hoverSinceMs > SlateTooltips.DELAY_MS) {
            SlateTooltips.request(hovered.tooltip(), null);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (final StageNode n : nodes) {
            try { n.dispose(); } catch (final Exception ignored) {}
        }
        nodes.clear();
        hovered = pressed = focused = null;
        if (target != null) { target.destroyBuffers(); target = null; }
        synchronized (LIVE) { LIVE.remove(this); }
    }

    /** Core hook: a new screen was set; stages bound to another screen are closed. */
    public static void onScreenOpened(@Nullable final Screen screen) {
        final List<Stage> toClose = new ArrayList<>();
        synchronized (LIVE) {
            for (final Stage s : LIVE) if (s.owner != null && s.owner != screen) toClose.add(s);
        }
        for (final Stage s : toClose) s.close();
    }

    /** Number of open stages (diagnostics). */
    public static int liveCount() {
        synchronized (LIVE) { return LIVE.size(); }
    }

    /** ARGB helper so callers can pass theme colours with a chosen alpha. */
    public static int withAlpha(final int argb, final float alpha) {
        return Colors.withAlpha(argb, Math.round(alpha * 255f));
    }
}
