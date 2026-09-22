package dev.fallingcloud.slate.core.fabric;

import dev.fallingcloud.slate.core.client.SlateClient;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.event.SlateKeys;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

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
    }
}
