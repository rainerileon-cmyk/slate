package dev.fallingcloud.slate.core.stage;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fallingcloud.slate.core.Slate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * The soft, stylized light of a stage: one shader ({@code slate_stage_soft}) that every node of a scene can draw
 * with, so a chest, a player and a planet standing together are lit as one picture. A warm key light hangs in the
 * scene like a lamp and wraps round forms, a weak cool fill comes from the other side, edges that turn away catch a
 * line of light, and an ambient floor keeps shade from going black. The rig is a stage's {@link StageLighting}.
 *
 * <p>Two programs from the same source, one per vertex format the game's models come in: {@link #ENTITY} (posed
 * models, anything built by hand) and {@link #BLOCK} (baked block quads, meshes on the block atlas). Nodes ask
 * {@code ctx.soft} before using them and keep their vanilla path for when the programs could not be built.</p>
 */
public final class StageSoft {

    /** Position, colour, texture, overlay, light, normal: what entity models are made of. */
    public static final VertexFormat ENTITY = DefaultVertexFormat.NEW_ENTITY;
    /** Position, colour, texture, light, normal: what block quads are made of. */
    public static final VertexFormat BLOCK = DefaultVertexFormat.BLOCK;

    @Nullable private static ShaderInstance entity, block;
    private static boolean failed;
    private static final Vector3f scratch = new Vector3f();
    private static float muted;

    /** Builds the programs if needed. False when they cannot be built: scenes are then lit the game's way. */
    public static boolean ready() {
        if (failed) return false;
        if (block != null) return true;
        try {
            final var resources = Minecraft.getInstance().getResourceManager();
            entity = new ShaderInstance(resources, "slate_stage_soft", ENTITY);
            block = new ShaderInstance(resources, "slate_stage_soft", BLOCK);
            return true;
        } catch (final Exception e) {
            failed = true;
            drop();
            Slate.LOGGER.warn("[Slate] stage: scenes are lit the game's own way, the soft shader did not build: {}", e.toString());
            return false;
        }
    }

    static void reload() {
        drop();
        failed = false;
    }

    private static void drop() {
        if (entity != null) { entity.close(); entity = null; }
        if (block != null) { block.close(); block = null; }
    }

    /** The program for a vertex format ({@link #ENTITY} or {@link #BLOCK}); null before {@link #ready()}. */
    @Nullable
    public static ShaderInstance shader(final VertexFormat format) {
        return format == BLOCK ? block : entity;
    }

    /** Hands the rig to both programs, in view space. The stage calls this once per pass. */
    static void light(final StageLighting rig, final Matrix4f view) {
        for (final ShaderInstance s : new ShaderInstance[] {entity, block}) {
            if (s == null) continue;
            scratch.set(rig.keyPos());
            view.transformPosition(scratch);
            s.safeGetUniform("KeyPos").set(scratch.x, scratch.y, scratch.z);
            scratch.set(rig.fillDir());
            view.transformDirection(scratch);
            s.safeGetUniform("FillDir").set(scratch.x, scratch.y, scratch.z);
            final float b = rig.brightness();
            s.safeGetUniform("KeyColor").set(rig.keyColor().x * b, rig.keyColor().y * b, rig.keyColor().z * b);
            s.safeGetUniform("FillColor").set(rig.fillColor().x * b, rig.fillColor().y * b, rig.fillColor().z * b);
            s.safeGetUniform("Ambient").set(rig.ambientColor().x * b, rig.ambientColor().y * b, rig.ambientColor().z * b);
            s.safeGetUniform("RimColor").set(rig.rimColor().x, rig.rimColor().y, rig.rimColor().z);
            s.safeGetUniform("RimStrength").set(rig.rimStrength() * b);
            s.safeGetUniform("Wrap").set(rig.wrap());
        }
    }

    /**
     * How muted what is drawn next is (0 = as it is, 1 = grey and dim): the look of a thing that is there but cannot
     * be used. A node's own {@code muted} is handed over by the stage around its draw.
     */
    public static void muted(final float amount) { muted = Math.max(0f, Math.min(1f, amount)); }

    /** Starts geometry built by hand or posed on the CPU: quads in {@code format}, positions already in view space. */
    public static BufferBuilder begin(final VertexFormat format) {
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, format);
    }

    /**
     * Draws what {@link #begin} collected, with {@code texture}, at {@code alpha}. The model-view matrix is whatever
     * the render system holds (identity inside a stage pass). Leaves depth test on and the shader colour at white.
     */
    public static void end(final BufferBuilder builder, final VertexFormat format, final ResourceLocation texture, final float alpha) {
        end(builder, format, texture, alpha, true);
    }

    /** As above; {@code cull = false} also draws the backs of faces (the outer layer of a skin). */
    public static void end(final BufferBuilder builder, final VertexFormat format, final ResourceLocation texture, final float alpha, final boolean cull) {
        final MeshData data = builder.build();
        if (data == null) return;
        final ShaderInstance shader = shader(format);
        if (shader == null) {
            data.close();
            return;
        }
        final float[] was = RenderSystem.getShaderColor().clone();
        state(alpha);
        if (!cull) RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        shader.safeGetUniform("Muted").set(muted);
        BufferUploader.drawWithShader(data);
        restore(was);
    }

    /** Draws a mesh kept in a vertex buffer (format {@link #BLOCK}), its positions moved by {@code modelView}. */
    public static void draw(final VertexBuffer buffer, final Matrix4f modelView, final Matrix4f projection, final ResourceLocation texture,
                            final float alpha, final boolean translucent) {
        final ShaderInstance shader = block;
        if (shader == null) return;
        final float[] was = RenderSystem.getShaderColor().clone();
        state(translucent ? Math.min(alpha, 0.999f) : alpha);
        if (translucent) RenderSystem.depthMask(false);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        shader.safeGetUniform("Muted").set(muted);
        buffer.bind();
        buffer.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();
        restore(was);
    }

    /** Puts back what the rest of the pass expects: blending on, depth written, the shader colour it had. */
    private static void restore(final float[] shaderColor) {
        RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableBlend();
        dev.fallingcloud.slate.core.stage.StageBlend.over();
    }

    private static void state(final float alpha) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        if (alpha < 1f) {
            RenderSystem.enableBlend();
            dev.fallingcloud.slate.core.stage.StageBlend.over();
        } else {
            RenderSystem.disableBlend();
        }
    }

    private StageSoft() {}
}
