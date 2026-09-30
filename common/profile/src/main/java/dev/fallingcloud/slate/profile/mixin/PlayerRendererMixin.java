package dev.fallingcloud.slate.profile.mixin;

import dev.fallingcloud.slate.profile.client.render.CosmeticsLayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every player renderer (there is one for each of the two models) gets one layer more: what a look wears in 3D.
 * Done here and not through a loader's own event so that the shared code has one way for both loaders.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private PlayerRendererMixin(final EntityRendererProvider.Context context, final PlayerModel<AbstractClientPlayer> model, final float shadow) {
        super(context, model, shadow);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void slate$cosmetics(final EntityRendererProvider.Context context, final boolean slim, final CallbackInfo ci) {
        this.addLayer(new CosmeticsLayer(this));
    }
}
