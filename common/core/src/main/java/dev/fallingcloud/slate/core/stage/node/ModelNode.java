package dev.fallingcloud.slate.core.stage.node;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.StageResources;
import dev.fallingcloud.slate.core.stage.mesh.StageModels;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * A JSON block/item model from resources (a Blockbench Java-model export), baked by {@link StageModels} and drawn
 * quad by quad with the stage's per-face shade. The model's 16-unit cube maps to one block centred on the node
 * (-0.5..0.5 on X/Z, 0..1 on Y). Optional spin around Y, e.g. a slowly turning cogwheel.
 */
public class ModelNode extends StageNode {

    private final ResourceLocation modelId;
    @Nullable private BakedModel model;
    private int resources = -1;
    private RenderType layer = RenderType.cutoutMipped();
    private float spinDegPerSec;
    private float spin;
    private float spinBoost = 1f;
    private final RandomSource random = RandomSource.create(7L);

    public ModelNode(final ResourceLocation modelId) {
        this.modelId = modelId;
        named(modelId.getPath());
    }

    /** The chunk layer to draw with ({@code solid}, {@code cutout}, {@code cutoutMipped}, {@code translucent}). */
    public ModelNode layer(final RenderType type) { this.layer = type; return this; }

    /** Degrees per second around Y. */
    public ModelNode spin(final float degPerSec) { this.spinDegPerSec = degPerSec; return this; }

    /** Temporary speed multiplier (hover effects); decays back to 1 by itself. */
    public ModelNode boostSpin(final float factor) { this.spinBoost = factor; return this; }

    public float spinAngle() { return spin; }

    @Override
    public void update(final StageRenderContext ctx) {
        if (Theme.current().motion() > 0f) {
            spin = (spin + spinDegPerSec * spinBoost * ctx.deltaMs / 1000f) % 360f;
            if (spinBoost != 1f) spinBoost += (1f - spinBoost) * (1f - (float) Math.exp(-ctx.deltaMs / 400f));
        }
    }

    @Override
    protected void draw(final StageRenderContext ctx) {
        final int gen = StageResources.generation();
        if (model == null || gen != resources) {
            model = StageModels.get(modelId);
            resources = gen;
        }
        if (model == null) return;
        ctx.pose.pushPose();
        if (spin != 0f) ctx.pose.mulPose(Axis.YP.rotationDegrees(spin));
        ctx.pose.translate(-0.5f, 0f, -0.5f);
        final VertexConsumer vc = ctx.buffers.getBuffer(alpha < 1f ? RenderType.translucentMovingBlock() : layer);
        for (final Direction d : Direction.values()) {
            random.setSeed(42L);
            emit(ctx, vc, model.getQuads(null, d, random));
        }
        random.setSeed(42L);
        emit(ctx, vc, model.getQuads(null, null, random));
        ctx.pose.popPose();
    }

    private void emit(final StageRenderContext ctx, final VertexConsumer vc, final List<BakedQuad> quads) {
        for (final BakedQuad q : quads) {
            final float s = ctx.lighting.shade(q.getDirection(), q.isShade());
            vc.putBulkData(ctx.pose.last(), q, s, s, s, 1f, ctx.light, OverlayTexture.NO_OVERLAY);
        }
    }
}
