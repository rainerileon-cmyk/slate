package dev.fallingcloud.slate.multiplayer.social;

/**
 * What a player is doing right now, as shared with friends. {@code server} is the address the player is
 * on ({@code "singleplayer"} for a local world, empty when hidden or in menus), {@code dimension} the
 * dimension path ({@code overworld}, {@code the_nether}...), {@code status} a free custom status line.
 * {@code sinceMs} is when this state began (wall clock, hub time); for OFFLINE it is "last seen".
 */
public record Presence(State state, String server, String dimension, String status, long sinceMs) {

    public enum State { OFFLINE, MENU, IN_GAME, AWAY }

    public static final String SINGLEPLAYER = "singleplayer";

    public Presence {
        if (state == null) state = State.OFFLINE;
        if (server == null) server = "";
        if (dimension == null) dimension = "";
        if (status == null) status = "";
    }

    public static Presence offline(final long lastSeenMs) {
        return new Presence(State.OFFLINE, "", "", "", lastSeenMs);
    }

    public boolean online() { return state != State.OFFLINE; }

    /** True when the player sits on a real multiplayer server a friend could join. */
    public boolean joinable() {
        return state == State.IN_GAME && !server.isEmpty() && !SINGLEPLAYER.equals(server);
    }

    public Presence withSince(final long ms) { return new Presence(state, server, dimension, status, ms); }

    public Presence withState(final State s) { return new Presence(s, server, dimension, status, sinceMs); }

    /** Same activity, ignoring the timestamp (used to decide whether to re-send). */
    public boolean sameActivity(final Presence o) {
        return o != null && state == o.state && server.equals(o.server) && dimension.equals(o.dimension) && status.equals(o.status);
    }
}
