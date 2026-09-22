package dev.fallingcloud.slate.multiplayer.social;

/**
 * One friend as seen by one player: the shared identity plus this player's private nickname and note,
 * the friend's current presence, and when the friendship started.
 */
public record FriendInfo(PlayerRef ref, String nick, String note, Presence presence, long sinceMs) {

    public FriendInfo {
        if (nick == null) nick = "";
        if (note == null) note = "";
        if (presence == null) presence = Presence.offline(0);
    }

    /** Nickname if set, else the real name. */
    public String display() {
        return nick.isEmpty() ? ref.display() : nick;
    }

    public FriendInfo withPresence(final Presence p) { return new FriendInfo(ref, nick, note, p, sinceMs); }

    public FriendInfo withNick(final String n, final String no) { return new FriendInfo(ref, n, no, presence, sinceMs); }
}
