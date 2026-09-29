package dev.fallingcloud.slate.core.stage.node;

import com.mojang.math.Axis;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * An item stack drawn through the item renderer with a display context ({@code FIXED} by default: the item-frame
 * look, 1 block wide, centred half a block above the node's position). Optional spin and bob for a "pickup" feel.
 */
public class ItemNode extends StageNode {

    private ItemStack stack;
    private ItemDisplayContext context = ItemDisplayContext.FIXED;
    private float spinDegPerSec;
    private float bobBlocks;
    private float spin;
    private float size = 1f;

    public ItemNode(final ItemStack stack) {
        this.stack = stack;
        bounds(-0.4f, 0.1f, -0.4f, 0.4f, 0.9f, 0.4f);
        hoverFeel(0.06f, 1.08f);
        named(stack.getHoverName());
    }

    public ItemNode stack(final ItemStack s) { this.stack = s; named(s.getHoverName()); return this; }

    public ItemStack stack() { return stack; }

    public ItemNode context(final ItemDisplayContext c) { this.context = c; return this; }

    /** Degrees per second around Y (0 = still). */
    public ItemNode spin(final float degPerSec) { this.spinDegPerSec = degPerSec; return this; }

    /** Vertical bob amplitude in blocks (0 = none). */
    public ItemNode bob(final float blocks) { this.bobBlocks = blocks; return this; }

    /** Size in blocks (1 = the item frame size). */
    public ItemNode size(final float blocks) {
        this.size = blocks;
        bounds(-0.4f * blocks, 0.5f - 0.4f * blocks, -0.4f * blocks, 0.4f * blocks, 0.5f + 0.4f * blocks, 0.4f * blocks);
        return this;
    }

    @Override
    public void update(final StageRenderContext ctx) {
        if (Theme.current().motion() > 0f) spin = (spin + spinDegPerSec * ctx.deltaMs / 1000f) % 360f;
    }

    @Override
    protected void draw(final StageRenderContext ctx) {
        if (stack.isEmpty()) return;
        final float bob = bobBlocks > 0f && Theme.current().motion() > 0f ? Mth.sin(ctx.seconds() * 2.2f) * bobBlocks : 0f;
        ctx.pose.pushPose();
        ctx.pose.translate(0f, 0.5f + bob, 0f);
        if (spin != 0f) ctx.pose.mulPose(Axis.YP.rotationDegrees(spin));
        if (size != 1f) ctx.pose.scale(size, size, size);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, context, ctx.light, OverlayTexture.NO_OVERLAY, ctx.pose, ctx.buffers, ctx.level, 0);
        ctx.pose.popPose();
    }
}
