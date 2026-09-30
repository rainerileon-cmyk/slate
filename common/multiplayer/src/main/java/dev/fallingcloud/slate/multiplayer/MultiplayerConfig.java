package dev.fallingcloud.slate.multiplayer;

/**
 * {@code config/slate/multiplayer.json} (client). Public fields with defaults; missing keys keep them
 * (see {@code JsonConfig}).
 */
public final class MultiplayerConfig {

    /** The hub the client talks to from anywhere ({@code host:port}); empty = only the server you play on. */
    public String homeHub = "";
    /** Verify the Mojang session with the hub when it runs in online mode (turn off only for LAN test hubs). */
    public boolean hubAuth = true;

    // ---- presence
    /** Share the address of the server you are on (friends get a Join button). */
    public boolean shareServer = true;
    public boolean shareDimension = true;
    /** Appear offline to everyone. */
    public boolean invisible = false;
    /** Report yourself as away instead of online. */
    public boolean away = false;
    /** Custom status line shown under your name. */
    public String statusText = "";

    // ---- notifications (toasts)
    public boolean notifyFriendOnline = true;
    public boolean notifyRequests = true;
    public boolean notifyMessages = true;
    public boolean notifyInvites = true;
    public boolean notifyStreams = true;

    // ---- screen share
    /** Screen sharing (streams): the Streams tab of the friends screen, the stream buttons, the notifications. Off unless asked for. */
    public boolean streams = false;
    /** Longest edge of a shared frame in pixels. */
    public int streamMaxWidth = 640;
    /** JPEG quality 0.2-0.9. */
    public double streamQuality = 0.5;
    public int streamMinFps = 4;
    public int streamMaxFps = 12;
    /** Picture-in-picture viewer size and position (persisted when dragged). */
    public int pipWidth = 160;
    public int pipX = -1;
    public int pipY = -1;

    // ---- friends panel element defaults
    public boolean panelShowOffline = false;
    public int panelMaxRows = 8;

    // ---- voice
    public boolean voiceSpeakingRings = true;
    /** Seconds a voice clip may last. */
    public int voiceClipMaxSeconds = 60;

    /** Messages kept per thread in the offline cache. */
    public int cacheMessagesPerThread = 100;
    /** Largest image accepted for a DM attachment, in KB (the hub has its own cap). */
    public int imageMaxKb = 2800;

    /** {@code host:port} split; port defaults to 25580. Null when no hub is configured. */
    public String hubHost() {
        final String h = homeHub == null ? "" : homeHub.trim();
        if (h.isEmpty()) return null;
        final int colon = h.lastIndexOf(':');
        return colon > 0 && h.indexOf(':') == colon ? h.substring(0, colon) : h;
    }

    public int hubPort() {
        final String h = homeHub == null ? "" : homeHub.trim();
        final int colon = h.lastIndexOf(':');
        if (colon > 0 && h.indexOf(':') == colon) {
            try { return Integer.parseInt(h.substring(colon + 1).trim()); } catch (final NumberFormatException ignored) {}
        }
        return MultiplayerServerConfig.DEFAULT_PORT;
    }

    /** A file-name safe key for per-hub caches. */
    public String hubKey() {
        final String h = homeHub == null ? "" : homeHub.trim().toLowerCase(java.util.Locale.ROOT);
        return h.isEmpty() ? "" : h.replaceAll("[^a-z0-9.-]", "_");
    }
}
