package dev.fallingcloud.slate.multiplayer;

/**
 * {@code config/slate/multiplayer-server.json}: the social hub every server with the module runs.
 * The TCP listener is what lets clients reach this hub from the title screen and from other servers.
 */
public final class MultiplayerServerConfig {

    public static final int DEFAULT_PORT = 25580;

    /** Run the hub at all (friends, DMs, presence for players on this server). */
    public boolean enabled = true;
    /** Name shown to clients (empty = the server's MOTD or "Slate hub"). */
    public String hubName = "";

    // ---- TCP listener
    /** Accept hub connections on {@link #port} (dedicated servers). */
    public boolean listen = true;
    /** Also listen when this is a singleplayer/LAN integrated server. */
    public boolean listenOnIntegrated = false;
    public int port = DEFAULT_PORT;
    public String bindAddress = "0.0.0.0";
    /** Verify clients with the Mojang session server (false trusts the uuid a client claims: LAN/test only). */
    public boolean onlineMode = true;
    public int maxConnections = 200;
    /** Seconds without any frame before a TCP session is dropped (clients ping every 20 s). */
    public int idleTimeoutSeconds = 90;

    // ---- limits
    public int maxFriends = 300;
    public int maxPendingRequests = 50;
    public int maxBlocked = 200;
    public int maxGroupsPerPlayer = 30;
    public int maxGroupMembers = 50;
    /** Messages kept per thread on disk. */
    public int historyCap = 500;
    /** Messages a session may send per second (burst 3x). */
    public int messagesPerSecond = 4;
    /** Any messages a session may send per second (burst 3x). */
    public int requestsPerSecond = 20;

    // ---- media (DM/group attachments the hub stores so history can show them later)
    public boolean storeMedia = true;
    /** Largest attachment stored/relayed, in KB. */
    public int mediaMaxKb = 3072;
    /** Total media store size in MB; oldest files go first. */
    public int mediaStoreMb = 256;
    /** Largest screen-share frame relayed, in KB. */
    public int frameMaxKb = 400;
    /** Bytes per second one session may push through the hub's blob relay (KB). */
    public int blobKbPerSecond = 1536;

    // ---- Core blob relay (in-game channel) caps exposed here
    public int blobRelayMaxKb = 3072;
    public int blobRelayChunksPerSecond = 80;
    public int blobRelayConcurrent = 3;

    public String effectiveHubName(final String fallback) {
        final String n = hubName == null ? "" : hubName.trim();
        return n.isEmpty() ? (fallback == null || fallback.isBlank() ? "Slate hub" : fallback) : n;
    }
}
