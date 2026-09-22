package dev.fallingcloud.slate.multiplayer.client;

/** Where the client's social link is at. */
public enum LinkState {
    /** No link (no hub configured and not on a server with the module). */
    NONE,
    CONNECTING,
    /** TCP connected, verifying the Mojang session / waiting for Welcome. */
    AUTHENTICATING,
    CONNECTED,
    /** The last attempt failed; the reason is in {@code SocialClient.stateDetail()}, a retry is scheduled. */
    FAILED;

    public boolean connected() { return this == CONNECTED; }
}
