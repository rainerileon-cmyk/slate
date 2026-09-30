package dev.fallingcloud.slate.core.stage;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.fallingcloud.slate.core.Slate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * The passes behind a {@link StageFinish}: the bright parts of the rendered scene are copied into a quarter-size
 * target, blurred there, and handed to the shader that puts the scene on screen. One instance per stage (it owns
 * the two small targets); the three shader programs are shared and built on first use. If they cannot be built
 * (a driver that rejects them), stages fall back to showing their scenes unfinished: nothing ever depends on this.
 *
 * <p>The shaders live in {@code assets/minecraft/shaders/core/slate_stage_*}: the {@code minecraft} namespace is
 * where vanilla's loader looks, which keeps this free of loader code.</p>
 */
final class StagePost implements AutoCloseable {

    private static final int DOWNSCALE = 4;

    @Nullable private static ShaderInstance bright, blur, finish;
    private static boolean failed;

    @Nullable private TextureTarget a, b;
    private final Matrix4f identity = new Matrix4f();

    /** Builds the programs if needed. False when they cannot be built. */
    static boolean ready() {
        if (failed) return false;
        if (finish != null) return true;
        try {
            final var resources = Minecraft.getInstance().getResourceManager();
            bright = new ShaderInstance(resources, "slate_stage_bright", DefaultVertexFormat.POSITION_TEX);
            blur = new ShaderInstance(resources, "slate_stage_blur", DefaultVertexFormat.POSITION_TEX);
            finish = new ShaderInstance(resources, "slate_stage_finish", DefaultVertexFormat.POSITION_TEX);
            return true;
        } catch (final Exception e) {
            failed = true;
            drop();
            Slate.LOGGER.warn("[Slate] stage: scenes are shown without their finish (glow, grade): {}", e.toString());
            return false;
        }
    }

    /** Resources were reloaded: the programs are rebuilt from whatever the packs hold now. */
    static void reload() {
        drop();
        failed = false;
    }

    private static void drop() {
        if (bright != null) { bright.close(); bright = null; }
        if (blur != null) { blur.close(); blur = null; }
        if (finish != null) { finish.close(); finish = null; }
    }

    @Nullable
    static ShaderInstance finishShader() { return finish; }

    /**
     * Fills the glow texture from {@code scene}. Call with the model-view matrix at identity; the projection matrix
     * and the bound target are left changed (the stage restores both after its pass).
     */
    void glow(final TextureTarget scene, final StageFinish look) {
        final int w = Math.max(1, scene.width / DOWNSCALE), h = Math.max(1, scene.height / DOWNSCALE);
        if (a == null || a.width != w || a.height != h) {
            release();
            a = target(w, h);
            b = target(w, h);
        }
        final ShaderInstance brightPass = bright, blurPass = blur;
        if (brightPass == null || blurPass == null || a == null || b == null) return;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setProjectionMatrix(identity, VertexSorting.ORTHOGRAPHIC_Z);

        brightPass.safeGetUniform("Threshold").set(look.threshold);
        pass(brightPass, scene.getColorTextureId(), a);
        // Twice, the second time wider: a soft, far-reaching glow out of a cheap blur.
        for (int i = 0; i < 2; i++) {
            final float reach = look.spread * (i + 1);
            blurPass.safeGetUniform("Step").set(reach / w, 0f);
            pass(blurPass, a.getColorTextureId(), b);
            blurPass.safeGetUniform("Step").set(0f, reach / h);
            pass(blurPass, b.getColorTextureId(), a);
        }
        RenderSystem.enableCull();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** The blurred bright parts, after {@link #glow}. 0 before the first. */
    int glowTexture() {
        return a == null ? 0 : a.getColorTextureId();
    }

    private static TextureTarget target(final int w, final int h) {
        final TextureTarget t = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        t.setFilterMode(GL11.GL_LINEAR);
        t.setClearColor(0f, 0f, 0f, 1f);
        return t;
    }

    private static void pass(final ShaderInstance shader, final int texture, final TextureTarget into) {
        into.bindWrite(true);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, texture);
        final BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.addVertex(-1f, -1f, 0f).setUv(0f, 0f);
        bb.addVertex(1f, -1f, 0f).setUv(1f, 0f);
        bb.addVertex(1f, 1f, 0f).setUv(1f, 1f);
        bb.addVertex(-1f, 1f, 0f).setUv(0f, 1f);
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    private void release() {
        if (a != null) { a.destroyBuffers(); a = null; }
        if (b != null) { b.destroyBuffers(); b = null; }
    }

    @Override
    public void close() {
        release();
    }
}
