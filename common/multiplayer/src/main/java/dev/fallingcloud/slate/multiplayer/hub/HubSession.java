package dev.fallingcloud.slate.multiplayer.hub;

import dev.fallingcloud.slate.multiplayer.social.PlayerRef;
import dev.fallingcloud.slate.multiplayer.social.Presence;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * One authenticated client of the hub, however it is connected: a player on this server (payload
 * channel) or a TCP client (title screen / another server). The hub only ever talks to this interface.
 */
public interface HubSession {

    UUID uuid();

    String name();

    default PlayerRef ref() { return new PlayerRef(uuid(), name()); }

    void send(SocialMessage message);

    /** Deliver a Core blob payload (Start/Chunk/End); the session picks the transport. */
    void sendBlob(CustomPacketPayload blobPayload);

    /** True when this is a player on this server using the in-game channel. */
    boolean isPayload();

    boolean isOpen();

    /** Close from the hub's side (replaced by a newer session, kicked, shutdown). */
    void close(String reason);

    /** Backpressure hint: false when the transport has more queued than it can drain (drop frames). */
    default boolean canTakeMore() { return true; }

    // ---- per-session mutable state kept by the hub (implemented in AbstractHubSession)

    long lastActivityMs();

    void touch(long now);

    Presence presence();

    void setPresence(Presence p);

    RateBucket requests();

    RateBucket chat();
}
