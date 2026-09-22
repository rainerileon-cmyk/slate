package dev.fallingcloud.slate.multiplayer.social;

/** A live screen share: who, what it is called, how many watch, since when. Frames travel as {@code s:<id>} blobs. */
public record StreamInfo(String id, PlayerRef owner, String title, int viewers, long startedMs) {

    public StreamInfo {
        if (id == null) id = "";
        if (owner == null) owner = new PlayerRef(PlayerRef.NIL, "");
        if (title == null) title = "";
    }

    public StreamInfo withViewers(final int n) { return new StreamInfo(id, owner, title, n, startedMs); }

    public String target() { return "s:" + id; }
}
