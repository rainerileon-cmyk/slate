package dev.fallingcloud.slate.core.stage.node;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.StageSoft;
import dev.fallingcloud.slate.core.stage.mesh.PropQuads;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The Overhaul layout's chest: Minecraft's chest, to the pixel the same size, in the stylized look of a trailer. Its
 * texture is Slate's own ({@code textures/stage/chest.png}, painted by {@code tools/overhaul/StageTextures.java}:
 * flat warm planks in a dark frame), it is lit by the soft stage shader, and it is hollow: the base has an inside
 * one looks into, and the lid a recessed panel framed by its own walls. That panel is what faces the viewer when
 * the chest stands open, and what a scene puts things on.
 *
 * <p>Drawn centred like a block: -0.5..0.5 on X and Z (the chest itself is 14 pixels wide), 0..1 on Y, its front
 * towards +Z. The lid swings on the back edge with a little overshoot and opens a touch past upright
 * ({@link #lidAngle}), so its panel looks at a camera that looks slightly down.</p>
 */
public class ChestNode extends StageNode {

    public static final ResourceLocation TEXTURE = Slate.id("textures/stage/chest.png");
    private static final int TEX = 64;
    /** The hinge: along X, at the top of the base's back edge (pixels). */
    private static final float HINGE_Y = 9f, HINGE_Z = -7f;

    private final Anim lid = new Anim(0, 640, Ease.OUT_BACK);
    private float lidAngle = 97f;
    private boolean open;

    public ChestNode() {
        bounds(-0.4375f, 0f, -0.4375f, 0.4375f, 0.875f, 0.4375f);
        hoverFeel(0f, 1f);
        named("chest");
    }

    /** Swings the lid open or shut. */
    public ChestNode open(final boolean open) {
        this.open = open;
        lid.set(open);
        return this;
    }

    /** The lid is open (or shut) at once, without the swing. */
    public ChestNode openNow(final boolean open) {
        this.open = open;
        lid.snap(open ? 1f : 0f);
        return this;
    }

    public boolean isOpen() { return open; }

    /** 0 = shut, 1 = open; a little over 1 while the lid swings past and comes back. */
    public float openness() { return lid.get(); }

    /** How far the lid opens, in degrees (90 = upright). */
    public ChestNode lidAngle(final float degrees) { this.lidAngle = degrees; return this; }

    public float lidAngle() { return lidAngle; }

    @Override
    protected void draw(final StageRenderContext ctx) {
        final float a = alpha();
        if (ctx.soft) {
            final BufferBuilder bb = StageSoft.begin(StageSoft.ENTITY);
            emit(ctx, bb, a);
            StageSoft.end(bb, StageSoft.ENTITY, TEXTURE, a);
        } else {
            emit(ctx, ctx.buffers.getBuffer(a < 1f ? RenderType.entityTranslucent(TEXTURE) : RenderType.entityCutout(TEXTURE)), a);
        }
    }

    private void emit(final StageRenderContext ctx, final VertexConsumer out, final float alpha) {
        final PropQuads base = new PropQuads(out, ctx.pose.last(), TEX, TEX, alpha);
        // The base, outside: darker towards the floor it stands on.
        base.face(-7, 0, 7, 14, 0, 0, 0, 9, 0, 0, 19, 14, 28, 0.78f, 1f);          // front
        base.face(7, 0, -7, -14, 0, 0, 0, 9, 0, 14, 19, 28, 28, 0.78f, 1f);        // back
        base.face(7, 0, 7, 0, 0, -14, 0, 9, 0, 14, 19, 28, 28, 0.78f, 1f);         // right
        base.face(-7, 0, -7, 0, 0, 14, 0, 9, 0, 14, 19, 28, 28, 0.78f, 1f);        // left
        base.face(-7, 0, -7, 14, 0, 0, 0, 0, 14, 42, 0, 56, 14, 0.6f, 0.6f);       // underside
        // The rim, as four strips round the opening.
        base.face(-7, 9, 7, 14, 0, 0, 0, 0, -1, 28, 13, 42, 14);
        base.face(-7, 9, -6, 14, 0, 0, 0, 0, -1, 28, 0, 42, 1);
        base.face(-7, 9, 6, 1, 0, 0, 0, 0, -12, 28, 1, 29, 13);
        base.face(6, 9, 6, 1, 0, 0, 0, 0, -12, 41, 1, 42, 13);
        // The inside: four walls looking inwards, darker the deeper they go, and the floor.
        base.face(6, 1, 6, -12, 0, 0, 0, 8, 0, 28, 14, 40, 22, 0.7f, 1f);
        base.face(-6, 1, -6, 12, 0, 0, 0, 8, 0, 28, 14, 40, 22, 0.7f, 1f);
        base.face(-6, 1, 6, 0, 0, -12, 0, 8, 0, 28, 14, 40, 22, 0.7f, 1f);
        base.face(6, 1, -6, 0, 0, 12, 0, 8, 0, 28, 14, 40, 22, 0.7f, 1f);
        base.face(-6, 1, 6, 12, 0, 0, 0, 0, -12, 29, 1, 41, 13, 0.8f, 0.8f);

        // The lid, in its own space: the hinge is the origin, Y up from the base's top, Z from the back edge forwards.
        ctx.pose.pushPose();
        ctx.pose.translate(0f, HINGE_Y / 16f, HINGE_Z / 16f);
        ctx.pose.mulPose(Axis.XP.rotationDegrees(-lidAngle * lid.get()));
        final PropQuads top = new PropQuads(out, ctx.pose.last(), TEX, TEX, alpha);
        top.face(-7, 5, 14, 14, 0, 0, 0, 0, -14, 0, 0, 14, 14);                     // outside, top
        top.face(-7, 0, 14, 14, 0, 0, 0, 5, 0, 0, 14, 14, 19);                      // front
        top.face(7, 0, 0, -14, 0, 0, 0, 5, 0, 14, 14, 28, 19);                      // back
        top.face(7, 0, 14, 0, 0, -14, 0, 5, 0, 14, 14, 28, 19);                     // right
        top.face(-7, 0, 0, 0, 0, 14, 0, 5, 0, 14, 14, 28, 19);                      // left
        // Its rim, looking down when shut.
        top.face(-7, 0, 0, 14, 0, 0, 0, 0, 1, 14, 0, 28, 1);
        top.face(-7, 0, 13, 14, 0, 0, 0, 0, 1, 14, 13, 28, 14);
        top.face(-7, 0, 1, 1, 0, 0, 0, 0, 12, 14, 1, 15, 13);
        top.face(6, 0, 1, 1, 0, 0, 0, 0, 12, 27, 1, 28, 13);
        // The walls of its hollow (light at the opening, shade at the panel), and the panel itself.
        top.face(6, 0, 13, -12, 0, 0, 0, 4, 0, 40, 18, 52, 14, 1f, 0.84f);
        top.face(-6, 0, 1, 12, 0, 0, 0, 4, 0, 40, 18, 52, 14, 1f, 0.84f);
        top.face(-6, 0, 13, 0, 0, -12, 0, 4, 0, 40, 18, 52, 14, 1f, 0.84f);
        top.face(6, 0, 1, 0, 0, 12, 0, 4, 0, 40, 18, 52, 14, 1f, 0.84f);
        top.face(-6, 4, 1, 12, 0, 0, 0, 0, 12, 15, 1, 27, 13, 0.94f, 0.94f);
        // The latch, hanging from the lid's front over the seam.
        top.face(-1, -2, 15, 2, 0, 0, 0, 4, 0, 28, 22, 30, 26);
        top.face(1, -2, 15, 0, 0, -1, 0, 4, 0, 30, 22, 31, 26);
        top.face(-1, -2, 14, 0, 0, 1, 0, 4, 0, 30, 22, 31, 26);
        top.face(-1, 2, 15, 2, 0, 0, 0, 0, -1, 31, 22, 33, 23);
        top.face(-1, -2, 14, 2, 0, 0, 0, 0, 1, 31, 22, 33, 23, 0.8f, 0.8f);
        ctx.pose.popPose();
    }
}
