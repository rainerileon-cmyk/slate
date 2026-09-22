package dev.fallingcloud.slate.core.neoforge;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.event.SlateEvents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NeoForge entry point of Slate core. Feeds loader events into {@link SlateEvents}. */
@Mod(Slate.MOD_ID)
public final class SlateNeoForge {

    public SlateNeoForge(final IEventBus modBus, final ModContainer container) {
        Slate.init();
        modBus.addListener(RegisterPayloadHandlersEvent.class, NeoForgeNetwork::flush);

        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, e -> SlateEvents.SERVER_STARTED.invoke(l -> l.accept(e.getServer())));
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, e -> SlateEvents.SERVER_STOPPING.invoke(l -> l.accept(e.getServer())));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> SlateEvents.SERVER_TICK_END.invoke(l -> l.accept(e.getServer())));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) SlateEvents.PLAYER_JOINED.invoke(l -> l.accept(p));
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) SlateEvents.PLAYER_LEFT.invoke(l -> l.accept(p));
        });

        if (FMLEnvironment.dist.isClient()) SlateNeoForgeClient.init(modBus, container);
    }
}
