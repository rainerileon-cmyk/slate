package dev.fallingcloud.slate.core.stage;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Vector3f;

/**
 * What a node gets while the stage renders or ticks it. One instance per stage, refilled every frame (no
 * allocation): the pose stack already holds the camera view (nodes push their own model on top), the shared
 * buffer source, the packed light, timing, and the mouse ray for look-at effects.
 */
public final class StageRenderContext {

    /** View transform on the bottom; nodes push/pop their own. */
    public final PoseStack pose = new PoseStack();
    public MultiBufferSource.BufferSource buffers;
    public Stage stage;
    public StageCamera camera;
    public StageLighting lighting;
    public StageLevel level;
    /** Packed lightmap coordinates for renderers. */
    public int light = LightTexture.FULL_BRIGHT;
    /** Sub-tick fraction (0..1) of the stage's own 20 Hz tick, for lid/entity interpolation. */
    public float partial;
    /** Milliseconds since the previous rendered frame (clamped while hidden). */
    public float deltaMs;
    /** Stage time in ms: advances only while the stage renders. */
    public float timeMs;
    /** Wall clock, for {@code Anim}s. */
    public long nowMs;
    /** True when the mouse is over the stage; the ray is then valid (world space). */
    public boolean hasMouse;
    public float mouseNdcX, mouseNdcY;
    public final Vector3f rayOrigin = new Vector3f();
    public final Vector3f rayDir = new Vector3f();
    /** Scratch vectors nodes may use inside one call. */
    public final Vector3f scratch = new Vector3f();
    public final Vector3f scratch2 = new Vector3f();

    /** Stage time in seconds, handy for sin/cos animation. */
    public float seconds() { return timeMs / 1000f; }
}
