package dev.fallingcloud.slate.core.stage;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The light of a stage: one fixed sun direction plus ambient, and full block/sky light for the lightmap.
 *
 * <p>Two things consume it. Block meshes bake a per-face shade at mesh time (vanilla's UP 1.0 / DOWN 0.5 / N-S 0.8 /
 * E-W 0.6 blended with a Lambert term from the sun, see {@link #shade}), so changing the sun bumps {@link #generation()}
 * and meshes rebuild lazily. Entities, items and block entities use the two shader light directions, which
 * {@link #applyShaderLights} derives from the sun in view space every frame (the vanilla entity shader adds a fixed
 * 0.4 ambient itself, so {@link #ambient} only shapes the block shade and the overall tint).</p>
 */
public final class StageLighting {

    private final Vector3f sun = new Vector3f(0.35f, 1.0f, 0.55f).normalize();
    private float ambient = 0.45f;
    /** How much the sun direction bends the vanilla per-face shade (0 = vanilla constants, 1 = pure Lambert). */
    private float sunInfluence = 0.55f;
    private int blockLight = 15;
    private int skyLight = 15;
    private float brightness = 1f;
    private int generation;

    // The soft rig: what the stylized shader (StageSoft) lights with. Colours are linear multipliers, not 0..255.
    private final Vector3f keyPos = new Vector3f(2.6f, 5.2f, 6.5f);
    private final Vector3f keyColor = new Vector3f(0.6f, 0.555f, 0.48f);
    private final Vector3f fillDir = new Vector3f(-1f, 0.25f, 0.2f).normalize();
    private final Vector3f fillColor = new Vector3f(0.11f, 0.14f, 0.2f);
    private final Vector3f ambientColor = new Vector3f(0.37f, 0.35f, 0.36f);
    private final Vector3f rimColor = new Vector3f(1f, 0.9f, 0.76f);
    private float rimStrength = 0.2f;
    private float wrap = 0.45f;

    // Scratch vectors (no per-frame allocation).
    private final Vector3f light0 = new Vector3f();
    private final Vector3f light1 = new Vector3f();
    private final Vector3f fill = new Vector3f();
    private static final Vector3f[] DIR = new Vector3f[6];

    static {
        for (final Direction d : Direction.values()) DIR[d.ordinal()] = new Vector3f(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    public StageLighting sun(final float x, final float y, final float z) {
        sun.set(x, y, z);
        if (sun.lengthSquared() < 1e-6f) sun.set(0, 1, 0);
        sun.normalize();
        generation++;
        return this;
    }

    public Vector3f sun() { return sun; }

    /** 0..1: the floor of the per-face block shade. */
    public StageLighting ambient(final float a) { this.ambient = Mth.clamp(a, 0f, 1f); generation++; return this; }

    public float ambient() { return ambient; }

    public StageLighting sunInfluence(final float f) { this.sunInfluence = Mth.clamp(f, 0f, 1f); generation++; return this; }

    /** Lightmap coordinates for everything on the stage (0..15 each). Full by default. */
    public StageLighting light(final int block, final int sky) {
        this.blockLight = Mth.clamp(block, 0, 15);
        this.skyLight = Mth.clamp(sky, 0, 15);
        generation++;
        return this;
    }

    public int blockLight() { return blockLight; }

    public int skyLight() { return skyLight; }

    /** Packed lightmap value handed to renderers. */
    public int packedLight() { return skyLight << 20 | blockLight << 4; }

    /** A global multiplier applied through the shader colour while the stage renders (1 = neutral). */
    public StageLighting brightness(final float b) { this.brightness = Mth.clamp(b, 0f, 2f); return this; }

    public float brightness() { return brightness; }

    // ------------------------------------------------------------------ the soft rig

    /** Where the key light hangs, in scene blocks: a lamp, so faces are lit across, not flat. */
    public StageLighting key(final float x, final float y, final float z) { keyPos.set(x, y, z); return this; }

    public StageLighting keyColor(final float r, final float g, final float b) { keyColor.set(r, g, b); return this; }

    /** The direction the fill light comes from, and its colour (keep it weak and cool against a warm key). */
    public StageLighting fill(final float x, final float y, final float z, final float r, final float g, final float b) {
        fillDir.set(x, y, z);
        if (fillDir.lengthSquared() < 1e-6f) fillDir.set(-1f, 0f, 0f);
        fillDir.normalize();
        fillColor.set(r, g, b);
        return this;
    }

    public StageLighting ambientColor(final float r, final float g, final float b) { ambientColor.set(r, g, b); return this; }

    /** The line of light along edges that turn away from the viewer. */
    public StageLighting rim(final float r, final float g, final float b, final float strength) {
        rimColor.set(r, g, b);
        rimStrength = Math.max(0f, strength);
        return this;
    }

    /** How far the key light reaches round a form (0 = a hard terminator, 1 = nearly all the way). */
    public StageLighting wrap(final float w) { this.wrap = Mth.clamp(w, 0f, 1f); return this; }

    public Vector3f keyPos() { return keyPos; }

    public Vector3f keyColor() { return keyColor; }

    public Vector3f fillDir() { return fillDir; }

    public Vector3f fillColor() { return fillColor; }

    public Vector3f ambientColor() { return ambientColor; }

    public Vector3f rimColor() { return rimColor; }

    public float rimStrength() { return rimStrength; }

    public float wrap() { return wrap; }

    /** Bumped whenever something baked into meshes changes. */
    public int generation() { return generation; }

    /** The per-face shade a {@code BlockAndTintGetter} hands to the block mesher ({@code getShade}). */
    public float shade(final Direction direction, final boolean shade) {
        if (!shade) return 1f;
        final float vanilla = switch (direction) {
            case DOWN -> 0.5f;
            case UP -> 1.0f;
            case NORTH, SOUTH -> 0.8f;
            default -> 0.6f;
        };
        final float lambert = Mth.clamp(ambient + (1f - ambient) * Math.max(0f, DIR[direction.ordinal()].dot(sun)), 0.3f, 1f);
        return Mth.lerp(sunInfluence, vanilla, lambert);
    }

    /**
     * Sets the two shader light directions from the sun, transformed into view space by the camera's view matrix
     * (the vanilla entity shaders light normals in view space). The fill light is a dim bounce from the opposite side.
     */
    public void applyShaderLights(final Matrix4f view) {
        light0.set(sun);
        view.transformDirection(light0).normalize();
        fill.set(-sun.x, 0.35f, -sun.z);
        if (fill.lengthSquared() < 1e-6f) fill.set(0, 1, 0);
        fill.normalize().mul(0.45f);
        light1.set(fill);
        view.transformDirection(light1);
        RenderSystem.setShaderLights(light0, light1);
    }
}
