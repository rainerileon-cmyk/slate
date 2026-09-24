package dev.fallingcloud.slate.core.neoforge;

import dev.fallingcloud.slate.core.client.CoreSettingsScreen;
import dev.fallingcloud.slate.core.client.SlateClient;
import dev.fallingcloud.slate.core.client.render.SlateRenderEvents;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.event.SlateKeys;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Client-side NeoForge wiring; only loaded on the client dist. */
final class SlateNeoForgeClient {

    static void init(final IEventBus modBus, final ModContainer container) {
        SlateClient.init();
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> SlateKeys.install(e::register));
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new CoreSettingsScreen(parent));

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> SlateEvents.CLIENT_TICK_END.invoke(Runnable::run));
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post.class, e ->
            SlateClient.onHudRendered(e.getGuiGraphics(), e.getPartialTick().getGameTimeDeltaPartialTick(false)));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> SlateEvents.CLIENT_JOINED_SERVER.invoke(Runnable::run));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> SlateEvents.CLIENT_LEFT_SERVER.invoke(Runnable::run));
        NeoForge.EVENT_BUS.addListener(InputEvent.Key.class, e -> {
            if (e.getAction() != 1 || Minecraft.getInstance().screen != null) return;
            SlateEvents.KEY_PRESSED.invokeUntilConsumed(l -> l.onKey(e.getKey(), e.getScanCode(), e.getModifiers()));
        });
        // World-render hook (see SlateRenderEvents for the matrix contract). The pose stack is a fresh identity one at this
        // stage; the camera rotation already sits in RenderSystem's model-view.
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, e -> {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !SlateRenderEvents.hasAfterTranslucentListeners()) return;
            SlateRenderEvents.fireAfterTranslucent(new SlateRenderEvents.WorldRenderContext(e.getPoseStack(), e.getModelViewMatrix(),
                e.getProjectionMatrix(), e.getCamera(), e.getPartialTick().getGameTimeDeltaPartialTick(false)));
        });
    }

    private SlateNeoForgeClient() {}
}
