package dev.fallingcloud.slate.multiplayer;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.action.ActionType;
import dev.fallingcloud.slate.core.module.SlateModule;
import dev.fallingcloud.slate.core.net.Flow;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.multiplayer.hub.SocialHub;
import dev.fallingcloud.slate.multiplayer.social.SocialPayload;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Slate Multiplayer module: Slate's social layer. Both sides: the server half runs a {@link SocialHub}
 * (friends, presence, DMs, groups, invites, screen-share routing, optional TCP listener); the client half
 * ({@code client.MultiplayerClient}) keeps the local model, the link to a hub, the Friends hub screen, the
 * layout elements and the screen-share/voice integration. Client classes are only ever referenced from
 * client-side entry points so a dedicated server never resolves them.
 */
public final class SlateMultiplayer implements SlateModule {

    public static final String MOD_ID = "slate_multiplayer";
    public static final Logger LOGGER = LoggerFactory.getLogger("Slate Multiplayer");
    public static final SlateMultiplayer MODULE = new SlateMultiplayer();

    private SlateMultiplayer() {}

    public static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override public String id() { return MOD_ID; }

    @Override public Component displayName() { return Component.translatable("slate_multiplayer.name"); }

    @Override public Icon icon() { return Icon.FRIENDS; }

    @Override
    public void init() {
        Slate.init();
        MultiplayerConfigs.serverFile();
        SlateNetwork.get().register(SocialPayload.TYPE, SocialPayload.CODEC, Flow.BOTH, (payload, ctx) -> {
            if (ctx.isClient()) ClientSide.onPayload(payload);
            else SocialHub.onPayload(payload.message(), ctx.sender());
        });
        SocialHub.registerBlobRouters();
        SlateEvents.SERVER_STARTED.register(SocialHub::onServerStarted);
        SlateEvents.SERVER_STOPPING.register(SocialHub::onServerStopping);
        SlateEvents.SERVER_TICK_END.register(SocialHub::onServerTick);
        SlateEvents.PLAYER_LEFT.register(SocialHub::onPlayerLeft);
        LOGGER.info("[Slate Multiplayer] init");
    }

    @Override
    public void initClient() {
        ClientSide.init();
    }

    @Override
    public List<HubEntry> hubEntries() {
        return SlatePlatform.get().isClient() ? ClientSide.hubEntries() : List.of();
    }

    @Override
    public List<ActionType> actions() {
        return SlatePlatform.get().isClient() ? ClientSide.actions() : List.of();
    }

    /** Client-only references live here so a dedicated server never resolves them. */
    private static final class ClientSide {
        static void init() { dev.fallingcloud.slate.multiplayer.client.MultiplayerClient.init(); }
        static void onPayload(final SocialPayload p) { dev.fallingcloud.slate.multiplayer.client.MultiplayerClient.onPayload(p.message()); }
        static List<HubEntry> hubEntries() { return dev.fallingcloud.slate.multiplayer.client.MultiplayerClient.hubEntries(); }
        static List<ActionType> actions() { return dev.fallingcloud.slate.multiplayer.client.MultiplayerClient.actions(); }
    }
}
