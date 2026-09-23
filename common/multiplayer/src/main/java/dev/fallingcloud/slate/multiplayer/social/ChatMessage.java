package dev.fallingcloud.slate.multiplayer.social;

/**
 * One message in a DM or group thread. Media rides the blob channel: {@code attachKind} is the blob kind
 * ({@code image}, {@code gif}, {@code voice}; empty = text only), {@code attachId} the blob id and
 * {@code attachMeta} the blob's free-form meta (mime, name, duration...). Ids and timestamps are assigned
 * by the hub, never trusted from a client.
 */
public record ChatMessage(String id, PlayerRef from, String text, String attachKind, String attachId, String attachMeta, long atMs) {

    public static final int MAX_TEXT = 4000;

    // Names the Chat module reads reflectively.
    public String sender() { return from == null ? "" : from.name(); }

    public java.util.UUID senderId() { return from == null ? null : from.uuid(); }

    public long time() { return atMs; }

    public ChatMessage {
        if (id == null) id = "";
        if (from == null) from = new PlayerRef(PlayerRef.NIL, "");
        if (text == null) text = "";
        if (attachKind == null) attachKind = "";
        if (attachId == null) attachId = "";
        if (attachMeta == null) attachMeta = "";
    }

    public static ChatMessage text(final PlayerRef from, final String text) {
        return new ChatMessage("", from, text, "", "", "", 0L);
    }

    public boolean hasAttachment() { return !attachKind.isEmpty() && !attachId.isEmpty(); }

    public ChatMessage stamped(final String newId, final PlayerRef sender, final long at) {
        return new ChatMessage(newId, sender, text, attachKind, attachId, attachMeta, at);
    }
}
