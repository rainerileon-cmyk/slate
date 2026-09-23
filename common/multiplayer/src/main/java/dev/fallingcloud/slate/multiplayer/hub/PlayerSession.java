package dev.fallingcloud.slate.multiplayer.hub;

import dev.fallingcloud.slate.core.net.SlateNetwork;
import dev.fallingcloud.slate.multiplayer.MultiplayerServerConfig;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;
import dev.fallingcloud.slate.multiplayer.social.SocialPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** A player on this server, reached through the {@code slate:social} payload channel. */
final class PlayerSession extends AbstractHubSession {

    private ServerPlayer player;
    private boolean closed;

    PlayerSession(final ServerPlayer player, final MultiplayerServerConfig cfg) {
        super(player.getUUID(), player.getGameProfile().getName(), cfg);
        this.player = player;
    }

    ServerPlayer player() { return player; }

    /** Vanilla hands out a new ServerPlayer on respawn / dimension change; the session follows the uuid. */
    void setPlayer(final ServerPlayer p) { this.player = p; }

    @Override
    public void send(final SocialMessage message) {
        if (closed || player.hasDisconnected()) return;
        SlateNetwork.get().sendToPlayer(player, new SocialPayload(message));
    }

    @Override
    public void sendBlob(final CustomPacketPayload blobPayload) {
        if (closed || player.hasDisconnected()) return;
        SlateNetwork.get().sendToPlayer(player, blobPayload);
    }

    @Override public boolean isPayload() { return true; }

    @Override public boolean isOpen() { return !closed && !player.hasDisconnected(); }

    @Override
    public void close(final String reason) {
        if (closed) return;
        // The player stays on the server; only the social session ends.
        send(new SocialMessage.Error(SocialMessage.Error.REPLACED, reason, "session"));
        closed = true;
    }
}
