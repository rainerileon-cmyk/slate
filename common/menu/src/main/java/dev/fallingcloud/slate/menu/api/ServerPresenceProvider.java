package dev.fallingcloud.slate.menu.api;

/**
 * Plugged in by the Multiplayer module (or anything else) so the server list can show a
 * "N friends here" chip. Called on the render thread, once per frame per visible server, so keep it a
 * cache lookup.
 */
@FunctionalInterface
public interface ServerPresenceProvider {

    /** How many friends are currently on the server with this address (as typed in the server list). 0 = none/unknown. */
    int friendsOn(String address);
}
