package dev.fallingcloud.slate.core.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.fallingcloud.slate.core.client.SlateClient;
import dev.fallingcloud.slate.core.client.render.SlateRenderEvents;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.event.SlateKeys;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/** Fabric client entry point of Slate core. */
public final class SlateFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SlateClient.init();
        SlateKeys.install(KeyBindingHelper::registerKeyBinding);
        FabricNetwork.installClient();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            SlateEvents.CLIENT_TICK_END.invoke(Runnable::run);
            FabricKeyInput.tick(mc);
        });
        HudRenderCallback.EVENT.register((g, tracker) -> SlateClient.onHudRendered(g, tracker.getGameTimeDeltaPartialTick(false)));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> SlateEvents.CLIENT_JOINED_SERVER.invoke(Runnable::run));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> SlateEvents.CLIENT_LEFT_SERVER.invoke(Runnable::run));
        // World-render hook (see SlateRenderEvents for the matrix contract). positionMatrix() is the view rotation that
        // vanilla already pushed onto RenderSystem's model-view; the level pose stack is identity at this stage.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
            if (!SlateRenderEvents.hasAfterTranslucentListeners()) return;
            final PoseStack pose = ctx.matrixStack() != null ? ctx.matrixStack() : new PoseStack();
            SlateRenderEvents.fireAfterTranslucent(new SlateRenderEvents.WorldRenderContext(pose, ctx.positionMatrix(),
                ctx.projectionMatrix(), ctx.camera(), ctx.tickCounter().getGameTimeDeltaPartialTick(false)));
        });
    }
}
