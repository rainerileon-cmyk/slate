package dev.fallingcloud.slate.core.fabric;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.event.SlateEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** Fabric entry point (both sides) of Slate core. */
public final class SlateFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Slate.init();
        ServerLifecycleEvents.SERVER_STARTED.register(s -> SlateEvents.SERVER_STARTED.invoke(l -> l.accept(s)));
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> SlateEvents.SERVER_STOPPING.invoke(l -> l.accept(s)));
        ServerTickEvents.END_SERVER_TICK.register(s -> SlateEvents.SERVER_TICK_END.invoke(l -> l.accept(s)));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SlateEvents.PLAYER_JOINED.invoke(l -> l.accept(handler.getPlayer())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SlateEvents.PLAYER_LEFT.invoke(l -> l.accept(handler.getPlayer())));
    }
}
