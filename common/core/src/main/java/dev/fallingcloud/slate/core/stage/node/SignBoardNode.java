package dev.fallingcloud.slate.core.stage.node;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.StageSoft;
import dev.fallingcloud.slate.core.stage.mesh.PropQuads;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The board of a wall sign in the Overhaul layout's stylized look: pale planks in a darker edge, lit by the soft
 * stage shader. The size of Minecraft's sign board, a block wide and half a block high, a good pixel thick. What is
 * written on it is a {@link TextNode} laid on its face, so a scene sets the size and the colour of the writing.
 *
 * <p>The node's origin is the middle of the board's lower back edge: put it against a wall (or a chest) and the
 * board hangs on it, its face towards +Z at {@link #FACE_Z}.</p>
 */
public class SignBoardNode extends StageNode {

    public static final ResourceLocation TEXTURE = Slate.id("textures/stage/sign.png");
    /** How thick the board is, in pixels. */
    private static final float THICK = 1.25f;
    /** Where the face lies, in blocks from the node's origin. */
    public static final float FACE_Z = THICK / 16f;
    /** The middle of the face, in blocks above the node's origin. */
    public static final float FACE_MID_Y = 0.25f;

    public SignBoardNode() {
        bounds(-0.5f, 0f, 0f, 0.5f, 0.5f, FACE_Z);
        hoverFeel(0f, 1f);
        named("sign");
    }

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

    private static void emit(final StageRenderContext ctx, final VertexConsumer out, final float alpha) {
        final PropQuads q = new PropQuads(out, ctx.pose.last(), 32, 16, alpha);
        q.face(-8, 0, THICK, 16, 0, 0, 0, 8, 0, 0, 0, 16, 8, 0.92f, 1f);            // face
        q.face(8, 0, 0, -16, 0, 0, 0, 8, 0, 16, 0, 32, 8, 0.8f, 0.8f);              // back
        q.face(-8, 8, THICK, 16, 0, 0, 0, 0, -THICK, 0, 8, 16, 9);                   // top edge
        q.face(-8, 0, 0, 16, 0, 0, 0, 0, THICK, 0, 9, 16, 10, 0.75f, 0.75f);         // lower edge
        q.face(8, 0, THICK, 0, 0, -THICK, 0, 8, 0, 0, 8, 1, 10, 0.85f, 0.95f);       // right edge
        q.face(-8, 0, 0, 0, 0, THICK, 0, 8, 0, 0, 8, 1, 10, 0.85f, 0.95f);           // left edge
    }
}
