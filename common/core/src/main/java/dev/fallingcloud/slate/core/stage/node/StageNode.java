package dev.fallingcloud.slate.core.stage.node;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Something on a stage. Every node has a transform (position, yaw/pitch/roll in degrees, scale), an alpha, a local
 * bounding box for picking and the outline, and the three state animations a Slate widget has (hover, press, focus,
 * all {@link Anim}s so {@code Theme.motion()} applies). Hover lifts and grows the node a little; press squashes it.
 *
 * <p>Subclasses draw in local space in {@link #draw}: the pose stack already holds view × model, so a unit block
 * spans -0.5..0.5 on X/Z and 0..1 on Y. Coordinates are blocks, Y up; yaw 0 faces +Z (towards a camera on +Z).</p>
 *
 * <p>The fluent setters return {@code StageNode}; call subclass-specific ones first when chaining.</p>
 */
public abstract class StageNode {

    protected final Vector3f position = new Vector3f();
    protected float yaw, pitch, roll;
    protected final Vector3f scale = new Vector3f(1f, 1f, 1f);
    protected float alpha = 1f;
    protected boolean visible = true;
    private boolean pickable;
    protected final Vector3f boundsMin = new Vector3f(-0.5f, 0f, -0.5f);
    protected final Vector3f boundsMax = new Vector3f(0.5f, 1f, 0.5f);
    protected float hoverLift = 0.05f;
    protected float hoverScale = 1.04f;

    protected final Anim hoverAnim = new Anim(0, 160, Ease.OUT_CUBIC);
    protected final Anim pressAnim = new Anim(0, 90, Ease.OUT_CUBIC);
    protected final Anim focusAnim = new Anim(0, 160, Ease.OUT_CUBIC);

    @Nullable private Runnable onClick;
    @Nullable private Consumer<Boolean> onHoverChanged;
    @Nullable private Component tooltip;
    private Component name = Component.empty();
    private boolean hovered, pressed, focused;

    /** Model matrix (local → world) and its inverse, rebuilt by {@link #prepare} every frame. */
    public final Matrix4f model = new Matrix4f();
    public final Matrix4f modelInverse = new Matrix4f();
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f pickOrigin = new Vector3f();
    private final Vector3f pickDir = new Vector3f();

    // ------------------------------------------------------------------ fluent configuration

    public StageNode at(final float x, final float y, final float z) { position.set(x, y, z); return this; }

    public StageNode at(final Vector3f p) { position.set(p); return this; }

    /** Degrees: yaw around Y, pitch around X, roll around Z. */
    public StageNode rotate(final float yaw, final float pitch, final float roll) { this.yaw = yaw; this.pitch = pitch; this.roll = roll; return this; }

    public StageNode yaw(final float degrees) { this.yaw = degrees; return this; }

    public StageNode scale(final float s) { scale.set(s, s, s); return this; }

    public StageNode scale(final float x, final float y, final float z) { scale.set(x, y, z); return this; }

    public StageNode alpha(final float a) { this.alpha = Math.max(0f, Math.min(1f, a)); return this; }

    public StageNode visible(final boolean v) { this.visible = v; return this; }

    /** Pickable nodes take hover, clicks and keyboard focus. */
    public StageNode pickable(final boolean p) { this.pickable = p; return this; }

    public StageNode onClick(@Nullable final Runnable r) { this.onClick = r; if (r != null) pickable = true; return this; }

    public StageNode onHover(@Nullable final Consumer<Boolean> c) { this.onHoverChanged = c; return this; }

    public StageNode tooltip(@Nullable final Component c) { this.tooltip = c; return this; }

    /** Narration label and debug name. */
    public StageNode named(final Component c) { this.name = c == null ? Component.empty() : c; return this; }

    public StageNode named(final String s) { return named(Component.literal(s)); }

    /** Local bounding box (blocks), used for picking and the focus outline. */
    public StageNode bounds(final float minX, final float minY, final float minZ, final float maxX, final float maxY, final float maxZ) {
        boundsMin.set(minX, minY, minZ);
        boundsMax.set(maxX, maxY, maxZ);
        return this;
    }

    /** How hover feels: {@code lift} in blocks and {@code scale} as a factor (1 = none). */
    public StageNode hoverFeel(final float lift, final float scale) { this.hoverLift = lift; this.hoverScale = scale; return this; }

    // ------------------------------------------------------------------ state

    public Vector3f position() { return position; }

    public float yaw() { return yaw; }

    public float pitch() { return pitch; }

    public float roll() { return roll; }

    public Vector3f scale() { return scale; }

    public float alpha() { return alpha; }

    public boolean visible() { return visible; }

    public boolean pickable() { return pickable; }

    public boolean hovered() { return hovered; }

    public boolean pressed() { return pressed; }

    public boolean focused() { return focused; }

    @Nullable public Component tooltip() { return tooltip; }

    public Component name() { return name; }

    public Vector3f boundsMin() { return boundsMin; }

    public Vector3f boundsMax() { return boundsMax; }

    /** 0..1 hover amount this frame. */
    public float hover() { return hoverAnim.get(); }

    public float press() { return pressAnim.get(); }

    public float focus() { return focusAnim.get(); }

    public void setHovered(final boolean h) {
        if (hovered == h) return;
        hovered = h;
        hoverAnim.set(h);
        if (onHoverChanged != null) onHoverChanged.accept(h);
    }

    public void setPressed(final boolean p) {
        pressed = p;
        pressAnim.set(p);
    }

    public void setFocused(final boolean f) {
        focused = f;
        focusAnim.set(f);
    }

    /** Runs the click action with the press feedback (mouse click or keyboard activation). */
    public void activate() {
        pressAnim.snap(1f);
        pressAnim.set(0f);
        if (onClick != null) onClick.run();
    }

    // ------------------------------------------------------------------ per frame

    /** Rebuilds the model matrix with the hover/press feel applied. Called by the stage before picking and drawing. */
    public void prepare(final StageRenderContext ctx) {
        final float h = hoverAnim.get();
        final float p = pressAnim.get();
        final float s = 1f + (hoverScale - 1f) * h - 0.04f * p;
        rotation.rotationYXZ((float) Math.toRadians(yaw), (float) Math.toRadians(pitch), (float) Math.toRadians(roll));
        model.translationRotateScale(position.x, position.y + hoverLift * h, position.z,
            rotation.x, rotation.y, rotation.z, rotation.w, scale.x * s, scale.y * s, scale.z * s);
        model.invert(modelInverse);
    }

    /** Per-frame logic before drawing (animation state); the transform is already prepared. */
    public void update(final StageRenderContext ctx) {}

    /** 20 Hz logic (lids, entity ticks). */
    public void tick(final StageRenderContext ctx) {}

    /** Pushes the model transform and calls {@link #draw}. */
    public void render(final StageRenderContext ctx) {
        if (!visible || alpha <= 0.004f) return;
        ctx.pose.pushPose();
        ctx.pose.mulPose(model);
        try {
            draw(ctx);
        } finally {
            ctx.pose.popPose();
        }
    }

    /** Draw in local space. */
    protected abstract void draw(StageRenderContext ctx);

    /** Release GPU or level resources; the node is not used afterwards. */
    public void dispose() {}

    // ------------------------------------------------------------------ picking

    /**
     * Ray/box test in local space. Returns the ray parameter of the hit (comparable across nodes, in world units)
     * or {@code Double.POSITIVE_INFINITY} for a miss.
     */
    public double pick(final Vector3f origin, final Vector3f dir) {
        pickOrigin.set(origin);
        modelInverse.transformPosition(pickOrigin);
        pickDir.set(dir);
        modelInverse.transformDirection(pickDir);
        double tmin = Double.NEGATIVE_INFINITY, tmax = Double.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            final float o = axis == 0 ? pickOrigin.x : axis == 1 ? pickOrigin.y : pickOrigin.z;
            final float d = axis == 0 ? pickDir.x : axis == 1 ? pickDir.y : pickDir.z;
            final float lo = axis == 0 ? boundsMin.x : axis == 1 ? boundsMin.y : boundsMin.z;
            final float hi = axis == 0 ? boundsMax.x : axis == 1 ? boundsMax.y : boundsMax.z;
            if (Math.abs(d) < 1e-7f) {
                if (o < lo || o > hi) return Double.POSITIVE_INFINITY;
                continue;
            }
            double t1 = (lo - o) / d, t2 = (hi - o) / d;
            if (t1 > t2) { final double t = t1; t1 = t2; t2 = t; }
            if (t1 > tmin) tmin = t1;
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return Double.POSITIVE_INFINITY;
        }
        if (tmax < 0) return Double.POSITIVE_INFINITY;
        return Math.max(0, tmin);
    }
}
