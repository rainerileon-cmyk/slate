package dev.fallingcloud.slate.multiplayer.hub;

import dev.fallingcloud.slate.multiplayer.MultiplayerServerConfig;
import dev.fallingcloud.slate.multiplayer.social.Presence;
import java.util.UUID;

/** The hub-side bookkeeping every session shares (presence, activity, rate buckets). */
abstract class AbstractHubSession implements HubSession {

    private final UUID uuid;
    private final String name;
    private long lastActivity = System.currentTimeMillis();
    private Presence presence = new Presence(Presence.State.MENU, "", "", "", System.currentTimeMillis());
    private final RateBucket requests;
    private final RateBucket chat;

    AbstractHubSession(final UUID uuid, final String name, final MultiplayerServerConfig cfg) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
        this.requests = new RateBucket(cfg.requestsPerSecond, cfg.requestsPerSecond * 3.0);
        this.chat = new RateBucket(cfg.messagesPerSecond, cfg.messagesPerSecond * 3.0);
    }

    @Override public UUID uuid() { return uuid; }

    @Override public String name() { return name; }

    @Override public long lastActivityMs() { return lastActivity; }

    @Override public void touch(final long now) { lastActivity = now; }

    @Override public Presence presence() { return presence; }

    @Override public void setPresence(final Presence p) { presence = p; }

    @Override public RateBucket requests() { return requests; }

    @Override public RateBucket chat() { return chat; }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + name + "/" + uuid + "]";
    }
}
