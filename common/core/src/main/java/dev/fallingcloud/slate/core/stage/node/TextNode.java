package dev.fallingcloud.slate.core.stage.node;

import dev.fallingcloud.slate.core.stage.StageRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Quaternionf;

/**
 * Text in 3D: a nameplate that faces the camera (billboard) or a flat label lying in the node's local XY plane
 * facing +Z. Sized in blocks per text pixel ({@code 0.025} = vanilla nameplates), with the usual nameplate
 * background and optional see-through mode.
 */
public class TextNode extends StageNode {

    private Component text;
    private FormattedCharSequence sequence;
    private float size = 0.025f;
    private boolean billboard = true;
    private boolean seeThrough;
    private boolean shadow;
    private int color = 0xFFFFFFFF;
    private int background = 0x40000000;
    private final Quaternionf facing = new Quaternionf();

    public TextNode(final Component text) {
        this.text = text;
        this.sequence = text.getVisualOrderText();
        pickable(false);
        named(text);
        updateBounds();
    }

    public TextNode text(final Component t) { this.text = t; this.sequence = t.getVisualOrderText(); named(t); updateBounds(); return this; }

    public Component text() { return text; }

    /** Blocks per text pixel. */
    public TextNode size(final float blocksPerPixel) { this.size = blocksPerPixel; updateBounds(); return this; }

    public TextNode billboard(final boolean on) { this.billboard = on; return this; }

    public TextNode seeThrough(final boolean on) { this.seeThrough = on; return this; }

    public TextNode shadow(final boolean on) { this.shadow = on; return this; }

    public TextNode color(final int argb) { this.color = argb; return this; }

    /** Background plate colour (ARGB; 0 = none). */
    public TextNode background(final int argb) { this.background = argb; return this; }

    private void updateBounds() {
        final Font font = Minecraft.getInstance().font;
        final float w = font.width(sequence) * size, h = font.lineHeight * size;
        bounds(-w / 2f, -h / 2f, -0.05f, w / 2f, h / 2f, 0.05f);
    }

    @Override
    protected void draw(final StageRenderContext ctx) {
        final Font font = Minecraft.getInstance().font;
        ctx.pose.pushPose();
        if (billboard) {
            // Undo the camera rotation (and the node's) so the text plane faces the viewer.
            ctx.pose.last().pose().getNormalizedRotation(facing);
            facing.conjugate();
            ctx.pose.mulPose(facing);
        }
        // Font space is x right, y down: one flip turns it into local x right, y up (and keeps the glyph winding front-facing).
        ctx.pose.scale(size, -size, size);
        final float x = -font.width(sequence) / 2f;
        final float y = -font.lineHeight / 2f;
        final int c = alpha < 1f ? (Math.round(((color >>> 24) & 0xFF) * alpha) << 24) | (color & 0xFFFFFF) : color;
        final int bg = alpha < 1f ? (Math.round(((background >>> 24) & 0xFF) * alpha) << 24) | (background & 0xFFFFFF) : background;
        final Font.DisplayMode mode = seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        if (seeThrough) {
            font.drawInBatch(sequence, x, y, c, shadow, ctx.pose.last().pose(), ctx.buffers, mode, bg, ctx.light);
            font.drawInBatch(sequence, x, y, c, shadow, ctx.pose.last().pose(), ctx.buffers, Font.DisplayMode.NORMAL, 0, ctx.light);
        } else {
            font.drawInBatch(sequence, x, y, c, shadow, ctx.pose.last().pose(), ctx.buffers, mode, bg, ctx.light);
        }
        ctx.pose.popPose();
    }
}
