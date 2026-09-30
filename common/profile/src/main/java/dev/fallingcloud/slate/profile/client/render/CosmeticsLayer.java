package dev.fallingcloud.slate.profile.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.fallingcloud.slate.profile.client.AppliedLooks;
import dev.fallingcloud.slate.profile.client.Cosmetic;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * In the world: what a player's look wears in 3D, drawn onto the player as the game draws them. A layer of the
 * player renderer like the game's own (armour, the cape, what is held), added to both player renderers by
 * {@code mixin.PlayerRendererMixin}. A player without a look costs one lookup and nothing else.
 */
public final class CosmeticsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public CosmeticsLayer(final RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(final PoseStack pose, final MultiBufferSource buffers, final int light, final AbstractClientPlayer player,
                       final float limbSwing, final float limbSwingAmount, final float partialTick, final float age, final float headYaw, final float headPitch) {
        final AppliedLooks.Applied look = AppliedLooks.of(player.getUUID());
        if (look == null || look.models().isEmpty() || player.isInvisible()) return;
        final int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
        for (final Cosmetic c : look.models()) {
            CosmeticRenderer.draw(pose, getParentModel(), c, age, light, overlay, 0xFFFFFFFF,
                cosmetic -> buffers.getBuffer(cosmetic.translucent() ? RenderType.entityTranslucent(cosmetic.texture()) : RenderType.entityCutoutNoCull(cosmetic.texture())));
        }
    }
}
