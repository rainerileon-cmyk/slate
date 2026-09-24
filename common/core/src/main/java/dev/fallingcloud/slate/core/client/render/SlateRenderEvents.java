package dev.fallingcloud.slate.core.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.fallingcloud.slate.core.event.Event;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;

/**
 * World-render hooks for Slate modules (client only). Core itself draws nothing in the world; modules such as
 * Slate Building use these to draw placement ghosts, selection boxes and mirror planes. Fired by Core's loader
 * client classes: NeoForge {@code RenderLevelStageEvent} at {@code AFTER_TRANSLUCENT_BLOCKS}, Fabric
 * {@code WorldRenderEvents.AFTER_TRANSLUCENT}.
 *
 * <p><b>Matrix contract (both loaders, 1.21.1).</b> While the event runs, the RenderSystem model-view matrix
 * ({@code RenderSystem.getModelViewMatrix()}) already holds the camera ROTATION, and {@link WorldRenderContext#poseStack()}
 * is (all but) identity. Draw in camera-relative coordinates: push the pose stack, translate by
 * {@code -camera.getPosition()}, emit world-space vertices, pop. Do not multiply {@link WorldRenderContext#modelView()}
 * into the pose again (that would rotate twice); it is passed for code that builds its own shader uniforms or
 * frustum tests. {@link WorldRenderContext#projection()} is the projection used for the level this frame.
 *
 * <p><b>Buffers.</b> Neither loader hands out a live buffer source at this stage (Fabric's {@code consumers()} is
 * null there), so draw through {@code Minecraft.getInstance().renderBuffers().bufferSource()} and call
 * {@code endBatch(type)} for the render types you used before returning, or use your own {@code BufferBuilder}.
 * Order relative to particles differs (NeoForge fires before particles in Fast/Fancy, Fabric after them), so do
 * not rely on it.
 */
public final class SlateRenderEvents {

    /** Listener type of {@link #AFTER_TRANSLUCENT}. */
    @FunctionalInterface
    public interface WorldRender {
        void render(WorldRenderContext context);
    }

    /**
     * What a world-render listener gets.
     *
     * @param poseStack   the level pose stack at this stage (identity apart from what earlier listeners pushed and popped)
     * @param modelView   the view rotation matrix of this frame; already applied to RenderSystem's model-view (see class doc)
     * @param projection  the projection matrix of this frame
     * @param camera      the main camera; {@code camera.getPosition()} is the translation to subtract
     * @param partialTick the game-time partial tick (0..1) for interpolating entity positions
     */
    public record WorldRenderContext(PoseStack poseStack, Matrix4f modelView, Matrix4f projection, Camera camera, float partialTick) {}

    /** After the translucent terrain layer: depth is populated, so ghosts and outlines sort against the world. */
    public static final Event<WorldRender> AFTER_TRANSLUCENT = new Event<>("after_translucent");

    /** Loader layer: fires {@link #AFTER_TRANSLUCENT}. A no-op without listeners (no allocation per frame). */
    public static void fireAfterTranslucent(final WorldRenderContext context) {
        AFTER_TRANSLUCENT.invoke(l -> l.render(context));
    }

    /** Loader layer: whether anything listens, so the loader can skip building the context. */
    public static boolean hasAfterTranslucentListeners() {
        return !AFTER_TRANSLUCENT.listeners().isEmpty();
    }

    private SlateRenderEvents() {}
}
